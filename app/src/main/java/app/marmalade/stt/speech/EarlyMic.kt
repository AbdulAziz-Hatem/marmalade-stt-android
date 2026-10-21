package app.marmalade.stt.speech

/*
 * Data flow (#78):
 *   SherpaSttEngine.listen opens the mic before it loads the model, then runs
 *   record() (Dispatchers.IO):
 *     mic hops -> queue (in memory only; MAX_QUEUED_HOPS, the oldest dropped;
 *                 a non-live source instead waits for room, #90)
 *              -> onLevel while no session reads hops yet (the meter moves
 *                 during the load)
 *   the model is loaded: the session collects hops -> the queue first (the
 *     backlog, in a burst), then each live hop as record() queues it
 *   stopRecording() (SherpaSttEngine.stop): the mic closes; hops end once the
 *     queue has been read, so a stop during the load loses nothing
 *   close(): the mic closes and the queue is dropped; hops end at once
 */

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [MicCapture] opened before the session that reads it: the engine opens
 * the mic as soon as it is asked to listen, and this keeps the audio while
 * the model loads, so words spoken meanwhile reach the session. Run [record]
 * for as long as the listen lasts; the session collects [hops] once.
 *
 * Audio stays in memory: at most [maxQueuedHops] hops (60 s) wait, which a
 * load never comes near; past that the oldest are dropped (counted in the
 * log, numbers only). A non-[live][MicCapture.live] source (a caller's
 * stream, #90) loses nothing instead: [record] stops reading it while the
 * queue is full, until the session has taken a hop or the capture is stopped
 * or closed.
 *
 * @param mic the open capture. Owned: [close] closes it.
 * @param onLevel the dBFS of each hop captured before [hops] is collected
 *   (the session meters the rest). Called on [record]'s thread, under a lock:
 *   keep it short and non-blocking.
 */
internal class EarlyMic(
    private val mic: MicCapture,
    private val onLevel: (rmsDb: Float) -> Unit = {},
    private val maxQueuedHops: Int = MAX_QUEUED_HOPS,
) : MicCapture {

    companion object {
        /** 60 s of 100 ms hops (3.8 MB). */
        const val MAX_QUEUED_HOPS = 600
        private const val TAG = "EarlyMic"
    }

    /** Guards the fields below. */
    private val lock = Any()
    private val queue = ArrayDeque<FloatArray>()
    private var handedOver = false // hops is being collected
    private var recording = true // record() hasn't ended
    private var closed = false
    private var stopped = false // stopRecording(): no more waiting for room
    private var failure: Exception? = null
    private var dropped = 0

    /** Nudged whenever a hop is queued or the capture ends. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Nudged whenever a hop is taken or the capture is stopped: a non-live [record] waits on it. */
    private val room = Channel<Unit>(Channel.CONFLATED)

    override val live: Boolean get() = mic.live

    @Volatile
    override var backlogHops: Int = 0
        private set

    override val hasPlatformAgc: Boolean get() = mic.hasPlatformAgc

    /**
     * Reads the mic into the queue until it ends: closed ([stopRecording],
     * [close]) or failed (the failure is thrown by [hops] once the queue has
     * been read). Collects the mic on the caller's dispatcher, which must
     * allow blocking.
     */
    suspend fun record() {
        try {
            val live = mic.live
            mic.hops.collect { hop ->
                // Backpressure: a stream is read no faster than the session takes it.
                while (!live && synchronized(lock) { !closed && !stopped && queue.size >= maxQueuedHops }) {
                    room.receive()
                }
                synchronized(lock) {
                    if (!closed) {
                        queue.addLast(hop)
                        if (live && queue.size > maxQueuedHops) {
                            queue.removeFirst()
                            dropped++
                        }
                        if (!handedOver) onLevel(NearSilence.rmsDb(hop))
                    }
                }
                wake.trySend(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(lock) { failure = e }
        } finally {
            val lost = synchronized(lock) {
                recording = false
                dropped
            }
            wake.trySend(Unit)
            if (lost > 0) Log.w(TAG, "Dropped the oldest $lost hops: more than $maxQueuedHops waited for the model")
        }
    }

    /**
     * The hops kept so far, then each new one as it is captured. Ends when
     * the capture has ended and the queue is read, or at once after [close].
     * Collect it once.
     */
    override val hops: Flow<FloatArray> = flow {
        synchronized(lock) {
            check(!handedOver) { "An EarlyMic's hops can be collected only once" }
            handedOver = true
            backlogHops = queue.size
        }
        while (true) {
            val (hop, ended, failed) = synchronized(lock) {
                Triple(queue.removeFirstOrNull(), closed || !recording, failure.takeUnless { closed })
            }
            when {
                hop != null -> {
                    room.trySend(Unit)
                    emit(hop)
                }
                ended -> {
                    failed?.let { throw it }
                    return@flow
                }
                else -> wake.receive()
            }
        }
    }

    /**
     * Stop recording: the mic closes, and [hops] ends once what was captured
     * until now has been read (a non-live [record] waiting for room keeps
     * its hop, past the bound).
     */
    fun stopRecording() {
        synchronized(lock) { stopped = true }
        mic.close()
        room.trySend(Unit)
    }

    /** Close the mic and drop what is still queued; [hops] ends at once. Idempotent, from any thread. */
    override fun close() {
        synchronized(lock) {
            closed = true
            queue.clear()
        }
        mic.close()
        wake.trySend(Unit)
        room.trySend(Unit)
    }
}
