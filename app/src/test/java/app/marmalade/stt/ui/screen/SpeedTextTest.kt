package app.marmalade.stt.ui.screen

import app.marmalade.stt.R
import app.marmalade.stt.install.EngineSpeed
import app.marmalade.stt.install.SttEngineCatalog
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTextTest {

    private val ultra = SttEngineCatalog.PARAKEET_ULTRA
    private val reference = mapOf(
        ultra to EngineSpeed(relativeCost = 1.0, peakRssMb = 1),
        "whisper-turbo" to EngineSpeed(relativeCost = 9.79, peakRssMb = 1),
    )

    @Test
    fun `one decimal, in the user's locale`() {
        assertEquals("1.7", formatSpeed(1.66, Locale.US))
        assertEquals("1,7", formatSpeed(1.66, Locale.GERMANY))
        assertEquals("0.5", formatSpeed(0.45000001, Locale.US))
    }

    @Test
    fun `speed fact is the cost against Parakeet Ultra, and says when the phone isn't measured`() {
        // A measured phone: just the cost. The tier already says how it runs here.
        assertEquals(
            listOf(TextRes(R.string.engine_speed_cost, listOf("9.8"))),
            speedFactLines("whisper-turbo", 2.0, Locale.US, reference),
        )
        // Ultra is the reference itself; an unmeasured phone says so.
        assertEquals(
            listOf(TextRes(R.string.engine_speed_reference), TextRes(R.string.engine_speed_phone_unmeasured)),
            speedFactLines(ultra, null, Locale.US, reference),
        )
        // No reference measurement: no row.
        assertNull(speedFactLines("granite-speech-5-470m", 2.0, Locale.US, reference))
    }

    @Test
    fun `re-measure row says measuring, the result, or not measured`() {
        assertEquals(TextRes(R.string.settings_developer_speed_measuring), phoneSpeedSummary(true, 1.2, Locale.US))
        assertEquals(TextRes(R.string.settings_developer_speed_unmeasured), phoneSpeedSummary(false, null, Locale.US))
        assertEquals(
            TextRes(R.string.settings_developer_speed_measured, listOf("1.2")),
            phoneSpeedSummary(false, 1.23, Locale.US),
        )
    }

    @Test
    fun `re-measure snackbar reports the factor or the failure`() {
        assertEquals(TextRes(R.string.settings_developer_speed_done, listOf("2.5")), remeasuredMessage(2.5, Locale.US))
        assertEquals(TextRes(R.string.settings_developer_speed_failed), remeasuredMessage(null, Locale.US))
    }
}
