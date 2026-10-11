package app.marmalade.stt.speech

import app.marmalade.stt.install.EngineFamily

/**
 * The hard cap on one offline decode (#48), shared by the live
 * [StreamingSession] and the benchmarks' splitter ([vadUtterances]) so both
 * cut a long utterance the same way.
 *
 * Silero's `max_speech_duration` ([MAX_UTTERANCE_SECONDS]) is not a hard
 * cut: past it, Silero only waits for a 0.1 s pause at a higher threshold,
 * so continuous speech made a 30.6 s segment (#47). Whisper reads at most
 * 29.5 s and silently drops the rest, and `SpeedBudget.REFERENCE`'s memory
 * is measured at the cap. So an utterance still going near [MAX_DECODE_SAMPLES]
 * is cut at a quiet point ([cutPoint]); that part is decoded as a finished
 * utterance and the rest goes on as the next one. When the VAD later closes
 * the segment, only its rest after the cut is decoded ([restHasSpeech]).
 *
 * Whisper in Hindi gets a shorter cap ([maxDecodeSamples], #54): its token
 * budget would reach what Whisper's decoder can hold.
 */
internal object UtteranceCap {
    /** The most audio one decode gets (28.6 s): [MAX_UTTERANCE_SECONDS] plus the pre-roll. */
    val MAX_DECODE_SAMPLES = (MAX_UTTERANCE_SECONDS * MIC_SAMPLE_RATE).toInt() + StreamingSession.PREROLL_SAMPLES

    /**
     * Whisper's cap on one decode, pre-roll included, in the languages where
     * its token budget ([WHISPER_TOKEN_BUDGETS]) would pass
     * [WHISPER_MAX_TOKENS] before [MAX_DECODE_SAMPLES] (#54): Hindi's
     * 20 a second + 10 reaches it at 21.6 s. A cut costs a little (Whisper
     * can repeat a sentence that ends mid-phrase), so no lower: a 12 s cap
     * with Whisper's own 224 tokens was 1.9 points worse on FLEURS Hindi,
     * this one 0.2 better, and 7.8 better on 11–35 s Hindi
     * (`docs/benchmarks/2026-10-whisper-token-budget.md`).
     */
    private val WHISPER_DECODE_SECONDS = mapOf("hi" to 21f)

    /**
     * The most audio one decode of a [family] engine gets in [language] (an
     * app code, "" for auto-detect): [MAX_DECODE_SAMPLES], or Whisper's
     * shorter cap where its token budget runs out first ([WHISPER_DECODE_SECONDS]).
     */
    fun maxDecodeSamples(family: EngineFamily, language: String): Int {
        val seconds = WHISPER_DECODE_SECONDS[language]?.takeIf { family == EngineFamily.WHISPER }
            ?: return MAX_DECODE_SAMPLES
        return (seconds * MIC_SAMPLE_RATE).toInt()
    }

    /**
     * The cut goes at the quietest VAD window in the last 1.5 s of the
     * utterance, most likely a gap between words.
     */
    const val CUT_SEARCH_SAMPLES = 24_000

    /**
     * The cut is due this far before the cap: one mic hop's VAD
     * windows, so no segment the VAD closes on the next hop is over the cap.
     */
    private const val HEADROOM_SAMPLES = StreamingSession.MIC_HOP_SAMPLES + StreamingSession.VAD_WINDOW_SIZE

    /**
     * Whether an utterance of [length] samples so far (pre-roll included) must
     * be cut now, under a cap of [maxDecode] samples ([maxDecodeSamples]).
     */
    fun isDue(length: Int, maxDecode: Int = MAX_DECODE_SAMPLES): Boolean = length > maxDecode - HEADROOM_SAMPLES

    /** Where to cut the utterance that ends at [end] in [buffer]. */
    fun cutPoint(buffer: SampleBuffer, end: Int): Int = quietestWindow(buffer, end - CUT_SEARCH_SAMPLES, end)

    /**
     * Whether the [samples] of a segment left after a cut hold speech, so are
     * decoded: the VAD saw at least one window of it, and it isn't
     * near-silent ([nearSilent], by [NearSilence]). Never by length beyond
     * that: a rest can be one short word ("…and that's it"). Less than a
     * window, or near-silence, is only the segment's trailing edge, and
     * Whisper makes up text for it.
     */
    fun restHasSpeech(samples: Int, nearSilent: Boolean): Boolean =
        samples >= StreamingSession.VAD_WINDOW_SIZE && !nearSilent
}
