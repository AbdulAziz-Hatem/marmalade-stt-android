package app.marmalade.stt.ui.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import app.marmalade.stt.textproc.TextProcConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The replacement dictionary and snippet editors ([DictionaryScreen],
 * [SnippetsScreen]) over [TextProcSettings]. Each edit writes the whole new
 * list; the list rules (one entry per key, merge on import) are in
 * [CleanupLists].
 *
 * Edits name the entry they change by its value (`original`), not by its row
 * index, and are applied to the list as last persisted, one at a time. Two
 * quick deletes therefore can't hit the wrong row while the first write is
 * still on its way back through [TextProcSettings.config].
 */
@HiltViewModel
class CleanupListsViewModel @Inject constructor(
    private val settings: TextProcSettings,
) : ViewModel() {

    /** Null until the settings have been read, so the list never flashes "empty". */
    val config: StateFlow<TextProcConfig?> = settings.config
        .catch { e ->
            // Unreadable settings file: show (and dictate with) the defaults.
            Log.w(TAG, "Couldn't read the cleanup settings", e)
            emit(TextProcConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saveFailed = MutableStateFlow(false)

    /** A save failed; the screen shows a snackbar, then calls [saveFailedShown]. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val writes = Mutex()

    /** Adds [rule] ([original] null) or replaces [original] with it. */
    fun saveRule(original: Replacement?, rule: Replacement) = editRules { rules ->
        CleanupLists.upsertReplacement(rules, rules.indexOrNull(original), rule)
    }

    fun deleteRule(rule: Replacement) = editRules { rules ->
        rules.indexOrNull(rule)?.let { CleanupLists.deleteAt(rules, it) } ?: rules
    }

    fun importRules(imported: List<Replacement>) = editRules { rules ->
        CleanupLists.mergeImported(rules, imported)
    }

    /** Adds [snippet] ([original] null) or replaces [original] with it. */
    fun saveSnippet(original: Snippet?, snippet: Snippet) = editSnippets { snippets ->
        CleanupLists.upsertSnippet(snippets, snippets.indexOrNull(original), snippet)
    }

    fun deleteSnippet(snippet: Snippet) = editSnippets { snippets ->
        snippets.indexOrNull(snippet)?.let { CleanupLists.deleteAt(snippets, it) } ?: snippets
    }

    fun saveFailedShown() {
        _saveFailed.value = false
    }

    private fun editRules(edit: (List<Replacement>) -> List<Replacement>) = save {
        val rules = settings.config.first().replacements
        val updated = edit(rules)
        if (updated != rules) settings.setReplacements(updated)
    }

    private fun editSnippets(edit: (List<Snippet>) -> List<Snippet>) = save {
        val snippets = settings.config.first().snippets
        val updated = edit(snippets)
        if (updated != snippets) settings.setSnippets(updated)
    }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                writes.withLock { write() }
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't save the cleanup lists", e)
                _saveFailed.value = true
            }
        }
    }

    // Null for "add": an entry that was deleted meanwhile is added back as new
    // rather than silently dropped.
    private fun <T> List<T>.indexOrNull(item: T?): Int? =
        item?.let { indexOf(it) }?.takeIf { it >= 0 }

    private companion object {
        const val TAG = "CleanupListsViewModel"
    }
}
