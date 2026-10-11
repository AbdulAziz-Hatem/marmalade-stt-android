package app.marmalade.stt.speech.turn

import java.io.File
import kotlin.math.abs
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * [OnnxTurnDetector] against the CLI's `SmartTurnDetector` on Python
 * onnxruntime (the probabilities in `smart-turn/index.tsv`, from
 * `tools/smart-turn/gen_fixtures.py`), run on the bundled asset (#44) the way
 * the app loads it: its bytes, with no download and no network.
 */
class OnnxTurnDetectorTest {

    /**
     * The goldens came from the dev machine (AVX2, Python ORT 1.29). The int8
     * kernels differ by CPU: the benchmark host (AVX-512 VNNI, ORT 1.28) gave up to
     * 0.04 more on the same features, bit-identical there. 0.05 still
     * catches a wrong window or a transposed input: those moved the short
     * clip from 0.89 to 0.98 and 0.21.
     */
    @Test
    fun `probabilities match python onnxruntime`() {
        val bad = TurnFixtures.cases.mapNotNull { case ->
            val expected = checkNotNull(case.probability) { "index.tsv has no probability for ${case.name}" }
            val actual = detector.completeProbability(case.audio)
            println("${case.name}: python $expected, kotlin $actual")
            if (abs(actual - expected) > TOLERANCE) "${case.name}: python $expected, kotlin $actual" else null
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun `any length gives a probability`() {
        for (n in intArrayOf(0, 1, 16_000, WhisperFeatures.N_SAMPLES * 2)) {
            val p = detector.completeProbability(TurnFixtures.signal(n, 9))
            assertTrue("$n samples: $p", p in 0f..1f)
        }
    }

    /** Not a gate: prints model and feature time per check on this machine, by thread count. */
    @Test
    fun `inference timing`() {
        val audio = TurnFixtures.signal(WhisperFeatures.N_SAMPLES, 7)
        for (threads in intArrayOf(1, 2, 4)) {
            val loadStart = System.nanoTime()
            OnnxTurnDetector(model, threads).use { d ->
                val loadMs = (System.nanoTime() - loadStart) / 1e6
                repeat(5) { d.completeProbability(audio) }
                val runs = 20
                val start = System.nanoTime()
                repeat(runs) { d.completeProbability(audio) }
                val totalMs = (System.nanoTime() - start) / 1e6 / runs
                println("OnnxTurnDetector, $threads threads: load %.0f ms, %.1f ms a check (features included)".format(loadMs, totalMs))
            }
        }
    }

    companion object {
        private const val TOLERANCE = 0.05f

        private val model = File("src/main/assets/${SmartTurnAsset.PATH}").readBytes()
        private lateinit var detector: OnnxTurnDetector

        @BeforeClass
        @JvmStatic
        fun load() {
            detector = OnnxTurnDetector(model)
        }

        @AfterClass
        @JvmStatic
        fun release() {
            if (::detector.isInitialized) detector.close()
        }
    }
}
