package app.marmalade.stt.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.marmalade.ui.theme.darkDepth
import app.marmalade.ui.theme.lightDepth
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The progress bars (setup-guide steps, model download, benchmark) paint
 * the accent's progress colour (toast in Marmalade's dark mode, Max, #87)
 * on an `outlineVariant` track. Material's default track,
 * `secondaryContainer`, was the same toast as the bar in dark mode before
 * the shared library, so 27 of 299 MB looked nearly full on the Pixel 8a
 * (#11).
 */
class ProgressTrackContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.compositeOver(b).luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return max(la, lb) / min(la, lb)
    }

    @Test
    fun `the track stands out from the bar in light and dark`() {
        for (preset in ThemePreset.entries - ThemePreset.SYSTEM) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val bar = accentColors(preset, dark, scheme).progress
                val depth = if (dark) darkDepth(scheme) else lightDepth(scheme)
                for (surface in listOf(depth.page, depth.card)) {
                    val track = scheme.outlineVariant.compositeOver(surface)
                    val ratio = contrast(bar, track)
                    assertTrue("${preset.name} dark=$dark: bar vs track contrast $ratio", ratio >= MIN_CONTRAST)
                }
            }
        }
    }

    private companion object {
        /** Enough to read the filled part at a glance; the hue differs as well. */
        const val MIN_CONTRAST = 1.5f
    }
}
