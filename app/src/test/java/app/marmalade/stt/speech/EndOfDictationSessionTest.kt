package app.marmalade.stt.speech

import app.marmalade.stt.speech.turn.TurnDetector
import java.util.Collections
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The end-of-dictation modes (#43) through [StreamingSession] and
 * [OnlineSession] with the usual fakes: End word (strip, continue, empty,
 * separate segment, an engine without punctuation, never on partials) and
 * Smart Turn (complete at the first check, incomplete then complete, speech
 * resuming as one turn, the 2 s ceiling, the 28 s cap, no detector = Pause).
 *
 * [FakeVad] detects speech while its sample count is in one interval, so a
 * VAD segment closing inside that interval is followed at once by a new
 * onset (speech resumes), and one closing at its end by silence.
 */
class EndOfDictationSessionTest {

    /** Answers [answers] in call order, then the last for ever; records the audio length of each call. */
    private class FakeDetector(private val answers: List<Float>) : TurnDetector {
        val calls: MutableList<Int> = Collections.synchronizedList(mutableListOf())

        override fun completeProbability(audio: FloatArray): Float {
            calls += audio.size
            return answers[minOf(calls.size - 1, answers.lastIndex)]
        }
    }

    private val silentHop = FloatArray(HOP)

    private suspend fun runOffline(
        script: List<FloatArray>,
        transcriber: OfflineTranscriber,
        vad: SpeechVad,
        options: SessionOptions,
        clock: () -> Long = slowClock(),
    ): List<SttEvent> {
        lateinit var s: StreamingSession
        val mic = FakeMic(script, afterScript = { s.stop() })
        s = StreamingSession(transcriber, vad, options, openMic = { _, _ -> mic }, nowMs = clock)
        return s.run().toList().filterNot { it is SttEvent.Level }
    }

    private val endWord = SessionOptions(end = EndOfDictation.END_WORD)
    private fun smartTurn(detector: TurnDetector?, patience: Patience = Patience.NORMAL) =
        SessionOptions(end = EndOfDictation.SMART_TURN, turnDetector = detector, patience = patience)

    private fun List<SttEvent>.finals() = filterIsInstance<SttEvent.Final>()

    // ── End word, offline session ───────────────────────────────────────────

    @Test
    fun `End word - kept until the word, stripped, then listening goes on`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(2 * HOP, 4 * HOP, 6 * HOP))
        val transcriber = FakeTranscriber(listOf("the man walked over the bridge.", "Over.", "Next part.", "the end"))

        val events = runOffline(List(8) { loudHop }, transcriber, vad, endWord)

        assertEquals(
            listOf(
                SttEvent.Final("the man walked over the bridge.", nearSilent = false, endWord = true),
                SttEvent.Final("Next part. the end", nearSilent = false),
            ),
            events.finals(),
        )
        assertTrue(events.contains(SttEvent.Partial("the man walked over the bridge.")))
        assertTrue(events.contains(SttEvent.Partial("Next part.")))
        assertTrue(events.none { it is SttEvent.NoSpeech })
    }

    @Test
    fun `End word - the word inside a segment after a full stop ends it, keeping the sentence`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 2 * HOP, segmentsAt = listOf(2 * HOP))

        val events = runOffline(List(4) { loudHop }, FakeTranscriber(listOf("Game over. Over.")), vad, endWord)

        assertEquals(listOf(SttEvent.Final("Game over.", nearSilent = false, endWord = true)), events.finals())
        assertEquals(SttEvent.Final("Game over.", nearSilent = false, endWord = true), events.last())
    }

    @Test
    fun `End word - the word alone gives an empty end-word Final`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 2 * HOP, segmentsAt = listOf(2 * HOP))

        val events = runOffline(List(4) { loudHop }, FakeTranscriber(listOf("Over.")), vad, endWord)

        assertEquals(listOf(SttEvent.Final("", nearSilent = true, endWord = true, silent = true)), events.finals())
        assertTrue("an end word is a result, not no speech", events.none { it is SttEvent.NoSpeech })
    }

    @Test
    fun `End word - an engine without punctuation needs the word as its own segment`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 6 * HOP, segmentsAt = listOf(2 * HOP, 4 * HOP, 6 * HOP))
        val transcriber = FakeTranscriber(listOf("he fell over", "the man walked over the bridge", "over"))

        val events = runOffline(List(7) { loudHop }, transcriber, vad, endWord)

        assertEquals(
            listOf(SttEvent.Final("he fell over the man walked over the bridge", nearSilent = false, endWord = true)),
            events.finals(),
        )
    }

    @Test
    fun `End word - without the word the kept text comes at stop, not as an end word`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(2 * HOP))

        val events = runOffline(List(4) { loudHop }, FakeTranscriber(listOf("he fell over.", "the end")), vad, endWord)

        assertEquals(listOf(SttEvent.Final("he fell over. the end", nearSilent = false)), events.finals())
    }

    @Test
    fun `End word - partials never trigger it, only the segment's text`() = runTest(timeout = TEST_TIMEOUT) {
        // Every decode says "Over." and the clock allows a partial at each
        // hop, but no segment closes before stop: the only end-word Final is
        // the stop flush's.
        val transcriber = FakeTranscriber(listOf("Over."))

        val events = runOffline(List(6) { loudHop }, transcriber, FakeVad(speechFromSample = 1), endWord, clock = fastClock())

        assertTrue("partials were shown: $events", events.any { it is SttEvent.Partial })
        assertEquals(listOf(SttEvent.Final("", nearSilent = true, endWord = true, silent = true)), events.finals())
        assertEquals(events.last(), events.finals().single())
    }

    @Test
    fun `Tap stop keeps everything for one Final and Pause sends one per segment`() = runTest(timeout = TEST_TIMEOUT) {
        fun vad() = FakeVad(speechFromSample = 1, speechUntilSample = 4 * HOP, segmentsAt = listOf(2 * HOP, 4 * HOP))

        val tap = runOffline(List(5) { loudHop }, FakeTranscriber(listOf("one.", "Over.")), vad(), SessionOptions(end = EndOfDictation.TAP_STOP))
        assertEquals(listOf(SttEvent.Final("one. Over.", nearSilent = false)), tap.finals())

        val pause = runOffline(List(5) { loudHop }, FakeTranscriber(listOf("one.", "Over.")), vad(), SessionOptions())
        assertEquals(listOf(SttEvent.Final("one.", false), SttEvent.Final("Over.", false)), pause.finals())
    }

    // ── Smart Turn, offline session ─────────────────────────────────────────

    @Test
    fun `Smart Turn - complete at the first check decodes the turn once`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
        val detector = FakeDetector(listOf(0.9f))
        val transcriber = FakeTranscriber(listOf("Hello there."))

        val events = runOffline(List(4) { loudHop } + List(6) { silentHop }, transcriber, vad, smartTurn(detector))

        assertEquals(listOf(SttEvent.Final("Hello there.", nearSilent = false)), events.finals())
        assertEquals(1, detector.calls.size)
        // The detector saw the turn and the silence after the segment (it trails it).
        val segmentEnd = 10 * StreamingSession.VAD_WINDOW_SIZE // the first window at or past 3 × HOP
        assertTrue("${detector.calls}", detector.calls.single() > segmentEnd)
        // The Final decodes from the pre-roll (the mic's first sample here) to the segment's end.
        assertEquals(listOf(segmentEnd), transcriber.calls.map { it.size })
    }

    @Test
    fun `Smart Turn - incomplete, then complete on the recheck after half a second more silence`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
        val detector = FakeDetector(listOf(0.1f, 0.9f))
        val transcriber = FakeTranscriber(listOf("Let me think."))

        val events = runOffline(List(4) { loudHop } + List(20) { silentHop }, transcriber, vad, smartTurn(detector))

        assertEquals(listOf(SttEvent.Final("Let me think.", nearSilent = false)), events.finals())
        assertEquals(2, detector.calls.size)
        val grew = detector.calls[1] - detector.calls[0]
        assertTrue("recheck after ${grew} more samples", grew >= SmartTurnGate.RECHECK_SAMPLES && grew < SmartTurnGate.RECHECK_SAMPLES + HOP)
        assertEquals(1, transcriber.calls.size)
    }

    @Test
    fun `Smart Turn - speech after the pause continues the turn as one Final`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 6 * HOP, segmentsAt = listOf(3 * HOP, 6 * HOP))
        val detector = FakeDetector(listOf(0.1f, 0.9f))
        val transcriber = FakeTranscriber(listOf("I went to the shop and bought milk."))

        val events = runOffline(List(7) { loudHop } + List(6) { silentHop }, transcriber, vad, smartTurn(detector))

        assertEquals(listOf(SttEvent.Final("I went to the shop and bought milk.", nearSilent = false)), events.finals())
        assertEquals("a new SpeechStarted is fine", 2, events.count { it == SttEvent.SpeechStarted })
        // One decode, of the whole turn: both segments and the gap between them.
        assertEquals(1, transcriber.calls.size)
        assertTrue("${transcriber.calls.single().size}", transcriber.calls.single().size >= 6 * HOP)
    }

    @Test
    fun `Smart Turn - the ceiling ends the turn whatever the model says`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
        val detector = FakeDetector(listOf(0.1f))

        val all = run {
            lateinit var s: StreamingSession
            val mic = FakeMic(List(4) { loudHop } + List(40) { silentHop }, afterScript = { s.stop() })
            s = StreamingSession(FakeTranscriber(listOf("Hmm.")), vad, smartTurn(detector), openMic = { _, _ -> mic }, nowMs = slowClock())
            s.run().toList()
        }

        assertEquals(listOf(SttEvent.Final("Hmm.", nearSilent = false)), all.finals())
        // Checks at the pause and every 0.5 s after it (0.1, 0.6, 1.1, 1.6,
        // 2.1 s), then Normal's ceiling at 2.5 s, well before stop (4 s of
        // silence): had stop ended the turn, the checks would have gone on.
        assertEquals(5, detector.calls.size)
    }

    @Test
    fun `Smart Turn - the session's patience sets the threshold and the ceiling`() = runTest(timeout = TEST_TIMEOUT) {
        suspend fun checks(patience: Patience, p: Float): Int {
            val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
            val detector = FakeDetector(listOf(p))
            lateinit var s: StreamingSession
            val mic = FakeMic(List(4) { loudHop } + List(40) { silentHop }, afterScript = { s.stop() })
            s = StreamingSession(FakeTranscriber(listOf("Hmm.")), vad, smartTurn(detector, patience), openMic = { _, _ -> mic }, nowMs = slowClock())
            s.run().toList()
            return detector.calls.size
        }
        // Just above the threshold, every patience ends at the first check; at it, none does.
        for (patience in Patience.entries) {
            assertEquals(1, checks(patience, patience.threshold + 0.01f))
            assertTrue(checks(patience, patience.threshold) > 1)
        }
        // A model that never says finished: the longer the ceiling, the more checks before it.
        assertEquals(5, checks(Patience.NORMAL, 0f))
        assertTrue(checks(Patience.QUICK, 0f) < 5)
        assertTrue(checks(Patience.RELAXED, 0f) > 5)
    }

    @Test
    fun `Smart Turn - a turn reaching 28 s ends at its last segment and the rest is the next turn`() = runTest(timeout = TEST_TIMEOUT) {
        // Speech throughout, a segment every 5 s, and a model that never
        // calls the turn complete.
        val every5s = (1..6).map { it * 5 * MIC_SAMPLE_RATE }
        val vad = FakeVad(speechFromSample = 1, segmentsAt = every5s)
        val transcriber = FakeTranscriber(listOf("first turn", "second turn"))

        val events = runOffline(List(312) { loudHop }, transcriber, vad, smartTurn(FakeDetector(listOf(0.1f))))

        assertEquals(listOf(SttEvent.Final("first turn", false), SttEvent.Final("second turn", false)), events.finals())
        val first = transcriber.calls[0].size
        assertTrue("first turn is $first samples", first <= StreamingSession.MAX_TURN_SAMPLES && first > 25 * MIC_SAMPLE_RATE)
        // Nothing lost or decoded twice: the two turns cover the 31.2 s.
        val total = first + transcriber.calls[1].size
        assertTrue("$total of ${312 * HOP}", total in 312 * HOP - StreamingSession.VAD_WINDOW_SIZE..312 * HOP)
    }

    @Test
    fun `Smart Turn without a detector is Pause`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 6 * HOP, segmentsAt = listOf(3 * HOP, 6 * HOP))

        val events = runOffline(List(7) { loudHop }, FakeTranscriber(listOf("one", "two")), vad, smartTurn(null))

        assertEquals(listOf(SttEvent.Final("one", false), SttEvent.Final("two", false)), events.finals())
    }

    @Test
    fun `Smart Turn - stop ends a turn waiting for its verdict`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))

        val events = runOffline(List(4) { loudHop } + List(2) { silentHop }, FakeTranscriber(listOf("Wait")), vad, smartTurn(FakeDetector(listOf(0.1f))))

        assertEquals(listOf(SttEvent.Final("Wait", false)), events.finals())
        assertEquals(events.last(), events.finals().single())
    }

    // ── Online (true streaming) session ─────────────────────────────────────

    /** A stream whose text is [text], and only once input is finished. */
    private class ScriptedStream(private val text: String) : RecognizerStream {
        var finished = false
        var released = false
        override fun acceptWaveform(samples: FloatArray) = check(!finished && !released)
        override fun decode() = Unit
        override fun text(): String = if (finished) text else ""
        override fun inputFinished() {
            finished = true
        }

        override fun release() {
            released = true
        }
    }

    private class ScriptedRecognizer(private val texts: List<String>) : StreamingRecognizer {
        val streams: MutableList<ScriptedStream> = Collections.synchronizedList(mutableListOf())
        override fun createStream(): RecognizerStream = ScriptedStream(texts[minOf(streams.size, texts.lastIndex)]).also { streams += it }
        override fun release() = Unit
    }

    private suspend fun runOnline(script: List<FloatArray>, recognizer: StreamingRecognizer, vad: SpeechVad, options: SessionOptions): List<SttEvent> {
        lateinit var s: OnlineSession
        val mic = FakeMic(script, afterScript = { s.stop() })
        s = OnlineSession(recognizer, vad, options) { _, _ -> mic }
        return s.run().toList().filterNot { it is SttEvent.Level }
    }

    @Test
    fun `online Smart Turn - the stream stays open across the turn's segments`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 6 * HOP, segmentsAt = listOf(3 * HOP, 6 * HOP))
        val recognizer = ScriptedRecognizer(listOf("I went to the shop and bought milk."))
        val detector = FakeDetector(listOf(0.1f, 0.9f))

        val events = runOnline(List(7) { loudHop } + List(4) { silentHop }, recognizer, vad, smartTurn(detector))

        assertEquals(listOf(SttEvent.Final("I went to the shop and bought milk.", false)), events.finals())
        assertEquals(1, recognizer.streams.size)
        assertTrue(recognizer.streams.single().finished && recognizer.streams.single().released)
        assertEquals(2, detector.calls.size)
    }

    @Test
    fun `online Smart Turn - a complete turn frees its stream and the next speech opens another`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 6 * HOP, segmentsAt = listOf(3 * HOP, 6 * HOP))
        val recognizer = ScriptedRecognizer(listOf("One.", "Two."))

        val events = runOnline(List(7) { loudHop }, recognizer, vad, smartTurn(FakeDetector(listOf(0.9f))))

        assertEquals(listOf(SttEvent.Final("One.", false), SttEvent.Final("Two.", false)), events.finals())
        assertEquals(2, recognizer.streams.size)
    }

    @Test
    fun `online Smart Turn - the ceiling ends a turn the model calls incomplete`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))
        val detector = FakeDetector(listOf(0.1f))
        lateinit var s: OnlineSession
        val mic = FakeMic(List(4) { loudHop } + List(40) { silentHop }, afterScript = { s.stop() })
        s = OnlineSession(ScriptedRecognizer(listOf("Hmm.")), vad, smartTurn(detector)) { _, _ -> mic }

        val all = s.run().toList()

        assertEquals(listOf(SttEvent.Final("Hmm.", false)), all.finals())
        assertEquals("the ceiling, not stop, ended it", 5, detector.calls.size)
    }

    @Test
    fun `online Smart Turn without a detector is Pause`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 6 * HOP, segmentsAt = listOf(3 * HOP, 6 * HOP))
        val recognizer = ScriptedRecognizer(listOf("One.", "Two."))

        val events = runOnline(List(7) { loudHop }, recognizer, vad, smartTurn(null))

        assertEquals(listOf(SttEvent.Final("One.", false), SttEvent.Final("Two.", false)), events.finals())
    }

    @Test
    fun `online End word - a separate segment with the word ends it`() = runTest(timeout = TEST_TIMEOUT) {
        // Nemotron writes no full stop before "over" when it is its own utterance.
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 6 * HOP, segmentsAt = listOf(2 * HOP, 4 * HOP, 6 * HOP))
        val recognizer = ScriptedRecognizer(listOf("walked over the bridge", "over", "more"))

        val events = runOnline(List(7) { loudHop }, recognizer, vad, endWord)

        assertEquals(
            listOf(
                SttEvent.Final("walked over the bridge", false, endWord = true),
                SttEvent.Final("more", false),
            ),
            events.finals(),
        )
        assertTrue(events.contains(SttEvent.Partial("walked over the bridge")))
    }
}
