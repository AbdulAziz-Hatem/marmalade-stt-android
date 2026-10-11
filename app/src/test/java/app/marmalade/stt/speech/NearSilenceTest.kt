package app.marmalade.stt.speech

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearSilenceTest {

    private fun sine(amplitude: Float) = FloatArray(16_000) { (amplitude * sin(2 * PI * 1000 * it / 16_000)).toFloat() }

    @Test
    fun `a full-scale sine is about -3 dBFS`() {
        assertEquals(-3.01f, NearSilence.rmsDb(sine(1f)), 0.02f)
    }

    @Test
    fun `halving the amplitude costs 6 dB`() {
        assertEquals(-9.03f, NearSilence.rmsDb(sine(0.5f)), 0.02f)
    }

    @Test
    fun `silence, empty input and vanishing signals report the floor`() {
        assertEquals(NearSilence.FLOOR_DB, NearSilence.rmsDb(FloatArray(1600)), 0f)
        assertEquals(NearSilence.FLOOR_DB, NearSilence.rmsDb(FloatArray(0)), 0f)
        assertEquals(NearSilence.FLOOR_DB, NearSilence.rmsDb(FloatArray(1600) { 1e-9f }), 0f)
    }

    @Test
    fun `near-silent means strictly below the threshold`() {
        assertTrue(NearSilence.isNearSilent(NearSilence.THRESHOLD_DB - 0.1f))
        assertFalse(NearSilence.isNearSilent(NearSilence.THRESHOLD_DB))
        assertFalse(NearSilence.isNearSilent(-20f))
    }

    @Test
    fun `Pixel 8a levels from the device pass fall on the right side`() {
        // #11: quietest speech measured, and the phantom "Nice." from room noise.
        assertFalse(NearSilence.isNearSilent(-49.2f))
        assertTrue(NearSilence.isNearSilent(-52.8f))
    }
}
