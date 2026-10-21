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
    data class Text(val text: String) : OneShotOutcome {
        /** The engine's words before `:textproc`, utterances joined by spaces (#90, `EXTRA_ENABLE_FORMATTING`'s raw text). */
        var raw: String = text
            private set

        constructor(text: String, raw: String) : this(text) {
            this.raw = raw
        }
    }

    /**
     * Nothing to return. [heardSpeech]: the VAD heard speech but it produced
     * no text (`SpeechRecognizer.ERROR_NO_MATCH`); otherwise nobody spoke
     * before the timeout (`ERROR_SPEECH_TIMEOUT`).
     */
    data class NoMatch(val heardSpeech: Boolean) : OneShotOutcome

    data class Failed(val kind: SttErrorKind) : OneShotOutcome
}

/** What ends a [OneShotSession] on its own (#90). */
sealed interface OneShotEnd {
    /**
     * The first utterance that leaves text, or an end word; nobody speaking
     * for the no-speech timeout. What every caller gets by default.
     */
    data object FirstEnd : OneShotEnd

    /**
     * Nobody speaking for [silenceMs] after the last utterance (at the
     * start, the no-speech timeout if that is longer): a segmented session
     * ended by `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`.
     */
    data class Pause(val silenceMs: Long) : OneShotEnd

    /**
     * Nothing: the request ends when its audio does (the caller stops, or
     * closes its stream), or the sheet's lock is released. No timeout.
     */
    data object AudioEnd : OneShotEnd
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
 * Other apps can ask for other ends (#90, [OneShotEnd]): a segmented
 * session runs to a long pause, a caller's own audio stream to its end.
 * [minimumLengthMs] holds every end of its own (first end, end word,
 * timeout) until the request has run that long since [SttEvent.Ready].
 * [maxLengthMs] stops the engine however the request was asked to end, the
 * minimum length and a lock included, once it has run that long since
 * [run] began: a caller's request can't hold the engine for ever.
 *
 * Each utterance joins the text so far through the pipeline, as the Dictate
 * screen does. Pure coroutine code over the engine's event flow, so it is
 * unit-tested with fakes.
 *
 * @param listen the engine's session flow (`SpeechEngineManager.listen`).
 * @param stopEngine graceful stop (`SpeechEngineManager.stop`): the engine
 *   flushes what it heard, then the flow ends.
 * @param pipeline the text pipeline for the current settings.
 * @param end what ends the request on its own.
 * @param minimumLengthMs `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS`, or 0.
 * @param maxLengthMs the hard cap ([RecognitionRequest.maxLengthMs]), or null for none.
 */
class OneShotSession(
    private val listen: () -> Flow<SttEvent>,
    private val stopEngine: () -> Unit,
    private val pipeline: suspend () -> TextPipeline,
    private val noSpeechTimeoutMs: Long = ListenTimeouts.NO_SPEECH_TIMEOUT_MS,
    private var end: OneShotEnd = OneShotEnd.FirstEnd,
    private val minimumLengthMs: Long = 0,
    private val maxLengthMs: Long? = null,
) {
    private var timeout: Job? = null

    /** The minimum length has run out (or there is none). */
    private var longEnough = minimumLengthMs <= 0

    /** An end came before [longEnough]: the engine stops once it is. */
    private var stopHeld = false

    /**
     * The user locked listening: nothing ends the request on its own any more
     * (no first-end stop, no no-speech timeout). Call it on the thread that
     * collects [run] (the main thread for both callers).
     */
    fun lock() {
        end = OneShotEnd.AudioEnd
        stopHeld = false
        disarmTimeout()
    }

    private fun disarmTimeout() {
        timeout?.cancel()
        timeout = null
    }

    /**
     * Run the session to its end. [onEvent] sees every engine event after
     * the session has handled it, partials after `TextPipeline.cleanPartial`
     * (possibly ""). [onSegment] gets each utterance's text as it joined the
     * result (after `:textproc`, never blank), and the engine's raw words for
     * it: segmented sessions send them as they come. Cancelling the caller
     * aborts without an outcome.
     */
    suspend fun run(
        onSegment: (text: String, raw: String) -> Unit = { _, _ -> },
        onEvent: (SttEvent) -> Unit = {},
    ): OneShotOutcome = coroutineScope {
        var text = ""
        val raw = StringBuilder()
        var heardSpeech = false
        var failure: SttErrorKind? = null
        var minimumLength: Job? = null
        val cap = maxLengthMs?.let {
            launch {
                delay(it)
                stopEngine()
            }
        }

        // Every end of the session's own goes through here, so the minimum length holds it.
        fun endNow() {
            if (longEnough) stopEngine() else stopHeld = true
        }

        fun armTimeout(afterSpeech: Boolean) {
            val ms = when (val e = end) {
                OneShotEnd.FirstEnd -> noSpeechTimeoutMs
                is OneShotEnd.Pause -> if (afterSpeech) e.silenceMs else maxOf(e.silenceMs, noSpeechTimeoutMs)
                OneShotEnd.AudioEnd -> return
            }
            if (timeout == null) timeout = launch { delay(ms); endNow() }
        }

        try {
            listen().collect { event0 ->
                // Callers show or send partials: loops and noise captions come out first.
                val event = if (event0 is SttEvent.Partial) SttEvent.Partial(pipeline().cleanPartial(event0.text)) else event0
                when (event) {
                    SttEvent.Ready -> {
                        if (!longEnough && minimumLength == null) {
                            minimumLength = launch {
                                delay(minimumLengthMs)
                                longEnough = true
                                if (stopHeld) stopEngine()
                            }
                        }
                        armTimeout(afterSpeech = false)
                    }
                    SttEvent.SpeechStarted -> {
                        heardSpeech = true
                        disarmTimeout()
                        // More speech: an end held for the minimum length no longer stands (the next Final decides).
                        stopHeld = false
                    }
                    is SttEvent.Final -> {
                        val edit = pipeline().process(event.text, before = text, nearSilent = event.nearSilent, silent = event.silent)
                        text = edit.applyTo(text)
                        val words = event.text.trim()
                        if (words.isNotEmpty() && edit.insert.isNotBlank()) {
                            if (raw.isNotEmpty()) raw.append(' ')
                            raw.append(words)
                            onSegment(edit.insert.trim(), words)
                        }
                        when (end) {
                            OneShotEnd.AudioEnd -> Unit
                            OneShotEnd.FirstEnd -> if (event.endWord || text.isNotBlank()) endNow() else armTimeout(afterSpeech = true)
                            is OneShotEnd.Pause -> if (event.endWord) endNow() else armTimeout(afterSpeech = true)
                        }
                    }
                    is SttEvent.Error -> failure = event.kind
                    else -> Unit
                }
                onEvent(event)
            }
        } finally {
            disarmTimeout()
            minimumLength?.cancel()
            cap?.cancel()
        }

        val result = text.trim()
        // Busy mid-session: the user's own surface took the engine (SessionArbiter); what was heard is dropped.
        if (failure == SttErrorKind.BUSY) return@coroutineScope OneShotOutcome.Failed(SttErrorKind.BUSY)
        if (result.isNotEmpty()) {
            OneShotOutcome.Text(result, raw.toString())
        } else {
            failure?.let { OneShotOutcome.Failed(it) } ?: OneShotOutcome.NoMatch(heardSpeech)
        }
    }
}
