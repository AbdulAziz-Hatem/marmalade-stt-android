package app.marmalade.stt.benchmark

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.FinishedUtterances
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig
import app.marmalade.stt.ui.screen.Dictation
import java.util.Locale

/**
 * Turns an engine's utterances from the A/B benchmark into the text dictation
 * would have written (#45): each utterance is a Final, committed in order with
 * [Dictation.commitFinal], the Dictate screen's own step, through a
 * [TextPipeline] built from the user's cleanup settings ([config], as
 * `TextProcSettings.config` gives them).
 *
 * Language: dictation cleans up in [SpeechPrefs.cleanupLanguage], which
 * depends on the active engine (its language hint, else its only language,
 * else the spoken one). Here each engine is cleaned as if it were the active
 * engine ([SpeechPrefs.cleanupLanguageFor]), not in the active engine's
 * language: an English-only engine benchmarked while a multilingual engine on
 * a German phone is active still gets the English rules, as it would in use.
 *
 * Every Final counts as not near-silent: the take keeps no per-utterance
 * level from before the gain, so phantom phrases ("Thank you.") are kept,
 * as a spoken one would be. Sound tags and decoder loops still go.
 * The engine's pre-speech phantom (Parakeet ja's 「うん」, #57's lists) goes as in a
 * session, when more speech follows it ([FinishedUtterances]).
 *
 * Not thread-safe; the runner calls it from one engine's turn at a time.
 */
class TakeCleanup(
    private val config: TextProcConfig,
    private val prefs: SpeechPrefs,
    private val systemLocale: String = Locale.getDefault().toLanguageTag(),
) {
    /** One pipeline per cleanup language: building one compiles its rules. */
    private val pipelines = mutableMapOf<String, TextPipeline>()

    /** [utterances] (raw engine text, one per VAD utterance) after the cleanup dictation runs on [engine]'s Finals. */
    fun clean(engine: SttEngineDescriptor, utterances: List<String>): String {
        val language = prefs.cleanupLanguageFor(engine, systemLocale)
        val recognizer = engine.family.recognizer
        val pipeline = pipelines.getOrPut("$language ${engine.lowerCaseLatin} $recognizer") {
            TextPipeline(config.copy(language = language, lowerCaseLatin = engine.lowerCaseLatin, recognizer = recognizer))
        }
        // As a session does (FinishedUtterances): the pre-speech phantom goes when speech follows.
        val first = utterances.indexOfFirst { it.isNotBlank() }
        val spoken = if (first >= 0 && utterances.drop(first + 1).any { it.isNotBlank() } &&
            engine.isPreSpeechPhantom(utterances[first])
        ) utterances.filterIndexed { i, _ -> i != first } else utterances
        return spoken.fold("") { transcript, text ->
            Dictation.commitFinal(pipeline, transcript, SttEvent.Final(text, nearSilent = false))
        }
    }
}
