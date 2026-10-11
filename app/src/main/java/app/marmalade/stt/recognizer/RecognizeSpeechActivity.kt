package app.marmalade.stt.recognizer

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.marmalade.stt.MainActivity
import app.marmalade.stt.data.AppearanceSettings
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.speech.EngineHold
import app.marmalade.stt.speech.ListenSurface
import app.marmalade.stt.speech.LivePipeline
import app.marmalade.stt.speech.SpeechEngineManager
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.system.SettingsLink
import app.marmalade.stt.system.SystemSettings
import app.marmalade.stt.ui.screen.Dictation
import app.marmalade.stt.ui.theme.MarmaladeSttTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * `ACTION_RECOGNIZE_SPEECH` (ARCHITECTURE.md D6): the "speak now" sheet that
 * mic buttons in other apps open. It listens for one request
 * ([OneShotSession]) and returns the text in `EXTRA_RESULTS`, or through
 * `EXTRA_RESULTS_PENDINGINTENT` when the caller passed one.
 *
 * Result codes: `RESULT_OK` with text; `RESULT_CANCELED` when the user closes
 * the sheet while it listens; after a problem is shown, the matching
 * `RecognizerIntent.RESULT_*` error code.
 *
 * The mic is never held in the background: leaving the sheet (home, another
 * app) aborts the session and closes it.
 *
 * From its first listen until it closes, the sheet holds the engine
 * ([SpeechEngineManager.hold], #78), so Retry doesn't reload the model.
 */
@AndroidEntryPoint
class RecognizeSpeechActivity : ComponentActivity() {

    @Inject lateinit var engines: SpeechEngineManager
    @Inject lateinit var speechSettings: SpeechSettings
    @Inject lateinit var textProcSettings: TextProcSettings
    @Inject lateinit var appearanceSettings: AppearanceSettings

    private val pipeline by lazy { LivePipeline(lifecycleScope, textProcSettings) }
    private var sheet by mutableStateOf(SheetState())
    private val level = mutableFloatStateOf(0f)
    private var session: Job? = null
    private var oneShot: OneShotSession? = null

    /** Keeps the model loaded between this sheet's listens (Retry), until it closes. */
    private var engineHold: EngineHold? = null

    /** The permission dialog is up; the pause it causes is not the user leaving. */
    private var askingForMic = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askingForMic = false
        if (granted) start() else sheet = SpeechSheet.micDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prompt = intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT)
        setContent {
            // Nothing until Settings → Appearance is read (milliseconds), so the sheet never opens in the wrong theme.
            val appearance = appearanceSettings.appearance.collectAsStateWithLifecycle(initialValue = null).value
                ?: return@setContent
            val darkTheme = appearance.mode.isDark(isSystemInDarkTheme())
            // The bars' icons follow the chosen mode, not the system's (as in MainActivity).
            LaunchedEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MarmaladeSttTheme(darkTheme = darkTheme, themePreset = appearance.preset) {
                RecognizeSpeechSheet(
                    state = sheet,
                    prompt = prompt,
                    level = { level.floatValue },
                    onCancel = ::close,
                    onDone = ::finishListening,
                    onLock = ::lock,
                    onRetry = ::begin,
                    onOpenApp = ::openApp,
                    onOpenSettings = ::openAppSettings,
                )
            }
        }
        begin()
    }

    override fun onStop() {
        super.onStop()
        // The user left (home, recents, another app): never keep the mic.
        if (!isChangingConfigurations && !askingForMic && !isFinishing) close()
    }

    override fun onDestroy() {
        // Finished, or recreated (the new sheet takes its own hold).
        closeEngineHold()
        super.onDestroy()
    }

    private fun begin() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            askingForMic = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun start() {
        if (session?.isActive == true) return
        val requestedAt = SystemClock.elapsedRealtime()
        if (engineHold == null) engineHold = engines.hold(preload = false) // the listen below loads
        sheet = SpeechSheet.started(modelLoaded = engines.engineOrNull()?.isLoaded == true)
        level.floatValue = 0f
        val language = requestedLanguage(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        session = lifecycleScope.launch {
            // A caller's language applies to this request only (engine hint and cleanup).
            val cleanupLanguage = language?.let { speechSettings.current().forRequestLanguage(it).cleanupLanguage() }
            val request = OneShotSession(
                listen = { engines.listen(ListenSurface.APPS, language = language, requestedAt = requestedAt).flowOn(Dispatchers.Default) },
                stopEngine = engines::stop,
                pipeline = { if (cleanupLanguage != null) pipeline.get(cleanupLanguage) else pipeline.get() },
            )
            oneShot = request
            // Pressed and held before this request existed: lock it too.
            if (sheet.locked) request.lock()
            val outcome = request.run { event ->
                if (event is SttEvent.Level) {
                    level.floatValue = Dictation.levelFraction(event.rmsDb)
                } else {
                    sheet = SpeechSheet.reduce(sheet, event)
                }
            }
            level.floatValue = 0f
            if (outcome is OneShotOutcome.Text) deliver(outcome.text) else sheet = SpeechSheet.ended(outcome)
        }
    }

    /** Press-and-hold on the mic (#43): nothing ends the request on its own until a tap. */
    private fun lock() {
        if (!sheet.canLock) return
        sheet = SpeechSheet.locking(sheet)
        oneShot?.lock()
    }

    /** Done, or a tap on the mic (which also releases a lock): transcribe what was heard, then return it. */
    private fun finishListening() {
        if (session?.isActive != true) return
        sheet = SpeechSheet.stopping(sheet)
        engines.stop()
    }

    private fun deliver(text: String) {
        val results = arrayListOf(text)
        val pending = pendingResultIntent()
        if (pending != null) {
            val fillIn = Intent()
                .putExtras(intent.getBundleExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT_BUNDLE) ?: Bundle())
                .putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results)
            try {
                pending.send(this, RESULT_OK, fillIn)
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "The caller's result PendingIntent was cancelled", e)
            }
            setResult(RESULT_OK)
        } else {
            setResult(RESULT_OK, Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results))
        }
        closeEngineHold()
        finish()
    }

    /** Close the sheet: abort any session and return the current result code. */
    private fun close() {
        session?.cancel()
        closeEngineHold()
        setResult(sheet.resultCode)
        finish()
    }

    /** The model may be freed now (after the running session, if any). */
    private fun closeEngineHold() {
        engineHold?.close()
        engineHold = null
    }

    private fun openApp() {
        close()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openAppSettings() {
        close()
        SystemSettings.open(this, SettingsLink.APP_DETAILS)
    }

    @Suppress("DEPRECATION")
    private fun pendingResultIntent(): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, PendingIntent::class.java)
        } else {
            intent.getParcelableExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT)
        }

    private companion object {
        const val TAG = "RecognizeSpeech"
    }
}
