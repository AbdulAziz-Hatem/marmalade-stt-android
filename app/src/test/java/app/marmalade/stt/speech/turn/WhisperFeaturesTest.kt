package app.marmalade.stt.speech.turn

import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WhisperFeatures] against marmalade-stt-cli's numpy `log_mel` (through its
 * `SmartTurnDetector._window`), on the golden values in
 * `src/test/resources/smart-turn/` (`tools/smart-turn/gen_fixtures.py`).
 * The goldens keep a quarter of the frames in full plus every frame's and
 * every mel bin's mean: the whole 80 x 800 array is 256 KB a case.
 */
class WhisperFeaturesTest {

    @Test
    fun `features match the python reference`() {
        assertEquals(4, TurnFixtures.cases.size)
        for (case in TurnFixtures.cases) {
            val mel = WhisperFeatures.logMel(WhisperFeatures.lastWindow(case.audio))
            assertEquals(WhisperFeatures.N_MELS, mel.size)
            mel.forEach { assertEquals(WhisperFeatures.N_FRAMES, it.size) }

            val frames = TurnFixtures.floats("${case.name}.frames.f32")
            assertEquals(TurnFixtures.frames.size * WhisperFeatures.N_MELS, frames.size)
            var worst = 0f
            TurnFixtures.frames.forEachIndexed { i, t ->
                for (m in 0 until WhisperFeatures.N_MELS) {
                    worst = max(worst, abs(mel[m][t] - frames[i * WhisperFeatures.N_MELS + m]))
                }
            }
            assertTrue("${case.name}: frames differ by up to $worst", worst < TOLERANCE)

            val frameMeans = TurnFixtures.floats("${case.name}.frame-means.f32")
            val melMeans = TurnFixtures.floats("${case.name}.mel-means.f32")
            val actualFrameMeans = FloatArray(WhisperFeatures.N_FRAMES) { t -> mel.sumOf { it[t].toDouble() }.div(WhisperFeatures.N_MELS).toFloat() }
            val actualMelMeans = FloatArray(WhisperFeatures.N_MELS) { m -> mel[m].sumOf { it.toDouble() }.div(WhisperFeatures.N_FRAMES).toFloat() }
            assertArrayEquals("${case.name} frame means", frameMeans, actualFrameMeans, TOLERANCE)
            assertArrayEquals("${case.name} mel means", melMeans, actualMelMeans, TOLERANCE)
            println("${case.name}: largest difference $worst")
        }
    }

    @Test
    fun `the window keeps the last 8 s and pads at the start`() {
        val short = FloatArray(1000) { it + 1f }
        val padded = WhisperFeatures.lastWindow(short)
        assertEquals(WhisperFeatures.N_SAMPLES, padded.size)
        assertTrue(padded.copyOfRange(0, WhisperFeatures.N_SAMPLES - 1000).all { it == 0f })
        assertArrayEquals(short, padded.copyOfRange(WhisperFeatures.N_SAMPLES - 1000, WhisperFeatures.N_SAMPLES), 0f)

        val long = FloatArray(WhisperFeatures.N_SAMPLES + 500) { it.toFloat() }
        assertArrayEquals(long.copyOfRange(500, long.size), WhisperFeatures.lastWindow(long), 0f)
    }

    @Test
    fun `silence is flat at the log floor`() {
        // All-zero power hits the 1e-10 floor: log10 = -10, and (-10 + 4) / 4 = -1.5.
        val mel = WhisperFeatures.logMel(FloatArray(WhisperFeatures.N_SAMPLES))
        mel.forEach { row -> row.forEach { assertEquals(-1.5f, it, 0f) } }
    }

    @Test
    fun `trailing silence sits 8 decades below the loudest bin`() {
        val mel = WhisperFeatures.logMel(WhisperFeatures.lastWindow(TurnFixtures.cases.single { it.zeros > 0 }.audio))
        val values = mel.flatMap { it.asList() }
        // The silent second has next to no power above DC (normalising leaves it at -mean / std);
        // the floor lifts those bins to the maximum - 8, which / 4 is 2 below it.
        assertEquals(2f, values.max() - values.min(), 1e-5f)
    }

    /** Not a gate: prints how long a call takes on this machine. */
    @Test
    fun `feature timing`() {
        val audio = TurnFixtures.signal(WhisperFeatures.N_SAMPLES, 7)
        repeat(10) { WhisperFeatures.logMel(audio) }
        val runs = 30
        val start = System.nanoTime()
        repeat(runs) { WhisperFeatures.logMel(audio) }
        println("WhisperFeatures.logMel: %.1f ms a call (JVM, mean of $runs)".format((System.nanoTime() - start) / 1e6 / runs))
    }

    private companion object {
        const val TOLERANCE = 1e-5f
    }
}
