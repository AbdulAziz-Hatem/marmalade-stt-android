package app.marmalade.stt.recognizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.os.SystemClock
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.speech.ListenSurface
import app.marmalade.stt.speech.LivePipeline
import app.marmalade.stt.speech.SpeechEngineManager
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.ui.screen.Dictation
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Android's speech-recognition service (ARCHITECTURE.md D6), for apps that use
 * `SpeechRecognizer`: with Marmalade STT chosen as the device's speech
 * recognition service, or when an app binds this component by name.
 *
 * One request, one result ([OneShotSession]). Events map onto the callback:
 *
 * | Engine            | Callback                                         |
 * |-------------------|--------------------------------------------------|
 * | Listening (mic open, the model may still load; #78) | readyForSpeech, once |
 * | first SpeechStarted | beginningOfSpeech                              |
 * | Level (dBFS)      | rmsChanged, on the -2..10 scale apps expect      |
 * | Partial           | partialResults, if the intent asked for them     |
 * | session ends      | endOfSpeech (if speech was heard), then results or error |
 *
 * Results go through `:textproc`; partials get its cheap `cleanPartial`
 * (loops and noise captions removed, possibly leaving "").
 *
 * `stopListening` is a graceful stop (what was heard is still transcribed);
 * `cancel` aborts at once. The mic is closed whenever the session ends.
 */
@AndroidEntryPoint
class MarmaladeRecognitionService : RecognitionService() {

    @Inject lateinit var engines: SpeechEngineManager
    @Inject lateinit var speechSettings: SpeechSettings
    @Inject lateinit var textProcSettings: TextProcSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pipeline by lazy { LivePipeline(scope, textProcSettings) }
    private var session: Job? = null

    /** The running request has heard speech and has not yet said endOfSpeech. */
    private var speechOpen = false

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        val requestedAt = SystemClock.elapsedRealtime()
        if (session?.isActive == true) {
            listener.sendOrLog { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Our own grant; Android checks the calling app's before we get here.
            listener.sendOrLog { error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
            return
        }
        val wantsPartials = recognizerIntent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        val language = requestedLanguage(recognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        speechOpen = false
        session = scope.launch {
            // A caller's language applies to this request only (engine hint and cleanup).
            val cleanupLanguage = language?.let { speechSettings.current().forRequestLanguage(it).cleanupLanguage() }
            val oneShot = OneShotSession(
                listen = { engines.listen(ListenSurface.APPS, language = language, requestedAt = requestedAt).flowOn(Dispatchers.Default) },
                stopEngine = engines::stop,
                pipeline = { if (cleanupLanguage != null) pipeline.get(cleanupLanguage) else pipeline.get() },
            )
            val ready = ReadyForSpeech()
            val outcome = oneShot.run { event ->
                if (ready.isDue(event)) listener.send { readyForSpeech(Bundle()) }
                when (event) {
                    SttEvent.SpeechStarted -> if (!speechOpen) {
                        speechOpen = true
                        listener.send { beginningOfSpeech() }
                    }
                    is SttEvent.Level -> listener.send { rmsChanged(clientRms(event.rmsDb)) }
                    is SttEvent.Partial ->
                        if (wantsPartials) listener.send { partialResults(resultsBundle(event.text)) }
                    else -> Unit
                }
            }
            endOfSpeech(listener)
            if (outcome is OneShotOutcome.Text) {
                listener.send { results(resultsBundle(outcome.text)) }
            } else {
                listener.send { error(speechRecognizerError(outcome)) }
            }
        }
    }

    override fun onStopListening(listener: Callback) {
        if (session?.isActive != true) return
        endOfSpeech(listener)
        engines.stop()
    }

    override fun onCancel(listener: Callback) {
        session?.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCheckRecognitionSupport(recognizerIntent: Intent, supportCallback: SupportCallback) {
        scope.launch {
            // The active engine's languages; "installed" once its model is downloaded.
            val languages = recognitionLanguages(speechSettings.current(), Locale.getDefault().toLanguageTag())
            val support = RecognitionSupport.Builder().apply {
                if (engines.isReady()) setInstalledOnDeviceLanguages(languages) else setSupportedOnDeviceLanguages(languages)
            }.build()
            try {
                supportCallback.onSupportResult(support)
            } catch (e: RuntimeException) {
                // The caller went away while the settings were read; the binder
                // error comes back rethrown as a RuntimeException.
                Log.w(TAG, "Client gone", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun endOfSpeech(listener: Callback) {
        if (!speechOpen) return
        speechOpen = false
        listener.sendOrLog { endOfSpeech() }
    }

    /** Call the client; if it has gone away, abort the session (and with it the mic). */
    private inline fun Callback.send(block: Callback.() -> Unit) {
        try {
            block()
        } catch (e: RemoteException) {
            Log.w(TAG, "Client gone; cancelling the session", e)
            throw CancellationException("Recognition client gone")
        }
    }

    private inline fun Callback.sendOrLog(block: Callback.() -> Unit) {
        try {
            block()
        } catch (e: RemoteException) {
            Log.w(TAG, "Client gone", e)
        }
    }

    private companion object {
        const val TAG = "RecognitionService"

        fun resultsBundle(text: String) = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        }

        /**
         * Google's recognizer reports rmsChanged on roughly -2 (quiet) to 10
         * (loud) and apps size their meters for that, so the meter's 0..1
         * level is mapped onto it.
         */
        fun clientRms(rmsDb: Float): Float = -2f + 12f * Dictation.levelFraction(rmsDb)
    }
}
