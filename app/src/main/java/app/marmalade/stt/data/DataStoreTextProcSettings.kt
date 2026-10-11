package app.marmalade.stt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import app.marmalade.stt.textproc.TextProcConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [TextProcSettings] in the app's Preferences DataStore. A key that was never
 * written reads as the [TextProcConfig] default, so changing a default in
 * `:textproc` reaches everyone who hasn't touched that setting. The filler
 * word list and the phrases-in-silence filter (#42) aren't user settings; they
 * always come from `:textproc`. The language comes from the speech settings
 * ([SpeechPrefs.cleanupLanguage]), and so do the active engine's
 * [app.marmalade.stt.install.SttEngineDescriptor.lowerCaseLatin] and its
 * family's phantom phrases ([app.marmalade.stt.install.EngineFamily.recognizer], #57).
 */
@Singleton
class DataStoreTextProcSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    speechSettings: SpeechSettings,
) : TextProcSettings {

    override val config: Flow<TextProcConfig> = dataStore.data
        .map { it.toConfig() }
        .combine(speechSettings.prefs.map { it.cleanupLanguage() to it.activeEngine }) { config, (language, engine) ->
            config.copy(language = language, lowerCaseLatin = engine.lowerCaseLatin, recognizer = engine.family.recognizer)
        }
        // Other screens' settings share the file; don't rebuild pipelines for them.
        .distinctUntilChanged()

    override suspend fun setRemoveFillers(on: Boolean) {
        dataStore.edit { it[REMOVE_FILLERS] = on }
    }

    override suspend fun setSpokenCommands(on: Boolean) {
        dataStore.edit { it[SPOKEN_COMMANDS] = on }
    }

    override suspend fun setSpokenPunctuation(on: Boolean) {
        dataStore.edit { it[SPOKEN_PUNCTUATION] = on }
    }

    override suspend fun setNumbersAsDigits(on: Boolean) {
        dataStore.edit { it[NUMBERS_AS_DIGITS] = on }
    }

    override suspend fun setCapitalizeSentences(on: Boolean) {
        dataStore.edit { it[CAPITALIZE] = on }
    }

    override suspend fun setReplacements(rules: List<Replacement>) {
        dataStore.edit { it[REPLACEMENTS] = TextProcJson.encodeReplacements(rules) }
    }

    override suspend fun setSnippets(snippets: List<Snippet>) {
        dataStore.edit { it[SNIPPETS] = TextProcJson.encodeSnippets(snippets) }
    }

    private companion object {
        val REMOVE_FILLERS = booleanPreferencesKey("cleanup_remove_fillers")
        val SPOKEN_COMMANDS = booleanPreferencesKey("cleanup_spoken_commands")
        val SPOKEN_PUNCTUATION = booleanPreferencesKey("cleanup_spoken_punctuation")
        val NUMBERS_AS_DIGITS = booleanPreferencesKey("cleanup_numbers_as_digits")
        val CAPITALIZE = booleanPreferencesKey("cleanup_capitalize_sentences")
        val REPLACEMENTS = stringPreferencesKey("cleanup_replacements_json")
        val SNIPPETS = stringPreferencesKey("cleanup_snippets_json")

        val DEFAULTS = TextProcConfig()

        val Preferences.spokenCommands: Boolean get() = this[SPOKEN_COMMANDS] ?: DEFAULTS.spokenCommands

        fun Preferences.toConfig() = TextProcConfig(
            // phantomFilter: always the default, NEAR_SILENT (#42). A value
            // stored under "cleanup_phantom_filter" before #42 is ignored.
            removeFillers = this[REMOVE_FILLERS] ?: DEFAULTS.removeFillers,
            spokenCommands = spokenCommands,
            // Punctuation words belong to Voice commands (#42): off while it
            // is off, the stored choice kept for when it's back on.
            spokenPunctuation = spokenCommands && (this[SPOKEN_PUNCTUATION] ?: DEFAULTS.spokenPunctuation),
            numbersAsDigits = this[NUMBERS_AS_DIGITS] ?: DEFAULTS.numbersAsDigits,
            capitalizeSentences = this[CAPITALIZE] ?: DEFAULTS.capitalizeSentences,
            replacements = TextProcJson.decodeReplacements(this[REPLACEMENTS]),
            snippets = TextProcJson.decodeSnippets(this[SNIPPETS]),
        )
    }
}
