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
 * [OnlineSession]'s state machine through fakes: the mic ([FakeMic]), the
 * VAD ([FakeVad]) and a streaming recognizer whose text is a pure function of
 * the samples fed to each stream. No AudioRecord, no sherpa natives.
 */
class OnlineSessionTest {

    /**
     * One stream's text is "w1 w2 … wN", one word per full [HOP] of audio
     * fed, plus [finalWord] once input is finished (the model's lookahead
     * flushing the last word).
     */
    private class FakeStream(private val finalWord: String?) : RecognizerStream {
        val chunks = mutableListOf<FloatArray>()
        var fed = 0
        var finished = false
        var released = false
        var decodes = 0

        override fun acceptWaveform(samples: FloatArray) {
            check(!finished) { "audio after inputFinished" }
            check(!released) { "stream used after release" }
            chunks += samples.copyOf()
            fed += samples.size
        }

        override fun decode() {
            decodes++
        }

        override fun text(): String {
            val words = (1..fed / HOP).map { "w$it" } + listOfNotNull(finalWord.takeIf { finished })
            return " " + words.joinToString(" ") // sherpa-style leading space
        }

        override fun inputFinished() {
            finished = true
        }

        override fun release() {
            released = true
        }
    }

    private class FakeRecognizer(private val finalWords: List<String?> = listOf("end")) : StreamingRecognizer {
        val streams: MutableList<FakeStream> = Collections.synchronizedList(mutableListOf())
        var releaseCalls = 0

        override fun createStream(): RecognizerStream =
            FakeStream(finalWords[minOf(streams.size, finalWords.lastIndex)]).also { streams += it }

        override fun release() {
            releaseCalls++
        }
    }

    private suspend fun runScripted(
        script: List<FloatArray>,
        recognizer: FakeRecognizer,
        vad: FakeVad,
        endpointing: Boolean = true,
    ): Pair<List<SttEvent>, FakeMic> {
        lateinit var s: OnlineSession
        val mic = FakeMic(script, afterScript = { s.stop() })
        s = OnlineSession(recognizer, vad, SessionOptions(end = if (endpointing) EndOfDictation.PAUSE else EndOfDictation.TAP_STOP)) { hop, hardwareEffects ->
            assertEquals(HOP, hop)
            assertTrue(hardwareEffects)
            mic
        }
        return s.run().toList() to mic
    }

    private fun List<SttEvent>.withoutLevels() = filterNot { it is SttEvent.Level }

    @Test
    fun `partials come from the model as audio arrives, the segment end flushes a Final`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 4 * HOP, segmentsAt = listOf(4 * HOP))
        val recognizer = FakeRecognizer()

        val (events, mic) = runScripted(List(5) { loudHop }, recognizer, vad)

        val shown = events.withoutLevels()
        assertEquals(SttEvent.Ready, shown.first())
        assertEquals(SttEvent.SpeechStarted, shown[1])
        val partials = shown.filterIsInstance<SttEvent.Partial>().map { it.text }
        assertTrue("partials grow with the audio: $partials", partials.size >= 2)
        partials.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", b.startsWith(a)) }
        // The last word only appears after inputFinished: it is in the Final, not a partial.
        assertTrue(partials.none { "end" in it })
        val final = shown.last() as SttEvent.Final
        assertTrue(final.text, final.text.endsWith("end"))
        assertFalse(final.nearSilent)
        assertEquals(1, vad.resetCalls)
        assertTrue(mic.closed)
        assertEquals(1, recognizer.streams.size)
        assertTrue(recognizer.streams.single().finished)
        assertTrue(recognizer.streams.single().released)
        assertEquals("the session doesn't own the recognizer", 0, recognizer.releaseCalls)
    }

    @Test
    fun `no audio reaches the model before speech starts`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = Int.MAX_VALUE)
        val recognizer = FakeRecognizer()

        val (events, _) = runScripted(List(10) { quietHop }, recognizer, vad)

        assertEquals(listOf(SttEvent.Ready, SttEvent.NoSpeech), events.withoutLevels())
        assertTrue(recognizer.streams.isEmpty())
    }

    @Test
    fun `a new stream starts with the pre-roll before the onset`() = runTest(timeout = TEST_TIMEOUT) {
        // Silence for 2 s (longer than the pre-roll, so the idle buffer is
        // trimmed), then the VAD hears speech.
        val onset = 20 * HOP
        val vad = FakeVad(speechFromSample = onset)
        val recognizer = FakeRecognizer()

        runScripted(List(20) { quietHop } + List(2) { loudHop }, recognizer, vad)

        val stream = recognizer.streams.single()
        assertTrue("real audio covers the pre-roll: no silence padding", stream.chunks.first().any { it != 0f })
        // Everything from 1 s before the onset window onwards was fed.
        assertTrue("fed ${stream.fed}", stream.fed >= OnlineSession.PREROLL_SAMPLES)
        assertTrue("fed ${stream.fed}, no older audio", stream.fed <= OnlineSession.PREROLL_SAMPLES + 3 * HOP)
    }

    @Test
    fun `a word spoken as the mic opens reaches the stream though the VAD notices it 0_9 s later`() = runTest(timeout = TEST_TIMEOUT) {
        // The fake VAD detects speech at the first window reaching 14400 samples (29 × 512).
        val vad = FakeVad(speechFromSample = 9 * HOP)
        val recognizer = FakeRecognizer()

        runScripted(List(2) { squareHop(0.25f) } + List(10) { loudHop }, recognizer, vad)

        val stream = recognizer.streams.single()
        // Only 14848 samples were recorded before the onset: silence makes up the 1 s pre-roll...
        val padding = stream.chunks.first()
        assertEquals(OnlineSession.PREROLL_SAMPLES - 29 * StreamingSession.VAD_WINDOW_SIZE, padding.size)
        assertTrue(padding.all { it == 0f })
        // ...then the stream got the audio from the mic's first sample.
        assertEquals(12 * HOP, stream.fed - padding.size)
        assertTrue(stream.chunks[1].first() != 0f)
    }

    @Test
    fun `each utterance gets its own stream and its own Final`() = runTest(timeout = TEST_TIMEOUT) {
        // Speech in two bursts; each ends in a VAD segment.
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(2 * HOP, 5 * HOP))
        val recognizer = FakeRecognizer(listOf("one", "two"))

        val (events, _) = runScripted(List(6) { loudHop }, recognizer, vad)

        val finals = events.filterIsInstance<SttEvent.Final>().map { it.text }
        assertTrue(finals.toString(), finals[0].endsWith("one"))
        assertTrue(finals.toString(), finals[1].endsWith("two"))
        assertTrue(recognizer.streams.size >= 2)
        assertTrue(recognizer.streams.all { it.released })
    }

    @Test
    fun `tap stop joins utterances and stop flushes the one in progress`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(2 * HOP))
        val recognizer = FakeRecognizer(listOf("one", "tail"))

        val (events, _) = runScripted(List(4) { loudHop }, recognizer, vad, endpointing = false)

        val finals = events.filterIsInstance<SttEvent.Final>()
        assertEquals(1, finals.size)
        assertTrue(finals.single().text, finals.single().text.contains("one"))
        assertTrue(finals.single().text, finals.single().text.endsWith("tail"))
        assertEquals(finals.single(), events.last())
        // Partials in tap stop mode carry the kept utterance too.
        val last = events.filterIsInstance<SttEvent.Partial>().last().text
        assertTrue(last, last.contains("one"))
        assertTrue(recognizer.streams.all { it.finished && it.released })
    }

    @Test
    fun `stop mid-utterance judges near-silence from the flushed VAD segment`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1)

        val (quietEvents, _) = runScripted(List(4) { quietHop }, FakeRecognizer(), vad)
        val (loudEvents, _) = runScripted(List(4) { loudHop }, FakeRecognizer(), FakeVad(speechFromSample = 1))

        assertTrue((quietEvents.last() as SttEvent.Final).nearSilent)
        assertFalse((loudEvents.last() as SttEvent.Final).nearSilent)
    }

    @Test
    fun `nearSilent is judged before SoftAgc's gain`() = runTest(timeout = TEST_TIMEOUT) {
        // No platform AGC: SoftAgc lifts a -60 dBFS hop to -30, the mic delivered -60 (#20).
        suspend fun final(hop: FloatArray): SttEvent.Final {
            lateinit var s: OnlineSession
            val mic = FakeMic(List(4) { hop }, afterScript = { s.stop() }, hasPlatformAgc = false)
            s = OnlineSession(FakeRecognizer(), FakeVad(speechFromSample = 1), options = SessionOptions()) { _, _ -> mic }
            return s.run().toList().last() as SttEvent.Final
        }

        assertTrue(final(quietHop).nearSilent)
        assertFalse(final(loudHop).nearSilent)
    }

    @Test
    fun `a model failure is a RECOGNITION error and frees the stream`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1)
        val broken = object : RecognizerStream {
            var released = false
            override fun acceptWaveform(samples: FloatArray) = Unit
            override fun decode() = throw IllegalStateException("decoder exploded")
            override fun text() = ""
            override fun inputFinished() = Unit
            override fun release() {
                released = true
            }
        }
        val recognizer = object : StreamingRecognizer {
            override fun createStream() = broken
            override fun release() = Unit
        }
        val mic = FakeMic(List(3) { loudHop })

        val events = OnlineSession(recognizer, vad, options = SessionOptions()) { _, _ -> mic }.run().toList()

        assertEquals(SttEvent.Error(SttErrorKind.RECOGNITION, "decoder exploded"), events.last())
        assertTrue(broken.released)
        assertTrue(mic.closed)
    }

    @Test
    fun `cancelling the collector releases the open stream and closes the mic`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1)
        val recognizer = FakeRecognizer()
        val mic = FakeMic(List(3) { loudHop })
        val sawPartial = CompletableDeferred<Unit>()
        val session = OnlineSession(recognizer, vad, options = SessionOptions()) { _, _ -> mic }

        val job = launch {
            session.run().collect { if (it is SttEvent.Partial) sawPartial.complete(Unit) }
        }
        sawPartial.await()
        job.cancelAndJoin()

        assertTrue(mic.closed)
        assertTrue(recognizer.streams.all { it.released })
    }

    @Test
    fun `mic that fails to open is a MIC error and no stream is made`() = runTest(timeout = TEST_TIMEOUT) {
        val recognizer = FakeRecognizer()
        val session = OnlineSession(recognizer, FakeVad(speechFromSample = 1), options = SessionOptions()) { _, _ ->
            throw MicCaptureException("mic busy")
        }

        assertEquals(listOf(SttEvent.Error(SttErrorKind.MIC, "mic busy")), session.run().toList())
        assertTrue(recognizer.streams.isEmpty())
    }
}
