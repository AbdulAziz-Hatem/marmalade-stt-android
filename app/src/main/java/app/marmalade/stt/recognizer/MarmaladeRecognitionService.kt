package app.marmalade.stt.recognizer

import android.Manifest
import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import app.marmalade.stt.BuildConfig
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.speech.ListenSurface
import app.marmalade.stt.speech.LivePipeline
import app.marmalade.stt.speech.MicCapture
import app.marmalade.stt.speech.SpeechEngineManager
import app.marmalade.stt.speech.StreamCapture
import app.marmalade.stt.speech.openMicCapture
import app.marmalade.stt.ui.screen.Dictation
import dagger.hilt.android.AndroidEntryPoint
import java.io.Closeable
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android's speech-recognition service (ARCHITECTURE.md D6, "Speech APIs"),
 * for apps that use `SpeechRecognizer`: with Marmalade STT chosen as the
 * device's speech recognition service, or when an app binds this component
 * by name.
 *
 * Each request is a [ServiceSession] over a [OneShotSession], shaped by the
 * caller's extras ([RecognitionRequest], #90): its language picks the engine
 * ([routeLanguage]), and it may ask for a segmented session, its own audio
 * stream (`EXTRA_AUDIO_SOURCE`), a pause or a minimum length. Results go
 * through `:textproc`; partials get its cheap `cleanPartial`.
 *
 * The mic is opened for the calling app (Android 12+, [callerAttribution]):
 * other apps ask while ours is in the background, where the mic would be
 * refused to us (#90, Duolingo's "could not recognize the input").
 *
 * `stopListening` is a graceful stop (what was heard is still transcribed);
 * `cancel` aborts at once. The mic is closed whenever the session ends. A
 * new start cancels a session of ours still running (the framework is done
 * with it) and waits for it to let go of the engine, rather than failing
 * busy; a caller's stream is closed when its request ends ([ServiceRequests]).
 * No request runs for ever: [RecognitionRequest.maxLengthMs] from the mic,
 * [StreamCapture]'s limits for a stream. And the user's own use wins: their
 * keyboard, Dictate or speak-now sheet starting ends a request
 * (`ERROR_RECOGNIZER_BUSY`, `SessionArbiter`).
 */
@AndroidEntryPoint
class MarmaladeRecognitionService : RecognitionService() {

    @Inject lateinit var engines: SpeechEngineManager
    @Inject lateinit var speechSettings: SpeechSettings
    @Inject lateinit var textProcSettings: TextProcSettings
    @Inject lateinit var installer: ModelInstaller

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pipeline by lazy { LivePipeline(scope, textProcSettings) }
    private val requests = ServiceRequests(scope, stopEngine = { engines.stop() })

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        val requestedAt = SystemClock.elapsedRealtime()
        val request = RecognitionRequest.from(recognizerIntent)
        // Ours to close from here on. Taken before anything copies the extras (the debug log
        // below): see RecognitionRequest.from.
        val descriptor = request.audioSource?.let { audioSource(recognizerIntent) }
        val fromFile = descriptor?.let { StreamCapture.isRegularFile(it.fileDescriptor) } ?: false
        debugLog { "startListening uid=${listener.callingUid} extras=${describe(recognizerIntent.extras)}" }
        val audio = audioInputFor(request, descriptor, fromFile, listener) ?: return
        val client = CallbackClient(listener)
        val run = ServiceSession(request, client)
        requests.start(audio.stream, onStop = run::stopped) {
            val prefs = speechSettings.current()
            val route = withContext(Dispatchers.IO) { routeLanguage(prefs, request.language, ::isInstalled) }
            debugLog { "route ${request.language} -> $route" }
            if (route is LanguageRoute.Unavailable) {
                client.finish { error(languageError(route)) }
                return@start
            }
            val engine = (route as? LanguageRoute.Engine)?.engine
            val cleanupLanguage = request.cleanupLanguage(prefs, engine)
            val oneShot = OneShotSession(
                listen = {
                    repeatingStop(
                        engines.listen(
                            ListenSurface.APPS,
                            language = request.language,
                            requestedAt = requestedAt,
                            audioInput = audio.open,
                            engine = engine,
                            pauseSeconds = request.enginePauseSeconds(),
                            shortCommand = request.shortCommand(fromFile),
                            // The user's own use wins (Max, 2026-10-21): their keyboard, Dictate or sheet ends this.
                            preemptible = true,
                        ),
                    ).flowOn(Dispatchers.Default)
                },
                stopEngine = engines::stop,
                pipeline = { if (cleanupLanguage != null) pipeline.get(cleanupLanguage) else pipeline.get() },
                end = request.end(fromFile),
                minimumLengthMs = request.minimumLengthMs ?: 0,
                maxLengthMs = request.maxLengthMs(),
            )
            run.run(oneShot)
        }
    }

    override fun onStopListening(listener: Callback) {
        debugLog { "stopListening" }
        requests.stop()
    }

    override fun onCancel(listener: Callback) {
        debugLog { "cancel" }
        requests.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCheckRecognitionSupport(recognizerIntent: Intent, supportCallback: SupportCallback) {
        debugLog { "checkRecognitionSupport extras=${describe(recognizerIntent.extras)}" }
        scope.launch {
            val languages = withContext(Dispatchers.IO) {
                recognitionLanguages(speechSettings.current(), Locale.getDefault().toLanguageTag(), ::isInstalled)
            }
            val support = RecognitionSupport.Builder()
                .setInstalledOnDeviceLanguages(languages.installed)
                .setSupportedOnDeviceLanguages(languages.downloadable)
                .build()
            try {
                supportCallback.onSupportResult(support)
            } catch (e: RuntimeException) {
                // The caller went away while the settings were read; the binder
                // error comes back rethrown as a RuntimeException.
                Log.w(TAG, "Client gone", e)
            }
        }
    }

    /**
     * Another app asks for the model its request needs (#90). Ready when an
     * installed engine transcribes the language. Otherwise we don't download
     * on another app's say-so (hundreds of MB, D10: downloads are the user's
     * choice); it gets the language error a request would get, and the user
     * downloads an engine on the Engines tab.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTriggerModelDownload(
        recognizerIntent: Intent,
        attributionSource: AttributionSource,
        listener: ModelDownloadListener,
    ) {
        debugLog { "triggerModelDownload extras=${describe(recognizerIntent.extras)}" }
        val language = RecognitionRequest.from(recognizerIntent).language
        scope.launch {
            val prefs = speechSettings.current()
            val outcome = withContext(Dispatchers.IO) {
                val route = routeLanguage(prefs, language, ::isInstalled)
                when {
                    route is LanguageRoute.Unavailable -> languageError(route)
                    route == LanguageRoute.Active && !isInstalled(prefs.activeEngine.id) ->
                        languageError(LanguageRoute.Unavailable(downloadable = true))
                    else -> null
                }
            }
            try {
                if (outcome == null) listener.onSuccess() else listener.onError(outcome)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Client gone", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun isInstalled(engineId: String): Boolean = installer.installedModel(engineId) != null

    /**
     * A request's audio: [open] makes the capture the engine reads; [stream]
     * is the caller's stream it reads from, or null for the mic.
     */
    private class AudioInput(val stream: Closeable?, val open: (hopSamples: Int) -> MicCapture)

    /**
     * Where the request's audio comes from: the caller's stream ([descriptor],
     * `EXTRA_AUDIO_SOURCE`), or the mic opened for the caller. Null, with an
     * error sent (and the descriptor closed), when the stream can't be read.
     */
    private fun audioInputFor(request: RecognitionRequest, descriptor: ParcelFileDescriptor?, fromFile: Boolean, listener: Callback): AudioInput? {
        // Also for a stream: the framework then skips its own mic-permission check, which a
        // caller that only hands us audio (a file) needn't pass.
        val attribution = callerAttribution(listener)
        val format = request.audioSource
        if (format == null) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // Our own grant; Android checks the calling app's before we get here.
                listener.sendOrLog { error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
                return null
            }
            return AudioInput(stream = null) { hop -> openMicCapture(hop, hardwareEffects = true, attribution = attribution) }
        }
        // Unparcelling the intent gave us our own descriptor (the caller closes its own).
        if (descriptor == null || !StreamCapture.supports(format)) {
            Log.w(TAG, "Unusable EXTRA_AUDIO_SOURCE ($format)")
            descriptor?.let { runCatching { it.close() } }
            listener.sendOrLog { error(SpeechRecognizer.ERROR_AUDIO) }
            return null
        }
        // Closed by the capture when the engine is done with it, and by ServiceRequests when the request ends.
        val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        // A pipe or socket is written as the caller records (Waze): its lag is measured (#118).
        return AudioInput(stream) { hop -> StreamCapture(stream, format, hop, paced = !fromFile) }
    }

    // Only called for a request that has the extra, which callers send from Android 13. Reading it
    // makes a descriptor of our own (Parcel.readFileDescriptor dups): call it once.
    @SuppressLint("InlinedApi")
    @Suppress("DEPRECATION")
    private fun audioSource(intent: Intent): ParcelFileDescriptor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, ParcelFileDescriptor::class.java)
        } else {
            intent.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }

    /**
     * The context to open the mic with for [listener]'s app (Android 12+):
     * Android then checks and blames the calling app's mic access, with us as
     * its proxy. Without it the mic is ours, and Android refuses it while our
     * app is in the background, which it is whenever another app asks
     * (`ERROR_INSUFFICIENT_PERMISSIONS` at once, #90). Made here, on the
     * service's thread, before `onStartListening` returns: that is how the
     * framework knows the mic will be opened for the caller.
     */
    private fun callerAttribution(listener: Callback): Context? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createContext(ContextParams.Builder().setNextAttributionSource(listener.callingAttributionSource).build())
        } else {
            null
        }

    /**
     * [RecognitionClient] over the framework's callback. A mid-session call to
     * a client that has gone away aborts the session (and with it the mic);
     * the error sent first clears the framework's session, so the next
     * caller isn't turned away busy.
     */
    private inner class CallbackClient(private val listener: Callback) : RecognitionClient {
        override fun readyForSpeech() = send { readyForSpeech(Bundle()) }
        override fun beginningOfSpeech() = send { beginningOfSpeech() }
        override fun rmsChanged(rmsDb: Float) = send { rmsChanged(clientRms(rmsDb)) }
        override fun partialResults(hypotheses: List<String>) = send { partialResults(resultsBundle(hypotheses, confidence = false)) }
        override fun endOfSpeech() = finish { endOfSpeech() }
        override fun results(hypotheses: List<String>) = finish { results(resultsBundle(hypotheses, confidence = true)) }

        // Segmented sessions are Android 13+; an older framework has no callback for them.
        override fun segmentResults(hypotheses: List<String>) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) send { segmentResults(resultsBundle(hypotheses, confidence = true)) }
        }

        override fun endOfSegmentedSession() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) finish { endOfSegmentedSession() }
        }

        override fun error(code: Int) = finish { error(code) }

        private inline fun send(block: Callback.() -> Unit) {
            try {
                listener.block()
            } catch (e: RemoteException) {
                Log.w(TAG, "Client gone; cancelling the session", e)
                listener.sendOrLog { error(SpeechRecognizer.ERROR_CLIENT) }
                throw CancellationException("Recognition client gone")
            }
        }

        /** A call that ends the session, or follows its end: a client that has gone away is only logged. */
        fun finish(block: Callback.() -> Unit) = listener.sendOrLog(block)
    }

    private fun Callback.sendOrLog(block: Callback.() -> Unit) {
        try {
            block()
        } catch (e: RemoteException) {
            Log.w(TAG, "Client gone", e)
        }
    }

    private companion object {
        const val TAG = "RecognitionService"

        /** Debug builds log every call and extra received (#90); release builds never do. */
        inline fun debugLog(message: () -> String) {
            if (BuildConfig.DEBUG) Log.d(TAG, message())
        }

        /**
         * Extras as `key=value`, for the debug log. The audio source isn't
         * read: `Intent.getExtras` is a copy, and reading a descriptor out of
         * it makes another one (a dup that only the garbage collector closes).
         */
        @SuppressLint("InlinedApi")
        @Suppress("DEPRECATION")
        fun describe(extras: Bundle?): String =
            extras?.keySet()?.joinToString(prefix = "{", postfix = "}") {
                // A value only the caller's classes can read throws; it is logged as such.
                val value = if (it == RecognizerIntent.EXTRA_AUDIO_SOURCE) "(descriptor)" else runCatching { extras.get(it) }.getOrElse { e -> "(unreadable: ${e.javaClass.simpleName})" }
                "${it.removePrefix("android.speech.extra.")}=$value"
            } ?: "{}"

        /**
         * The results bundle: [hypotheses] in `RESULTS_RECOGNITION`, and with
         * [confidence] a `CONFIDENCE_SCORES` entry of
         * [RecognitionRequest.CONFIDENCE_UNAVAILABLE] for each.
         */
        fun resultsBundle(hypotheses: List<String>, confidence: Boolean) = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, ArrayList(hypotheses))
            if (confidence) {
                putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, FloatArray(hypotheses.size) { RecognitionRequest.CONFIDENCE_UNAVAILABLE })
            }
        }

        /**
         * Google's recognizer reports rmsChanged on roughly -2 (quiet) to 10
         * (loud) and apps size their meters for that, so the meter's 0..1
         * level is mapped onto it.
         */
        fun clientRms(rmsDb: Float): Float = -2f + 12f * Dictation.levelFraction(rmsDb)
    }
}

