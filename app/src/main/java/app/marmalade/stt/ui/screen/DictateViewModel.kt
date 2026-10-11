package app.marmalade.stt.ui.screen

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.speech.EngineHold
import app.marmalade.stt.speech.ListenSurface
import app.marmalade.stt.speech.SpeechEngineManager
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.TextPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State and actions for [DictateScreen]. The transcript lives here, in
 * memory only; it is never written to disk (ARCHITECTURE.md D10).
 *
 * One listening session at a time runs in [viewModelScope]. [stop] asks the
 * engine to flush what it heard (the Final still arrives); [onCleared]
 * cancels outright.
 *
 * While the screen is on show ([onVisible] to [onBackground]) it holds the
 * engine and preloads it ([SpeechEngineManager.hold], #78): the user who
 * opens it is about to dictate, and a second tap doesn't reload the model.
 *
 * Finals go through a `TextPipeline` built from the user's cleanup settings
 * ([TextProcSettings]) and rebuilt whenever they change, so a change made in
 * Settings applies from the next utterance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictateViewModel @Inject constructor(
    private val engines: SpeechEngineManager,
    installer: ModelInstaller,
    textProc: TextProcSettings,
) : ViewModel() {

    // The stock config until the settings have been read, a few milliseconds
    // after this ViewModel is created, long before a first Final can arrive.
    @Volatile
    private var dictation = Dictation()
    private val _state = MutableStateFlow(DictationState())

    /**
     * The screen state without the level, so the 10-per-second level
     * updates don't recompose the transcript. The meter reads [level].
     */
    val state: StateFlow<DictationState> = _state
        .map { it.copy(level = 0f) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value)

    /** Mic input level, 0..1. */
    val level: StateFlow<Float> = _state
        .map { it.level }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    /**
     * True when the active engine's model is installed. Follows the engine the
     * user picked (Engines tab) and updates live as downloads finish or files
     * are removed.
     */
    val modelReady: StateFlow<Boolean> = engines.activeEngine
        .flatMapLatest { installer.state(it.id) }
        .map { it == InstallState.Installed }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            installer.state(engines.activeEngine.value.id).value == InstallState.Installed,
        )

    private var listenJob: Job? = null

    /** Open while the screen is on show; survives a rotation with this ViewModel. */
    private var engineHold: EngineHold? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            textProc.config
                .catch { e ->
                    // Unreadable settings file: keep the stock cleanup.
                    Log.w(TAG, "Couldn't read the cleanup settings", e)
                }
                // Building a pipeline compiles its rules, hence Default.
                .collect { config -> dictation = Dictation(TextPipeline(config)) }
        }

        // A model that finishes installing while the screen is open, or a
        // switch to another installed engine: load it now if it is kept
        // (Keep engine ready, or this screen's hold), so the first tap
        // doesn't wait. (At startup the Application already warms up an
        // installed model, hence drop(1).)
        viewModelScope.launch(Dispatchers.Default) {
            combine(engines.activeEngine, modelReady) { engine, ready -> engine.id to ready }
                .distinctUntilChanged()
                .drop(1)
                .filter { (_, ready) -> ready }
                .collect { engines.warmUp() }
        }
    }

    /** Start listening. The caller has checked the mic permission. No-op while a session runs. */
    fun start() {
        if (listenJob?.isActive == true) return
        val requestedAt = SystemClock.elapsedRealtime()
        val modelLoaded = engines.engineOrNull()?.isLoaded == true
        _state.update { dictation.started(it, modelLoaded) }
        listenJob = viewModelScope.launch {
            try {
                // Pause, fixed: only the mic tap stops the Dictate screen (#43).
                engines.listen(ListenSurface.DICTATE, requestedAt = requestedAt)
                    // Settings read, file probe and releasing the model all stay off the main thread.
                    .flowOn(Dispatchers.Default)
                    .collect { event -> _state.update { dictation.reduce(it, event) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The engine reports its own failures as Error events; this is anything else.
                Log.e(TAG, "Listening failed", e)
                _state.update {
                    dictation.reduce(it, SttEvent.Error(SttErrorKind.RECOGNITION, e.message ?: "Listening failed"))
                }
            } finally {
                _state.update { dictation.ended(it) }
            }
        }
    }

    /** Finish gracefully: the engine transcribes what it heard, then the session ends. */
    fun stop() {
        if (listenJob?.isActive != true) return
        _state.update { dictation.stopping(it) }
        engines.stop()
    }

    /** The screen is on show: keep the engine loaded, and load it now if it is installed. */
    fun onVisible() {
        if (engineHold == null) engineHold = engines.hold(preload = true)
    }

    /**
     * The screen went to the background (not a rotation): never keep the mic
     * open behind the user's back, and let the model go (after the session).
     */
    fun onBackground() {
        if (_state.value.phase.isActive && _state.value.phase != DictationPhase.FINISHING) stop()
        closeEngineHold()
    }

    private fun closeEngineHold() {
        engineHold?.close()
        engineHold = null
    }

    fun clear() {
        _state.update { dictation.cleared(it) }
    }

    fun noticeShown() {
        _state.update { dictation.noticeShown(it) }
    }

    override fun onCleared() {
        // Aborts at once, without a final result: nobody is left to show it.
        listenJob?.cancel()
        closeEngineHold()
    }

    private companion object {
        const val TAG = "DictateViewModel"
    }
}
