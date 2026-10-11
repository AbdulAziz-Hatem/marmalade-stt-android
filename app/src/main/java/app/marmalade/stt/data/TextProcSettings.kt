package app.marmalade.stt.data

import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import app.marmalade.stt.textproc.TextProcConfig
import kotlinx.coroutines.flow.Flow

/**
 * The user's `:textproc` cleanup settings. Every listening surface (the Dictate
 * screen, the voice IME, the RecognizerIntent activity and the
 * RecognitionService) builds its `TextPipeline` from [config] and rebuilds it
 * when [config] emits. [config] also carries the language the cleanup works in
 * (`TextProcConfig.language`, [SpeechPrefs.cleanupLanguage]), so a change of
 * engine or Settings → Recognition language rebuilds the pipelines too.
 *
 * The phrases-in-silence filter isn't a setting (#42): [config] always has
 * `PhantomFilter.NEAR_SILENT`.
 *
 * The setters persist; [config] then emits the new value. They throw
 * `IOException` when the settings file can't be written.
 */
interface TextProcSettings {
    /** The current config. Emits again whenever the user changes a setting. */
    val config: Flow<TextProcConfig>

    suspend fun setRemoveFillers(on: Boolean)

    suspend fun setSpokenCommands(on: Boolean)

    suspend fun setSpokenPunctuation(on: Boolean)

    suspend fun setNumbersAsDigits(on: Boolean)

    suspend fun setCapitalizeSentences(on: Boolean)

    /** Replaces the whole replacement dictionary, in this order (the first of two equal left sides wins). */
    suspend fun setReplacements(rules: List<Replacement>)

    /** Replaces the whole snippet list, in this order. */
    suspend fun setSnippets(snippets: List<Snippet>)
}
