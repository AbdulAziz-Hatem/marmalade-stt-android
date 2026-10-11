package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NeMo decodes get silence after the last word: Parakeet's int8 export drifted
 * into English without it (#25), and streaming engines dropped utterance ends.
 */
class TailPaddingTest {

    @Test
    fun appendsZerosAfterTheSamples() {
        val padded = withTailPadding(floatArrayOf(0.5f, -0.25f), 3)
        assertArrayEquals(floatArrayOf(0.5f, -0.25f, 0f, 0f, 0f), padded, 0f)
    }

    @Test
    fun noPaddingReturnsTheSameArray() {
        val samples = floatArrayOf(0.1f)
        assertSame(samples, withTailPadding(samples, 0))
    }

    @Test
    fun nemoPaddingIsAFewTenthsOfASecond() {
        assertEquals(0.3f, NEMO_TAIL_PADDING_SAMPLES.toFloat() / MIC_SAMPLE_RATE, 1e-6f)
    }

    @Test
    fun `a streaming engine gets its chunk plus 0_2 s of zeros before each utterance ends`() {
        assertEquals(21_120, streamingTailPaddingSamples(1120))
        assertEquals(0, streamingTailPaddingSamples(null))
        // More than the window sherpa-onnx waits for (121 frames of 10 ms for 1120 ms).
        assertTrue(streamingTailPaddingSamples(1120) > 121 * MIC_SAMPLE_RATE / 100)
        // Every streaming catalog engine has a chunk size, so every one is padded.
        for (engine in SttEngineCatalog.engines.filter { it.family.streaming }) {
            assertTrue(engine.id, streamingTailPaddingSamples(engine.chunkMs) > 0)
        }
    }
}
