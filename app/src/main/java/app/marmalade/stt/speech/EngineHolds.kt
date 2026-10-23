package app.marmalade.stt.speech

import android.content.ComponentCallbacks2
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A listening surface's claim on the active engine's model while it is on
 * screen ([SpeechEngineManager.hold], #78). Close it when the surface goes
 * away; closing it again does nothing.
 */
class EngineHold internal constructor(private val onClose: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

/**
 * Keeps the active engine's model loaded between sessions while a listening
 * surface is on screen (#78). With "Keep engine ready" off, a session's end
 * frees the model ([sessionEnded]) unless a hold is open; closing the last
 * hold frees it then (deferred by the engine while a session runs). With
 * Keep engine ready on, nothing here frees anything.
 *
 * A session that ran another engine or language than the active one (#90:
 * another app's language routed it) took the active engine's place in
 * memory. Where one is kept loaded (Keep engine ready, or a hold), the active
 * engine is put back once no such session has run for [RESTORE_DELAY_MS]
 * ([routedSessionEnded]): the user's keyboard finds its engine ready again,
 * and a language app asking again soon finds its own still warm.
 *
 * The warm hold (#91, Max 2026-10-22): where [lingerMs] gives a time (phones
 * with 6 GB or more, [WarmHold]), "frees" above means "frees once that long
 * has passed with no session and no hold", so the next keyboard or popup
 * open within it skips the model load (Ultra: 2.6-2.8 s on a Pixel 6a). Only
 * the model stays: no mic, service or wake lock. A session or hold starting
 * cancels the pending release; [trimMemory] under memory pressure frees the
 * model at once. The wait counts while the phone is awake: asleep, nothing
 * runs to free it, and Android's low-memory killer may end the cached
 * process instead.
 *
 * Thread-safe: surfaces open and close holds on the main thread, sessions end
 * on a background one. Freeing runs on [scope], never on the caller's thread.
 *
 * @param keepLoaded the "Keep engine ready" setting, read when it matters.
 * @param activeEngine the active engine, built if its model is installed,
 *   else null (a preload then does nothing).
 * @param builtEngine the engine already built, or null; never builds one.
 * @param lingerMs how long a model stays loaded after it would be freed;
 *   0 frees it at once ([WarmHold.lingerMs]).
 */
internal class EngineHolds(
    private val scope: CoroutineScope,
    private val keepLoaded: suspend () -> Boolean,
    private val activeEngine: () -> SttEngine?,
    private val builtEngine: () -> SttEngine?,
    private val lingerMs: () -> Long = { 0L },
) {
    private val lock = Any()
    private var count = 0
    private var restore: Job? = null

    /** The warm hold: the engine whose release is pending, and the job that will free it. */
    private var lingering: SttEngine? = null
    private var linger: Job? = null

    /** True while at least one hold is open. */
    val held: Boolean get() = synchronized(lock) { count > 0 }

    /**
     * Opens a hold. [preload]: also load the active engine and warm it up
     * ([SttEngine.warmUp]) in the background, whatever Keep engine ready says.
     */
    fun hold(preload: Boolean): EngineHold {
        synchronized(lock) { count++ }
        cancelLinger()
        // A release the last hold's closing deferred behind a running session no longer stands.
        builtEngine()?.keep()
        if (preload) scope.launch { preload() }
        return EngineHold(::closeOne)
    }

    /** Loads the active engine for an open hold, so a surface's first session doesn't wait for it. */
    suspend fun preload() {
        val e = activeEngine() ?: return
        if (!held || e.isLoaded) return
        Log.i(TAG, "A surface's hold preloads ${e.descriptor.id}")
        try {
            e.warmUp()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Preload failed", t)
        }
    }

    /** A session of [engine] ended: free its model unless a hold is open or Keep engine ready is on. */
    suspend fun sessionEnded(engine: SttEngine) {
        if (held || keepLoaded()) return
        releaseLater(engine)
    }

    /** A session starts: a pending restore would only compete with it for the engine, and its model stays. */
    fun sessionStarting() {
        synchronized(lock) {
            restore?.cancel()
            restore = null
        }
        cancelLinger()
    }

    /**
     * Android's `onTrimMemory`: at a level that means memory is short
     * ([WarmHold.releasesOnTrim]), free a model the warm hold keeps. A model
     * a surface holds, or Keep engine ready keeps, stays.
     */
    fun trimMemory(level: Int) {
        if (!WarmHold.releasesOnTrim(level)) return
        val e = synchronized(lock) {
            linger?.cancel()
            linger = null
            lingering.also { lingering = null }
        } ?: return
        // Trim callbacks arrive on the main thread; freeing native memory there could stall the keyboard.
        scope.launch {
            if (e.isLoaded) Log.i(TAG, "Memory is short (trim level $level): freeing ${e.descriptor.id} now")
            e.release()
        }
    }

    /**
     * Free [engine]'s model now, or after [lingerMs] with no session or hold
     * in between. An engine that is no longer the built one (another engine
     * or language was picked) is freed now.
     */
    private fun releaseLater(engine: SttEngine) {
        val ms = lingerMs()
        if (ms <= 0 || !engine.isLoaded || builtEngine() !== engine) {
            engine.release()
            return
        }
        synchronized(lock) {
            linger?.cancel()
            lingering = engine
            linger = scope.launch {
                delay(ms)
                synchronized(lock) {
                    if (lingering !== engine) return@launch
                    lingering = null
                    linger = null
                }
                if (held || keepLoaded()) return@launch
                Log.i(TAG, "Warm hold over: freeing ${engine.descriptor.id}")
                engine.release()
            }
        }
    }

    private fun cancelLinger() {
        synchronized(lock) {
            linger?.cancel()
            linger = null
            lingering = null
        }
    }

    /**
     * A session that ran another engine or language than the active one
     * ended: in [RESTORE_DELAY_MS], unless another session starts first,
     * load and warm up the active engine again where one is kept loaded.
     * Building it drops the routed one ([activeEngine]).
     */
    fun routedSessionEnded() {
        synchronized(lock) {
            restore?.cancel()
            restore = scope.launch {
                delay(RESTORE_DELAY_MS)
                if (!held && !keepLoaded()) return@launch
                val e = activeEngine() ?: return@launch
                if (e.isLoaded) return@launch
                Log.i(TAG, "Putting ${e.descriptor.id} back after another app's language ran another engine")
                try {
                    e.warmUp()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Restoring the active engine failed", t)
                }
            }
        }
    }

    private fun closeOne() {
        val last = synchronized(lock) { --count == 0 }
        if (!last) return
        scope.launch {
            // A surface may have opened a new hold meanwhile (the panel shown again).
            if (held || keepLoaded()) return@launch
            val e = builtEngine() ?: return@launch
            if (e.isLoaded) Log.i(TAG, "Last hold closed: freeing ${e.descriptor.id}${if (lingerMs() > 0) " after the warm hold" else ""}")
            releaseLater(e)
        }
    }

    companion object {
        private const val TAG = "EngineHolds"

        /** How long after a routed session the active engine is put back: a minute. */
        const val RESTORE_DELAY_MS = 60_000L
    }
}

/**
 * The warm hold's policy (#91, Max 2026-10-22: five minutes, phones with
 * 6 GB or more). Its cost is the model's memory while idle (Pixel 6a:
 * Parakeet Ultra 858 MB against 180 MB freed, 110M 315 MB, Whisper turbo
 * 1.18 GB) and no CPU; its gain is the load on the next open within it
 * (`docs/benchmarks/2026-10-speed-audit.md` §8).
 */
internal object WarmHold {
    const val LINGER_MS = 5 * 60_000L

    /**
     * `ActivityManager.MemoryInfo.totalMem` of the smallest phone that gets
     * the hold. Phones report a little under their nominal size (Pixel 6a,
     * 6 GB: 5.47 GiB; 4 GB phones about 3.6-3.8), so 5 GiB splits 6 GB
     * phones from 4 GB ones.
     */
    const val MIN_TOTAL_MEM_BYTES = 5L * 1024 * 1024 * 1024

    /** How long a model stays after its last use on a phone with [totalMemBytes] (null: unknown, no hold). */
    fun lingerMs(totalMemBytes: Long?): Long =
        if (totalMemBytes != null && totalMemBytes >= MIN_TOTAL_MEM_BYTES) LINGER_MS else 0L

    /**
     * Whether `onTrimMemory(level)` frees a warm-held model: when memory is
     * running low while we run, or Android is reclaiming cached processes.
     * Not at UI_HIDDEN or BACKGROUND, which come whenever the keyboard or
     * popup goes away (and are all Android 14+ still sends): the hold is for
     * exactly that moment, and Android's low-memory killer ends the cached
     * process if it needs the memory.
     */
    @Suppress("DEPRECATION") // The pressure levels are deprecated in API 34 but still sent before Android 14.
    fun releasesOnTrim(level: Int): Boolean =
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
}
