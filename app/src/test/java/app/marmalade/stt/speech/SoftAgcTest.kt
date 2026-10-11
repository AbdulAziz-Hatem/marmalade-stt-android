package app.marmalade.stt.speech

import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SoftAgc] on synthesized hops, and its wiring into the session (the same cases as the harness's test). */
class SoftAgcTest {

    private fun noise(dbfs: Double, seed: Int): FloatArray {
        val rng = Random(seed)
        val amplitude = Math.pow(10.0, dbfs / 20.0) * Math.sqrt(3.0) // uniform noise: RMS = amplitude / sqrt(3)
        return FloatArray(HOP) { ((rng.nextDouble() * 2 - 1) * amplitude).toFloat() }
    }

    private fun gainDb(before: FloatArray, after: FloatArray) =
        NearSilence.rmsDb(after) - NearSilence.rmsDb(before)

    @Test
    fun `quiet input gets the full 30 dB from the first hop`() {
        val quiet = noise(-60.0, 1)
        assertEquals(30f, gainDb(quiet, SoftAgc().process(quiet.copyOf())), 0.01f)
    }

    @Test
    fun `speech louder than the target is left alone, and a loud hop is never over-amplified`() {
        val agc = SoftAgc()
        agc.process(noise(-60.0, 2)) // full gain
        val loud = noise(-15.0, 3)
        assertArrayEquals(loud, agc.process(loud.copyOf()), 0f)
    }

    @Test
    fun `after loud speech the gain comes back slowly`() {
        val agc = SoftAgc()
        agc.process(noise(-15.0, 4))
        val quiet = noise(-60.0, 5)
        // 100 ms later the envelope has released 0.3 dB: the target is still below it.
        assertEquals(0f, gainDb(quiet, agc.process(quiet.copyOf())), 0.01f)
    }

    @Test
    fun `the gain never pushes a peak above -1 dBFS`() {
        val spiky = FloatArray(HOP).also { it[5] = 0.05f } // low RMS, -26 dBFS peak
        val out = SoftAgc().process(spiky)
        assertTrue(out.maxOf { abs(it) } <= Math.pow(10.0, -1 / 20.0).toFloat() + 1e-6f)
    }

    @Test
    fun `the session applies it only when the mic has no platform AGC`() = runTest(timeout = TEST_TIMEOUT) {
        suspend fun levels(hasPlatformAgc: Boolean): List<Float> {
            lateinit var s: StreamingSession
            val mic = FakeMic(List(3) { quietHop }, afterScript = { s.stop() }, hasPlatformAgc = hasPlatformAgc)
            s = StreamingSession(FakeTranscriber(listOf("x")), FakeVad(Int.MAX_VALUE), options = SessionOptions(), openMic = { _, _ -> mic })
            return s.run().toList().filterIsInstance<SttEvent.Level>().map { it.rmsDb }
        }

        val withPlatformAgc = levels(hasPlatformAgc = true)
        val withSoftAgc = levels(hasPlatformAgc = false)
        assertEquals(3, withSoftAgc.size)
        for ((plain, gained) in withPlatformAgc.zip(withSoftAgc)) assertEquals(30f, gained - plain, 0.5f)
    }
}
