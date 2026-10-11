package app.marmalade.stt.ui.screen

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.R
import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.ui.components.MarmaladeOutlinedButton
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.components.rememberAnnouncer
import app.marmalade.stt.ui.contentDirection
import app.marmalade.stt.ui.isolate
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Settings → "Replacement dictionary" → AppRoot → Routes.CleanupDictionary
//     → DictionaryScreen(onBack)
//     │
//     ├── rules ◄── CleanupListsViewModel.config.replacements
//     │               ◄── TextProcSettings.config (DataStore)
//     │
//     ├── Add (FAB) / tap a rule → RuleDialog → saveRule / deleteRule
//     ├── Import → ImportDialog (pasted `wrong -> right` text,
//     │     CleanupLists.previewImport) → importRules (merge, CleanupLists.mergeImported)
//     │       → CleanupListsViewModel applies CleanupLists.* to the persisted list
//     │         → TextProcSettings.setReplacements(whole list) → DataStore
//     │         → config emits → every listening surface rebuilds its TextPipeline
//     │       save failure (IOException) → snackbar
//     │
//     └── Copy all → Replacement.formatList(rules) → clipboard (no file I/O)
// -----------------------------------------------------------------------------

/**
 * Settings → Text cleanup → Replacement dictionary: the user's `wrong -> right`
 * rules, in stored order, with add, edit, delete, paste-in import and
 * copy-out export. Detail screen; AppRoot hides the bottom bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictionaryScreen(
    onBack: () -> Unit,
    viewModel: CleanupListsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.cleanup_exported)
    val announce = rememberAnnouncer()
    val deletedText = stringResource(R.string.a11y_rule_deleted)

    // DIALOG_ADD, a row index, or null (closed). Survives rotation.
    var dialog by rememberSaveable { mutableStateOf<Int?>(null) }
    var importing by rememberSaveable { mutableStateOf(false) }

    SaveFailedSnackbar(saveFailed, snackbarHostState, viewModel::saveFailedShown)

    val rules = config?.replacements
    CleanupListScaffold(
        title = stringResource(R.string.cleanup_dictionary),
        addLabel = stringResource(R.string.cleanup_dictionary_add),
        showAdd = rules != null,
        onBack = onBack,
        onAdd = { dialog = DIALOG_ADD },
        snackbarHostState = snackbarHostState,
    ) {
        item(key = "help") { CleanupHelpText(stringResource(R.string.cleanup_dictionary_help)) }
        if (rules == null) return@CleanupListScaffold

        item(key = "actions") {
            // FlowRow: at large font sizes the second button wraps under the first (#68).
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarmaladeOutlinedButton(onClick = { importing = true }) {
                    Text(stringResource(R.string.cleanup_import))
                }
                MarmaladeOutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(Replacement.formatList(rules)))
                        // Android 13+ confirms every copy itself; a snackbar on
                        // top would say it twice.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            scope.launch { snackbarHostState.showSnackbar(copiedText) }
                        }
                    },
                    enabled = rules.isNotEmpty(),
                ) { Text(stringResource(R.string.cleanup_export)) }
            }
        }
        item(key = "divider") { HorizontalDivider(modifier = Modifier.padding(top = 12.dp)) }

        if (rules.isEmpty()) {
            item(key = "empty") { CleanupEmptyText(stringResource(R.string.cleanup_dictionary_empty)) }
        } else {
            // Not keyed by left side: an older stored list may repeat one, and
            // duplicate keys crash LazyColumn.
            itemsIndexed(rules) { index, rule ->
                RuleRow(rule = rule, onClick = { dialog = index })
            }
        }
    }

    if (rules != null) {
        val editIndex = dialog?.takeIf { it != DIALOG_ADD }
        // A stale index (the list changed under the dialog) just closes it.
        val original = editIndex?.let { rules.getOrNull(it) }
        if (dialog == DIALOG_ADD || original != null) {
            RuleDialog(
                original = original,
                rules = rules,
                editIndex = editIndex,
                onSave = { rule ->
                    viewModel.saveRule(original, rule)
                    dialog = null
                },
                onDelete = if (original == null) {
                    null
                } else {
                    {
                        viewModel.deleteRule(original)
                        dialog = null
                        // The row just vanishes; say so (#68).
                        announce(deletedText)
                    }
                },
                onDismiss = { dialog = null },
            )
        }
        if (importing) {
            ImportDialog(
                onImport = { imported ->
                    viewModel.importRules(imported)
                    importing = false
                },
                onDismiss = { importing = false },
            )
        }
    }
}

/** One rule as `wrong → right`; an empty right side reads "(deleted)". */
@Composable
private fun RuleRow(rule: Replacement, onClick: () -> Unit) {
    val deleted = stringResource(R.string.cleanup_dictionary_deleted)
    val description = if (rule.to.isEmpty()) {
        stringResource(R.string.cleanup_dictionary_rule_deletes_description, rule.from)
    } else {
        stringResource(R.string.cleanup_dictionary_rule_description, rule.from, rule.to)
    }
    val muted = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Right to left (#69): the rule reads from the right, so the arrow points
    // left, and each side keeps its own direction ("teh." not ".teh").
    val arrow = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "  ←  " else "  →  "
    val text = buildAnnotatedString {
        append(isolate(rule.from))
        withStyle(muted) { append(arrow) }
        if (rule.to.isEmpty()) {
            withStyle(muted.copy(fontStyle = FontStyle.Italic)) { append(deleted) }
        } else {
            append(isolate(rule.to))
        }
    }
    ListItem(
        headlineContent = {
            // TalkBack would read the arrow as "right arrow"; say "becomes" instead.
            Text(text = text, modifier = Modifier.clearAndSetSemantics { contentDescription = description })
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.cleanup_edit_action), onClick = onClick),
    )
}

/**
 * Add ([original] null) or edit one rule. The left side must be non-blank and
 * export-safe ([CleanupLists.leftSideProblem]); the right side may be empty. If
 * the left side matches another rule, a hint says saving replaces it.
 */
@Composable
private fun RuleDialog(
    original: Replacement?,
    rules: List<Replacement>,
    editIndex: Int?,
    onSave: (Replacement) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var from by rememberSaveable { mutableStateOf(original?.from.orEmpty()) }
    var to by rememberSaveable { mutableStateOf(original?.to.orEmpty()) }
    val problem = CleanupLists.leftSideProblem(from)
    val replaces = remember(from, rules, editIndex) {
        CleanupLists.indexOfKey(rules, CleanupLists.cleanupKey(from), editIndex) { it.from }?.let { rules[it].from }
    }
    val save = { if (problem == null) onSave(Replacement(from, to)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val breaksExportText = stringResource(R.string.cleanup_dictionary_from_breaks_export)
    val hint = when {
        problem == CleanupLists.LeftSideProblem.BREAKS_EXPORT -> breaksExportText
        replaces != null -> stringResource(R.string.cleanup_dictionary_replaces, isolate(replaces))
        else -> null
    }
    AnnounceHint(hint)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(if (original == null) R.string.cleanup_dictionary_add else R.string.cleanup_dictionary_edit),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text(stringResource(R.string.cleanup_dictionary_from)) },
                    textStyle = LocalTextStyle.current.contentDirection(),
                    singleLine = true,
                    isError = problem == CleanupLists.LeftSideProblem.BREAKS_EXPORT,
                    supportingText = when {
                        problem == CleanupLists.LeftSideProblem.BREAKS_EXPORT -> {
                            {
                                Text(
                                    text = breaksExportText,
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = LITERAL_ARROWS),
                                )
                            }
                        }
                        hint != null -> {
                            { Text(hint) }
                        }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        // The specific error, not Material's generic "Invalid input" (#68).
                        .semantics {
                            if (problem == CleanupLists.LeftSideProblem.BREAKS_EXPORT) error(breaksExportText)
                        },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text(stringResource(R.string.cleanup_dictionary_to)) },
                    textStyle = LocalTextStyle.current.contentDirection(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.cleanup_dictionary_to_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            MarmaladeTextButton(onClick = save, enabled = problem == null) { Text(stringResource(R.string.cleanup_save)) }
        },
        dismissButton = { DeleteAndCancelButtons(onDelete = onDelete, onCancel = onDismiss) },
    )
}

/**
 * Paste `wrong -> right` lines. Shows how many rules were understood (and how
 * many lines weren't) before the user confirms; confirming merges them in.
 */
@Composable
private fun ImportDialog(onImport: (List<Replacement>) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val preview = remember(text) { CleanupLists.previewImport(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cleanup_import_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.cleanup_import_help),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = LITERAL_ARROWS),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.cleanup_import_field)) },
                    textStyle = LocalTextStyle.current.copy(fontFeatureSettings = LITERAL_ARROWS).contentDirection(),
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // One live region for both counts, so TalkBack reads them as
                // they change while the user pastes or types (#68).
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    val found = preview.rules.size
                    Text(
                        text = if (found == 0) {
                            stringResource(R.string.cleanup_import_none)
                        } else {
                            pluralStringResource(R.plurals.cleanup_import_found, found, found)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (preview.skippedLines > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.cleanup_import_skipped,
                                preview.skippedLines,
                                preview.skippedLines,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            MarmaladeTextButton(onClick = { onImport(preview.rules) }, enabled = preview.rules.isNotEmpty()) {
                Text(stringResource(R.string.cleanup_import))
            }
        },
        dismissButton = {
            MarmaladeTextButton(onClick = onDismiss) { Text(stringResource(R.string.cleanup_cancel)) }
        },
    )
}

/**
 * Manrope draws "->" as a single arrow glyph (a contextual ligature). Where the
 * user reads or types the `wrong -> right` format, show the two characters,
 * or they'd copy an arrow that the import doesn't take as the separator.
 */
private const val LITERAL_ARROWS = "calt 0, liga 0"
