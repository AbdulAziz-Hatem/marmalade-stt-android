package app.marmalade.stt.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.marmalade.ui.theme.MarmaladeTheme
import app.marmalade.ui.theme.MarmaladeToast
import app.marmalade.ui.theme.MarmaladeToastInk
import app.marmalade.ui.theme.darkDepth
import app.marmalade.ui.theme.lightDepth

/** The preset's accent colours ([AccentColors]), provided by [MarmaladeSttTheme]. */
val LocalAccentColors = staticCompositionLocalOf {
    AccentColors(pill = MarmaladeToast, pillInk = MarmaladeToastInk, ink = MarmaladeToastInk, marmaladePalette = true)
}

/** The accent colours of the theme in use: `MaterialTheme.accent.ink`. */
val MaterialTheme.accent: AccentColors
    @Composable @ReadOnlyComposable
    get() = LocalAccentColors.current

/**
 * The app theme for the user's [appearance] (Settings → Appearance, #35):
 * its preset, dark as its mode says ([ThemeMode.isDark] of the system's
 * dark theme).
 */
@Composable
fun MarmaladeSttTheme(appearance: Appearance, content: @Composable () -> Unit) {
    MarmaladeSttTheme(
        darkTheme = appearance.mode.isDark(isSystemInDarkTheme()),
        themePreset = appearance.preset,
        content = content,
    )
}

/**
 * Marmalade STT app theme: the shared library's [MarmaladeTheme] (#87) with
 * this app's presets, accent colours and focus ripple.
 *
 * @param darkTheme Whether to use the dark color scheme.
 * @param themePreset The selected theme preset. [ThemePreset.SYSTEM] uses
 *   Material You dynamic colors on Android 12+, and is Marmalade on older
 *   devices. [ThemePreset.MARMALADE] uses the hand-tuned orange palette.
 *   Other presets are the library's, or High contrast ([presetScheme]).
 * @param content The composable content to render within this theme.
 */
@Composable
fun MarmaladeSttTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePreset: ThemePreset = ThemePreset.MARMALADE,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Material You needs Android 12; before it, the wallpaper preset is Marmalade.
    val preset = if (themePreset == ThemePreset.SYSTEM && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        ThemePreset.MARMALADE
    } else {
        themePreset
    }
    val colorScheme = remember(preset, darkTheme, context) {
        if (preset == ThemePreset.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            presetScheme(preset, darkTheme)
        }
    }

    // Marmalade keeps the locked wordmark tokens: Orange #F97316 light /
    // Cream #FFEDD5 dark (High contrast too: a logotype has no contrast
    // requirement). Other presets use their primary.
    val wordmarkColor = when (preset) {
        ThemePreset.MARMALADE, ThemePreset.HIGH_CONTRAST -> if (darkTheme) Color(0xFFFFEDD5) else Color(0xFFF97316)
        else -> colorScheme.primary
    }

    CompositionLocalProvider(
        LocalAccentColors provides accentColors(preset, darkTheme, colorScheme),
        LocalRippleConfiguration provides FocusVisibleRipple,
    ) {
        MarmaladeTheme(
            colorScheme = colorScheme,
            darkTheme = darkTheme,
            wordmarkColor = wordmarkColor,
            depth = remember(colorScheme, darkTheme, preset) {
                val depth = if (darkTheme) darkDepth(colorScheme) else lightDepth(colorScheme)
                // High contrast keeps its full-strength quiet text in the ramp too.
                if (preset == ThemePreset.HIGH_CONTRAST) depth.copy(dim = colorScheme.onSurfaceVariant) else depth
            },
            content = content,
        )
    }
}

/**
 * Material's state layers, with a stronger focus layer. A hardware keyboard,
 * D-pad or Switch Access user sees where focus is only by this overlay, and
 * Material's 10 % was barely visible on the orange mic and the cream page
 * (#68). Touch never shows it; pressed, hovered and dragged keep Material's
 * values. Every Material component and `Modifier.clickable` (through the
 * theme's ripple indication) reads it.
 */
private val FocusVisibleRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.32f, hoveredAlpha = 0.08f, pressedAlpha = 0.1f),
)
