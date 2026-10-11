package app.marmalade.stt.speech

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.install.SttEngineDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * The one place listening surfaces get a speech engine from: the Dictate
 * screen, the voice IME and the RecognitionService.
 *
 * The active engine is the user's pick on the Engines tab
 * ([SpeechPrefs.activeEngine]: the default unless they chose another, and
 * never a developer engine while those are hidden) with its language hint.
 * The manager builds that engine once its model is installed, keeps it while
 * the choice stays the same, and honours "Keep engine ready". While a
 * listening surface is on screen it [hold]s the model, which then stays
 * loaded between that surface's sessions (#78). A different choice drops the
 * old engine ([SttEngine.drop]: freed after its session, if one runs, and
 * never loaded again by a caller that still holds it). The installer calls
 * [release] before it deletes or replaces an engine's files.
 */
@Singleton
class SpeechEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installer: ModelInstaller,
    private val settings: SpeechSettings,
    private val smartTurn: SmartTurnModel,
    private val hardware: PhoneHardware,
) {
    /** Which engine runs, with which language hint and ONNX Runtime tuning (#31). */
    private data class Choice(val descriptor: SttEngineDescriptor, val language: String, val tuning: OrtTuning)

    /** The phone's RAM, which decides whether an engine runs without ONNX Runtime's arena (#47). */
    private val totalMemBytes: Long? by lazy {
        runCatching { hardware.totalMemBytes() }.onFailure { Log.w(TAG, "Couldn't read the phone's RAM", it) }.getOrNull()
    }

    private fun SpeechPrefs.choice() =
        Choice(activeEngine, languageFor(activeEngine), tuningFor(this, activeEngine, totalMemBytes))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Kept current from DataStore for the synchronous callers (isReady,
    // engineOrNull). The first value is read once, blocking, when the manager
    // is created (a small preferences file) so those callers never answer for
    // the wrong engine at startup.
    private val choice: StateFlow<Choice> = settings.prefs
        .map { it.choice() }
        .catch { e ->
            Log.w(TAG, "Couldn't read the speech settings", e)
            emit(SpeechPrefs().choice())
        }
        .stateIn(scope, SharingStarted.Eagerly, initialChoice())

    private fun initialChoice(): Choice = try {
        runBlocking { settings.current() }.choice()
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't read the speech settings", e)
        SpeechPrefs().choice()
    }

    /** The engine that runs now. Updates when the user picks another or hides developer engines. */
    val activeEngine: StateFlow<SttEngineDescriptor> = choice
        .map { it.descriptor }
        .stateIn(scope, SharingStarted.Eagerly, choice.value.descriptor)

    private var engine: SttEngine? = null
    private var engineChoice: Choice? = null

    /** The active engine, or null when its model isn't installed. */
    fun engineOrNull(): SttEngine? = engineFor(choice.value)

    private val holds = EngineHolds(
        scope = scope,
        keepLoaded = { settings.current().keepModelLoaded },
        activeEngine = ::engineOrNull,
        builtEngine = { synchronized(this) { engine } },
    )

    /**
     * Keep the active engine's model loaded between sessions while the
     * returned hold is open (#78): a listening surface takes one while it is
     * on screen and closes it when it goes away. Closing the last hold frees
     * the model, after the running session if any, unless "Keep engine
     * ready" is on. Holds nest; a hold follows engine changes (the new
     * engine is kept instead).
     *
     * @param preload also load the active engine now and warm it up, if it
     *   is installed, whatever "Keep engine ready" says (the Dictate screen,
     *   whose user is about to dictate).
     */
    fun hold(preload: Boolean): EngineHold = holds.hold(preload)

    @Synchronized
    private fun engineFor(wanted: Choice): SttEngine? {
        val cached = engine
        if (cached != null && engineChoice != wanted) {
            // Another engine, language or tuning now: drop this one (freed once its session ends).
            cached.drop()
            engine = null
            engineChoice = null
        }
        val installed = installer.installedModel(wanted.descriptor.id)
        if (installed == null) {
            // Files gone (removed, or never installed): drop a stale engine.
            engine?.drop()
            engine = null
            engineChoice = null
            return null
        }
        engine?.let { return it }
        if (wanted.tuning.arena == false) Log.i(TAG, "${wanted.descriptor.id} runs without ONNX Runtime's memory arena on this phone")
        return SttEngines.create(installed, context.assets, wanted.language, tuning = wanted.tuning).also {
            engine = it
            engineChoice = wanted
        }
    }

    /** True when the active engine's model is installed. */
    fun isReady(): Boolean = installer.installedModel(choice.value.descriptor.id) != null

    /**
     * Load the model ahead of the first session, if it is installed and the
     * user keeps it loaded or a surface [hold]s it (otherwise the first
     * session loads it instead), and decode a moment of silence with it so
     * the first session's decodes are warm ([SttEngine.warmUp], #31). The
     * bundled Smart Turn model (8.7 MB, always kept) is loaded too when a
     * surface uses it.
     */
    suspend fun warmUp() {
        val prefs = settings.current()
        if (prefs.usesSmartTurn) smartTurn.detectorOrNull()
        if (!prefs.keepModelLoaded) return holds.preload()
        val e = engineFor(prefs.choice()) ?: return
        runCatching { e.warmUp() }.onFailure { Log.w(TAG, "Warm-up failed", it) }
    }

    /**
     * The engine of the session in progress. Kept apart from [engine] so
     * [stop] still reaches a session whose engine was dropped from the cache
     * meanwhile (the files were removed, or the user picked another engine;
     * the old engine frees itself once the session ends).
     */
    @Volatile
    private var sessionEngine: SttEngine? = null

    private val _tooSlow = MutableSharedFlow<SttEngineDescriptor>(extraBufferCapacity = 4)

    /**
     * Engines that reported [SttEvent.TooSlow] in a session: they can't keep
     * up with the mic on this device (#30). [SpeedCalibrator.watch] keeps them
     * as speed-budget evidence (#27). Nothing is replayed.
     */
    val tooSlow: SharedFlow<SttEngineDescriptor> = _tooSlow.asSharedFlow()

    /** [stop] arrived, maybe before the engine had claimed the session (repeated at [SttEvent.Listening]). */
    @Volatile
    private var stopRequested = false

    /**
     * Listen with the user's current settings. Emits a single
     * [SttEvent.Error] of kind [SttErrorKind.MODEL] when no model is
     * installed. With "Keep engine ready" off, the model is freed when the
     * session ends, unless a surface [hold]s it.
     *
     * @param surface who listens: picks the end of speech
     *   ([SpeechPrefs.sessionOptions]).
     * @param language a caller's language for this session only (a BCP-47
     *   tag, [SpeechPrefs.forRequestLanguage]), or null for the settings'.
     *   An engine whose language hint it changes is rebuilt for the session,
     *   and again for the next one without it: engines take their hint when
     *   they load.
     * @param requestedAt when the user asked ([SystemClock.elapsedRealtime]):
     *   the start of the timing line logged when the session ends ([ListenTiming]).
     */
    fun listen(
        surface: ListenSurface,
        language: String? = null,
        requestedAt: Long = SystemClock.elapsedRealtime(),
    ): Flow<SttEvent> = flow {
        stopRequested = false
        val prefs = settings.current().let { if (language != null) it.forRequestLanguage(language) else it }
        val e = engineFor(prefs.choice())
        if (e == null) {
            emit(SttEvent.Error(SttErrorKind.MODEL, "No speech model is installed."))
            return@flow
        }
        sessionEngine = e
        // The process's age tells a cold start (the keyboard's process just launched) from a warm one.
        val processAge = requestedAt - Process.getStartElapsedRealtime()
        val timing = ListenTiming("${surface.name.lowercase()} ${e.descriptor.id} loaded=${e.isLoaded} process=${processAge}ms", requestedAt)
        // Smart Turn's model loads once the engine has opened the mic (#78): its first load mustn't delay it.
        val smartTurnDetector = if (prefs.endFor(surface) == EndOfDictation.SMART_TURN) ::smartTurnDetector else null
        emitAll(
            e.listen(prefs.sessionOptions(surface).copy(loadTurnDetector = smartTurnDetector))
                // A stop() that came before the engine claimed the session is
                // repeated as soon as the engine has (its mic is open). It
                // closes the mic; what was heard is still transcribed.
                .onEach {
                    timing.on(it)
                    if (it == SttEvent.Listening && stopRequested) e.stop()
                    if (it == SttEvent.TooSlow) _tooSlow.tryEmit(e.descriptor)
                }
                .onCompletion {
                    timing.log()
                    if (sessionEngine === e) sessionEngine = null
                    holds.sessionEnded(e)
                },
        )
    }

    /** Logged once per process: Smart Turn is on but its model isn't there. */
    @Volatile
    private var smartTurnMissingLogged = false

    /** Smart Turn's detector, loaded on first use (blocking); null (the session then runs as Pause) if it failed to load. */
    private fun smartTurnDetector() =
        smartTurn.detectorOrNull().also {
            if (it == null && !smartTurnMissingLogged) {
                smartTurnMissingLogged = true
                Log.i(TAG, "Smart Turn model not available: Smart Turn sessions behave as Pause")
            }
        }

    /** Finish the running session, if any, including one still starting up. */
    fun stop() {
        stopRequested = true
        (sessionEngine ?: synchronized(this) { engine })?.stop()
    }

    /** Free the loaded model now if no session is running; otherwise when it ends. */
    @Synchronized
    fun releaseModel() {
        engine?.drop()
        engine = null
        engineChoice = null
    }

    /** Free [engineId]'s native memory before its files change. */
    @Synchronized
    fun release(engineId: String) {
        val e = engine ?: return
        if (e.descriptor.id != engineId) return
        e.drop()
        engine = null
        engineChoice = null
    }

    companion object {
        private const val TAG = "SpeechEngineManager"

        /**
         * The ONNX Runtime tuning [engine] loads with on a phone with
         * [totalMemBytes]: [SpeechPrefs.ortTuning] (#31), without the arena
         * where the engine fits the phone only that way (#47,
         * [OrtTuning.forPhone]), as the recommender counted it. Dictation
         * and the A/B benchmark both load with it, with the live RAM.
         */
        internal fun tuningFor(prefs: SpeechPrefs, engine: SttEngineDescriptor, totalMemBytes: Long?): OrtTuning =
            prefs.ortTuning.forPhone(engine.id, totalMemBytes)
    }
}
