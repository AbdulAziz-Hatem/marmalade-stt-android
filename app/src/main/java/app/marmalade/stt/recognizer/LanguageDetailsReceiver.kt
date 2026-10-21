package app.marmalade.stt.recognizer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.install.ModelInstaller
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * `RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS` (#90): the ordered broadcast
 * an app sends a recognizer's package to learn which languages it
 * recognizes. The answer is [languageDetails].
 *
 * Only callers that address our package (or this component) reach it:
 * manifest receivers get no implicit broadcasts since Android 8, and
 * `RecognizerIntent.getVoiceDetailsIntent()` only finds receivers that a
 * recognizer names in the meta-data of its `ACTION_WEB_SEARCH` activity,
 * which we don't have (we're not a search app).
 */
@AndroidEntryPoint
class LanguageDetailsReceiver : BroadcastReceiver() {

    @Inject lateinit var speechSettings: SpeechSettings
    @Inject lateinit var installer: ModelInstaller

    override fun onReceive(context: Context, intent: Intent) {
        // Only an ordered broadcast carries an answer back.
        if (intent.action != RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS || !isOrderedBroadcast) return
        val onlyPreference = intent.getBooleanExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        val pending = goAsync()
        scope.launch {
            try {
                runCatching {
                    languageDetails(
                        speechSettings.current(),
                        Locale.getDefault().toLanguageTag(),
                        onlyPreference,
                    ) { installer.installedModel(it) != null }
                }.onSuccess {
                    pending.setResult(Activity.RESULT_OK, null, it)
                }.onFailure {
                    // Unreadable settings (DataStore's IOException): no answer, rather than a crash on another app's broadcast.
                    Log.w(TAG, "Couldn't answer a language-details request", it)
                    pending.resultCode = Activity.RESULT_CANCELED
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "LanguageDetails"

        /** Outlives each receiver instance (one per broadcast); one failure never cancels the others. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * The language-details answer under [prefs]: `EXTRA_LANGUAGE_PREFERENCE`, the
 * language the user speaks as a BCP-47 tag (the chosen recognition
 * language, else the phone's [systemLocale]), and unless [onlyPreference]
 * (`EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE`) `EXTRA_SUPPORTED_LANGUAGES`,
 * what the installed engines transcribe now ([RecognitionLanguages.installed]).
 *
 * @param installed whether an engine's model is installed.
 */
fun languageDetails(
    prefs: SpeechPrefs,
    systemLocale: String,
    onlyPreference: Boolean,
    installed: (String) -> Boolean,
): Bundle = Bundle().apply {
    putString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, prefs.recognitionLanguage.ifEmpty { systemLocale })
    if (!onlyPreference) {
        val languages = recognitionLanguages(prefs, systemLocale, installed)
        putStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, ArrayList(languages.installed))
    }
}
