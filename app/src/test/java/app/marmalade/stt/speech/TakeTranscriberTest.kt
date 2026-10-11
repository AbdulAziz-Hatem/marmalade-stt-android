package app.marmalade.stt.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The benchmark's whole-take transcription: same VAD segments for every family. */
class TakeTranscriberTest {

    private val window = StreamingSession.VAD_WINDOW_SIZE

    @Test
    fun `utterances are cut as live and the open one is flushed at the end`() {
        val vad = FakeVad(speechFromSample = 0, segmentsAt = listOf(10 * window))

        val utterances = vadUtterances(vad, FloatArray(25 * window) { 0.1f }, StreamingSession.PREROLL_SAMPLES)

        assertEquals(listOf(10 * window, 15 * window), utterances.map { it.size })
        assertEquals(1, vad.resetCalls)
    }

    @Test
    fun `each utterance gets the pre-roll before its onset, never the previous utterance's audio`() {
        // Speech from the first sample; the VAD detects it at window 10 and
        // starts its segment at window 8. The second utterance starts right
        // after the first segment.
        val vad = FakeVad(speechFromSample = 10 * window, segmentsAt = listOf(20 * window), segmentFrom = 8 * window)
        val take = FloatArray(25 * window) { it.toFloat() }

        val utterances = vadUtterances(vad, take, prerollSamples = 25 * window)

        assertEquals(listOf(20 * window, 5 * window), utterances.map { it.size })
        assertEquals(0f, utterances[0].first()) // from the take's first sample
        assertEquals((20 * window).toFloat(), utterances[1].first())
    }

    @Test
    fun `with padStart, silence makes up a pre-roll cut short by the take's start`() {
        val vad = FakeVad(speechFromSample = 10 * window, segmentsAt = listOf(20 * window), segmentFrom = 8 * window)
        val take = FloatArray(25 * window) { 1f }

        val first = vadUtterances(vad, take, prerollSamples = 25 * window, padStart = true).first()

        // Onset at window 10 (10 windows recorded), pre-roll 25 windows: 15 windows of silence, then the audio.
        assertEquals(15 * window + 20 * window, first.size)
        assertTrue(first.copyOfRange(0, 15 * window).all { it == 0f })
        assertTrue(first.copyOfRange(15 * window, first.size).all { it == 1f })
    }

    @Test
    fun `offline take gives each segment's trimmed text and skips blanks`() {
        val vad = FakeVad(speechFromSample = 0, segmentsAt = listOf(4 * window, 8 * window))
        val model = FakeTranscriber(listOf(" Hello.", "  ", " World."))
        val take = OfflineTakeTranscriber(model, vad)

        assertEquals(listOf("Hello.", "World."), take.transcribeUtterances(FloatArray(12 * window)))
        assertEquals(3, model.calls.size)

        take.release()
        assertEquals(1, model.releaseCalls)
        assertEquals(1, vad.releaseCalls)
    }

    @Test
    fun `streaming take feeds each segment in 100 ms chunks to its own flushed stream`() {
        val chunks = mutableListOf<Int>()
        var streams = 0
        var finished = 0
        var released = 0
        val model = object : StreamingRecognizer {
            override fun createStream(): RecognizerStream {
                val n = ++streams
                return object : RecognizerStream {
                    override fun acceptWaveform(samples: FloatArray) {
                        chunks += samples.size
                    }

                    override fun decode() = Unit
                    override fun text() = " part $n"
                    override fun inputFinished() {
                        finished++
                    }

                    override fun release() {
                        released++
                    }
                }
            }

            override fun release() = Unit
        }
        val vad = FakeVad(speechFromSample = 0, segmentsAt = listOf(5 * window))
        val take = StreamingTakeTranscriber(model, vad)

        val texts = take.transcribeUtterances(FloatArray(8 * window))

        assertEquals(listOf("part 1", "part 2"), texts)
        assertEquals(2, streams)
        assertEquals(2, finished)
        assertEquals(2, released)
        assertTrue(chunks.all { it <= StreamingSession.MIC_HOP_SAMPLES })
        // Each utterance's pre-roll is 1 window long, so silence makes up the rest of 1 s.
        assertEquals(8 * window + 2 * (OnlineSession.PREROLL_SAMPLES - window), chunks.sum())
    }
}
