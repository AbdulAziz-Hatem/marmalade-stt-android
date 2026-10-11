package app.marmalade.stt.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * App-wide single-choice chip with consistent fill-only styling — no borders.
 * Ported from marmalade-tts-android (as `MarmaladeFilterChip`); used by
 * Settings → Appearance. Put a group of them in a container with
 * `Modifier.selectableGroup()`.
 *
 * Unselected: `surfaceVariant` — a soft neutral pastel that sits clearly
 * against the peach Marmalade light surface (and the dark counterpart in
 * dark mode) without needing an outline.
 *
 * Selected: `primaryContainer` plus a leading check mark. The two fills are
 * only about 1.2:1 apart, so the check is what shows the choice; colour
 * alone would fail WCAG 1.4.1 (#68).
 *
 * No border on either state — the fill alone carries the chip shape,
 * which gives a cleaner UI than the default M3 outline-when-unselected
 * mix. Every chip in the app should go through this helper so the
 * selected/unselected states are consistent everywhere.
 *
 * Semantics (#68): Material's FilterChip says "checkbox", but these chips
 * pick one of several, so each reads as a radio button ("Light, radio
 * button, selected"). The role set here is the outermost semantics modifier
 * on the chip's node, so it wins over FilterChip's own `Role.Checkbox` (checked
 * under Robolectric; the selected state comes from FilterChip).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmaladeChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.semantics { role = Role.RadioButton },
        enabled = enabled,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = null,
    )
}
