package app.marmalade.stt.ui.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.marmalade.stt.data.AppearanceSettings
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.speech.EndOfDictation
import app.marmalade.stt.speech.EndWords
import app.marmalade.stt.speech.Patience
import app.marmalade.stt.speech.SpeechEngineManager
import app.marmalade.stt.speech.SpeedCalibrator
import app.marmalade.stt.textproc.TextProcConfig
import app.marmalade.stt.ui.theme.Appearance
import app.marmalade.stt.ui.theme.ThemeMode
import app.marmalade.stt.ui.theme.ThemePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Settings tab's speech and developer sections over [SpeechSettings] and
 * its text cleanup section over [TextProcSettings] (both DataStore). The next listening session
 * picks speech changes up; a running one keeps its options. Cleanup changes
 * apply as soon as [TextProcSettings.config] emits. "Re-measure phone speed"
 * runs in [SpeedCalibrator]'s own scope; the result lands in [SpeechSettings].
 * The Appearance section is [AppearanceSettings]; the app restyles as it emits.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SpeechSettings,
    private val appearanceSettings: AppearanceSettings,
    private val engines: SpeechEngineManager,
    private val textProc: TextProcSettings,
    private val calibrator: SpeedCalibrator,
) : ViewModel() {

    /** Null until DataStore has been read, so the rows never flash defaults. */
    val speech: StateFlow<SpeechPrefs?> = settings.prefs
        .catch { e ->
            // Unreadable settings file: show (and listen with) the defaults.
            Log.w(TAG, "Couldn't read the speech settings", e)
            emit(SpeechPrefs())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Settings → Appearance (#35); null until DataStore has been read, like [speech]. */
    val appearance: StateFlow<Appearance?> = appearanceSettings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemePreset(preset: ThemePreset) = save { appearanceSettings.setPreset(preset) }

    fun setThemeMode(mode: ThemeMode) = save { appearanceSettings.setMode(mode) }

    /** Null until DataStore has been read, like [speech]. */
    val cleanup: StateFlow<TextProcConfig?> = textProc.config
        .catch { e ->
            // Unreadable settings file: show (and dictate with) the defaults.
            Log.w(TAG, "Couldn't read the cleanup settings", e)
            emit(TextProcConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saveFailed = MutableStateFlow(false)

    /** A save failed; the screen shows a snackbar, then calls [saveFailedShown]. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    /**
     * Settings → Recognition language (#27): the hint for engines that take
     * one, from the next session on, and a new recommendation for the phone
     * (no re-measuring). Doesn't switch engines: the Engines tab says when
     * the one in use doesn't transcribe the language.
     */
    fun setRecognitionLanguage(code: String) = save {
        settings.setRecognitionLanguage(code)
        calibrator.recommendAgain()
    }

    /** The slider was let go; the only caller, so an unmoved slider stores nothing and follows the default. */
    fun setMinSilenceSeconds(seconds: Float) = save { settings.setMinSilenceSeconds(snapToTenth(seconds)) }

    /** Settings → End of speech → Voice keyboard (#43), from the next session on. */
    fun setKeyboardEnd(mode: EndOfDictation) = save { settings.setKeyboardEnd(mode) }

    /** Settings → End of speech → Other apps (#43), from the next request on. */
    fun setAppsEnd(mode: EndOfDictation) = save { settings.setAppsEnd(mode) }

    /** Settings → End of speech → Smart Turn patience (#43). */
    fun setPatience(patience: Patience) = save { settings.setPatience(patience) }

    /** The end words as typed, comma-separated. Nothing usable leaves the stored words as they are. */
    fun setEndWords(commaSeparated: String) = save { settings.setEndWords(EndWords.parse(commaSeparated)) }

    fun setKeepModelLoaded(on: Boolean) = save {
        settings.setKeepModelLoaded(on)
        // Off: free the memory now (or when the running session ends).
        if (!on) engines.releaseModel()
    }

    /**
     * Show or hide the developer engines. Hiding them while one is selected
     * makes the default run again (SpeechEngineManager follows the prefs);
     * the choice itself is kept for when they are shown again. Recommends
     * again.
     */
    fun setDeveloperEngines(on: Boolean) = save {
        settings.setDeveloperEngines(on)
        calibrator.recommendAgain()
    }

    /**
     * Run recognizers with this ONNX Runtime tuning ([id], an OrtTuning.id;
     * null for the default) while Developer engines are on. The engine is
     * rebuilt at its next load.
     */
    fun setOrtTuning(id: String?) = save { settings.setOrtTuning(id) }

    /** True while the phone's speed is being measured (here or by the Engines tab). */
    val measuring: StateFlow<Boolean> = calibrator.running

    private val _speedMessage = MutableStateFlow<TextRes?>(null)

    /** The re-measure result as a one-off snackbar, cleared by [speedMessageShown]. */
    val speedMessage: StateFlow<TextRes?> = _speedMessage.asStateFlow()

    /** Measure the phone's speed again (#24); the stored factor updates the rows and the Engines tab. */
    fun remeasureSpeed() {
        if (calibrator.running.value) return
        calibrator.remeasure { result -> _speedMessage.value = remeasuredMessage(result) }
    }

    fun speedMessageShown() {
        _speedMessage.value = null
    }

    fun setRemoveFillers(on: Boolean) = save { textProc.setRemoveFillers(on) }

    fun setSpokenCommands(on: Boolean) = save { textProc.setSpokenCommands(on) }

    fun setSpokenPunctuation(on: Boolean) = save { textProc.setSpokenPunctuation(on) }

    fun setNumbersAsDigits(on: Boolean) = save { textProc.setNumbersAsDigits(on) }

    fun setCapitalizeSentences(on: Boolean) = save { textProc.setCapitalizeSentences(on) }

    fun saveFailedShown() {
        _saveFailed.value = false
    }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't save a setting", e)
                _saveFailed.value = true
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"

        /** The silence slider moves in 0.1 s steps; store 1.2, not 1.2000000476. */
        fun snapToTenth(seconds: Float): Float = (seconds * 10f).roundToInt() / 10f
    }
}
