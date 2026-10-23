package app.marmalade.stt.speech

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #91 prototype: the early VAD's speculative Final ([StreamingSession]'s
 * `earlyVad`). The fake VADs end their segments where the silence began, as
 * Silero does, so the early and the main VAD can agree on the end.
 */
class SpeculativeFinalTest {

    /**
     * Speech from [speechFrom] until [closeAt] samples have been accepted; then
     * a segment of the windows from [segmentFrom] up to [endAt] (where the
     * silence began). Fed one window at a time, as the session does.
     */
    private class TrimmingVad(
        private val speechFrom: Int,
        private val closeAt: Int,
        private val endAt: Int,
        private val segmentFrom: Int = 0,
    ) : SpeechVad {
        private val accepted = SampleBuffer()
        private val segments = ArrayDeque<VadSegment>()
        private var closed = false

        override fun acceptWaveform(samples: FloatArray) {
            accepted.append(samples)
            if (!closed && accepted.size >= closeAt) {
                closed = true
                segments += VadSegment(segmentFrom, accepted.copyRange(segmentFrom, endAt))
            }
        }

        override fun isSpeechDetected() = !closed && accepted.size >= speechFrom
        override fun empty() = segments.isEmpty()
        override fun front() = segments.first()
        override fun pop() {
            segments.removeFirst()
        }

        override fun flush() {
            if (!closed && accepted.size > segmentFrom) {
                closed = true
                segments += VadSegment(segmentFrom, accepted.copyRange(segmentFrom, accepted.size))
            }
        }

        override fun reset() = Unit
        override fun release() = Unit
    }

    private suspend fun run(
        main: SpeechVad,
        early: SpeechVad?,
        transcriber: FakeTranscriber,
        hops: Int,
        end: EndOfDictation = EndOfDictation.PAUSE,
        trimAtStop: Boolean = true,
    ): List<SttEvent> {
        lateinit var s: StreamingSession
        val script = List(hops) { i -> if (i in 2 until 8) loudHop else FloatArray(HOP) }
        val mic = FakeMic(script, afterScript = { s.stop() })
        s = StreamingSession(
            transcriber = transcriber,
            vad = main,
            options = SessionOptions(end = end),
            openMic = { _, _ -> mic },
            nowMs = slowClock(),
            earlyVad = early,
            trimAtStop = trimAtStop,
        )
        return s.run().toList().filter { it is SttEvent.Final }
    }

    // Speech in hops 2-7; silence from sample 8 * HOP.
    private val speechEnd = 8 * HOP

    @Test
    fun `the main VAD ending where the early one did takes the speculative text, decoded once`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("early", "late"))
        val finals = run(
            main = TrimmingVad(speechFrom = 3 * HOP, closeAt = 20 * HOP, endAt = speechEnd, segmentFrom = 2 * HOP),
            early = TrimmingVad(speechFrom = 3 * HOP, closeAt = 12 * HOP, endAt = speechEnd, segmentFrom = 2 * HOP),
            transcriber = transcriber,
            hops = 24,
        )
        assertEquals(listOf("early"), finals.map { (it as SttEvent.Final).text })
        assertEquals(1, transcriber.calls.size)
    }

    @Test
    fun `the speculative decode gets exactly the audio the Final would have`() = runTest(timeout = TEST_TIMEOUT) {
        val plain = FakeTranscriber(listOf("x"))
        run(TrimmingVad(3 * HOP, 20 * HOP, speechEnd, 2 * HOP), null, plain, hops = 24)
        val speculative = FakeTranscriber(listOf("x"))
        run(TrimmingVad(3 * HOP, 20 * HOP, speechEnd, 2 * HOP), TrimmingVad(3 * HOP, 12 * HOP, speechEnd, 2 * HOP), speculative, hops = 24)
        assertArrayEquals(plain.calls.single(), speculative.calls.single(), 0f)
    }

    @Test
    fun `a main VAD ending elsewhere decodes again`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("early", "late"))
        val finals = run(
            // Speech the early VAD didn't count moved the main VAD's end.
            main = TrimmingVad(speechFrom = 3 * HOP, closeAt = 20 * HOP, endAt = speechEnd + HOP, segmentFrom = 2 * HOP),
            early = TrimmingVad(speechFrom = 3 * HOP, closeAt = 12 * HOP, endAt = speechEnd, segmentFrom = 2 * HOP),
            transcriber = transcriber,
            hops = 24,
        )
        assertEquals(listOf("late"), finals.map { (it as SttEvent.Final).text })
        assertEquals(2, transcriber.calls.size)
    }

    @Test
    fun `trim at stop sends the speech without the silence up to the stop`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("speech only", "with silence"))
        val finals = run(
            // The main VAD never closes before the stop (Tap stop): stop flushes it.
            main = TrimmingVad(speechFrom = 3 * HOP, closeAt = Int.MAX_VALUE, endAt = 0, segmentFrom = 2 * HOP),
            early = TrimmingVad(speechFrom = 3 * HOP, closeAt = 12 * HOP, endAt = speechEnd, segmentFrom = 2 * HOP),
            transcriber = transcriber,
            hops = 16,
            end = EndOfDictation.TAP_STOP,
        )
        assertEquals(listOf("speech only"), finals.map { (it as SttEvent.Final).text })
        assertEquals(1, transcriber.calls.size)
    }

    @Test
    fun `without trim at stop the flushed segment is decoded as before`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("speech only", "with silence"))
        val finals = run(
            main = TrimmingVad(speechFrom = 3 * HOP, closeAt = Int.MAX_VALUE, endAt = 0, segmentFrom = 2 * HOP),
            early = TrimmingVad(speechFrom = 3 * HOP, closeAt = 12 * HOP, endAt = speechEnd, segmentFrom = 2 * HOP),
            transcriber = transcriber,
            hops = 16,
            end = EndOfDictation.TAP_STOP,
            trimAtStop = false,
        )
        assertEquals(listOf("with silence"), finals.map { (it as SttEvent.Final).text })
        assertTrue(transcriber.calls.last().size > transcriber.calls.first().size)
    }

    @Test
    fun `the early VAD's silence is a whole number of windows shorter than the main one's`() {
        val mains = (3..15).map { it / 10f } + Patience.entries.map { it.proposeSeconds }
        for (main in mains) {
            val early = EarlyVad.minSilenceSeconds(main)
            if ((MIC_SAMPLE_RATE * main).toInt() < EarlyVad.TARGET_SAMPLES + StreamingSession.VAD_WINDOW_SIZE) {
                assertNull("$main", early)
                continue
            }
            assertNotNull("$main", early)
            // As sherpa-onnx computes the lengths: an int times a float, truncated.
            val mainSamples = (MIC_SAMPLE_RATE * main).toInt()
            val earlySamples = (MIC_SAMPLE_RATE * early!!).toInt()
            assertEquals("$main", 0, (mainSamples - earlySamples) % StreamingSession.VAD_WINDOW_SIZE)
            assertTrue("$main: $earlySamples", earlySamples in EarlyVad.TARGET_SAMPLES until EarlyVad.TARGET_SAMPLES + StreamingSession.VAD_WINDOW_SIZE)
        }
    }
    @Test
    fun `the early VAD runs for every end by default, and for Tap stop alone without the speculative Final`() {
        try {
            SpeedKnobs.reset()
            for (end in EndOfDictation.entries) assertTrue("$end", SpeedKnobs.earlyVadFor(end))
            SpeedKnobs.speculativeFinal = false
            assertTrue(SpeedKnobs.earlyVadFor(EndOfDictation.TAP_STOP))
            assertFalse(SpeedKnobs.earlyVadFor(EndOfDictation.PAUSE))
            assertFalse(SpeedKnobs.earlyVadFor(EndOfDictation.SMART_TURN))
        } finally {
            SpeedKnobs.reset()
        }
    }

    /** Partials decoded before the stop, on a clock that never moves: only one at the onset can happen. */
    private suspend fun partialsOnAFrozenClock(partialAtOnset: Boolean): Int {
        val transcriber = FakeTranscriber(listOf("hello"))
        lateinit var s: StreamingSession
        val mic = FakeMic(List(6) { loudHop }, afterScript = { s.stop() })
        s = StreamingSession(
            transcriber = transcriber,
            vad = FakeVad(speechFromSample = 1),
            options = SessionOptions(),
            openMic = { _, _ -> mic },
            nowMs = { 0L },
            partialAtOnset = partialAtOnset,
        )
        return s.run().toList().count { it is SttEvent.Partial }
    }

    @Test
    fun `the first partial decodes at the onset, not a partial interval later`() = runTest(timeout = TEST_TIMEOUT) {
        assertEquals(1, partialsOnAFrozenClock(partialAtOnset = true))
        assertEquals(0, partialsOnAFrozenClock(partialAtOnset = false))
    }
}
