package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.turn.TurnDetector
import kotlinx.coroutines.flow.Flow

/**
 * When a listening session finalises text (#43, Settings → End of speech).
 * The session never stops on its own in any mode: whether a Final ends
 * listening is the surface's decision ([ListenSurface], ARCHITECTURE.md
 * "End of speech").
 */
enum class EndOfDictation {
    /** Each pause the VAD detects (the silence slider) ends an utterance: one [SttEvent.Final] per pause. */
    PAUSE,

    /**
     * A short pause proposes the end and the Smart Turn model decides whether
     * the speaker finished ([SmartTurnGate]): one [SttEvent.Final] per turn.
     * Without a detector ([SessionOptions.turnDetector] null) this is [PAUSE].
     */
    SMART_TURN,

    /**
     * Utterances are kept until the user says an end word on its own
     * ([EndWords.match]); then one [SttEvent.Final] with [SttEvent.Final.endWord]
     * set carries everything before the word. What is kept at stop comes as a
     * last Final.
     */
    END_WORD,

    /** Utterances are kept and joined into one [SttEvent.Final] after [SttEngine.stop]. */
    TAP_STOP,
}

/**
 * Options for one listening session. Every surface that listens (the Dictate
 * screen, the voice IME, the one-shot recognizers) builds one from the user's
 * settings for that surface (`SpeechPrefs.sessionOptions`).
 *
 * @property end when text is finalised; see [EndOfDictation].
 * @property minSilenceSeconds how long a pause must last to end an utterance
 *   (the silence slider, 0.3–1.5 s). Smart Turn uses its [patience]'s
 *   shorter proposal pause instead.
 * @property endWords the words or phrases that end dictation in
 *   [EndOfDictation.END_WORD] mode, lower case.
 * @property turnDetector the loaded Smart Turn model; [EndOfDictation.SMART_TURN]
 *   only. Null (it failed to load) makes Smart Turn behave
 *   exactly as [EndOfDictation.PAUSE].
 * @property patience Smart Turn's numbers ([SmartTurnGate]).
 * @property loadTurnDetector loads [turnDetector] later ([withTurnDetector]):
 *   the engine runs it once the mic is open (#78), so Smart Turn's first load
 *   (8.7 MB, `SmartTurnModel`) never keeps the mic waiting.
 * @property audioInput opens the session's audio instead of the engine's own
 *   mic (#90), called with the hop size: the RecognitionService's mic opened
 *   for the calling app, or a caller's stream (`EXTRA_AUDIO_SOURCE`). Null:
 *   the engine's mic.
 */
data class SessionOptions(
    val end: EndOfDictation = EndOfDictation.PAUSE,
    val minSilenceSeconds: Float = DEFAULT_MIN_SILENCE_SECONDS,
    val endWords: List<String> = EndWords.DEFAULT,
    val turnDetector: TurnDetector? = null,
    val patience: Patience = Patience.NORMAL,
    val loadTurnDetector: (() -> TurnDetector?)? = null,
    val audioInput: ((hopSamples: Int) -> MicCapture)? = null,
) {
    /** These options with [loadTurnDetector]'s detector as [turnDetector]. Blocks while it loads. */
    fun withTurnDetector(): SessionOptions =
        loadTurnDetector?.let { copy(turnDetector = it(), loadTurnDetector = null) } ?: this

    /** The mode the session runs: [end], except Smart Turn without a detector runs as Pause. */
    val effectiveEnd: EndOfDictation
        get() = if (end == EndOfDictation.SMART_TURN && turnDetector == null) EndOfDictation.PAUSE else end

    /**
     * The slider's pause. Settings keep it in the slider's range; a caller's
     * `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` may ask for up to
     * [MAX_CALLER_SILENCE_SECONDS] (#90).
     */
    private val sliderSilenceSeconds: Float
        get() = minSilenceSeconds.coerceIn(MIN_SILENCE_RANGE_START, MAX_CALLER_SILENCE_SECONDS)

    /** The VAD's minimum silence: Smart Turn's proposal pause ([Patience.proposeSeconds]), else the slider. */
    val vadMinSilenceSeconds: Float
        get() = if (effectiveEnd == EndOfDictation.SMART_TURN) patience.proposeSeconds else sliderSilenceSeconds

    /** The silence that ends a Smart Turn once its detector has failed mid-session: the slider's, as Pause. */
    internal val fallbackSilenceSamples: Int
        get() = (sliderSilenceSeconds * MIC_SAMPLE_RATE).toInt()

    companion object {
        /**
         * The slider's default (#43): 1.2 s cut 12 % of long AMI sentences
         * off mid-way, 0.9 s 28 % (`docs/benchmarks/2026-09-smart-turn-ami.md`).
         * Only a moved slider is stored, so everyone else gets this.
         */
        const val DEFAULT_MIN_SILENCE_SECONDS = 1.2f
        const val MIN_SILENCE_RANGE_START = 0.3f
        const val MIN_SILENCE_RANGE_END = 1.5f

        /** The longest end-of-speech pause another app may ask for (#90). */
        const val MAX_CALLER_SILENCE_SECONDS = 5f
    }
}

/** Why a session failed. Maps onto `SpeechRecognizer.ERROR_*` for the RecognitionService. */
enum class SttErrorKind {
    /** The microphone couldn't be opened (permission revoked, held by another app). */
    MIC,

    /** The model files are missing or failed to load. */
    MODEL,

    /** Decoding failed mid-session. */
    RECOGNITION,

    /** Another session already holds the engine. */
    BUSY,
}

/** What a listening session reports, in order. */
sealed interface SttEvent {
    /**
     * The mic is open and its audio is kept (in memory) for the session: the
     * user can speak now. The first event, sent before the model has
     * necessarily loaded (#78); [Level]s may come before [Ready].
     */
    data object Listening : SttEvent

    /**
     * The session runs: the model is loaded and works through the audio,
     * what was kept since [Listening] first. Timeouts that wait for speech
     * count from here, so a slow load never counts as silence.
     */
    data object Ready : SttEvent

    /** Input level of the last mic hop (100 ms) in dBFS, for a level meter or `onRmsChanged`. */
    data class Level(val rmsDb: Float) : SttEvent

    /** The VAD heard the start of speech. */
    data object SpeechStarted : SttEvent

    /**
     * The utterance [SpeechStarted] began is over (#124): the VAD's pause
     * closed it, Smart Turn judged its turn complete, or a stop flushed it.
     * Sent as soon as that is decided, before the decode, so its [Final] (if
     * it leaves text) follows. In Smart Turn mode once per turn: a pause the
     * turn goes on after doesn't end it. Neither does a split while the user
     * is still speaking (an utterance at the decode cap, a turn at 28 s): its
     * Final comes before the end.
     */
    data object SpeechEnded : SttEvent

    /**
     * Best guess at the utterance so far. Replaces the previous partial. In
     * [EndOfDictation.TAP_STOP] and [EndOfDictation.END_WORD] modes it
     * includes the utterances kept so far in this session.
     */
    data class Partial(val text: String) : SttEvent

    /**
     * A finished utterance, raw from the model (before `:textproc`). Its text
     * is never blank, except for an end-word Final.
     *
     * @property nearSilent the audio behind it was near-silent (see
     *   [NearSilence]), so a stock phrase like "Thanks for watching!" is likely
     *   a hallucination. Feed it to `TextPipeline.process`.
     * @property silent quieter still: digital silence or the mic floor
     *   ([NearSilence.SILENT_THRESHOLD_DB]). Only then may an ordinary phrase
     *   like "Thank you." go (#57). Feed it to `TextPipeline.process` too.
     * @property endWord the user said an end word ([EndOfDictation.END_WORD]):
     *   [text] is what came before it, with the word removed, and **may be
     *   empty** when the word was all that was kept. The user said they are
     *   done: it ends listening where any Final does (the voice keyboard's
     *   auto modes, one-shot requests), even when empty.
     */
    data class Final(
        val text: String,
        val nearSilent: Boolean,
        val endWord: Boolean = false,
        val silent: Boolean = false,
    ) : SttEvent {
        constructor(text: String, quiet: Quiet, endWord: Boolean = false) :
            this(text, quiet.nearSilent, endWord, quiet.silent)
    }

    /**
     * The engine has fallen behind the mic ([behind] true: by more than
     * [LagMonitor.BEHIND_SECONDS]) or caught up again (false). While behind,
     * partials are skipped or batched so the text can catch up (#30).
     */
    data class CatchingUp(val behind: Boolean) : SttEvent

    /**
     * The engine can't keep up with the mic on this device: it has kept
     * falling behind for [LagMonitor.TOO_SLOW_AFTER_SECONDS]. At most once per
     * session; the session goes on (#30).
     */
    data object TooSlow : SttEvent

    /** The session ended having transcribed nothing. Not an error. */
    data object NoSpeech : SttEvent

    data class Error(val kind: SttErrorKind, val message: String) : SttEvent
}

/**
 * One installed speech engine (ARCHITECTURE.md D4). Implementations own their
 * native resources. At most one session runs at a time per engine.
 */
interface SttEngine {
    val descriptor: SttEngineDescriptor

    /** True once [load] has finished and until [release]. */
    val isLoaded: Boolean

    /**
     * Load the model into memory. Idempotent and safe to call from several
     * coroutines; runs on a background dispatcher. Throws if the files are
     * missing or can't be loaded.
     */
    suspend fun load()

    /**
     * [load], then one short decode of silence when this call loaded the
     * model, so the first real decode doesn't pay ONNX Runtime's first-run
     * cost (#31). For loading ahead of a session ("Keep engine ready"). The
     * model is usable as soon as it is loaded: a session waiting for the load
     * skips the warm-up, and one that starts during the decode waits only for
     * that decode.
     */
    suspend fun warmUp()

    /**
     * Listen to the microphone. The returned flow is cold: collecting it
     * opens the mic ([SttEvent.Listening]), loads the model if needed
     * ([SttEvent.Ready]) and emits [SttEvent]s. Audio captured while the
     * model loads is kept and transcribed first, so no words are lost (#78).
     *
     * It ends after [stop] (once the last utterance is flushed as a
     * [SttEvent.Final], or [SttEvent.NoSpeech] if nothing was said), or after
     * an [SttEvent.Error]. Cancelling the collector aborts at once, without a
     * final result. The mic is always closed when the flow ends. Audio is
     * held in memory only (the current utterance, plus what was captured
     * while the model loaded until the session has worked through it) and
     * never written.
     */
    fun listen(options: SessionOptions): Flow<SttEvent>

    /**
     * Ask the running session to finish: the mic closes, everything heard so
     * far is still transcribed (also while the model is still loading), then
     * the flow ends. No-op when idle.
     */
    fun stop()

    /** Free the model's memory. A later [load] or [listen] reloads it. */
    fun release()

    /**
     * The model is wanted again: undo a [release] that a running session
     * deferred, so it stays loaded when that session ends (a surface held
     * the engine again, or the user's own session takes over from another
     * app's, #90). Does nothing after [drop].
     */
    fun keep() {}

    /**
     * The owner is done with this engine for good (another engine was picked,
     * or its files are about to change): [release], and never keep a model
     * again. A later [load] or [warmUp] loads nothing. A [listen] collected
     * after this (its caller got the engine just before) still runs, and
     * frees the model when it ends.
     */
    fun drop()
}
