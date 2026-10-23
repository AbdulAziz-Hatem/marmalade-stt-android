package app.marmalade.stt.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.marmalade.ui.theme.MarmaladeDarkColors
import app.marmalade.ui.theme.MarmaladeLightColors
import app.marmalade.ui.theme.MarmaladeToast
import app.marmalade.ui.theme.MarmaladeToastInk
import app.marmalade.ui.theme.buildPresetScheme
import app.marmalade.ui.theme.darkDepth
import app.marmalade.ui.theme.lightDepth
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import app.marmalade.ui.theme.ThemePreset as SharedPreset

/**
 * Settings → Appearance's presets (#35) on the shared design library (#87):
 * the shared presets are the library's schemes exactly (marmalade-tts-android's
 * look and text contrast), High contrast is AA everywhere, and this app's
 * accent colours stay legible where they are drawn.
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
    fun `the shared presets are the library's schemes exactly`() {
        assertSame(MarmaladeLightColors, presetScheme(ThemePreset.MARMALADE, isDark = false))
        assertSame(MarmaladeDarkColors, presetScheme(ThemePreset.MARMALADE, isDark = true))
        for (preset in curated - ThemePreset.MARMALADE - ThemePreset.HIGH_CONTRAST) {
            for (dark in listOf(false, true)) {
                val base = if (dark) MarmaladeDarkColors else MarmaladeLightColors
                assertEquals(
                    "${preset.name} dark=$dark",
                    roles(buildPresetScheme(base, SharedPreset.valueOf(preset.name), dark)),
                    roles(presetScheme(preset, dark)),
                )
            }
        }
        // Every app preset but High contrast is a library one.
        assertEquals(listOf(ThemePreset.HIGH_CONTRAST), ThemePreset.entries.filter { it.shared == null })
    }

    @Test
    fun `High contrast changes only the roles that fell short of AA`() {
        val light = presetScheme(ThemePreset.HIGH_CONTRAST, isDark = false)
        assertEquals(
            setOf("primary", "surfaceTint", "outline", "onSurfaceVariant"),
            changed(MarmaladeLightColors, light),
        )
        val dark = presetScheme(ThemePreset.HIGH_CONTRAST, isDark = true)
        assertEquals(setOf("onSurfaceVariant"), changed(MarmaladeDarkColors, dark))
    }

    @Test
    fun `Marmalade's pills are toast, and its dark mic and progress bars stay toast`() {
        for (dark in listOf(false, true)) {
            val scheme = presetScheme(ThemePreset.MARMALADE, dark)
            val accent = accentColors(ThemePreset.MARMALADE, dark, scheme)
            assertEquals(MarmaladeToast, accent.pill)
            assertEquals(MarmaladeToastInk, accent.pillInk)
            // Section titles and text buttons: the scheme's primary, as TTS draws them.
            assertEquals(scheme.primary, accent.text)
            assertEquals(if (dark) MarmaladeToast else scheme.primary, accent.mic)
            // Progress bars and busy rings keep their colour from before the library (Max, #87).
            assertEquals(if (dark) MarmaladeToast else scheme.primary, accent.progress)
        }
    }

    @Test
    fun `this app's accent colours are legible on the page and on their pills`() {
        for (preset in curated) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val accent = accentColors(preset, dark, scheme)
                val name = "${preset.name} ${if (dark) "dark" else "light"}"
                val depth = if (dark) darkDepth(scheme) else lightDepth(scheme)
                for (surface in listOf(depth.page, depth.card, depth.raised, depth.sheet)) {
                    val ratio = contrast(accent.ink, surface)
                    assertTrue("$name ink on ${hex(surface)}: $ratio", ratio >= TEXT_CONTRAST)
                    // Section titles too, but for Marmalade's light orange 500 (Max's call).
                    if (dark || preset != ThemePreset.MARMALADE) {
                        val title = contrast(accent.text, surface)
                        assertTrue("$name text on ${hex(surface)}: $title", title >= TEXT_CONTRAST)
                    }
                }
                assertTrue("$name pill ink", contrast(accent.pillInk, accent.pill) >= TEXT_CONTRAST)
                // The mic, in dark mode and High contrast: its fill against the
                // page, its icon against the fill. (Light mode keeps the brand's
                // white on orange 500, Max's call, #68.)
                if (dark || preset == ThemePreset.HIGH_CONTRAST) {
                    assertTrue("$name mic", contrast(accent.mic, depth.page) >= UI_CONTRAST)
                    assertTrue("$name mic icon", contrast(accent.onMic, accent.mic) >= UI_CONTRAST)
                }
            }
        }
    }

    /**
     * The selected tab's pill, as the library's
     * marmaladeNavigationBarItemColors picks it for AppRoot: toast on the
     * Marmalade palette; otherwise primary in dark mode and the scheme's
     * secondaryContainer in light. Dark mode: 3:1 on the bar in every preset.
     * Light mode: no pale pill reaches 3:1 on the cream bar (toast is 1.2:1),
     * so it is the preset's own pale tone, never the bar's colour or Orange
     * 50 (#87 review: Orange 50 on the Orange 100 bar, 1.08:1).
     */
    @Test
    fun `the selected tab's pill stands off the nav bar`() {
        for (preset in curated) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val accent = accentColors(preset, dark, scheme)
                val pill = when {
                    accent.marmaladePalette -> MarmaladeToast
                    dark -> scheme.primary
                    else -> scheme.secondaryContainer
                }
                val name = "${preset.name} ${if (dark) "dark" else "light"}"
                if (dark) {
                    val ratio = contrast(pill, scheme.surfaceContainer)
                    assertTrue("$name pill on the bar: $ratio", ratio >= UI_CONTRAST)
                } else {
                    val own = preset.shared?.takeIf { !accent.marmaladePalette }?.lightPrimaryContainer ?: MarmaladeToast
                    assertEquals("$name pill", own, pill)
                    assertTrue("$name pill is the bar's colour", pill != scheme.surfaceContainer && pill != ORANGE_50)
                }
            }
        }
    }

    /**
     * Midnight, Forest and Berry carry none of Marmalade's orange in this
     * app's colours either: pills, links, titles, the mic, progress and the
     * tab pill (#87 review).
     */
    @Test
    fun `no orange is left in the other presets`() {
        for (preset in listOf(ThemePreset.MIDNIGHT, ThemePreset.FOREST, ThemePreset.BERRY)) {
            for (dark in listOf(false, true)) {
                val scheme = presetScheme(preset, dark)
                val accent = accentColors(preset, dark, scheme)
                val colors = mapOf(
                    "pill" to accent.pill, "ink" to accent.ink, "text" to accent.text, "mic" to accent.mic,
                    "progress" to accent.progress, "tab pill" to if (dark) scheme.primary else scheme.secondaryContainer,
                    "default progress track" to scheme.secondaryContainer,
                    "dots" to scheme.tertiary, "slider track" to scheme.primaryContainer,
                )
                for ((role, color) in colors) {
                    assertFalse("${preset.name} dark=$dark $role is orange: ${hex(color)}", color.isOrange())
                }
            }
        }
    }

    /** Orange: a saturated hue from 15 to 45 degrees (red is 0, amber 45). */
    private fun Color.isOrange(): Boolean {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        if (max - min < 0.05f) return false // grey or near it (Orange 50 is 0.07)
        val hue = when (max) {
            red -> 60f * (((green - blue) / (max - min)) % 6f)
            green -> 60f * ((blue - red) / (max - min) + 2f)
            else -> 60f * ((red - green) / (max - min) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return hue in 15f..45f
    }

    /**
     * Dark mode is AA in every preset, as TTS #89 made the library's: text on
     * every surface it sits on, error text included (the library's red 400
     * since #87 round 3).
     */
    @Test
    fun `dark mode text meets AA on every surface, in every preset`() {
        for (preset in curated) {
            val scheme = presetScheme(preset, isDark = true)
            val accent = accentColors(preset, isDark = true, scheme)
            val depth = darkDepth(scheme).let { if (preset == ThemePreset.HIGH_CONTRAST) it.copy(dim = scheme.onSurfaceVariant) else it }
            val texts = mapOf(
                "onSurface" to scheme.onSurface, "onSurfaceVariant" to scheme.onSurfaceVariant, "depth.dim" to depth.dim,
                "accent.ink" to accent.ink, "accent.text" to accent.text, "error" to scheme.error,
            )
            val surfaces = listOf(depth.page, depth.card, depth.raised, depth.sheet, scheme.surfaceContainerHigh)
            for ((role, text) in texts) {
                for (surface in surfaces) {
                    val ratio = contrast(text, surface)
                    assertTrue("${preset.name} $role on ${hex(surface)}: $ratio", ratio >= TEXT_CONTRAST)
                }
            }
        }
    }

    /** Dark mode's controls: filled buttons' labels at AA, their fills and the control edges at 3:1 on the page and cards. */
    @Test
    fun `dark mode controls meet AA in every preset`() {
        for (preset in curated) {
            val scheme = presetScheme(preset, isDark = true)
            val depth = darkDepth(scheme)
            val name = preset.name
            assertTrue("$name onPrimary", contrast(scheme.onPrimary, scheme.primary) >= TEXT_CONTRAST)
            assertTrue("$name onPrimaryContainer", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= TEXT_CONTRAST)
            for (surface in listOf(depth.page, depth.card, depth.raised)) {
                for ((role, part) in mapOf("primary" to scheme.primary, "outline" to scheme.outline)) {
                    val ratio = contrast(part, surface)
                    assertTrue("$name $role on ${hex(surface)}: $ratio", ratio >= UI_CONTRAST)
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

    /** Every colour role of [scheme] by name (ColorScheme has no equals). */
    private fun roles(scheme: ColorScheme): Map<String, Color> = with(scheme) {
        mapOf(
            "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer,
            "onPrimaryContainer" to onPrimaryContainer, "inversePrimary" to inversePrimary,
            "secondary" to secondary, "onSecondary" to onSecondary, "secondaryContainer" to secondaryContainer,
            "onSecondaryContainer" to onSecondaryContainer, "tertiary" to tertiary, "onTertiary" to onTertiary,
            "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
            "background" to background, "onBackground" to onBackground, "surface" to surface,
            "onSurface" to onSurface, "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
            "surfaceTint" to surfaceTint, "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
            "error" to error, "onError" to onError, "errorContainer" to errorContainer,
            "onErrorContainer" to onErrorContainer, "outline" to outline, "outlineVariant" to outlineVariant,
            "scrim" to scrim, "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim,
            "surfaceContainerLowest" to surfaceContainerLowest, "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
        )
    }

    /** The roles [b] sets differently from [a]. */
    private fun changed(a: ColorScheme, b: ColorScheme): Set<String> {
        val ra = roles(a)
        return roles(b).filter { (role, color) -> ra[role] != color }.keys
    }

    /** WCAG contrast of [a] drawn over [b] (a translucent ink composited first). */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.compositeOver(b).luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return max(la, lb) / min(la, lb)
    }

    private fun hex(c: Color) = "#%08X".format(c.value.toLong() ushr 32)

    private companion object {
        /** WCAG AA for normal text. */
        const val TEXT_CONTRAST = 4.5f

        /** WCAG AA for icons and the parts of controls (1.4.11). */
        const val UI_CONTRAST = 3f

        /** Marmalade's light secondaryContainer, the pill colour that vanished on the bar. */
        val ORANGE_50 = Color(0xFFFFF7ED)
    }
}
