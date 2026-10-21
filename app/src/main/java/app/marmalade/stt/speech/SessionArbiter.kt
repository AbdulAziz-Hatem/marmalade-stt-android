package app.marmalade.stt.speech

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Who gets the engine when two sessions want it (#90, Max 2026-10-21: "your
 * own use wins"). Another app's RecognitionService session is
 * [preemptible][session]: the user starting one of their own surfaces (the
 * voice keyboard, Dictate, the speak-now sheet) ends it, rather than being
 * turned away busy for as long as that app keeps listening (up to an hour
 * for a stream). Never the other way round: another app's request still gets
 * BUSY from the engine while the user's own session runs.
 *
 * The pre-empted session is cancelled, so its capture (mic or the caller's
 * stream) closes and the engine's claim is given back, and it ends with
 * [SttEvent.Error] ([SttErrorKind.BUSY], [PREEMPTED_MESSAGE]): what it had
 * heard is dropped. The engine and its loaded model stay as they are, so
 * the user's session doesn't reload a model the pre-empted one was using.
 *
 * Framework-free and thread-safe; unit-tested with a real [SherpaSttEngine].
 */
class SessionArbiter {

    private class Session(val preemptible: Boolean) {
        val preempt = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
    }

    private val lock = Any()

    /** The sessions running now (normally one; one turned away busy ends at once). */
    private val running = mutableSetOf<Session>()

    /** The user's own sessions from [userStarting] to [userEnded]. */
    private var userSessions = 0

    /**
     * One of the user's own sessions is starting or running: another app's
     * request is turned away busy at once, before it picks an engine. (Routed
     * to another engine or language hint, it would otherwise build a second
     * engine beside the user's and claim it: two mics, two models.)
     */
    fun userSessionRunning(): Boolean = synchronized(lock) { userSessions > 0 }

    /** One of the user's own sessions starts: call first, before [makeRoom], and [userEnded] when it ends. */
    fun userStarting() {
        synchronized(lock) { userSessions++ }
    }

    fun userEnded() {
        synchronized(lock) { userSessions-- }
    }

    /**
     * Make room for a session: for one of the user's own ([preemptible]
     * false), end the preemptible sessions running and wait until they have
     * let go of the engine. Does nothing for a preemptible one.
     */
    suspend fun makeRoom(preemptible: Boolean) {
        if (preemptible) return
        val victims = synchronized(lock) { running.filter { it.preemptible } }
        victims.forEach { it.preempt.complete(Unit) }
        victims.forEach { it.ended.await() }
    }

    /**
     * [events] (the engine's session) as one arbitrated session. A
     * [preemptible] one ends early, with the BUSY error above, when
     * [makeRoom] asks; [onPreempted] runs then, before the error is sent.
     */
    fun session(
        preemptible: Boolean,
        events: Flow<SttEvent>,
        onPreempted: () -> Unit = {},
    ): Flow<SttEvent> = channelFlow {
        val me = Session(preemptible)
        synchronized(lock) { running += me }

        // The engine has let go: whoever waits in makeRoom may start now.
        fun letGo() {
            synchronized(lock) { running -= me }
            me.ended.complete(Unit)
        }

        try {
            val engine = launch { events.collect { send(it) } }
            val watcher = launch {
                me.preempt.await()
                engine.cancel()
            }
            // Until the engine's session has finished unwinding: its capture closed, its claim given back.
            engine.join()
            watcher.cancel()
            letGo()
            if (engine.isCancelled && me.preempt.isCompleted) {
                onPreempted()
                send(SttEvent.Error(SttErrorKind.BUSY, PREEMPTED_MESSAGE))
            }
        } finally {
            letGo()
        }
    }

    companion object {
        const val PREEMPTED_MESSAGE = "busy: the user started listening in Marmalade STT"
    }
}
