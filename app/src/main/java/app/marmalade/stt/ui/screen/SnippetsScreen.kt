package app.marmalade.stt.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.R
import app.marmalade.stt.textproc.Snippet
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.components.rememberAnnouncer
import app.marmalade.stt.ui.contentDirection
import app.marmalade.stt.ui.isolate
import app.marmalade.ui.theme.depth

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Settings → "Snippets" → AppRoot → Routes.CleanupSnippets → SnippetsScreen(onBack)
//     │
//     ├── snippets ◄── CleanupListsViewModel.config.snippets
//     │                  ◄── TextProcSettings.config (DataStore)
//     │
//     └── Add (FAB) / tap a snippet → SnippetDialog → saveSnippet / deleteSnippet
//           → CleanupListsViewModel applies CleanupLists.* to the persisted list
//             → TextProcSettings.setSnippets(whole list) → DataStore
//             → config emits → every listening surface rebuilds its TextPipeline
//           save failure (IOException) → snackbar
// -----------------------------------------------------------------------------

/**
 * Settings → Text cleanup → Snippets: trigger phrases that insert saved text
 * verbatim. Same shape as [DictionaryScreen], without import/export. Detail
 * screen; AppRoot hides the bottom bar.
 */
@Composable
fun SnippetsScreen(
    onBack: () -> Unit,
    viewModel: CleanupListsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val announce = rememberAnnouncer()
    val deletedText = stringResource(R.string.a11y_snippet_deleted)

    // DIALOG_ADD, a row index, or null (closed). Survives rotation.
    var dialog by rememberSaveable { mutableStateOf<Int?>(null) }

    SaveFailedSnackbar(saveFailed, snackbarHostState, viewModel::saveFailedShown)

    val snippets = config?.snippets
    CleanupListScaffold(
        title = stringResource(R.string.cleanup_snippets),
        addLabel = stringResource(R.string.cleanup_snippets_add),
        showAdd = snippets != null,
        onBack = onBack,
        onAdd = { dialog = DIALOG_ADD },
        snackbarHostState = snackbarHostState,
    ) {
        item(key = "help") { CleanupHelpText(stringResource(R.string.cleanup_snippets_help)) }
        if (snippets == null) return@CleanupListScaffold

        item(key = "divider") { HorizontalDivider(color = MaterialTheme.depth.line) }
        if (snippets.isEmpty()) {
            item(key = "empty") { CleanupEmptyText(stringResource(R.string.cleanup_snippets_empty)) }
        } else {
            // Not keyed by trigger: an older stored list may repeat one.
            itemsIndexed(snippets) { index, snippet ->
                SnippetRow(snippet = snippet, onClick = { dialog = index })
            }
        }
    }

    if (snippets != null) {
        val editIndex = dialog?.takeIf { it != DIALOG_ADD }
        // A stale index (the list changed under the dialog) just closes it.
        val original = editIndex?.let { snippets.getOrNull(it) }
        if (dialog == DIALOG_ADD || original != null) {
            SnippetDialog(
                original = original,
                snippets = snippets,
                editIndex = editIndex,
                onSave = { snippet ->
                    viewModel.saveSnippet(original, snippet)
                    dialog = null
                },
                onDelete = if (original == null) {
                    null
                } else {
                    {
                        viewModel.deleteSnippet(original)
                        dialog = null
                        // The row just vanishes; say so (#68).
                        announce(deletedText)
                    }
                },
                onDismiss = { dialog = null },
            )
        }
    }
}

/** The trigger, then the first two lines of the saved text. */
@Composable
private fun SnippetRow(snippet: Snippet, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = snippet.trigger, style = LocalTextStyle.current.contentDirection(), modifier = Modifier.fillMaxWidth()) },
        supportingContent = {
            Text(
                text = snippet.text,
                style = LocalTextStyle.current.contentDirection(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.cleanup_edit_action), onClick = onClick),
        colors = cleanupRowColors(),
    )
}

/**
 * Add ([original] null) or edit one snippet. The trigger is one line and must
 * be non-blank; the text is multi-line and saved exactly as typed. If the
 * trigger matches another snippet's, a hint says saving replaces it.
 */
@Composable
private fun SnippetDialog(
    original: Snippet?,
    snippets: List<Snippet>,
    editIndex: Int?,
    onSave: (Snippet) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var trigger by rememberSaveable { mutableStateOf(original?.trigger.orEmpty()) }
    var text by rememberSaveable { mutableStateOf(original?.text.orEmpty()) }
    val replaces = remember(trigger, snippets, editIndex) {
        CleanupLists.indexOfKey(snippets, CleanupLists.cleanupKey(trigger), editIndex) { it.trigger }
            ?.let { snippets[it].trigger }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val hint = replaces?.let { stringResource(R.string.cleanup_snippets_replaces, isolate(it)) }
    AnnounceHint(hint)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(if (original == null) R.string.cleanup_snippets_add else R.string.cleanup_snippets_edit),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text(stringResource(R.string.cleanup_snippets_trigger)) },
                    textStyle = LocalTextStyle.current.contentDirection(),
                    singleLine = true,
                    supportingText = hint?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                // Enter inserts a line break: line breaks are part of the snippet.
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.cleanup_snippets_text)) },
                    textStyle = LocalTextStyle.current.contentDirection(),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            MarmaladeTextButton(
                onClick = { onSave(Snippet(trigger, text)) },
                enabled = CleanupLists.isValidSnippet(trigger, text),
            ) { Text(stringResource(R.string.cleanup_save)) }
        },
        dismissButton = { DeleteAndCancelButtons(onDelete = onDelete, onCancel = onDismiss) },
    )
}
