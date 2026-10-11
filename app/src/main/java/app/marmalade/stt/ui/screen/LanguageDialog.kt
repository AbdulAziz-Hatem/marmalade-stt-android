package app.marmalade.stt.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.theme.accent

/**
 * Single-choice language dialog, like the cleanup filter dialog: tapping a
 * choice saves it and closes the dialog. The list scrolls. Settings →
 * Recognition language, the info dialog's per-engine override and
 * Settings → Developer → Runtime tuning use it;
 * [label] names each code ("Auto (German)", "Japanese").
 */
@Composable
fun LanguageDialog(
    title: String,
    options: List<String>,
    selected: String,
    label: @Composable (String) -> String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = code == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(code) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The accent text colour: ink (orange 500 is 2.45:1 on the dialog, under the 3:1 a
                        // state needs, #68) except in Marmalade light (brand orange, Max's decision).
                        RadioButton(
                            selected = code == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.accent.text),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(text = label(code), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            MarmaladeTextButton(onClick = onDismiss) { Text(stringResource(R.string.engine_language_cancel)) }
        },
    )
}
