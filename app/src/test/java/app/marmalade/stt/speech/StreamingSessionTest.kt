package app.marmalade.stt.speech

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamingSession] end to end through fakes ([FakeMic], [FakeVad],
 * [FakeTranscriber]): no AudioRecord, no sherpa natives. The fakes are pure
 * functions of the samples the session feeds them, and the partial cadence
 * runs on an injected clock, so the event sequences are deterministic.
 * Ported from the Marmalade client's WhisperEngineTest, plus the events the
 * SttEvent contract added.
 */
class StreamingSessionTest {

    private fun session(
        transcriber: OfflineTranscriber,
        vad: SpeechVad,
        mic: MicCapture,
        endpointing: Boolean = true,
        clock: () -> Long = slowClock(),
    ) = StreamingSession(
        transcriber = transcriber,
        vad = vad,
        options = SessionOptions(end = if (endpointing) EndOfDictation.PAUSE else EndOfDictation.TAP_STOP),
        openMic = { hop, hardwareEffects ->
            assertEquals(HOP, hop)
            assertTrue("STT wants the platform NS/AGC", hardwareEffects)
            mic
        },
        nowMs = clock,
    )

    /** Runs a session whose mic plays [script] and then calls stop(). */
    private suspend fun runScripted(
        script: List<FloatArray>,
        transcriber: OfflineTranscriber,
        vad: SpeechVad,
        endpointing: Boolean = true,
        clock: () -> Long = slowClock(),
    ): Pair<List<SttEvent>, FakeMic> {
        lateinit var s: StreamingSession
        val mic = FakeMic(script, afterScript = { s.stop() })
        s = session(transcriber, vad, mic, endpointing, clock)
        return s.run().toList() to mic
    }

    private fun List<SttEvent>.withoutLevels() = filterNot { it is SttEvent.Level }

    // ── endpoint mode (ported) ───────────────────────────────────────────────

    @Test
    fun `endpoint mode - VAD segment completion emits Final from the segment audio`() = runTest(timeout = TEST_TIMEOUT) {
        // Speech ends exactly when the segment completes: no second onset.
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
        val transcriber = FakeTranscriber(listOf("hello world"))

        val (events, mic) = runScripted(List(4) { loudHop }, transcriber, vad)

        assertEquals(SttEvent.Ready, events.first())
        assertEquals(SttEvent.Final("hello world", nearSilent = false), events.withoutLevels().last())
        assertEquals(1, vad.resetCalls)
        assertTrue("mic must be closed when the flow ends", mic.closed)
        // The fake VAD echoes what it accepted, so the decoded audio is the
        // segment, not the rolling buffer.
        assertEquals(1, transcriber.calls.size)
        assertTrue(transcriber.calls.single().size >= 3 * HOP)
    }

    @Test
    fun `endpoint mode - blank segment text yields only NoSpeech`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 2 * HOP, segmentsAt = listOf(2 * HOP))

        val (events, _) = runScripted(List(3) { loudHop }, FakeTranscriber(listOf("   ")), vad)

        assertEquals(
            listOf(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.NoSpeech),
            events.withoutLevels(),
        )
    }

    @Test
    fun `a productive endpoint session does not also report NoSpeech`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 2 * HOP, segmentsAt = listOf(2 * HOP))

        val (events, _) = runScripted(List(3) { loudHop }, FakeTranscriber(listOf("hello world")), vad)

        assertEquals(SttEvent.Final("hello world", nearSilent = false), events.withoutLevels().last())
        assertTrue(events.none { it is SttEvent.NoSpeech })
    }

    // ── tap stop (ported patient mode) ──────────────────────────────────────────

    @Test
    fun `tap stop - segment becomes a partial, stop joins it with the flushed tail`() = runTest(timeout = TEST_TIMEOUT) {
        // One segment mid-stream; speech goes on after it, so the stop flush
        // must transcribe the tail and join it to the kept utterance.
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(2 * HOP))
        val transcriber = FakeTranscriber(listOf("first segment", "tail text"))

        val (events, _) = runScripted(List(4) { loudHop }, transcriber, vad, endpointing = false)

        assertEquals(SttEvent.Ready, events.first())
        assertTrue(events.contains(SttEvent.Partial("first segment")))
        assertEquals(SttEvent.Final("first segment tail text", nearSilent = false), events.withoutLevels().last())
    }

    @Test
    fun `stop mid-speech flushes the audio in progress as Final`() = runTest(timeout = TEST_TIMEOUT) {
        // Speech never ends, so only the stop flush can produce text.
        val transcriber = FakeTranscriber(listOf("flushed tail"))

        val (events, _) = runScripted(List(3) { loudHop }, transcriber, FakeVad(speechFromSample = 1), endpointing = false)

        assertEquals(SttEvent.Final("flushed tail", nearSilent = false), events.withoutLevels().last())
        assertEquals(1, transcriber.calls.size)
    }

    @Test
    fun `silence then stop ends with NoSpeech so the caller knows the session is over`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("never"))

        val (events, _) = runScripted(emptyList(), transcriber, FakeVad(speechFromSample = Int.MAX_VALUE), endpointing = false)

        assertEquals(listOf(SttEvent.Ready, SttEvent.NoSpeech), events)
        assertEquals(0, transcriber.calls.size)
    }

    // ── partials (ported cadence + new ordering/stripping) ───────────────────

    @Test
    fun `partials are emitted during speech once the interval elapses`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, _) = runScripted(
            List(2) { loudHop }, FakeTranscriber(listOf("partial text")), FakeVad(speechFromSample = 1),
            clock = fastClock(),
        )

        assertTrue(events.contains(SttEvent.Partial("partial text")))
    }

    @Test
    fun `SpeechStarted comes after Ready and before the first Partial`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, _) = runScripted(
            List(3) { loudHop }, FakeTranscriber(listOf("words")), FakeVad(speechFromSample = 1),
            clock = fastClock(),
        )

        val started = events.indexOf(SttEvent.SpeechStarted)
        val firstPartial = events.indexOfFirst { it is SttEvent.Partial }
        assertTrue("no SpeechStarted in $events", started > events.indexOf(SttEvent.Ready))
        assertTrue("no Partial in $events", firstPartial >= 0)
        assertTrue(started < firstPartial)
    }

    @Test
    fun `partials drop trailing punctuation, the final keeps it`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))

        val (events, _) = runScripted(
            List(4) { loudHop }, FakeTranscriber(listOf(" Hello world.")), vad, clock = fastClock(),
        )

        val partials = events.filterIsInstance<SttEvent.Partial>()
        assertTrue("no partials in $events", partials.isNotEmpty())
        assertTrue(partials.all { it.text == "Hello world" })
        assertEquals(SttEvent.Final("Hello world.", nearSilent = false), events.withoutLevels().last())
    }

    @Test
    fun `tap stop strips only the utterance in progress`() = runTest(timeout = TEST_TIMEOUT) {
        // Quiet audio decodes as "One.", loud audio as "Two.". The segment
        // (7 VAD windows) is all quiet; the tail after it is mostly loud.
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(7 * StreamingSession.VAD_WINDOW_SIZE))
        val transcriber = FakeTranscriber { samples ->
            if (samples.maxOf { kotlin.math.abs(it) } > 0.1f) " Two." else " One."
        }

        val (events, _) = runScripted(
            List(3) { quietHop } + List(2) { loudHop }, transcriber, vad,
            endpointing = false, clock = fastClock(),
        )

        val partials = events.filterIsInstance<SttEvent.Partial>().map { it.text }
        assertTrue("utterance in progress is stripped: $partials", "One" in partials)
        assertTrue("finished utterance keeps its full stop: $partials", "One." in partials)
        assertTrue("only the new tail is stripped: $partials", "One. Two" in partials)
        assertEquals(SttEvent.Final("One. Two.", nearSilent = false), events.withoutLevels().last())
    }

    // ── levels ───────────────────────────────────────────────────────────────

    @Test
    fun `one Level per mic hop, in dBFS of the filtered hop`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, _) = runScripted(List(3) { loudHop }, FakeTranscriber(listOf("x")), FakeVad(Int.MAX_VALUE))

        val levels = events.filterIsInstance<SttEvent.Level>()
        assertEquals(3, levels.size)
        // A 0.5-amplitude square wave is 20·log10(0.5) = -6.02 dBFS.
        for (level in levels) assertEquals(-6.02f, level.rmsDb, 0.3f)
        assertEquals(SttEvent.Ready, events.first())
    }

    @Test
    fun `digital silence reports the floor level`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, _) = runScripted(List(2) { FloatArray(HOP) }, FakeTranscriber(listOf("x")), FakeVad(Int.MAX_VALUE))

        assertEquals(
            listOf(SttEvent.Level(NearSilence.FLOOR_DB), SttEvent.Level(NearSilence.FLOOR_DB)),
            events.filterIsInstance<SttEvent.Level>(),
        )
    }

    // ── nearSilent ───────────────────────────────────────────────────────────

    private suspend fun endpointFinal(hop: FloatArray): SttEvent {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
        return runScripted(List(4) { hop }, FakeTranscriber(listOf("Thank you.")), vad).first.last()
    }

    @Test
    fun `a quiet utterance is nearSilent, a loud one is not`() = runTest(timeout = TEST_TIMEOUT) {
        assertEquals(SttEvent.Final("Thank you.", nearSilent = true, silent = true), endpointFinal(quietHop))
        assertEquals(SttEvent.Final("Thank you.", nearSilent = false), endpointFinal(loudHop))
    }

    @Test
    fun `soft speech is nearSilent but not silent`() = runTest(timeout = TEST_TIMEOUT) {
        // -55 dBFS: between the near-silence gate (-50) and the silent one (-58) (#57).
        assertEquals(SttEvent.Final("Thank you.", nearSilent = true, silent = false), endpointFinal(softHop))
    }

    @Test
    fun `nearSilent is judged before SoftAgc's gain`() = runTest(timeout = TEST_TIMEOUT) {
        // No platform AGC (the Pixel 8a): SoftAgc lifts a -60 dBFS hop to
        // -30 for the VAD and the model, but the mic delivered -60 (#20).
        suspend fun final(hop: FloatArray): SttEvent {
            val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
            lateinit var s: StreamingSession
            val mic = FakeMic(List(4) { hop }, afterScript = { s.stop() }, hasPlatformAgc = false)
            s = session(FakeTranscriber(listOf("Thank you.")), vad, mic)
            return s.run().toList().last()
        }

        assertEquals(SttEvent.Final("Thank you.", nearSilent = true, silent = true), final(quietHop))
        assertEquals(SttEvent.Final("Thank you.", nearSilent = false), final(loudHop))
    }

    @Test
    fun `tap stop Final is nearSilent only if every joined utterance was`() = runTest(timeout = TEST_TIMEOUT) {
        val segmentAt = listOf(7 * StreamingSession.VAD_WINDOW_SIZE)

        val (mixed, _) = runScripted(
            List(3) { quietHop } + List(2) { loudHop }, FakeTranscriber(listOf("one", "two")),
            FakeVad(speechFromSample = 1, segmentsAt = segmentAt), endpointing = false,
        )
        assertEquals(SttEvent.Final("one two", nearSilent = false), mixed.last())

        val (allQuiet, _) = runScripted(
            List(5) { quietHop }, FakeTranscriber(listOf("one", "two")),
            FakeVad(speechFromSample = 1, segmentsAt = segmentAt), endpointing = false,
        )
        assertEquals(SttEvent.Final("one two", nearSilent = true, silent = true), allQuiet.last())
    }

    @Test
    fun `tap stop - a quiet utterance after a loud one does not make the Final nearSilent`() = runTest(timeout = TEST_TIMEOUT) {
        // 8 hops = 25 VAD windows exactly, so each segment is purely loud or
        // purely quiet. Speech ends with the second segment: no stop flush.
        val window = StreamingSession.VAD_WINDOW_SIZE
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 50 * window, segmentsAt = listOf(25 * window, 50 * window))
        val transcriber = FakeTranscriber { samples -> if (samples.maxOf { kotlin.math.abs(it) } > 0.1f) "loud" else "quiet" }

        val (events, _) = runScripted(
            List(8) { loudHop } + List(9) { quietHop }, transcriber, vad, endpointing = false,
        )

        assertEquals(SttEvent.Final("loud quiet", nearSilent = false), events.withoutLevels().last())
    }

    // ── buffer ───────────────────────────────────────────────────────────────

    @Test
    fun `after long silence the utterance still starts exactly the pre-roll before onset`() = runTest(timeout = TEST_TIMEOUT) {
        // Onset after 2 s of silence. The session trims silent audio it no
        // longer needs; the pre-roll must survive that. The fake VAD detects
        // speech at the first window reaching 32000 samples (63 × 512 = 32256)
        // and, like Silero, starts its segment about 0.3 s earlier (53 × 512);
        // at stop the VAD has been fed up to 39936 of the 40000 samples.
        val transcriber = FakeTranscriber(listOf("late words"))
        val vad = FakeVad(speechFromSample = 20 * HOP, segmentFrom = 20 * HOP - 5_024)

        runScripted(List(25) { quietHop }, transcriber, vad, endpointing = false)

        val tail = transcriber.calls.single()
        assertEquals(39_936 - (32_256 - StreamingSession.PREROLL_SAMPLES), tail.size)
    }

    // ── the first word at mic open (#15) ─────────────────────────────────────

    /** 100 ms of the first word: quieter than [loudHop], so the transcriber can tell them apart. */
    private val wordHop = squareHop(0.25f)

    /** The first word, then the rest of the utterance. */
    private val wordThenSpeech = List(2) { wordHop } + List(4) { loudHop }

    /** "Call" if the audio holds any of [wordHop], then "mom" if it holds any of [loudHop]. */
    private fun wordTranscriber() = FakeTranscriber { samples ->
        listOfNotNull(
            "Call".takeIf { samples.any { kotlin.math.abs(it) in 0.2f..0.3f } },
            "mom".takeIf { samples.any { kotlin.math.abs(it) > 0.4f } },
        ).joinToString(" ")
    }

    /**
     * Silero at mic open: it detects speech 0.3 s in and starts its segment
     * after the first word (at the first window from 0.2 s, 7 × 512).
     */
    private fun lateVad(segmentsAt: List<Int> = emptyList(), speechUntilSample: Int = Int.MAX_VALUE) =
        FakeVad(3 * HOP, speechUntilSample, segmentsAt, segmentFrom = 2 * HOP)

    @Test
    fun `endpoint mode - a word spoken as the mic opens survives a late VAD onset`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = wordTranscriber()

        val (events, _) = runScripted(
            wordThenSpeech + List(4) { quietHop }, transcriber, lateVad(listOf(8 * HOP), speechUntilSample = 6 * HOP),
        )

        assertEquals(SttEvent.Final("Call mom", nearSilent = false), events.withoutLevels().last())
        // Decoded: the pre-roll from the mic's first sample, then the segment to its end.
        assertEquals(8 * HOP, transcriber.calls.single().size)
    }

    @Test
    fun `tap stop - a word spoken as the mic opens survives a late VAD onset`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, _) = runScripted(
            wordThenSpeech + List(4) { quietHop }, wordTranscriber(), lateVad(listOf(8 * HOP), speechUntilSample = 6 * HOP),
            endpointing = false,
        )

        assertTrue(events.contains(SttEvent.Partial("Call mom")))
        assertEquals(SttEvent.Final("Call mom", nearSilent = false), events.withoutLevels().last())
    }

    @Test
    fun `stop mid-speech keeps a word spoken as the mic opened, in both modes`() = runTest(timeout = TEST_TIMEOUT) {
        for (endpointing in listOf(true, false)) {
            val (events, _) = runScripted(wordThenSpeech, wordTranscriber(), lateVad(), endpointing = endpointing)

            assertEquals("endpointing=$endpointing", SttEvent.Final("Call mom", nearSilent = false), events.withoutLevels().last())
        }
    }

    @Test
    fun `the pre-roll never reaches back into the previous utterance`() = runTest(timeout = TEST_TIMEOUT) {
        // A loud utterance ends in a segment (8 hops = 25 windows exactly);
        // the next one, cut short by stop, is only the word.
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(8 * HOP))

        val (events, _) = runScripted(List(8) { loudHop } + List(4) { wordHop }, wordTranscriber(), vad)

        assertEquals(
            listOf(SttEvent.Final("mom", false), SttEvent.Final("Call", false)),
            events.filterIsInstance<SttEvent.Final>(),
        )
    }

    @Test
    fun `nearSilent comes from the VAD segment, not the quiet pre-roll`() = runTest(timeout = TEST_TIMEOUT) {
        // 2 s of near-silence before a loud segment: over the whole decoded
        // audio the RMS would be lower, but the judgement uses the segment.
        val vad = FakeVad(speechFromSample = 20 * HOP, speechUntilSample = 24 * HOP, segmentsAt = listOf(24 * HOP), segmentFrom = 20 * HOP)

        val (events, _) = runScripted(List(20) { quietHop } + List(5) { loudHop }, FakeTranscriber(listOf("hi")), vad)

        assertEquals(SttEvent.Final("hi", nearSilent = false), events.withoutLevels().last())
    }

    // ── failures and cancellation ────────────────────────────────────────────

    @Test
    fun `PreGainAudio measures by VAD sample index and forgets what is dropped`() {
        val audio = PreGainAudio()
        audio.append(squareHop(0.1f)) // -20 dBFS
        audio.append(squareHop(0.01f)) // -40 dBFS
        audio.append(squareHop(0.001f)) // -60 dBFS
        assertEquals(-40f, audio.rmsDb(HOP, HOP), 0.01f)

        audio.dropBefore(2 * HOP)
        assertEquals(-60f, audio.rmsDb(0, 3 * HOP), 0.01f) // clipped to what is held
        audio.append(squareHop(0.1f))
        assertEquals(-20f, audio.rmsDb(3 * HOP, HOP), 0.01f)
    }

    @Test
    fun `quietestWindow finds the gap between words`() {
        val buffer = SampleBuffer()
        buffer.append(FloatArray(10_000) { i -> if (i in 5_000 until 6_000) 0f else if (i % 2 == 0) 0.5f else -0.5f })

        val cut = quietestWindow(buffer, 0, 10_000)
        assertTrue("cut at $cut", cut >= 5_000 && cut + StreamingSession.VAD_WINDOW_SIZE <= 6_000)
        assertEquals(300, quietestWindow(buffer, 0, 300)) // no window fits
    }

    @Test
    fun `mic open failure emits Error MIC and ends`() = runTest(timeout = TEST_TIMEOUT) {
        val s = StreamingSession(
            FakeTranscriber(listOf("unused")), FakeVad(1), options = SessionOptions(),
            openMic = { _, _ -> throw MicCaptureException("no mic") },
        )

        assertEquals(listOf(SttEvent.Error(SttErrorKind.MIC, "no mic")), s.run().toList())
    }

    @Test
    fun `mic failing mid-session ends with Error MIC and closes the mic`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = FakeMic(List(2) { loudHop }, failAfterScript = MicCaptureException("read failed"))
        val s = session(FakeTranscriber(listOf("x")), FakeVad(speechFromSample = 1), mic)

        val events = s.run().toList()

        assertEquals(SttEvent.Error(SttErrorKind.MIC, "read failed"), events.last())
        assertTrue(events.none { it is SttEvent.Final || it is SttEvent.NoSpeech })
        assertTrue(mic.closed)
    }

    @Test
    fun `a decode failure ends with Error RECOGNITION and closes the mic`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 2 * HOP, segmentsAt = listOf(2 * HOP))
        val mic = FakeMic(List(3) { loudHop })
        val s = session(FakeTranscriber { error("decoder exploded") }, vad, mic)

        val events = s.run().toList()

        assertEquals(SttEvent.Error(SttErrorKind.RECOGNITION, "decoder exploded"), events.last())
        assertTrue(mic.closed)
    }

    @Test
    fun `cancelling the collector emits no Error and closes the mic`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = FakeMic() // live mic: silent hops until closed
        val s = session(FakeTranscriber(listOf("x")), FakeVad(Int.MAX_VALUE), mic)
        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val listening = CompletableDeferred<Unit>()

        val job = launch {
            s.run().collect {
                events += it
                if (it is SttEvent.Level) listening.complete(Unit)
            }
        }
        listening.await()
        job.cancelAndJoin()

        assertTrue("mic must be closed once the collector is gone", mic.closed)
        assertTrue(events.none { it is SttEvent.Error })
        assertFalse(events.any { it is SttEvent.NoSpeech || it is SttEvent.Final })
    }

    @Test
    fun `stop before collection ends the session right after Ready`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = FakeMic()
        val s = session(FakeTranscriber(listOf("x")), FakeVad(1), mic)
        s.stop()

        assertEquals(listOf(SttEvent.Ready, SttEvent.NoSpeech), s.run().toList())
        assertTrue(mic.closed)
    }

    @Test
    fun `without live partials only the Final carries text`() = runTest(timeout = TEST_TIMEOUT) {
        // A slow model (Qwen3-ASR, Whisper turbo): the clock crosses the
        // partial gate at every hop, yet nothing is re-decoded mid-utterance.
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
        val transcriber = FakeTranscriber(listOf("only the final"))
        lateinit var s: StreamingSession
        val mic = FakeMic(List(4) { loudHop }, afterScript = { s.stop() })
        s = StreamingSession(transcriber, vad, options = SessionOptions(), openMic = { _, _ -> mic }, livePartials = false, nowMs = fastClock())

        val events = s.run().toList()

        assertTrue(events.none { it is SttEvent.Partial })
        assertEquals(SttEvent.Final("only the final", nearSilent = false), events.withoutLevels().last())
        assertEquals(1, transcriber.calls.size)
    }
}
