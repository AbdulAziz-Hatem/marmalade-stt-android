package app.marmalade.stt.ui.onboarding

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import app.marmalade.stt.install.InstallException
import app.marmalade.stt.install.InstallFailure
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.marmalade.stt.R
import app.marmalade.stt.data.AppearanceSettings
import app.marmalade.stt.data.OnboardingSettings
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.ime.VoiceInputService
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.ModelDownloads
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.recognizer.MarmaladeRecognitionService
import app.marmalade.stt.speech.SpeedCalibrator
import app.marmalade.stt.ui.theme.ThemePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State and actions for [OnboardingScreen]. The step logic is
 * [OnboardingState] (pure); this class feeds it what the system says (mic
 * permission, enabled keyboards, installed FUTO packages, Android's speech
 * recognition service, the recommended
 * engine's install state and whether any engine is installed), keeps the
 * current step across process death (the saved instance state, and
 * [OnboardingSettings.resumeStep] for a first run killed in the foreground
 * or swiped away), and writes the "onboarding done" flag the first time the
 * guide reaches its end.
 *
 * System state is re-read by [refreshSystemState] on every ON_RESUME: every
 * step that changes it (permission dialog, app settings, keyboard settings,
 * FUTO's dialog, the Voice input picker) happens in another activity.
 *
 * The model step's engine is [RecommendedEngine]: [SpeedCalibrator.recommend]
 * (the CPU probe the first time, about a second), then that engine's install
 * state; it is selected in [SpeechSettings] when it finishes installing here.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installer: ModelInstaller,
    private val downloads: ModelDownloads,
    private val settings: OnboardingSettings,
    calibrator: SpeedCalibrator,
    speechSettings: SpeechSettings,
    private val appearance: AppearanceSettings,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val recommended = RecommendedEngine(
        scope = viewModelScope,
        recommend = calibrator::recommend,
        installState = installer::state,
        select = { id ->
            try {
                speechSettings.setSelectedEngine(id)
            } catch (e: IOException) {
                // Not fatal: the Engines tab can still pick it.
                Log.w(TAG, "Couldn't select $id", e)
            }
        },
    )

    /** The engine the model step offers; null while the phone is checked. */
    val recommendation: StateFlow<EngineRecommender.Recommendation?> = recommended.recommendation

    /** The recommended engine's live install state, for the embedded install card. */
    val modelState: StateFlow<InstallState> = recommended.state

    /**
     * The speech settings, for the recommended engine's speed on this phone
     * (the card's Speed tile); null until read.
     */
    val speechPrefs: StateFlow<SpeechPrefs?> = speechSettings.prefs
        .catch { e ->
            Log.w(TAG, "Couldn't read the speech settings", e)
            emit(SpeechPrefs())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the High contrast colours are on (the welcome step's switch, #68); null until read. */
    val highContrast: StateFlow<Boolean?> = appearance.appearance
        .map { it.preset == ThemePreset.HIGH_CONTRAST }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Serialises the switch's saves, so they land in tap order. */
    private val highContrastLock = Mutex()

    /**
     * Turns the High contrast preset on, or off again: back to the preset in
     * use before ([AppearanceSettings.setHighContrast]). Settings → Appearance
     * changes it too.
     */
    fun setHighContrast(on: Boolean) {
        viewModelScope.launch {
            highContrastLock.withLock {
                try {
                    appearance.setHighContrast(on)
                } catch (e: IOException) {
                    Log.w(TAG, "Couldn't save the colours", e)
                }
            }
        }
    }

    private val _futoPackage = MutableStateFlow(findFutoPackage())

    /** The FUTO Keyboard package to talk to, or null when none is installed. */
    val futoPackage: StateFlow<String?> = _futoPackage.asStateFlow()

    /**
     * Whether the saved instance state had a step. Read before anything
     * calls [update], which writes it (the install-state collector in init
     * does, straight away).
     */
    private val restoredFromInstanceState = savedState.contains(KEY_STEP)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** False until the saved step is restored; the screen draws only its background until then. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)

    /** A one-off snackbar message (a string resource), cleared by [messageShown]. */
    val message: StateFlow<Int?> = _message.asStateFlow()

    private var doneWritten = false
    private var doneJob: Job? = null

    init {
        viewModelScope.launch {
            recommended.state.collect { s -> update { it.copy(model = modelStatusOf(s)) } }
        }
        viewModelScope.launch {
            combine(SttEngineCatalog.engines.map { installer.state(it.id) }) { states ->
                states.any { it == InstallState.Installed }
            }.collect { any -> update { it.copy(anyEngineInstalled = any) } }
        }
        // Cheap disk probes, so a model deleted behind the app's back reads right.
        viewModelScope.launch { for (engine in SttEngineCatalog.engines) installer.verify(engine.id) }
        if (restoredFromInstanceState) {
            // Restored straight onto the last step (process death after reaching it).
            if (_state.value.step == OnboardingStep.DONE) markDone()
            _ready.value = true
        } else {
            viewModelScope.launch {
                // The Preferences are already in memory (MainActivity read
                // them to pick the start destination), so this is quick.
                OnboardingState.stepNamed(settings.resumeStep.first())?.let { step ->
                    update { it.resumedAt(step) }
                }
                _ready.value = true
            }
        }
    }

    // --- Navigation ------------------------------------------------------------

    fun next() = update { it.next() }

    /** @return false on the first step: the caller leaves the guide. */
    fun back(): Boolean {
        val previous = _state.value.back() ?: return false
        update { previous }
        return true
    }

    /** "Try it": make sure the done flag is on disk before the guide goes away. */
    suspend fun finish() {
        doneJob?.join()
        writeDone()
    }

    // --- System state ----------------------------------------------------------

    /** Re-read the mic permission, enabled keyboards, installed FUTO packages and the speech service. */
    fun refreshSystemState() {
        val futo = findFutoPackage()
        _futoPackage.value = futo
        update { it.withSystemState().withFutoInstalled(futo != null) }
    }

    /** A FUTO switch round trip came back with [resultCode]. */
    fun onFutoResult(mode: FutoMode, resultCode: Int) {
        val status = FutoVoiceInput.resultFromCode(mode, resultCode)
        update { it.copy(futo = status) }
    }

    /** FUTO's switch activity couldn't be started: this version can't be asked. */
    fun onFutoUnavailable() = update { it.copy(futo = FutoStatus.UNKNOWN) }

    // --- Model -----------------------------------------------------------------

    // The card and its actions only show once the recommendation is known.
    private val engineId: String? get() = recommendation.value?.engine?.id

    /** Start, resume or retry the download. It outlives this screen (ModelDownloads). */
    fun download() {
        engineId?.let(downloads::start)
    }

    /** Import the recommended engine from a local .zip archive via SAF. */
    fun importArchive(uri: Uri, contentResolver: ContentResolver) {
        val id = engineId ?: return
        if (downloads.isRunning(id)) downloads.cancel(id)
        viewModelScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    installer.importArchive(id, stream).fold(
                        onSuccess = {
                            show(R.string.engine_import_success)
                        },
                        onFailure = { e ->
                            Log.w(TAG, "Failed to import $id during onboarding", e)
                            val res = when ((e as? InstallException)?.failure) {
                                InstallFailure.CHECKSUM -> R.string.engine_import_checksum_error
                                InstallFailure.NO_SPACE -> R.string.engine_failure_no_space
                                else -> R.string.engine_import_failed
                            }
                            show(res)
                        }
                    )
                } ?: show(R.string.engine_import_failed)
            } catch (t: Throwable) {
                Log.w(TAG, "Import error for $id in onboarding", t)
                show(R.string.engine_import_failed)
            }
        }
    }

    fun cancelDownload() {
        engineId?.let(downloads::cancel)
    }

    fun removeDownload() {
        val id = engineId ?: return
        viewModelScope.launch {
            installer.discardDownload(id).onFailure { e ->
                Log.w(TAG, "Couldn't remove the download of $id", e)
                show(R.string.engine_remove_download_failed)
            }
        }
    }

    fun show(@StringRes message: Int) {
        _message.value = message
    }

    fun messageShown() {
        _message.value = null
    }

    // --- Internals -------------------------------------------------------------

    private fun update(transform: (OnboardingState) -> OnboardingState) {
        val old = _state.value
        val new = transform(old)
        _state.value = new
        savedState[KEY_STEP] = new.step.name
        if (new.step != old.step) saveStep(new.step)
        if (new.step == OnboardingStep.DONE) markDone()
    }

    private fun saveStep(step: OnboardingStep) {
        viewModelScope.launch {
            try {
                settings.setStep(step.name)
            } catch (e: IOException) {
                // Not fatal: an interrupted first run just starts over.
                Log.w(TAG, "Couldn't save the setup step", e)
            }
        }
    }

    private fun initialState(): OnboardingState {
        val state = OnboardingState(
            futoInstalled = _futoPackage.value != null,
            anyEngineInstalled = SttEngineCatalog.engines.any { installer.state(it.id).value == InstallState.Installed },
            speechServiceSupported = context.packageManager.hasSystemFeature(SpeechService.FEATURE_VOICE_RECOGNIZERS),
        ).withSystemState()
        val restored = OnboardingState.stepNamed(savedState.get<String>(KEY_STEP))
        return if (restored != null) state.resumedAt(restored) else state
    }

    /** Reaching the last step counts as finishing, even if the user never taps "Try it". */
    private fun markDone() {
        if (doneWritten || doneJob?.isActive == true) return
        doneJob = viewModelScope.launch { writeDone() }
    }

    private suspend fun writeDone() {
        if (doneWritten) return
        try {
            settings.setDone()
            doneWritten = true
        } catch (e: IOException) {
            // Not fatal: the guide just shows again on the next launch.
            Log.w(TAG, "Couldn't save that setup is done", e)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** The mic permission, the keyboards and the speech service setting, as Android has them now. */
    private fun OnboardingState.withSystemState(): OnboardingState {
        val enabled = enabledKeyboards()
        val ourId = ComponentName(context.packageName, VOICE_IME_CLASS).flattenToString()
        val defaultId = defaultImeId()
        return copy(
            micGranted = hasMicPermission(),
            keyboardEnabled = enabled.any { it.id == ourId },
            otherVoiceKeyboards = Keyboards.otherVoiceKeyboards(enabled, ourId, defaultId),
            keyboards = Keyboards.toGuide(enabled, defaultId),
            speechServiceSelected = isOurSpeechService(),
        )
    }

    /**
     * Android's enabled keyboards. Other apps' keyboards are visible thanks
     * to the manifest's `<queries>` entry for input methods; without it
     * Android leaves them out of this list.
     */
    private fun enabledKeyboards(): List<EnabledKeyboard> {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        return imm.enabledInputMethodList.map { imi ->
            EnabledKeyboard(
                id = imi.component.flattenToString(),
                label = imi.loadLabel(context.packageManager).toString(),
                subtypeModes = (0 until imi.subtypeCount).map { imi.getSubtypeAt(it).mode },
            )
        }
    }

    private fun defaultImeId(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)
            ?.flattenToString()

    /** Whether Android's speech recognition service setting names ours. */
    private fun isOurSpeechService(): Boolean {
        val value = try {
            Settings.Secure.getString(context.contentResolver, VOICE_RECOGNITION_SERVICE)
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't read $VOICE_RECOGNITION_SERVICE", e)
            null
        }
        return SpeechService.isOurs(value, context.packageName, RECOGNITION_SERVICE_CLASS)
    }

    private fun findFutoPackage(): String? {
        val installed = FutoVoiceInput.PACKAGES.filter(::isInstalled).toSet()
        if (installed.isEmpty()) return null
        val defaultIme = defaultImeId()?.substringBefore('/')
        return FutoVoiceInput.pickPackage(installed, defaultIme)
    }

    /** Visible thanks to the manifest's `<queries>` entries for FUTO's packages. */
    private fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
        const val KEY_STEP = "onboarding_step"

        /** The voice keyboard service (ARCHITECTURE.md D6). */
        val VOICE_IME_CLASS: String = VoiceInputService::class.java.name

        /** Our RecognitionService (D6), as the speech recognition service setting names it. */
        val RECOGNITION_SERVICE_CLASS: String = MarmaladeRecognitionService::class.java.name

        /** Settings.Secure.VOICE_RECOGNITION_SERVICE, which is hidden from the SDK but readable. */
        const val VOICE_RECOGNITION_SERVICE = "voice_recognition_service"
    }
}
