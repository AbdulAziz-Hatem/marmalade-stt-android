package app.marmalade.stt.ui.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.marmalade.stt.ui.theme.ThemePreset
import app.marmalade.stt.ui.theme.presetScheme
import app.marmalade.ui.theme.darkDepth
import app.marmalade.ui.theme.lightDepth
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The speed and quality meters (#68): every lit colour, and the outline of
 * an unlit segment, stand out 3:1 (WCAG 1.4.11) from every surface an
 * engine card sits on, in both modes and every preset: the page (light
 * cards), the light in-use card's cream container, the library's card step
 * (dark cards, in use or not, #87) and its raised step (the details
 * dialog's tiles).
 */
class MeterContrastTest {

    @Test
    fun `lit and unlit segments stand out from every card`() {
        for (preset in ThemePreset.entries - ThemePreset.SYSTEM) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val name = "${preset.name} ${if (dark) "dark" else "light"}"
                // The page, the light in-use card, the card step and the raised step.
                val depth = if (dark) darkDepth(scheme) else lightDepth(scheme)
                val cards = listOf(scheme.surface, scheme.surfaceContainer, depth.card, depth.raised)
                for (card in cards) {
                    for (band in MeterBand.entries) {
                        val ratio = contrast(meterBandColor(band, dark), card)
                        assertTrue("$name $band: $ratio", ratio >= 3f)
                    }
                    val unlit = contrast(meterUnlitColor(dark), card)
                    assertTrue("$name unlit outline: $unlit", unlit >= 3f)
                }
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return max(la, lb) / min(la, lb)
    }
}
