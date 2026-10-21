package app.marmalade.stt.speech

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
 * Thread-safe: surfaces open and close holds on the main thread, sessions end
 * on a background one. Freeing runs on [scope], never on the caller's thread.
 *
 * @param keepLoaded the "Keep engine ready" setting, read when it matters.
 * @param activeEngine the active engine, built if its model is installed,
 *   else null (a preload then does nothing).
 * @param builtEngine the engine already built, or null; never builds one.
 */
internal class EngineHolds(
    private val scope: CoroutineScope,
    private val keepLoaded: suspend () -> Boolean,
    private val activeEngine: () -> SttEngine?,
    private val builtEngine: () -> SttEngine?,
) {
    private val lock = Any()
    private var count = 0
    private var restore: Job? = null

    /** True while at least one hold is open. */
    val held: Boolean get() = synchronized(lock) { count > 0 }

    /**
     * Opens a hold. [preload]: also load the active engine and warm it up
     * ([SttEngine.warmUp]) in the background, whatever Keep engine ready says.
     */
    fun hold(preload: Boolean): EngineHold {
        synchronized(lock) { count++ }
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
        engine.release()
    }

    /** A session starts: a pending restore would only compete with it for the engine. */
    fun sessionStarting() {
        synchronized(lock) {
            restore?.cancel()
            restore = null
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
            if (e.isLoaded) Log.i(TAG, "Last hold closed: freeing ${e.descriptor.id}")
            e.release()
        }
    }

    companion object {
        private const val TAG = "EngineHolds"

        /** How long after a routed session the active engine is put back: a minute. */
        const val RESTORE_DELAY_MS = 60_000L
    }
}
