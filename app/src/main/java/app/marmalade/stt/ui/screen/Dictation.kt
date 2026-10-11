package app.marmalade.stt.ui.screen

import androidx.compose.runtime.Immutable
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig

// The Dictate screen's state machine, kept free of Android and coroutines so
// it can be unit-tested: DictateViewModel feeds it the SttEvents of one
// listening session plus the user's start/stop/clear actions.

/** Where the Dictate screen's listening session stands. */
enum class DictationPhase {
    /** No session. */
    IDLE,

    /** Session started, the model not in memory; until the mic opens (Listening, #78). */
    LOADING,

    /** The mic is live (the model may still be loading; the audio is kept); nobody is talking. */
    LISTENING,

    /** The VAD heard speech; partials are coming. */
    HEARING,

    /** Stop was asked for; the engine is transcribing what it heard. */
    FINISHING,
    ;

    /** True while a session runs (the mic button reads "Stop"). */
    val isActive: Boolean get() = this != IDLE
}

/** A one-off message for the snackbar. */
enum class DictationNotice { NO_SPEECH, MIC_ERROR, MODEL_ERROR, RECOGNITION_ERROR, BUSY, TOO_SLOW }

/**
 * Everything the Dictate screen shows.
 *
 * @property transcript finalized text, after `:textproc`. Lives only in memory.
 * @property partial the engine's current guess at the utterance in progress,
 *   after [TextPipeline.cleanPartial]. Shown after [transcript] but not part
 *   of it until a Final arrives.
 * @property level mic input level for the meter, 0 (silence) to 1 (full scale).
 * @property catchingUp the engine is behind the mic (#30); the status line says so.
 * @property notice a message to show once, cleared by [Dictation.noticeShown].
 */
@Immutable
data class DictationState(
    val phase: DictationPhase = DictationPhase.IDLE,
    val transcript: String = "",
    val partial: String = "",
    val level: Float = 0f,
    val catchingUp: Boolean = false,
    val notice: DictationNotice? = null,
)

/**
 * Pure transitions of [DictationState]. Holds the one [TextPipeline] every
 * Final goes through (ARCHITECTURE.md "Stage-1 pipeline" step 9); partials
 * get its cheap [TextPipeline.cleanPartial].
 */
class Dictation(private val pipeline: TextPipeline = TextPipeline(TextProcConfig())) {

    /** A session is starting. [modelLoaded]: the model is already in memory, so skip "Loading model…". */
    fun started(state: DictationState, modelLoaded: Boolean): DictationState = state.copy(
        phase = if (modelLoaded) DictationPhase.LISTENING else DictationPhase.LOADING,
        partial = "",
        level = 0f,
        catchingUp = false,
        notice = null,
    )

    /** The user pressed stop; the engine flushes what it heard, then the session ends. */
    fun stopping(state: DictationState): DictationState =
        if (state.phase.isActive) state.copy(phase = DictationPhase.FINISHING) else state

    /** The session's flow ended, however it ended. A partial never becomes text on its own. */
    fun ended(state: DictationState): DictationState =
        state.copy(phase = DictationPhase.IDLE, partial = "", level = 0f, catchingUp = false)

    /** The Clear button: drop the transcript and the partial. A running session keeps running. */
    fun cleared(state: DictationState): DictationState = state.copy(transcript = "", partial = "")

    fun noticeShown(state: DictationState): DictationState = state.copy(notice = null)

    /** Apply one event of the running session. */
    fun reduce(state: DictationState, event: SttEvent): DictationState = when (event) {
        // The mic is open (#78; the model may still be loading), or the session runs: whichever comes first.
        SttEvent.Listening, SttEvent.Ready ->
            if (state.phase == DictationPhase.LOADING) state.copy(phase = DictationPhase.LISTENING) else state

        is SttEvent.Level -> state.copy(level = levelFraction(event.rmsDb))

        SttEvent.SpeechStarted ->
            if (state.phase == DictationPhase.FINISHING) state else state.copy(phase = DictationPhase.HEARING)

        // Loops and noise captions come out now; the rest of the cleanup waits for the Final.
        is SttEvent.Partial -> state.copy(partial = pipeline.cleanPartial(event.text))

        // A Final only finalises text; only the mic tap stops the Dictate
        // screen (#43: it always runs Pause, a Final at each pause).
        is SttEvent.Final -> {
            state.copy(
                transcript = commitFinal(pipeline, state.transcript, event),
                partial = "",
                // The utterance is over; wait for the next one.
                phase = if (state.phase == DictationPhase.HEARING) DictationPhase.LISTENING else state.phase,
            )
        }

        is SttEvent.CatchingUp -> state.copy(catchingUp = event.behind)

        // The session goes on; the snackbar suggests a lighter engine.
        SttEvent.TooSlow -> state.copy(notice = DictationNotice.TOO_SLOW)

        SttEvent.NoSpeech -> state.copy(partial = "", notice = DictationNotice.NO_SPEECH)

        is SttEvent.Error -> state.copy(
            phase = DictationPhase.IDLE,
            partial = "",
            level = 0f,
            catchingUp = false,
            notice = when (event.kind) {
                SttErrorKind.MIC -> DictationNotice.MIC_ERROR
                SttErrorKind.MODEL -> DictationNotice.MODEL_ERROR
                SttErrorKind.RECOGNITION -> DictationNotice.RECOGNITION_ERROR
                SttErrorKind.BUSY -> DictationNotice.BUSY
            },
        )
    }

    companion object {
        /** The meter's floor: quieter than this reads as silence. */
        const val METER_FLOOR_DB = -60f

        /** dBFS to the meter's 0..1 scale, linear over [METER_FLOOR_DB]..0 dBFS. */
        fun levelFraction(rmsDb: Float): Float {
            if (rmsDb.isNaN()) return 0f
            return ((rmsDb - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f)
        }

        /**
         * [transcript] after [final]: [pipeline]'s cleanup of the Final, applied
         * to the transcript. Every Final on the Dictate screen goes through
         * this, and so does each utterance of the A/B benchmark (#45), so the
         * benchmark shows the text dictation would have written.
         */
        fun commitFinal(pipeline: TextPipeline, transcript: String, final: SttEvent.Final): String =
            pipeline.process(final.text, before = transcript, nearSilent = final.nearSilent, silent = final.silent)
                .applyTo(transcript)

        /** What goes between the transcript and the partial on screen: a space unless one isn't needed. */
        fun partialSeparator(transcript: String): String =
            if (transcript.isEmpty() || transcript.last().isWhitespace()) "" else " "
    }
}
