package app.marmalade.stt.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The progress bars (setup-guide steps, model download) paint `primary` on an
 * `outlineVariant` track. Material's default track, `secondaryContainer`, is
 * the same toast as `primary` in dark mode, so 27 of 299 MB looked nearly
 * full on the Pixel 8a (#11).
 */
class ProgressTrackContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return max(la, lb) / min(la, lb)
    }

    private fun schemes(): Map<String, ColorScheme> = ThemePreset.entries
        .filter { it != ThemePreset.SYSTEM }
        .flatMap { preset ->
            listOf(
                "${preset.name} light" to buildPresetScheme(MarmaladeLightColors, preset, isDark = false),
                "${preset.name} dark" to buildPresetScheme(MarmaladeDarkColors, preset, isDark = true),
            )
        }
        .toMap()

    @Test
    fun `the track stands out from the bar in light and dark`() {
        for ((name, scheme) in schemes()) {
            val ratio = contrast(scheme.primary, scheme.outlineVariant)
            assertTrue("$name: bar vs track contrast $ratio", ratio >= MIN_CONTRAST)
        }
    }

    @Test
    fun `Material's default track would not, in Marmalade dark`() {
        val dark = MarmaladeDarkColors
        assertTrue(contrast(dark.primary, dark.secondaryContainer) < MIN_CONTRAST)
    }

    private companion object {
        /** Enough to read the filled part at a glance; the hue differs as well. */
        const val MIN_CONTRAST = 1.5f
    }
}
