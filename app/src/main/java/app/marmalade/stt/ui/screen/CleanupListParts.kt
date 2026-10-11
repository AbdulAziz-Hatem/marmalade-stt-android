package app.marmalade.stt.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.ui.components.DetailTopBar
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.components.rememberAnnouncer

// Building blocks shared by DictionaryScreen and SnippetsScreen, which have the
// same shape: help text, a list, an "Add" button, and an edit dialog.

/**
 * Detail-screen frame for a cleanup list: back arrow, snackbars, and an
 * extended FAB for adding (the screen's one accent action; the FAB's
 * primaryContainer is toast in light mode and rich brown in dark mode, never
 * orange). The FAB is hidden until the list has loaded ([showAdd]).
 */
@Composable
internal fun CleanupListScaffold(
    title: String,
    addLabel: String,
    showAdd: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = { DetailTopBar(title = title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showAdd) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                    text = { Text(addLabel) },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Room under the last row so the FAB never covers it.
            contentPadding = PaddingValues(bottom = 96.dp),
            content = content,
        )
    }
}

/** Shows the "couldn't save" snackbar once per failure, like SettingsScreen. */
@Composable
internal fun SaveFailedSnackbar(saveFailed: Boolean, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val text = stringResource(R.string.settings_save_failed)
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(text)
            onShown()
        }
    }
}

/** The rule summary at the top of a list screen. */
@Composable
internal fun CleanupHelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Shown in place of the list when it has no entries. */
@Composable
internal fun CleanupEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

/**
 * An edit dialog's left-hand buttons: Delete (editing only, in the error
 * colour) and Cancel. Call it straight from `dismissButton`, which AlertDialog
 * lays out in its button FlowRow: with no Row around them the two buttons are
 * separate flow items, so at large font sizes they wrap apart instead of
 * squeezing (#68).
 */
@Composable
internal fun DeleteAndCancelButtons(onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    if (onDelete != null) {
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.cleanup_delete)) }
    }
    MarmaladeTextButton(onClick = onCancel) { Text(stringResource(R.string.cleanup_cancel)) }
}

/**
 * Speaks an edit field's [hint] ("Replaces your rule for X", an input error)
 * when it appears or changes while the user types (#68). It shows as the
 * field's supporting text, which is a new node merged into the field, so a
 * live region can't carry it (and one on the field would read every
 * keystroke).
 */
@Composable
internal fun AnnounceHint(hint: String?) {
    val announce = rememberAnnouncer()
    LaunchedEffect(hint) { if (hint != null) announce(hint) }
}

/** `rememberSaveable` value for "the Add dialog is open"; any other value is the edited row's index. */
internal const val DIALOG_ADD = -1
