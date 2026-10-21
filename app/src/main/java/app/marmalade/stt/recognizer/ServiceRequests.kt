package app.marmalade.stt.recognizer

import app.marmalade.stt.speech.SttEvent
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The RecognitionService's requests over time (#90), apart from the
 * framework so it is unit-tested:
 *
 * - **One at a time.** Android lets one session run (it turns a second
 *   client away busy itself), so a request of ours still running when a new
 *   one starts is one the framework is done with: cancelled, or its client
 *   died and the cancel is still on its way. The new request cancels it and
 *   waits for it to let go of the engine (a model load can't be
 *   interrupted); starting before it has would fail busy. A request
 *   cancelled while it waits still ends only after the one it waited for,
 *   so the next request in line waits for the real holder too.
 * - **A stop before the engine listens is repeated.** A [stop] that comes
 *   while the request waits for the previous one, routes its language or
 *   loads its engine is repeated once the engine listens
 *   ([Request.repeatingStop]): the engine forgets a stop from before its
 *   session started.
 * - **The caller's audio stream belongs to its request.** It is closed when
 *   the request ends, however it ends: an error before the engine started,
 *   a cancel while it waited for the previous request, a scope cancelled
 *   before it ran. The engine's capture closes it too when it is done with
 *   it; closing twice does nothing.
 *
 * Call [start], [stop] and [cancel] on one thread (the service's main thread).
 *
 * @param stopEngine the engine's graceful stop (`SpeechEngineManager.stop`).
 */
class ServiceRequests(private val scope: CoroutineScope, private val stopEngine: () -> Unit) {

    /** One request. Its body runs as the receiver of this. */
    inner class Request internal constructor(private val onStop: () -> Unit) {
        /** Written on the main thread, read where the engine's events are collected. */
        @Volatile
        private var stopAsked = false

        internal lateinit var job: Job

        /** The engine's [events], with a [stop] that came before it listened repeated once it does. */
        fun repeatingStop(events: Flow<SttEvent>): Flow<SttEvent> =
            events.onEach { if (it == SttEvent.Listening && stopAsked) stopEngine() }

        internal fun stop() {
            stopAsked = true
            onStop()
            stopEngine()
        }
    }

    private var current: Request? = null

    /**
     * Start a request: [body] runs on [scope] once the previous request has
     * ended.
     *
     * @param stream the caller's audio stream, closed when this request ends;
     *   null for the mic.
     * @param onStop called on [stop] (`ServiceSession.stopped`).
     */
    fun start(stream: Closeable?, onStop: () -> Unit = {}, body: suspend Request.() -> Unit): Job {
        val previous = current?.job
        val request = Request(onStop)
        current = request
        request.job = scope.launch {
            try {
                previous?.cancelAndJoin()
            } finally {
                // Cancelled while waiting: still end after it, or the next request would start too early.
                withContext(NonCancellable) { previous?.join() }
            }
            request.body()
        }
        // Also when the job never ran (a cancelled scope): a handler on a finished job runs at once.
        if (stream != null) request.job.invokeOnCompletion { runCatching { stream.close() } }
        return request.job
    }

    /** `stopListening`: the running request stops gracefully (what was heard is still transcribed). */
    fun stop() {
        current?.takeIf { it.job.isActive }?.stop()
    }

    /** `cancel`: the running request ends at once, with no answer. */
    fun cancel() {
        current?.job?.cancel()
    }
}
