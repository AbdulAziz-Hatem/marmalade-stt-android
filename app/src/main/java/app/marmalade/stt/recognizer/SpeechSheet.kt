package app.marmalade.stt.recognizer

import android.app.Activity
import android.speech.RecognizerIntent
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent

// The speech-input sheet's state machine (RecognizeSpeechActivity), kept free
// of Android views and coroutines so it can be unit-tested. The listening
// itself is OneShotSession's job; this only decides what the sheet shows and
// what the activity returns when the user leaves.

enum class SheetPhase {
    /** The model isn't in memory; until the mic opens (Listening, #78). */
    LOADING,

    /** The mic is live (the model may still be loading; the audio is kept); nobody is talking. */
    LISTENING,

    /** The VAD heard speech; partials are coming. */
    HEARING,

    /** Done was pressed; the engine is transcribing what it heard. */
    FINISHING,

    /** The session ended without text; [SheetState.problem] says why. */
    PROBLEM,
}

/** Why there is no text. Each has its own message and actions on the sheet. */
enum class SheetProblem {
    NO_SPEECH,
    NO_MODEL,

    /** The user refused the mic permission. */
    MIC_BLOCKED,
    MIC_ERROR,
    RECOGNITION_ERROR,
    BUSY,

    /** No installed engine transcribes the caller's language; one the user could download does (#90). */
    LANGUAGE_UNAVAILABLE,

    /** No engine of ours transcribes the caller's language (#90). */
    LANGUAGE_NOT_SUPPORTED,
    ;

    /** Try again makes sense (the user can't fix a missing model, language or permission from the sheet). */
    val canRetry: Boolean
        get() = this != NO_MODEL && this != MIC_BLOCKED && this != LANGUAGE_UNAVAILABLE && this != LANGUAGE_NOT_SUPPORTED
}

/**
 * @property resultCode what the activity returns if the user closes the
 *   sheet now: cancelled while listening, the problem's code after one.
 * @property locked the user pressed and held the mic (#43): the request
 *   doesn't end on its own ([OneShotSession.lock]) until a tap on the mic
 *   or Done.
 * @property language the caller's `EXTRA_LANGUAGE` that the language
 *   problems name; null otherwise.
 */
data class SheetState(
    val phase: SheetPhase = SheetPhase.LOADING,
    val partial: String = "",
    val problem: SheetProblem? = null,
    val resultCode: Int = Activity.RESULT_CANCELED,
    val locked: Boolean = false,
    val language: String? = null,
) {
    /** Press-and-hold would lock listening now: a session runs, not yet finishing or locked. */
    val canLock: Boolean
        get() = !locked && (phase == SheetPhase.LOADING || phase == SheetPhase.LISTENING || phase == SheetPhase.HEARING)
}

object SpeechSheet {

    /** A session is starting. [modelLoaded]: skip "Loading model…". */
    fun started(modelLoaded: Boolean) =
        SheetState(phase = if (modelLoaded) SheetPhase.LISTENING else SheetPhase.LOADING)

    /** Done, or the mic, was tapped: what was heard is returned. A lock is released. */
    fun stopping(state: SheetState): SheetState =
        if (state.phase == SheetPhase.PROBLEM) state else state.copy(phase = SheetPhase.FINISHING, locked = false)

    /**
     * The mic was pressed and held (#43): lock listening, when [SheetState.canLock].
     * The activity then calls [OneShotSession.lock].
     */
    fun locking(state: SheetState): SheetState = if (state.canLock) state.copy(locked = true) else state

    fun reduce(state: SheetState, event: SttEvent): SheetState = when (event) {
        // The mic is open (#78; the model may still be loading), or the session runs: whichever comes first.
        SttEvent.Listening, SttEvent.Ready ->
            if (state.phase == SheetPhase.LOADING) state.copy(phase = SheetPhase.LISTENING) else state
        SttEvent.SpeechStarted ->
            if (state.phase == SheetPhase.FINISHING) state else state.copy(phase = SheetPhase.HEARING)
        is SttEvent.Partial -> state.copy(partial = event.text)
        // An utterance that left no text (the session goes on): wait for the next.
        is SttEvent.Final -> state.copy(
            partial = "",
            phase = if (state.phase == SheetPhase.HEARING) SheetPhase.LISTENING else state.phase,
        )
        else -> state
    }

    /** The session ended without text. (With text, the activity returns it and closes.) */
    fun ended(outcome: OneShotOutcome): SheetState {
        val problem = when (outcome) {
            is OneShotOutcome.Text -> error("Text is returned, not shown: $outcome")
            is OneShotOutcome.NoMatch -> SheetProblem.NO_SPEECH
            is OneShotOutcome.Failed -> when (outcome.kind) {
                SttErrorKind.MIC -> SheetProblem.MIC_ERROR
                SttErrorKind.MODEL -> SheetProblem.NO_MODEL
                SttErrorKind.RECOGNITION -> SheetProblem.RECOGNITION_ERROR
                SttErrorKind.BUSY -> SheetProblem.BUSY
            }
        }
        return SheetState(phase = SheetPhase.PROBLEM, problem = problem, resultCode = outcome.recognizerResultCode())
    }

    /**
     * No installed engine transcribes [language], the caller's (#90,
     * [LanguageRoute.Unavailable]): the sheet doesn't listen. The result is
     * `RESULT_CLIENT_ERROR`: `RecognizerIntent` has no language code, and
     * the request itself (its language) is what can't be served, as with a
     * missing model; `RESULT_NO_MATCH` would invite the caller to just ask
     * again, and the server and network codes would point it at a
     * connection we never use.
     */
    fun languageUnavailable(route: LanguageRoute.Unavailable, language: String) = SheetState(
        phase = SheetPhase.PROBLEM,
        problem = if (route.downloadable) SheetProblem.LANGUAGE_UNAVAILABLE else SheetProblem.LANGUAGE_NOT_SUPPORTED,
        resultCode = RecognizerIntent.RESULT_CLIENT_ERROR,
        language = language,
    )

    /** The user said no to the microphone. */
    fun micDenied() = SheetState(
        phase = SheetPhase.PROBLEM,
        problem = SheetProblem.MIC_BLOCKED,
        resultCode = RecognizerIntent.RESULT_AUDIO_ERROR,
    )
}
