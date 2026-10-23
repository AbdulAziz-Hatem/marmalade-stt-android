package app.marmalade.stt.speech

/**
 * Speed switches from the #91 audit (`docs/benchmarks/2026-10-speed-audit.md`),
 * read when an engine starts a session. The defaults are what users get;
 * debug builds can override any of them for an A/B (`debug/ClipMic`'s
 * `DebugKnobs` reads `files/debug-mic/knobs`).
 *
 * - [speculativeFinal]: an early VAD ([EarlyVad]) closes the utterance after
 *   ~0.6 s of silence; the session then decodes exactly the audio the Final
 *   will have, and uses that text when the main VAD closes the same range.
 *   No partials are decoded in that silence.
 * - [trimAtStop]: a stop during the early VAD's silence sends the
 *   speculative text (the speech without the silence up to the stop) instead
 *   of decoding the flushed segment. Tap stop sessions run the early VAD for
 *   it even with [speculativeFinal] off.
 * - [partialAtOnset]: the first partial decodes at the VAD's onset instead
 *   of [StreamingSession.PARTIAL_INTERVAL_MS] later.
 * - [verifySpeculation] (measurement only): also decode a speculated Final
 *   the usual way and log whether the texts match.
 * - [threads] / [tuningId] / [smartTurnThreads]: overrides for the
 *   recognizer's thread count, its ONNX Runtime tuning ([OrtTuning.parse])
 *   and Smart Turn's threads; null keeps the device's and the user's.
 */
object SpeedKnobs {
    @Volatile var speculativeFinal = true

    @Volatile var trimAtStop = true

    @Volatile var partialAtOnset = true

    @Volatile var verifySpeculation = false

    @Volatile var threads: Int? = null

    @Volatile var tuningId: String? = null

    /** Smart Turn's ONNX Runtime threads; read when the detector first loads (once per process). */
    @Volatile var smartTurnThreads: Int? = null

    /** Back to the defaults above. */
    fun reset() {
        speculativeFinal = true
        trimAtStop = true
        partialAtOnset = true
        verifySpeculation = false
        threads = null
        tuningId = null
        smartTurnThreads = null
    }

    /** Whether a session ending [end]'s way runs the early VAD. */
    fun earlyVadFor(end: EndOfDictation): Boolean = speculativeFinal || (trimAtStop && end == EndOfDictation.TAP_STOP)

    override fun toString() =
        "speculativeFinal=$speculativeFinal trimAtStop=$trimAtStop partialAtOnset=$partialAtOnset verify=$verifySpeculation threads=$threads tuning=$tuningId smartTurnThreads=$smartTurnThreads"
}

/**
 * The early VAD's minimum silence for a main VAD of [mainSeconds] (#91): about
 * [TARGET_SAMPLES], and a whole number of VAD windows shorter than the main
 * one's, so both end a segment at the same sample. 0.6 s rather than 0.4:
 * pauses inside a sentence close the early VAD too, and each wastes a
 * decode; on the host's speech sets 1-5 times a minute at 0.6 s, 4-10 at
 * 0.4 (`tools/speed-audit/early_vad_sim.py`). Silero ends a segment
 * where its silence began (its `temp_end`), counted in whole windows; with
 * silences that differ by whole windows, the early VAD's end is the main
 * one's, unless speech too short to start a segment came in between. Null
 * when the main silence is too short to leave room for an early one.
 *
 * sherpa-onnx truncates `16000 * seconds` (a float) to whole samples, so the
 * main VAD's length is computed the same way, and the early one is given as
 * the middle of its sample.
 */
object EarlyVad {
    const val TARGET_SAMPLES = 9_600 // 0.6 s

    fun minSilenceSeconds(mainSeconds: Float): Float? {
        val main = (MIC_SAMPLE_RATE * mainSeconds).toInt()
        val window = StreamingSession.VAD_WINDOW_SIZE
        val k = (main - TARGET_SAMPLES) / window
        if (k < 1) return null
        return (main - k * window + 0.5f) / MIC_SAMPLE_RATE
    }
}
