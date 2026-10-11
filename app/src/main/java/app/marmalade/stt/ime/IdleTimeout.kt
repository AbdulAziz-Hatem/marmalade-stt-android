package app.marmalade.stt.ime

import app.marmalade.stt.speech.ListenTimeouts
import app.marmalade.stt.speech.SttEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The voice keyboard's idle timeout: calls [onIdle] once nobody has spoken
 * for [timeoutMs]. The countdown starts when the session runs (Ready, the
 * model loaded; not at Listening, so a slow load never counts as silence) and
 * restarts at every sign of speech (SpeechStarted, Partial). Other events
 * don't touch it. The service stops the session gracefully on idle, so any
 * Final still lands, and the panel stays open, paused.
 *
 * Runs in the session's own [scope], so it ends with the session.
 */
class IdleTimeout(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = ListenTimeouts.IME_IDLE_TIMEOUT_MS,
    private val onIdle: () -> Unit,
) {
    private var countdown: Job? = null

    fun onEvent(event: SttEvent) {
        when (event) {
            SttEvent.Ready, SttEvent.SpeechStarted, is SttEvent.Partial -> restart()
            else -> Unit
        }
    }

    fun cancel() {
        countdown?.cancel()
        countdown = null
    }

    private fun restart() {
        countdown?.cancel()
        countdown = scope.launch {
            delay(timeoutMs)
            countdown = null
            onIdle()
        }
    }
}
