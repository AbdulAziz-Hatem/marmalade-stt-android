package app.marmalade.stt.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GraniteFrontend] against golden vectors from the harness's numpy front-end
 * (`tools/asr-bench/granite/dump_golden.py frontend`, in
 * `src/test/resources/granite/`), which #26 held to transformers' own
 * extractor within 7e-5.
 */
class GraniteFrontendTest {

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("granite/$name")) { "missing granite/$name" }
            .use { it.readBytes() }

    private fun floats(name: String): FloatArray {
        val buf = ByteBuffer.wrap(resource(name)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buf.remaining()).also { buf.get(it) }
    }

    private val cases = resource("index.tsv").decodeToString().lines().filter { it.isNotBlank() }.map {
        val (name, samples, frames) = it.split('\t')
        Triple(name, samples.toInt(), frames.toInt())
    }

    @Test
    fun `features match the python reference`() {
        assertEquals(4, cases.size)
        for ((name, samples, frames) in cases) {
            val x = floats("$name.samples.f32")
            val expected = floats("$name.features.f32")
            assertEquals(name, samples, x.size)
            assertEquals(name, frames * GraniteFrontend.WIDTH, expected.size)
            assertEquals(name, frames, GraniteFrontend.frames(samples))

            val actual = GraniteFrontend.features(x)
            assertEquals(name, expected.size, actual.size)
            var worst = 0f
            for (i in actual.indices) worst = max(worst, abs(actual[i] - expected[i]))
            assertTrue("$name: largest difference $worst", worst < 1e-5f)
        }
    }

    @Test
    fun `silence floors every bin 8 decades below the loudest`() {
        val x = FloatArray(16_000) { if (it < 4000) 0f else (((it * 7919) % 200) - 100) / 1000f }
        val f = GraniteFrontend.features(x)
        val mels = (0 until f.size / GraniteFrontend.WIDTH).flatMap { row ->
            val base = row * GraniteFrontend.WIDTH
            (0 until 80).map { f[base + it] } + (160 until 240).map { f[base + it] }
        }
        assertEquals(mels.max() - 2f, mels.min(), 1e-5f)
    }
}
