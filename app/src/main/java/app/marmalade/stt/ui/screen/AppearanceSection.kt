package app.marmalade.stt.ui.screen

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.ui.MarmaladeChoiceChip
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.theme.Appearance
import app.marmalade.stt.ui.theme.ThemeMode
import app.marmalade.stt.ui.theme.ThemePreset

/**
 * Settings → Appearance (#35), marmalade-tts-android's: Mode (System, Light,
 * Dark) and Color (the presets) as chips, independent of each other. A tap
 * saves and the app restyles at once. The Wallpaper preset (Material You)
 * is offered from Android 12, which has it. For TalkBack each chip row is
 * a single-choice group of radio buttons, and its label a heading (#68).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSection(
    appearance: Appearance,
    onModeChange: (ThemeMode) -> Unit,
    onPresetChange: (ThemePreset) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_appearance))
    ChipGroupLabel(R.string.settings_mode)
    ChipRow {
        ThemeMode.entries.forEach { mode ->
            MarmaladeChoiceChip(
                selected = mode == appearance.mode,
                onClick = { onModeChange(mode) },
                label = { Text(stringResource(mode.labelRes())) },
            )
        }
    }
    ChipGroupLabel(R.string.settings_color)
    ChipRow {
        offeredPresets().forEach { preset ->
            MarmaladeChoiceChip(
                selected = preset == appearance.preset,
                onClick = { onPresetChange(preset) },
                label = { Text(stringResource(preset.labelRes())) },
            )
        }
    }
}

/** The presets to offer: all of them, without Wallpaper before Android 12 (it would be Marmalade). */
private fun offeredPresets(): List<ThemePreset> =
    ThemePreset.entries.filter { it != ThemePreset.SYSTEM || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

@Composable
private fun ChipGroupLabel(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

/** One choice group of chips, wrapping at large font sizes instead of running off the edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .selectableGroup()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_mode_system
    ThemeMode.LIGHT -> R.string.settings_mode_light
    ThemeMode.DARK -> R.string.settings_mode_dark
}

@StringRes
private fun ThemePreset.labelRes(): Int = when (this) {
    ThemePreset.SYSTEM -> R.string.settings_color_system
    ThemePreset.MARMALADE -> R.string.settings_color_marmalade
    ThemePreset.MIDNIGHT -> R.string.settings_color_midnight
    ThemePreset.FOREST -> R.string.settings_color_forest
    ThemePreset.BERRY -> R.string.settings_color_berry
    ThemePreset.HIGH_CONTRAST -> R.string.settings_color_high_contrast
}
