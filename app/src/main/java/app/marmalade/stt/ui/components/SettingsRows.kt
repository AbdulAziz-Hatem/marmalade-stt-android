package app.marmalade.stt.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.ui.theme.accent
import app.marmalade.ui.components.SteppedSliderTrack
import app.marmalade.ui.theme.depth

// Small building blocks shared by the Settings, About, Licenses and Privacy
// screens (ported from marmalade-tts-android's SettingsScreen, where they
// were file-private; here several screens need them).

/**
 * Section label: titleSmall in the accent text colour (orange in Marmalade's
 * light mode, ink in High contrast and dark mode; #68), announced as a
 * heading.
 */
@Composable
fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.accent.text,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * Settings/About row drawn as a plain centered [Row] rather than M3's
 * ListItem: with two supporting lines ListItem becomes "three-line" and pins
 * leadingContent to the top, which reads as misaligned next to rows whose
 * icon floats mid-height. Center-aligning everything is what the list wants.
 * Pass a clickable [modifier] for rows that do something. The row sits on
 * the page with no fill of its own; its leading icon and chevron take the
 * quiet glyph ink, `depth.faint` (#84; an icon that sets its own tint keeps
 * it).
 */
@Composable
fun SettingsRow(
    label: String,
    supporting: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.depth.faint) { leading() }
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.depth.faint,
            )
        }
    }
}

/**
 * A setting that is on or off. The whole row is the toggle (one TalkBack
 * stop, announced as a switch); the [Switch] only shows the state. A row
 * that isn't [enabled] is greyed out and can't be toggled; its supporting
 * text should say why, so only the label and the switch dim: the reason
 * stays at full contrast (#68). [indent] marks a setting that belongs to the
 * row above it.
 */
@Composable
fun SettingsSwitchRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indent: Boolean = false,
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = if (indent) 32.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(alpha))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Material's alpha for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/**
 * A setting picked on a range: label and current value on one line, the
 * supporting text, then the slider. [valueText] is what the row shows
 * ("0.9 s"); [valueDescription] is what TalkBack says ("0.9 seconds"). The
 * slider carries the label and value for TalkBack, so the line above it is
 * hidden from it rather than read twice (#68).
 * Callers keep the dragged value locally and save it in
 * [onValueChangeFinished], so a drag writes once, not per step.
 */
@Composable
fun SettingsSliderRow(
    label: String,
    supporting: String,
    valueText: String,
    valueDescription: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(modifier = Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.accent.text,
            )
        }
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val haptics = LocalHapticFeedback.current
        Slider(
            value = value,
            onValueChange = {
                // Each new stop gives a light tick, now that no marks show them.
                if (it != value) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange(it)
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            // The library's stepped track, as TTS's speed slider (#84 S): no stop
            // marks, and a visible inactive track in light mode.
            track = { SteppedSliderTrack(it) },
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueDescription
            },
        )
    }
}

/**
 * Top bar for detail screens: centered title plus a back arrow. The title is
 * a heading and stays on one line, which the fixed-height bar has room for
 * at any font size; TalkBack still reads it in full (#68).
 *
 * `windowInsets = WindowInsets(0)` is the nested-Scaffold inset handoff:
 * AppRoot's outer Scaffold already consumes the status-bar inset, so an inner
 * TopAppBar that also applied it would render at ~2× height. Callers' inner
 * Scaffolds set `contentWindowInsets = WindowInsets(0)` for the same reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { TopBarTitle(title) },
        windowInsets = WindowInsets(0),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
        },
        // As wide as the back button, so the full-width title stays centred on the screen.
        actions = { Spacer(Modifier.width(48.dp)) },
    )
}

/**
 * A top app bar's title: a heading, one line, ellipsised when too wide (#68;
 * see [DetailTopBar]). It fills the bar's title slot and centres itself:
 * measured at its own width, an Arabic title came out a little too narrow at
 * 200 % text and lost its last letter ("الإعدادا…").
 */
@Composable
fun TopBarTitle(title: String) {
    Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
}
