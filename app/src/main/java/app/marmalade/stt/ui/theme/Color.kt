package app.marmalade.stt.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.marmalade.ui.theme.MarmaladeDarkColors
import app.marmalade.ui.theme.MarmaladeLightColors
import app.marmalade.ui.theme.MarmaladeToast
import app.marmalade.ui.theme.MarmaladeToastInk
import app.marmalade.ui.theme.buildPresetScheme
import app.marmalade.ui.theme.ThemePreset as SharedPreset

// =============================================================================
// The app's colours on top of the shared design library (marmalade-ui-android,
// #87): the shared presets are the library's schemes exactly as
// marmalade-tts-android draws them. What is this app's own: the High
// contrast preset (#68) and [AccentColors].
// =============================================================================

/** Whether this is a dark scheme (its surface is dark). */
val ColorScheme.isDark: Boolean get() = surface.luminance() < 0.5f

/**
 * The accent colours no Material role carries in both modes, per preset
 * ([accentColors]); read them as `MaterialTheme.accent`.
 *
 * @property pill the "Recommended for this phone" and "✓ In use" pills, and
 *   the in-use card's outline: toast in Marmalade.
 * @property pillInk text on [pill].
 * @property ink links and small marks on a page or card ("Show more",
 *   quality dots, text buttons on the engine screens, Copy / Clear). In
 *   light mode the preset's dark ink (toast ink in Marmalade), so they read
 *   at AA on cream (#68); in dark mode the scheme's primary, as TTS draws
 *   its text buttons.
 * @property text small accent-coloured text elsewhere: section headers, text
 *   buttons, links, the slider's value. The scheme's primary in Marmalade, as
 *   TTS draws its section titles (light orange 500 is knowingly below AA,
 *   Max's call); [ThemePreset.HIGH_CONTRAST]'s is the AA version, and the
 *   other presets' light mode takes their dark ink (Forest's green 600 was
 *   2.9:1 on cards, Berry's red 600 4.2, #87 review).
 * @property mic the mic buttons' fill ([app.marmalade.stt.ui.components.micColors])
 *   and [onMic] its icon: primary, except toast in Marmalade's dark mode,
 *   which keeps a big orange disc off the stone page (#68, Max's pick P2-B).
 * @property progress progress bars and busy rings, as [mic]: toast in
 *   Marmalade's dark mode, the colour they had before the library (Max, #87
 *   round 3: "progress bar should stay the color it is in walkthrough").
 * @property marmaladePalette whether the preset draws the Marmalade palette
 *   (Marmalade, High contrast, System before Android 12): the cue for the
 *   toast tab pill, as TTS's `usesMarmaladePalette`.
 */
@Immutable
data class AccentColors(
    val pill: Color,
    val pillInk: Color,
    val ink: Color,
    val text: Color = ink,
    val mic: Color = Color.Unspecified,
    val onMic: Color = Color.Unspecified,
    val progress: Color = Color.Unspecified,
    val marmaladePalette: Boolean = false,
)

// =============================================================================
// Theme Presets
// =============================================================================

/**
 * Settings → Appearance → Colour (#35). [SYSTEM], [MARMALADE] (this app's
 * default), [MIDNIGHT], [FOREST] and [BERRY] are the library's presets
 * ([SharedPreset]), drawn exactly as in marmalade-tts-android.
 * [HIGH_CONTRAST] (#68) is this app's: Marmalade made WCAG AA in both modes
 * ([highContrastScheme]).
 */
enum class ThemePreset {
    SYSTEM,
    MARMALADE,
    MIDNIGHT,
    FOREST,
    BERRY,
    HIGH_CONTRAST,
    ;

    /** The library preset this one is, or null for this app's own. */
    val shared: SharedPreset? get() = SharedPreset.entries.find { it.name == name }

    companion object {
        /** The preset stored under [name], [MARMALADE] (the default) for none or an unknown one. */
        fun fromString(name: String?): ThemePreset =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: MARMALADE
    }
}

/**
 * The colour scheme of a curated preset (not [ThemePreset.SYSTEM], which is
 * the wallpaper's): the library's Marmalade scheme, its preset scheme, or
 * [highContrastScheme].
 */
fun presetScheme(preset: ThemePreset, isDark: Boolean): ColorScheme {
    val base = if (isDark) MarmaladeDarkColors else MarmaladeLightColors
    return when (preset) {
        ThemePreset.SYSTEM, ThemePreset.MARMALADE -> base
        ThemePreset.HIGH_CONTRAST -> highContrastScheme(isDark)
        else -> buildPresetScheme(base, checkNotNull(preset.shared), isDark)
    }
}

/**
 * High contrast (#68): Marmalade with the roles that fall short of WCAG AA
 * changed, and nothing else. Light: orange 700 where Marmalade fills with
 * orange 500 (white on it is 5.2:1), stone 500 control edges and stone 600
 * quiet text. Dark (already AA in the library): stone 300 quiet text. The
 * quiet text stays at its old full strength, so nothing reads fainter for
 * the people who turn this on. Error red is the library's, AA in both modes.
 */
fun highContrastScheme(isDark: Boolean): ColorScheme = if (isDark) {
    MarmaladeDarkColors.copy(
        onSurfaceVariant = Color(0xFFD6D3D1), // Stone 300
    )
} else {
    val orange700 = Color(0xFFC2410C)
    MarmaladeLightColors.copy(
        primary = orange700,
        surfaceTint = orange700,
        outline = Color(0xFF78716C),          // Stone 500: 3:1 for control edges on cream
        onSurfaceVariant = Color(0xFF57534E), // Stone 600
    )
}

/**
 * [scheme]'s accent colours for [preset] (after the theme has swapped a
 * [ThemePreset.SYSTEM] without Material You for [ThemePreset.MARMALADE]).
 */
fun accentColors(preset: ThemePreset, isDark: Boolean, scheme: ColorScheme): AccentColors {
    val toastMic = isDark && (preset == ThemePreset.MARMALADE || preset == ThemePreset.HIGH_CONTRAST)
    val mic = if (toastMic) MarmaladeToast else scheme.primary
    // White on orange 500 in light mode: the brand's, Max's call (#68).
    val onMic = if (toastMic) MarmaladeToastInk else scheme.onPrimary
    return when (preset) {
        ThemePreset.MARMALADE, ThemePreset.HIGH_CONTRAST -> AccentColors(
            pill = MarmaladeToast,
            pillInk = MarmaladeToastInk,
            ink = if (isDark) scheme.primary else MarmaladeToastInk,
            // High contrast's light-mode text is toast ink: orange 700 is 4.4:1 on a toast card.
            text = if (!isDark && preset == ThemePreset.HIGH_CONTRAST) MarmaladeToastInk else scheme.primary,
            mic = mic,
            onMic = onMic,
            progress = mic,
            marmaladePalette = true,
        )
        ThemePreset.SYSTEM -> AccentColors(
            scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, mic = mic, onMic = onMic, progress = mic,
        )
        else -> if (isDark) {
            AccentColors(
                pill = scheme.primary, pillInk = scheme.onPrimary, ink = scheme.primary, mic = mic, onMic = onMic, progress = mic,
            )
        } else {
            AccentColors(
                pill = scheme.primaryContainer,
                pillInk = lightInk(preset),
                ink = lightInk(preset),
                mic = mic,
                onMic = onMic,
                progress = mic,
            )
        }
    }
}

/**
 * Midnight, Forest and Berry's dark ink (blue 800, green 800, red 800), this
 * app's addition for small accent text and marks on the cream page: AA on
 * the page and cards, where their primary is not.
 */
private fun lightInk(preset: ThemePreset): Color = when (preset) {
    ThemePreset.MIDNIGHT -> Color(0xFF1E40AF)
    ThemePreset.FOREST -> Color(0xFF166534)
    ThemePreset.BERRY -> Color(0xFF991B1B)
    else -> error("$preset draws its accent text from its own scheme")
}
