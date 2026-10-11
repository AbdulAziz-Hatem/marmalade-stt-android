package app.marmalade.stt.recognizer

import app.marmalade.stt.speech.ListenTimeouts
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.TextPipeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** How a [OneShotSession] ended. */
sealed interface OneShotOutcome {
    /** The recognised text, after `:textproc`. Never blank. */
    data class Text(val text: String) : OneShotOutcome

    /**
     * Nothing to return. [heardSpeech]: the VAD heard speech but it produced
     * no text (`SpeechRecognizer.ERROR_NO_MATCH`); otherwise nobody spoke
     * before the timeout (`ERROR_SPEECH_TIMEOUT`).
     */
    data class NoMatch(val heardSpeech: Boolean) : OneShotOutcome

    data class Failed(val kind: SttErrorKind) : OneShotOutcome
}

/**
 * One request for one result: the listening logic the speech-input activity
 * and the RecognitionService share. Callers of both APIs expect a single
 * answer, so:
 *
 * - the request ends at the first detected end, whatever the end mode
 *   (#43): the first utterance that leaves text after `:textproc` stops the
 *   engine (a hallucination dropped by the filter doesn't count, so
 *   listening goes on), and so does an end word ([SttEvent.Final.endWord]),
 *   even with nothing before it: the user said they are done, so the
 *   request ends with no match. Tap stop never reaches it (#41): the
 *   engine runs the other apps' end of speech
 *   (`SpeechEngineManager.listen(ListenSurface.APPS)`), which is never Tap
 *   stop, or no utterance would end until the caller stopped;
 * - if nobody speaks for [noSpeechTimeoutMs] after the session starts
 *   ([SttEvent.Ready], the model loaded: a slow load isn't silence, and the
 *   audio kept during it is heard first), or after an utterance that left
 *   no text, the engine is stopped, so a forgotten
 *   request never holds the mic;
 * - [lock] (the sheet's press-and-hold, #43) turns both off for the rest of
 *   the request: Finals only join the text, and it ends when the caller
 *   stops the engine (the sheet's mic tap or Done).
 *
 * Each utterance joins the text so far through the pipeline, as the Dictate
 * screen does. Pure coroutine code over the engine's event flow, so it is
 * unit-tested with fakes.
 *
 * @param listen the engine's session flow (`SpeechEngineManager.listen`).
 * @param stopEngine graceful stop (`SpeechEngineManager.stop`): the engine
 *   flushes what it heard, then the flow ends.
 * @param pipeline the text pipeline for the current settings.
 */
class OneShotSession(
    private val listen: () -> Flow<SttEvent>,
    private val stopEngine: () -> Unit,
    private val pipeline: suspend () -> TextPipeline,
    private val noSpeechTimeoutMs: Long = ListenTimeouts.NO_SPEECH_TIMEOUT_MS,
) {
    private var locked = false
    private var timeout: Job? = null

    /**
     * The user locked listening: nothing ends the request on its own any more
     * (no first-end stop, no no-speech timeout). Call it on the thread that
     * collects [run] (the main thread for both callers).
     */
    fun lock() {
        locked = true
        disarmTimeout()
    }

    private fun disarmTimeout() {
        timeout?.cancel()
        timeout = null
    }

    /**
     * Run the session to its end. [onEvent] sees every engine event after
     * the session has handled it, partials after `TextPipeline.cleanPartial`
     * (possibly ""). Cancelling the caller aborts without an outcome.
     */
    suspend fun run(onEvent: (SttEvent) -> Unit = {}): OneShotOutcome = coroutineScope {
        var text = ""
        var heardSpeech = false
        var failure: SttErrorKind? = null

        fun armTimeout() {
            if (timeout == null && !locked) timeout = launch { delay(noSpeechTimeoutMs); stopEngine() }
        }

        try {
            listen().collect { raw ->
                // Callers show or send partials: loops and noise captions come out first.
                val event = if (raw is SttEvent.Partial) SttEvent.Partial(pipeline().cleanPartial(raw.text)) else raw
                when (event) {
                    SttEvent.Ready -> armTimeout()
                    SttEvent.SpeechStarted -> {
                        heardSpeech = true
                        disarmTimeout()
                    }
                    is SttEvent.Final -> {
                        text = pipeline().process(event.text, before = text, nearSilent = event.nearSilent, silent = event.silent)
                            .applyTo(text)
                        when {
                            locked -> Unit
                            event.endWord || text.isNotBlank() -> stopEngine()
                            else -> armTimeout()
                        }
                    }
                    is SttEvent.Error -> failure = event.kind
                    else -> Unit
                }
                onEvent(event)
            }
        } finally {
            disarmTimeout()
        }

        val result = text.trim()
        if (result.isNotEmpty()) {
            OneShotOutcome.Text(result)
        } else {
            failure?.let { OneShotOutcome.Failed(it) } ?: OneShotOutcome.NoMatch(heardSpeech)
        }
    }
}
