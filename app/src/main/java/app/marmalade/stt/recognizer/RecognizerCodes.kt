package app.marmalade.stt.recognizer

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.LanguageOptions
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent

// How a one-shot outcome, and the languages, are reported through Android's two speech APIs.

/**
 * When the RecognitionService says `readyForSpeech`: once per request, as
 * soon as the mic listens ([SttEvent.Listening], #78, before the model may
 * have loaded), or at [SttEvent.Ready] from an engine that sends no
 * Listening. Make one per request.
 */
class ReadyForSpeech {
    private var sent = false

    /** True for the event that should send `readyForSpeech`. */
    fun isDue(event: SttEvent): Boolean {
        if (sent || (event != SttEvent.Listening && event != SttEvent.Ready)) return false
        sent = true
        return true
    }
}

/** The activity result code for `ACTION_RECOGNIZE_SPEECH`. */
fun OneShotOutcome.recognizerResultCode(): Int = when (this) {
    is OneShotOutcome.Text -> Activity.RESULT_OK
    is OneShotOutcome.NoMatch -> RecognizerIntent.RESULT_NO_MATCH
    is OneShotOutcome.Failed -> when (kind) {
        SttErrorKind.MIC -> RecognizerIntent.RESULT_AUDIO_ERROR
        SttErrorKind.MODEL, SttErrorKind.RECOGNITION, SttErrorKind.BUSY -> RecognizerIntent.RESULT_CLIENT_ERROR
    }
}

/**
 * The `SpeechRecognizer.ERROR_*` code for an outcome without text. A missing
 * model is "language not available yet" where Android has that code (12+).
 */
// ERROR_LANGUAGE_UNAVAILABLE is 31+; sdkInt gates it (lint can't see through the parameter).
@SuppressLint("InlinedApi")
fun speechRecognizerError(outcome: OneShotOutcome, sdkInt: Int = Build.VERSION.SDK_INT): Int = when (outcome) {
    is OneShotOutcome.Text -> error("Not an error: $outcome")
    is OneShotOutcome.NoMatch ->
        if (outcome.heardSpeech) SpeechRecognizer.ERROR_NO_MATCH else SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    is OneShotOutcome.Failed -> when (outcome.kind) {
        SttErrorKind.MIC -> SpeechRecognizer.ERROR_AUDIO
        SttErrorKind.MODEL ->
            if (sdkInt >= Build.VERSION_CODES.S) SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE else SpeechRecognizer.ERROR_CLIENT
        SttErrorKind.RECOGNITION -> SpeechRecognizer.ERROR_CLIENT
        SttErrorKind.BUSY -> SpeechRecognizer.ERROR_RECOGNIZER_BUSY
    }
}

/**
 * The languages the RecognitionService reports for `onCheckRecognitionSupport`
 * (#41), as BCP-47 tags: what the active engine transcribes, honouring
 * Settings → Recognition language.
 *
 * - An engine run with a language hint ([SpeechPrefs.languageFor]) writes
 *   only that language, so only it is reported.
 * - Otherwise every language the engine knows, the spoken one
 *   ([SpeechPrefs.spokenLanguage]) first when it is among them.
 * - The phone's full tag ([systemLocale], e.g. "en-US") is added after its
 *   language, so a caller asking for its own locale finds it.
 */
fun recognitionLanguages(prefs: SpeechPrefs, systemLocale: String): List<String> {
    val engine = prefs.activeEngine
    val hint = prefs.languageFor(engine, systemLocale)
    val codes = if (hint != LanguageOptions.AUTO) {
        listOf(EngineLanguages.normalize(hint))
    } else {
        val spoken = prefs.spokenLanguage(systemLocale)
        EngineLanguages.of(engine).sorted().sortedByDescending { it == spoken }
    }
    val system = EngineLanguages.normalize(systemLocale)
    return codes.flatMap { if (it == system && systemLocale != it) listOf(it, systemLocale) else listOf(it) }
}

/** A caller's `RecognizerIntent.EXTRA_LANGUAGE` ([extra]), or null when it passed none or a blank one. */
fun requestedLanguage(extra: String?): String? = extra?.trim()?.takeIf { it.isNotEmpty() }
