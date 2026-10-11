package app.marmalade.stt.ime

import androidx.compose.runtime.Immutable
import app.marmalade.stt.speech.EndOfDictation
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.ui.screen.Dictation
import app.marmalade.stt.ui.screen.DictationNotice

// The voice keyboard panel's state machine, kept free of Android and
// coroutines so it can be unit-tested: VoiceInputService feeds it the
// SttEvents of one listening session plus the user's Cancel / mic / Done
// taps and presses-and-holds on the mic (the lock, #43). Writing text into the editor is ImeTextWriter's job, not this one's.

/** Where the voice keyboard's listening session stands. */
enum class VoiceImePhase {
    /** No session: paused by the user, blocked, or stopped by an error. */
    IDLE,

    /** Session started, the model not in memory; until the mic opens (Listening, #78). */
    LOADING,

    /** The mic is live (the model may still be loading; the audio is kept); nobody is talking. */
    LISTENING,

    /** The VAD heard speech; partials are coming. */
    HEARING,

    /** Pause or Done was tapped; the engine is transcribing what it heard. */
    FINISHING,
    ;

    /** True while a session runs. */
    val isActive: Boolean get() = this != IDLE
}

/** Why the panel can't listen at all. Checked each time the keyboard is shown. */
enum class VoiceImeBlocker { NO_MIC_PERMISSION, NO_MODEL }

/**
 * Everything the voice keyboard panel shows.
 *
 * @property partial the engine's current guess at the utterance in progress,
 *   already through `TextPipeline.cleanPartial` (VoiceInputService does that,
 *   so the editor's composing text is the same).
 * @property level mic input level for the meter, 0 (silence) to 1 (full scale).
 * @property notice what went wrong (or "no speech") in the last session. Shown
 *   while idle; cleared when the next session starts.
 * @property exitWhenDone Done was tapped: switch back to the previous keyboard
 *   once the running session has delivered its Final.
 * @property autoStop the keyboard's end of speech is an auto mode (#43; Smart
 *   Turn, Pause, End word), not Tap to stop: the first Final acts as Done and
 *   the 30 s idle pause runs. Set by [VoiceIme.configured] once the session
 *   has read the settings; kept while idle.
 * @property locked the user pressed and held the mic in an auto mode (#43):
 *   Finals are still committed but none acts as Done, and the idle pause is
 *   off, until a tap on the mic stops listening.
 */
@Immutable
data class VoiceImeState(
    val phase: VoiceImePhase = VoiceImePhase.IDLE,
    val partial: String = "",
    val level: Float = 0f,
    val blocker: VoiceImeBlocker? = null,
    val notice: DictationNotice? = null,
    val exitWhenDone: Boolean = false,
    val autoStop: Boolean = false,
    val locked: Boolean = false,
) {
    /**
     * Pressing and holding the mic means something now: while idle it starts
     * listening locked ([VoiceIme.started]); in an auto mode it locks the
     * running session ([VoiceIme.locking]). In Tap to stop it doesn't, and a
     * long press is just a tap.
     */
    val canLock: Boolean
        get() = when (phase) {
            VoiceImePhase.IDLE -> blocker == null
            VoiceImePhase.FINISHING -> false
            else -> autoStop && !locked
        }
}

/** Pure transitions of [VoiceImeState]. */
object VoiceIme {

    /** The keyboard was shown and checked what it needs. A non-null [blocker] means don't start. */
    fun blocked(state: VoiceImeState, blocker: VoiceImeBlocker?): VoiceImeState = state.copy(blocker = blocker)

    /**
     * A session is starting. [modelLoaded]: the model is already in memory, so
     * skip "Loading model…". [locked]: started by pressing and holding the
     * mic; [configured] drops the lock again in Tap to stop.
     */
    fun started(state: VoiceImeState, modelLoaded: Boolean, locked: Boolean = false): VoiceImeState = state.copy(
        phase = if (modelLoaded) VoiceImePhase.LISTENING else VoiceImePhase.LOADING,
        partial = "",
        level = 0f,
        notice = null,
        exitWhenDone = false,
        locked = locked,
    )

    /** The session read the keyboard's end of speech ([end]). A lock means nothing in Tap to stop. */
    fun configured(state: VoiceImeState, end: EndOfDictation): VoiceImeState {
        val autoStop = end != EndOfDictation.TAP_STOP
        return state.copy(autoStop = autoStop, locked = state.locked && autoStop)
    }

    /** The mic was pressed and held during a session (#43): lock it, when [VoiceImeState.canLock]. */
    fun locking(state: VoiceImeState): VoiceImeState =
        if (state.phase.isActive && state.canLock) state.copy(locked = true) else state

    /**
     * Pause ([exitAfter] false; the mic tap, which also releases a lock) or
     * Done ([exitAfter] true) during a session: the engine flushes what it
     * heard, then the session ends. No-op when idle; the service handles
     * Done-while-idle by switching back at once.
     */
    fun stopping(state: VoiceImeState, exitAfter: Boolean): VoiceImeState =
        if (!state.phase.isActive) {
            state
        } else {
            state.copy(
                phase = VoiceImePhase.FINISHING,
                exitWhenDone = state.exitWhenDone || exitAfter,
                locked = false,
            )
        }

    /**
     * The session's flow ended, however it ended. Returns the idle state and
     * whether to switch back to the previous keyboard (Done was tapped and no
     * error came in between).
     */
    fun ended(state: VoiceImeState): Pair<VoiceImeState, Boolean> =
        state.copy(phase = VoiceImePhase.IDLE, partial = "", level = 0f, exitWhenDone = false, locked = false) to
            state.exitWhenDone

    /**
     * Whether the Final [event], already committed, acts like tapping Done
     * (#43): in an auto mode, unlocked, while listening (not the Final a tap
     * flushes). [committed]: it put text in the editor; one the cleanup
     * dropped (a hallucination) doesn't end listening, but an end word does,
     * even an empty one: the user said they are done.
     */
    fun actsAsDone(state: VoiceImeState, event: SttEvent, committed: Boolean): Boolean =
        event is SttEvent.Final &&
            (committed || event.endWord) &&
            state.autoStop &&
            !state.locked &&
            state.phase.isActive &&
            state.phase != VoiceImePhase.FINISHING

    /**
     * Whether the 30 s no-speech pause ([IdleTimeout]) applies now: only in an
     * auto mode, unlocked. In Tap to stop the user asked for the mic to stay on.
     */
    fun idlePauses(state: VoiceImeState): Boolean = state.autoStop && !state.locked

    /** Cancel, or the keyboard was dismissed: the session is dropped without a Final. */
    fun cancelled(state: VoiceImeState): VoiceImeState =
        state.copy(phase = VoiceImePhase.IDLE, partial = "", level = 0f, exitWhenDone = false, locked = false)

    /** Apply one event of the running session. */
    fun reduce(state: VoiceImeState, event: SttEvent): VoiceImeState = when (event) {
        // The mic is open (#78; the model may still be loading, the audio is kept), or
        // the session runs: either one ends "Loading model…", whichever comes first.
        SttEvent.Listening, SttEvent.Ready ->
            if (state.phase == VoiceImePhase.LOADING) state.copy(phase = VoiceImePhase.LISTENING) else state

        is SttEvent.Level -> state.copy(level = Dictation.levelFraction(event.rmsDb))

        SttEvent.SpeechStarted ->
            if (state.phase == VoiceImePhase.FINISHING) state else state.copy(phase = VoiceImePhase.HEARING)

        is SttEvent.Partial -> state.copy(partial = event.text)

        // The text itself goes to the editor (ImeTextWriter); the panel only drops the partial.
        is SttEvent.Final -> state.copy(
            partial = "",
            phase = if (state.phase == VoiceImePhase.HEARING) VoiceImePhase.LISTENING else state.phase,
        )

        SttEvent.NoSpeech -> state.copy(partial = "", notice = DictationNotice.NO_SPEECH)

        // The panel has no room for these (#30); the Dictate screen shows them.
        is SttEvent.CatchingUp, SttEvent.TooSlow -> state

        // Stay on the panel so the user can read why; Cancel or Done still switch back.
        is SttEvent.Error -> state.copy(
            phase = VoiceImePhase.IDLE,
            partial = "",
            level = 0f,
            exitWhenDone = false,
            locked = false,
            notice = when (event.kind) {
                SttErrorKind.MIC -> DictationNotice.MIC_ERROR
                SttErrorKind.MODEL -> DictationNotice.MODEL_ERROR
                SttErrorKind.RECOGNITION -> DictationNotice.RECOGNITION_ERROR
                SttErrorKind.BUSY -> DictationNotice.BUSY
            },
        )
    }
}
