package app.marmalade.stt.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// =============================================================================
// Marmalade Orange Palette
// Matches the palette in marmalade-android and marmalade-tts-android.
// =============================================================================

val MarmaladeLightColors = lightColorScheme(
    primary = Color(0xFFF97316),            // Orange 500
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFED7AA),    // Orange 200
    onPrimaryContainer = Color(0xFF4A1808),  // Rich dark brown
    secondary = Color(0xFFEA580C),           // Orange 600
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF7ED),  // Orange 50
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = Color(0xFFC2410C),            // Orange 700
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFED7AA),
    onTertiaryContainer = Color(0xFF7C2D12),
    surface = Color(0xFFFFF7ED),             // Orange 50
    onSurface = Color(0xFF1C1917),           // Stone 900
    surfaceVariant = Color(0xFFFFEDD5),      // Orange 100
    onSurfaceVariant = Color(0xFF57534E),    // Stone 600
    // The surfaceContainer family MUST be set explicitly. Material 3 fills
    // any role left unspecified from its *baseline* palette, which is
    // purple-tinted — and Card reads surfaceContainerLow, NavigationBar
    // reads surfaceContainer, ModalBottomSheet reads surfaceContainerLow.
    // Omitting these is why cards and the bottom bar rendered lavender on a
    // cream page for several marmalade-tts-android releases.
    // Cream is the whole ladder. Material's filled Card reads
    // surfaceContainerHighest and NavigationBar reads surfaceContainer, so
    // making the top of the ladder Orange 200 (toast) turned every card into
    // a saturated slab that fought the page instead of sitting on it. Toast
    // is a *chip* colour, not a surface — see the brand scheme's "orange is
    // precious" rule.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBF6),
    surfaceContainer = Color(0xFFFFEDD5),        // Orange 100 — nav bar
    surfaceContainerHigh = Color(0xFFFFEDD5),
    surfaceContainerHighest = Color(0xFFFFEDD5), // Orange 100 — cards
    background = Color(0xFFFFF7ED),          // Orange 50
    onBackground = Color(0xFF1C1917),        // Stone 900
    // Red 700, not 600: error text on the cream page and on dialogs needs
    // 4.5:1 (600 was 4.2 on a dialog, #68).
    error = Color(0xFFB91C1C),               // Red 700
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Color(0xFF78716C),             // Stone 500: 3:1 for control edges on cream (#68)
    outlineVariant = Color(0xFFD6D3D1),      // Stone 300
    inverseSurface = Color(0xFF1C1917),
    inverseOnSurface = Color(0xFFFAFAF9),
    inversePrimary = Color(0xFFFB923C),      // Orange 400
    surfaceTint = Color(0xFFF97316),
)

// Dark mode per marmalade-design: orange goes on NOTHING. Material paints
// `primary` both as a fill (filled Button, Switch track, Slider) and as text
// or an icon on the surface (section headers, TextButton labels, links,
// tints), so it can't be the brand's rich-brown accent (#422006 text is
// invisible on stone). It is Toast, the brand's default button, selected
// chip and dark-mode tab colour: legible as text, soft as a fill.
// `primaryContainer` carries the brand's dark accent button (rich brown on
// toast), used for each screen's single most important action.
val MarmaladeDarkColors = darkColorScheme(
    primary = Color(0xFFFED7AA),             // Toast
    onPrimary = Color(0xFF7C2D12),           // Toast ink
    primaryContainer = Color(0xFF422006),    // Rich brown (dark accent button)
    onPrimaryContainer = Color(0xFFFED7AA),  // Toast
    secondary = Color(0xFFFFEDD5),           // Cream
    onSecondary = Color(0xFF1C1917),
    // NavigationBar's selected pill: the brand's dark-mode active tab.
    secondaryContainer = Color(0xFFFED7AA),  // Toast
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = Color(0xFFFDBA74),            // Peach
    onTertiary = Color(0xFF422006),
    tertiaryContainer = Color(0xFF422006),   // Rich brown
    onTertiaryContainer = Color(0xFFFED7AA),
    // Stone deep, the brand's dark surface: neutral-warm, never amber-brown.
    surface = Color(0xFF1C1917),
    onSurface = Color(0xFFF5F5F4),           // Stone 100 (brand dark ink)
    surfaceVariant = Color(0xFF3D3835),
    onSurfaceVariant = Color(0xFFD6D3D1),    // Stone 300
    // Same reason as the light scheme — see the note there. Warm-neutral
    // stone ladder rather than Material's baseline, which reads violet in
    // dark mode too. Per the brand scheme, dark mode gets no orange
    // surfaces: the warmth comes from the cream text on stone.
    surfaceContainerLowest = Color(0xFF141210),
    surfaceContainerLow = Color(0xFF1F1D1B),
    surfaceContainer = Color(0xFF292524),    // Stone 800 — cards, nav bar
    surfaceContainerHigh = Color(0xFF332F2C),
    surfaceContainerHighest = Color(0xFF3D3835),
    background = Color(0xFF1C1917),          // Stone deep
    onBackground = Color(0xFFF5F5F4),
    error = Color(0xFFF87171),               // Red 400: 4.8:1 on dialogs (500 was 3.5, #68)
    onError = Color(0xFF1A1A1A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    outline = Color(0xFFA8A29E),             // Stone 400: 3:1 for control edges on dialogs too (#68)
    outlineVariant = Color(0xFF3D3835),
    inverseSurface = Color(0xFFFAFAF9),
    inverseOnSurface = Color(0xFF1C1917),
    inversePrimary = Color(0xFF422006),      // Rich brown: action text on the light inverse surface (snackbars)
    surfaceTint = Color(0xFFFED7AA),
)

// =============================================================================
// Brand tokens outside the Material roles
// =============================================================================

/**
 * Toast (Orange 200) and its ink, marmalade-design's pill: the same in both
 * modes. In light mode Toast is also `primaryContainer`, in dark mode
 * `primary`; neither role is Toast in both. The Marmalade preset's
 * [AccentColors].
 */
val Toast = Color(0xFFFED7AA)
val ToastInk = Color(0xFF7C2D12)

/** Whether this is a dark scheme (its surface is dark). */
val ColorScheme.isDark: Boolean get() = surface.luminance() < 0.5f

/**
 * The accent colours no Material role carries in both modes, per preset
 * ([accentColors]); read them as `MaterialTheme.accent`.
 *
 * @property pill the "Recommended for this phone" and "✓ In use" pills, and
 *   the in-use card's outline: Toast in Marmalade.
 * @property pillInk text on [pill].
 * @property ink links and small marks on a page or card ("Show more",
 *   quality dots, text buttons on the engine screens, Copy / Clear): toast
 *   ink in light mode and toast in dark in Marmalade. Material's `primary`
 *   would be orange in Marmalade's light mode.
 * @property text small accent-coloured text elsewhere: section headers, text
 *   buttons, links, the slider's value. The same as [ink], except in
 *   Marmalade's light mode, where it is the brand's orange 500 (2.6:1 on
 *   cream; Max kept it, 2026-10-06). [ThemePreset.HIGH_CONTRAST] is the AA
 *   version.
 */
@Immutable
data class AccentColors(val pill: Color, val pillInk: Color, val ink: Color, val text: Color = ink)

// =============================================================================
// Theme Presets
// =============================================================================

/**
 * Curated theme presets, Settings → Appearance → Colour (#35). [SYSTEM] uses
 * Material You dynamic colors from the device wallpaper on Android 12+.
 * [MARMALADE] (the default) uses the hand-tuned orange palette above.
 * [HIGH_CONTRAST] (#68) is Marmalade made WCAG AA in light mode too: orange
 * 700 for filled buttons, switches, sliders and the mic (white on it is
 * 5.2:1) and toast ink for small accent text; its dark mode is Marmalade's.
 * The others recolour the accent roles ([buildPresetScheme]) while keeping the
 * Marmalade surfaces: cream in light mode, stone in dark.
 *
 * Mirrors the marmalade-android / marmalade-tts-android ThemePreset enum so
 * the apps stay in lockstep visually; [lightInk] is this app's addition, for
 * text in the preset's colour on the cream page. Chat-specific tokens (user
 * bubble, avatar) are intentionally absent.
 */
enum class ThemePreset(
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightPrimaryContainer: Color,
    val darkPrimaryContainer: Color,
    val lightInk: Color,
) {
    SYSTEM(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified),
    MARMALADE(Color(0xFFF97316), Color(0xFFFED7AA), Color(0xFFFED7AA), Color(0xFF422006), ToastInk),
    MIDNIGHT(Color(0xFF2563EB), Color(0xFF60A5FA), Color(0xFFDBEAFE), Color(0xFF1E3A5F), Color(0xFF1E40AF)),
    FOREST(Color(0xFF16A34A), Color(0xFF4ADE80), Color(0xFFDCFCE7), Color(0xFF14532D), Color(0xFF166534)),
    BERRY(Color(0xFFDC2626), Color(0xFFF87171), Color(0xFFFEE2E2), Color(0xFF7F1D1D), Color(0xFF991B1B)),
    HIGH_CONTRAST(Color(0xFFC2410C), Color(0xFFFED7AA), Color(0xFFFED7AA), Color(0xFF422006), ToastInk),
    ;

    companion object {
        /** The preset stored under [name], [MARMALADE] (the default) for none or an unknown one. */
        fun fromString(name: String?): ThemePreset =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: MARMALADE
    }
}

/**
 * The colour scheme of a curated preset (not [ThemePreset.SYSTEM], which is
 * the wallpaper's): Marmalade's own, or [buildPresetScheme] over it.
 */
fun presetScheme(preset: ThemePreset, isDark: Boolean): ColorScheme {
    val base = if (isDark) MarmaladeDarkColors else MarmaladeLightColors
    return when (preset) {
        ThemePreset.SYSTEM, ThemePreset.MARMALADE -> base
        // Marmalade with orange 700 where light mode fills with orange 500.
        ThemePreset.HIGH_CONTRAST -> if (isDark) base else base.copy(primary = preset.lightPrimary, surfaceTint = preset.lightPrimary)
        else -> buildPresetScheme(base, preset, isDark)
    }
}

/**
 * Build a color scheme for a curated preset by recolouring the base scheme's
 * accent roles (primary, secondary and tertiary families) in the preset's
 * colour. Surface / background tones come from the base, so every preset
 * keeps the same warm-stone look; and in dark mode no orange is left (the
 * Marmalade dark scheme's toast nav pill and peach tertiary are replaced).
 */
fun buildPresetScheme(base: ColorScheme, preset: ThemePreset, isDark: Boolean): ColorScheme {
    val primary = if (isDark) preset.darkPrimary else preset.lightPrimary
    val primaryContainer = if (isDark) preset.darkPrimaryContainer else preset.lightPrimaryContainer
    val onPrimaryContainer = if (isDark) onColor(primaryContainer) else preset.lightInk
    // Light: the ink, like Marmalade's orange 600/700. Dark: the preset's light tone, as Marmalade uses toast.
    val accent = if (isDark) preset.darkPrimary else preset.lightInk
    // The nav bar's selected pill: toast in Marmalade dark, the preset's light tone here.
    val secondaryContainer = if (isDark) preset.darkPrimary else preset.lightPrimaryContainer
    return base.copy(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = accent,
        onSecondary = onColor(accent),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = if (isDark) onColor(secondaryContainer) else preset.lightInk,
        tertiary = accent,
        onTertiary = onColor(accent),
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        surfaceTint = primary,
        inversePrimary = if (isDark) preset.lightInk else preset.darkPrimary,
    )
}

/**
 * [scheme]'s accent colours for [preset] (after the theme has swapped a
 * [ThemePreset.SYSTEM] without Material You for [ThemePreset.MARMALADE]).
 */
fun accentColors(preset: ThemePreset, isDark: Boolean, scheme: ColorScheme): AccentColors = when (preset) {
    ThemePreset.MARMALADE -> AccentColors(
        pill = Toast,
        pillInk = ToastInk,
        ink = if (isDark) Toast else ToastInk,
        text = if (isDark) Toast else scheme.primary,
    )
    ThemePreset.HIGH_CONTRAST -> AccentColors(pill = Toast, pillInk = ToastInk, ink = if (isDark) Toast else ToastInk)
    ThemePreset.SYSTEM -> AccentColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
    else -> if (isDark) {
        AccentColors(pill = preset.darkPrimary, pillInk = onColor(preset.darkPrimary), ink = preset.darkPrimary)
    } else {
        AccentColors(pill = preset.lightPrimaryContainer, pillInk = preset.lightInk, ink = preset.lightInk)
    }
}

/** White or stone 900 on [background], whichever contrasts more (blue 400 is too light for white text). */
private fun onColor(background: Color): Color {
    val bg = background.luminance() + 0.05f
    val stone = Color(0xFF1C1917)
    return if (1.05f / bg >= bg / (stone.luminance() + 0.05f)) Color.White else stone
}
