package app.marmalade.stt.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings → Appearance's presets (#35) against the marmalade-design rules:
 * no bright orange in dark mode (none at all outside Marmalade), and the
 * accent colours legible where they are drawn.
 */
class ThemePresetTest {

    private val curated = ThemePreset.entries - ThemePreset.SYSTEM

    @Test
    fun `the mode decides dark, or leaves it to the system`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `stored names parse, with Marmalade and System as the defaults`() {
        assertEquals(ThemePreset.BERRY, ThemePreset.fromString("BERRY"))
        assertEquals(ThemePreset.MARMALADE, ThemePreset.fromString(null))
        assertEquals(ThemePreset.MARMALADE, ThemePreset.fromString("nope"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString(null))
    }

    @Test
    fun `Marmalade is the hand-tuned palette itself`() {
        assertSame(MarmaladeLightColors, presetScheme(ThemePreset.MARMALADE, isDark = false))
        assertSame(MarmaladeDarkColors, presetScheme(ThemePreset.MARMALADE, isDark = true))
        assertEquals(
            AccentColors(pill = Toast, pillInk = ToastInk, ink = Toast),
            accentColors(ThemePreset.MARMALADE, isDark = true, MarmaladeDarkColors),
        )
    }

    @Test
    fun `no preset has bright orange in dark mode`() {
        for (preset in curated) {
            val scheme = presetScheme(preset, isDark = true)
            for ((role, color) in colors(scheme, accentColors(preset, isDark = true, scheme))) {
                assertFalse("${preset.name} dark $role ${hex(color)} is bright orange", isBrightOrange(color))
            }
        }
    }

    @Test
    fun `the other presets leave no orange at all in dark mode`() {
        // High contrast's dark mode is Marmalade's.
        for (preset in curated - ThemePreset.MARMALADE - ThemePreset.HIGH_CONTRAST) {
            val scheme = presetScheme(preset, isDark = true)
            for ((role, color) in colors(scheme, accentColors(preset, isDark = true, scheme))) {
                assertFalse("${preset.name} dark $role ${hex(color)} is orange", isOrange(color))
            }
        }
    }

    @Test
    fun `accent text is legible on the page and on its pill`() {
        for (preset in curated) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val accent = accentColors(preset, dark, scheme)
                val name = "${preset.name} ${if (dark) "dark" else "light"}"
                assertTrue("$name ink on surface", contrast(accent.ink, scheme.surface) >= TEXT_CONTRAST)
                assertTrue("$name pill ink", contrast(accent.pillInk, accent.pill) >= TEXT_CONTRAST)
                assertTrue("$name on primary container", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= TEXT_CONTRAST)
                assertTrue("$name on secondary container", contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `text roles are legible on every surface they are drawn on`() {
        for (preset in curated) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val accent = accentColors(preset, dark, scheme)
                val name = "${preset.name} ${if (dark) "dark" else "light"}"
                // The page, cards and the in-use card, dialogs and sheets (#68).
                val surfaces = with(scheme) {
                    mapOf(
                        "surface" to surface, "surfaceContainerLow" to surfaceContainerLow,
                        "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
                    )
                }
                val texts = mapOf(
                    "onSurface" to scheme.onSurface, "onSurfaceVariant" to scheme.onSurfaceVariant,
                    "error" to scheme.error, "accent.ink" to accent.ink,
                ) + if (preset == ThemePreset.MARMALADE && !dark) {
                    // The brand's orange text in Marmalade's light mode (Max, 2026-10-06).
                    emptyMap()
                } else {
                    mapOf("accent.text" to accent.text)
                }
                for ((surfaceName, surface) in surfaces) {
                    for ((textName, text) in texts) {
                        val ratio = contrast(text, surface)
                        assertTrue("$name $textName on $surfaceName: $ratio", ratio >= TEXT_CONTRAST)
                    }
                }
                assertTrue("$name on error", contrast(scheme.onError, scheme.error) >= TEXT_CONTRAST)
                assertTrue("$name on error container", contrast(scheme.onErrorContainer, scheme.errorContainer) >= TEXT_CONTRAST)
            }
        }
    }

    /**
     * Dark mode is the AA-contrast theme (#68: light mode keeps the brand's
     * white on orange 500, Max's call): its filled controls (the mic, a
     * checked switch, a slider) stand out 3:1 from the page, its accent
     * button's label is 4.5:1, and control edges are 3:1 in both modes.
     */
    @Test
    fun `controls meet AA contrast in dark mode, and edges in both`() {
        for (preset in curated) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val name = "${preset.name} ${if (dark) "dark" else "light"}"
                for (surface in listOf(scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHigh)) {
                    val edge = contrast(scheme.outline, surface)
                    assertTrue("$name outline on ${hex(surface)}: $edge", edge >= UI_CONTRAST)
                    if (dark || preset == ThemePreset.HIGH_CONTRAST) {
                        val fill = contrast(scheme.primary, surface)
                        assertTrue("$name primary on ${hex(surface)}: $fill", fill >= UI_CONTRAST)
                    }
                }
                if (dark || preset == ThemePreset.HIGH_CONTRAST) {
                    val label = contrast(scheme.onPrimary, scheme.primary)
                    assertTrue("$name onPrimary on primary: $label", label >= TEXT_CONTRAST)
                }
            }
        }
    }

    /**
     * The High contrast preset (#68) is AA in both modes, every text role on
     * every surface it can sit on, every filled role under its label, and the
     * controls' fills and edges: light mode's orange text and white on orange
     * 500 are the default theme's, not this one's.
     */
    @Test
    fun `High contrast meets AA everywhere, in light and dark mode`() {
        val preset = ThemePreset.HIGH_CONTRAST
        for (dark in listOf(false, true)) {
            val scheme = presetScheme(preset, dark)
            val accent = accentColors(preset, dark, scheme)
            val name = if (dark) "dark" else "light"
            val surfaces = with(scheme) {
                listOf(
                    background, surface, surfaceContainerLowest, surfaceContainerLow,
                    surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
                )
            }
            val texts = with(scheme) {
                mapOf(
                    "onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant, "onBackground" to onBackground,
                    "error" to error, "primary" to primary, "accent.ink" to accent.ink, "accent.text" to accent.text,
                )
            }
            // Text sits on these; the lowest and highest containers are only Material's track fills.
            val textSurfaces = with(scheme) { listOf(background, surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh) }
            for (surface in surfaces) {
                if (surface in textSurfaces) {
                    for ((role, text) in texts) {
                        val ratio = contrast(text, surface)
                        assertTrue("$name $role on ${hex(surface)}: $ratio", ratio >= TEXT_CONTRAST)
                    }
                }
                for ((role, part) in mapOf("primary" to scheme.primary, "outline" to scheme.outline)) {
                    val ratio = contrast(part, surface)
                    assertTrue("$name $role edge on ${hex(surface)}: $ratio", ratio >= UI_CONTRAST)
                }
            }
            val pairs = with(scheme) {
                mapOf(
                    "onPrimary" to (onPrimary to primary),
                    "onPrimaryContainer" to (onPrimaryContainer to primaryContainer),
                    "onSecondaryContainer" to (onSecondaryContainer to secondaryContainer),
                    "onTertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                    "onError" to (onError to error),
                    "onErrorContainer" to (onErrorContainer to errorContainer),
                    "inverseOnSurface" to (inverseOnSurface to inverseSurface),
                    "pillInk" to (accent.pillInk to accent.pill),
                )
            }
            for ((role, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue("$name $role: $ratio", ratio >= TEXT_CONTRAST)
            }
        }
    }

    /** Every colour role of [scheme], and the accent colours. */
    private fun colors(scheme: ColorScheme, accent: AccentColors): Map<String, Color> = with(scheme) {
        mapOf(
            "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer,
            "onPrimaryContainer" to onPrimaryContainer, "inversePrimary" to inversePrimary,
            "secondary" to secondary, "onSecondary" to onSecondary, "secondaryContainer" to secondaryContainer,
            "onSecondaryContainer" to onSecondaryContainer, "tertiary" to tertiary, "onTertiary" to onTertiary,
            "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
            "background" to background, "onBackground" to onBackground, "surface" to surface,
            "onSurface" to onSurface, "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
            "surfaceTint" to surfaceTint, "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
            "outline" to outline, "outlineVariant" to outlineVariant,
            "surfaceContainerLowest" to surfaceContainerLowest, "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "accent.pill" to accent.pill, "accent.pillInk" to accent.pillInk, "accent.ink" to accent.ink,
        )
    }

    /** HSL hue in degrees, saturation and lightness. */
    private fun hsl(c: Color): Triple<Float, Float, Float> {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0f) return Triple(0f, 0f, l)
        val s = d / (1 - kotlin.math.abs(2 * l - 1))
        val h = when (mx) {
            c.red -> 60 * (((c.green - c.blue) / d) % 6)
            c.green -> 60 * ((c.blue - c.red) / d + 2)
            else -> 60 * ((c.red - c.green) / d + 4)
        }
        return Triple((h + 360) % 360, s, l)
    }

    /** An orange hue, saturated: toast, peach and rich brown included. */
    private fun isOrange(c: Color): Boolean {
        val (h, s, l) = hsl(c)
        return h in 15f..45f && s >= 0.5f && l in 0.1f..0.95f
    }

    /** Orange 400–700 territory: the mid tones the brand keeps off dark surfaces (toast and rich brown are allowed). */
    private fun isBrightOrange(c: Color): Boolean = isOrange(c) && hsl(c).third in 0.3f..0.7f

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return max(la, lb) / min(la, lb)
    }

    private fun hex(c: Color) = "#%08X".format(c.value.toLong() ushr 32)

    private companion object {
        /** WCAG AA for normal text. */
        const val TEXT_CONTRAST = 4.5f

        /** WCAG AA for icons and the parts of controls (1.4.11). */
        const val UI_CONTRAST = 3f
    }
}
