package app.marmalade.stt.speech

/*
 * Data flow:
 *   listen(options)
 *     -> claim the engine (one session at a time; a second listen gets
 *        Error(BUSY) and opens no mic)
 *     -> open the mic before anything slow (#78); failure -> Error(MIC), end
 *        -> Listening; EarlyMic.record() keeps the hops in memory and sends
 *           their Level while the model loads
 *        -> SessionOptions.withTurnDetector() alongside (Smart Turn's first load)
 *     -> wait for a warm-up decode in progress
 *     -> load(): the model's files from modelDir -> a sherpa-onnx recognizer
 *        (kept in memory until release()); which one depends on the family,
 *        see SttEngines.create
 *        failure -> mic closed, Error(MODEL), end
 *     -> Silero VAD from the bundled asset, rebuilt only when its minimum
 *        silence changes (the slider, or Smart Turn's proposal pause from
 *        its Patience: SessionOptions.vadMinSilenceSeconds)
 *     -> newSession(model, vad, options, the EarlyMic).run(): Ready, then the
 *        kept hops, then live ones
 *        offline families: StreamingSession (VAD segments -> re-decode)
 *        streaming family: OnlineSession (model decodes as audio arrives)
 *     stop(), at any point after the mic opened: the mic closes; the session
 *        still reads what was kept, then flushes
 *     -> session ends: mic closed, unclaim; run a release() that arrived
 *        meanwhile, or free the model of a dropped engine
 */

import android.util.Log
import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.SttEngineDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Every engine (ARCHITECTURE.md D4): one sherpa-onnx model [M] plus the
 * bundled Silero VAD, run by the [ListeningSession] its family needs. Build
 * one with [SttEngines.create].
 *
 * Concurrency, beyond the [SttEngine] contract:
 * - **One session at a time.** A [listen] collected while another session
 *   runs emits [SttEvent.Error] ([SttErrorKind.BUSY], [BUSY_MESSAGE])
 *   and ends; the running session is not disturbed. Cancel the first
 *   collector before starting another.
 * - The mic opens before the model loads ([SttEvent.Listening], then
 *   [SttEvent.Ready] once the session runs); its audio waits in memory
 *   ([EarlyMic]). [stop] closes the mic at once, even during the load: the
 *   session still transcribes everything captured until then. A [stop]
 *   between the claim and the mic opening closes the mic as soon as it opens.
 * - [release] during a session is deferred until the session ends; until
 *   then [isLoaded] stays true. A [release] racing a plain [load] outside a
 *   session: whichever finishes last wins (nothing leaks either way).
 * - [drop] is [release] for good: no model is kept after it except for a
 *   session's lifetime, so a load that started before the drop, or a
 *   [warmUp] that comes after it, leaves nothing loaded.
 *
 * @param loadModel blocking native load of the model (seconds).
 * @param createVad Silero VAD for a minimum-silence setting.
 * @param openMic mic factory, called as a listen starts (before the model
 *   loads); tests pass a fake.
 * @param newSession the session type for this family.
 */
class SherpaSttEngine<M : LoadedModel> internal constructor(
    override val descriptor: SttEngineDescriptor,
    private val loadModel: () -> M,
    private val createVad: (minSilenceSeconds: Float) -> SpeechVad,
    private val openMic: (hopSamples: Int, hardwareEffects: Boolean) -> MicCapture,
    private val newSession: NewSession<M>,
) : SttEngine {

    /** Builds the session for one listen: (model, vad, options, mic factory). */
    internal fun interface NewSession<M> {
        fun create(
            model: M,
            vad: SpeechVad,
            options: SessionOptions,
            openMic: (hopSamples: Int, hardwareEffects: Boolean) -> MicCapture,
            earlyVad: SpeechVad?,
        ): ListeningSession
    }

    companion object {
        const val BUSY_MESSAGE = "busy: another listening session is running"
        private const val TAG = "SherpaSttEngine"
    }

    /**
     * Serialises native loads (the recognizer and the VAD). A session waits
     * on it only after its mic is open, and needs the model before it can
     * start anyway, so the VAD sharing it (behind a warm-up's load) costs
     * nothing extra.
     */
    private val loadMutex = Mutex()

    /** Guards the fields below. */
    private val lock = Any()

    @Volatile
    private var model: M? = null
    private var vad: SpeechVad? = null
    private var vadMinSilence = Float.NaN
    private var earlyVad: SpeechVad? = null // #91 SpeedKnobs.earlyVadFor
    private var earlyVadMinSilence = Float.NaN
    private var sessionActive = false

    /** The session's mic, from the moment it opens; [stop] closes it. */
    private var mic: EarlyMic? = null

    /** [stop] came after the claim, before the mic opened. */
    private var stopPending = false
    private var releasePending = false

    /** Bumped by every release, so a load that a release overtook drops its model instead of publishing it. */
    private var generation = 0

    /** Set while [warmUp]'s silence decode runs; a session waits for it before decoding. */
    private var warmingUp: CompletableDeferred<Unit>? = null

    /** [drop] was called: a model is kept only while a session holds the claim. */
    private var dropped = false

    /** Nothing may keep a model now: dropped, and no session to serve. Caller holds [lock]. */
    private val unwanted: Boolean get() = dropped && !sessionActive

    override val isLoaded: Boolean get() = model != null

    override suspend fun load() {
        loadModelIfNeeded()
    }

    /**
     * Loads, publishes the model, then decodes 0.5 s of silence with it,
     * outside [loadMutex]: a session that wants the model never waits for the
     * warm-up to start. The decode is skipped when a session has claimed the
     * engine meanwhile (it was waiting for this load), and a session that
     * starts during it waits only for that one decode before its own, so the
     * two never decode on the recognizer at once. [release] is deferred
     * until it ends.
     *
     * Not for Whisper: its 30 s window makes the silence decode cost as much
     * as a real one, and the Pixel 8a showed no first-dictation gain (#31,
     * docs/benchmarks/2026-10-pixel8a-ort.md).
     */
    override suspend fun warmUp() {
        if (!loadModelIfNeeded() || descriptor.family == EngineFamily.WHISPER) return
        val (warm, done) = synchronized(lock) {
            val current = model
            if (sessionActive || warmingUp != null || current == null) return
            current to CompletableDeferred<Unit>().also { warmingUp = it }
        }
        val startedAt = System.nanoTime()
        try {
            withContext(Dispatchers.Default) { warmUpDecode(warm) }
            Log.i(TAG, "Warm-up decode of ${descriptor.id}: ${(System.nanoTime() - startedAt) / 1_000_000} ms")
        } finally {
            synchronized(lock) {
                warmingUp = null
                if ((releasePending || dropped) && !sessionActive) {
                    releasePending = false
                    releaseNow()
                }
            }
            done.complete(Unit)
        }
    }

    /**
     * Loads the model unless it is loaded; true when this call published it.
     * Loads nothing for a dropped engine outside a session. A [release] or
     * [drop] while it loads (e.g. the user picked another engine meanwhile)
     * frees the new model instead, unless a session holds the claim.
     */
    private suspend fun loadModelIfNeeded(): Boolean {
        if (model != null || synchronized(lock) { unwanted }) return false
        return withContext(Dispatchers.Default) {
            loadMutex.withLock {
                val startGeneration = synchronized(lock) {
                    if (model != null || unwanted) return@withLock false
                    generation
                }
                val startedAt = System.nanoTime()
                val loaded = loadModel()
                val published = synchronized(lock) {
                    (generation == startGeneration && !unwanted).also { if (it) model = loaded }
                }
                if (!published) {
                    loaded.release()
                    Log.i(TAG, "Released ${descriptor.id}: released or dropped while it loaded")
                    return@withLock false
                }
                Log.i(TAG, "Loaded ${descriptor.id} in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
                true
            }
        }
    }

    override fun listen(options: SessionOptions): Flow<SttEvent> = channelFlow {
        if (!claim()) {
            send(SttEvent.Error(SttErrorKind.BUSY, BUSY_MESSAGE))
            return@channelFlow
        }
        try {
            // Before anything slow (#78): users start talking as soon as they tap the mic.
            val capture = try {
                options.audioInput?.invoke(StreamingSession.MIC_HOP_SAMPLES)
                    ?: openMic(StreamingSession.MIC_HOP_SAMPLES, true)
            } catch (e: MicCaptureException) {
                Log.w(TAG, "Couldn't open the microphone", e)
                send(SttEvent.Error(SttErrorKind.MIC, e.message ?: "Couldn't open the microphone"))
                return@channelFlow
            }
            val early = EarlyMic(capture, onLevel = { trySend(SttEvent.Level(it)) })
            try {
                val stopEarly = synchronized(lock) {
                    mic = early
                    stopPending
                }
                if (stopEarly) early.stopRecording()
                send(SttEvent.Listening)
                val recorder = launch(Dispatchers.IO) { early.record() }
                val sessionOptions = async(Dispatchers.Default) { options.withTurnDetector() }

                suspend fun fail(error: SttEvent.Error) {
                    early.close()
                    recorder.cancelAndJoin() // no Level may follow the Error
                    send(error)
                }

                // A warm-up decode in progress finishes first: one recognizer, one decode at a time.
                synchronized(lock) { warmingUp }?.await()
                val (loadedModel, sessionVad) = try {
                    load()
                    // Not null: release() is deferred while this session holds the claim.
                    val loaded = checkNotNull(model) { "model released during load" }
                    loaded to vadFor(sessionOptions.await().vadMinSilenceSeconds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't load ${descriptor.id}", e)
                    fail(SttEvent.Error(SttErrorKind.MODEL, e.message ?: "Couldn't load the speech model"))
                    return@channelFlow
                } catch (e: LinkageError) {
                    // The sherpa-onnx native library is missing or broken (e.g. no build for this ABI).
                    Log.e(TAG, "Speech runtime failed to load", e)
                    fail(SttEvent.Error(SttErrorKind.MODEL, "Speech runtime failed to load: ${e.message}"))
                    return@channelFlow
                }

                val minSilence = sessionOptions.await().vadMinSilenceSeconds
                val sessionEarlyVad = if (SpeedKnobs.earlyVadFor(sessionOptions.await().effectiveEnd)) EarlyVad.minSilenceSeconds(minSilence)?.let { earlyVadFor(it) } else null
                // The session reads the open mic: the hops kept during the load first.
                val created = newSession.create(loadedModel, sessionVad, sessionOptions.await(), { _, _ -> early }, sessionEarlyVad)
                // Only the mic judges the engine's speed: a caller's stream is timed by the caller (#118).
                created.run().collect { if (it != SttEvent.TooSlow || capture.live) send(it) }
            } finally {
                early.close()
            }
        } finally {
            unclaim()
        }
    }

    override fun stop() {
        val open = synchronized(lock) {
            mic ?: run {
                if (sessionActive) stopPending = true // the mic isn't open yet
                null
            }
        }
        // Not created.stop(): that would drop the hops the session hasn't read yet.
        open?.stopRecording()
    }

    override fun release() {
        synchronized(lock) {
            if (sessionActive || warmingUp != null) {
                releasePending = true
                return
            }
            releaseNow()
        }
    }

    override fun keep() {
        synchronized(lock) { if (!dropped) releasePending = false }
    }

    override fun drop() {
        synchronized(lock) { dropped = true }
        release()
    }

    private fun claim(): Boolean = synchronized(lock) {
        if (sessionActive) return false
        sessionActive = true
        stopPending = false
        true
    }

    private fun unclaim() {
        synchronized(lock) {
            sessionActive = false
            mic = null
            stopPending = false
            if ((releasePending || dropped) && warmingUp == null) {
                releasePending = false
                releaseNow()
            }
        }
    }

    /** Caller holds [lock]. */
    private fun releaseNow() {
        generation++
        val t = model
        val v = vad
        val ev = earlyVad
        model = null
        vad = null
        vadMinSilence = Float.NaN
        earlyVad = null
        earlyVadMinSilence = Float.NaN
        // Release all even if one throws.
        try {
            t?.release()
        } finally {
            try {
                v?.release()
            } finally {
                ev?.release()
            }
        }
        if (t != null) Log.i(TAG, "Released ${descriptor.id}")
    }

    /** #91: the early VAD ([EarlyVad]) for this session, cached like [vadFor]'s. */
    private suspend fun earlyVadFor(minSilence: Float): SpeechVad = withContext(Dispatchers.Default) {
        loadMutex.withLock {
            val (current, currentMinSilence) = synchronized(lock) { earlyVad to earlyVadMinSilence }
            if (current != null && currentMinSilence == minSilence) return@withLock current
            synchronized(lock) {
                earlyVad = null
                earlyVadMinSilence = Float.NaN
            }
            current?.release()
            val created = createVad(minSilence)
            synchronized(lock) {
                earlyVad = created
                earlyVadMinSilence = minSilence
            }
            Log.i(TAG, "Early Silero VAD ready (minSilence=${minSilence}s)")
            created
        }
    }

    /**
     * The VAD for this session, with [minSilence] seconds of minimum silence
     * (already clamped by [SessionOptions.vadMinSilenceSeconds]). Only called
     * while a session holds the claim.
     */
    private suspend fun vadFor(minSilence: Float): SpeechVad {
        return withContext(Dispatchers.Default) {
            loadMutex.withLock {
                val (current, currentMinSilence) = synchronized(lock) { vad to vadMinSilence }
                if (current != null && currentMinSilence == minSilence) return@withLock current
                if (current != null) {
                    // Forget it before releasing, so a failed rebuild can't leave a dead VAD cached.
                    synchronized(lock) {
                        vad = null
                        vadMinSilence = Float.NaN
                    }
                    current.release()
                }
                val created = createVad(minSilence)
                synchronized(lock) {
                    vad = created
                    vadMinSilence = minSilence
                }
                Log.i(TAG, "Silero VAD ready (minSilence=${minSilence}s)")
                created
            }
        }
    }
}
