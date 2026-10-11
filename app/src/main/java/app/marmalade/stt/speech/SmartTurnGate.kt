package app.marmalade.stt.speech

import android.util.Log
import app.marmalade.stt.speech.turn.TurnDetector

/**
 * How patient Smart Turn is (#43, Settings → End of speech → Patience): the
 * numbers [SmartTurnGate] works with.
 *
 * @property proposeSeconds the VAD's minimum silence: the pause that proposes
 *   an end ([SessionOptions.vadMinSilenceSeconds]).
 * @property threshold completeness probability above which the turn ends.
 * @property ceilingSeconds silence after which the turn ends whatever the
 *   model says.
 */
enum class Patience(val proposeSeconds: Float, val threshold: Float, val ceilingSeconds: Float) {
    // From the #43 patience sweep on AMI (docs/benchmarks/2026-09-smart-turn-ami.md, "Patience") and
    // the #64 check across languages (docs/benchmarks/2026-10-smart-turn-defaults.md): all three
    // share the threshold; only the pause that proposes an end and the ceiling differ.

    /**
     * Asks the model after 0.5 s: a finished sentence ends after about 0.6 s, but the model calls
     * many 0.5-1 s mid-sentence pauses finished (#64: 11-41 % of single sentences cut off, by
     * language; 36 % of long AMI turns).
     */
    QUICK(proposeSeconds = 0.5f, threshold = 0.7f, ceilingSeconds = 1.5f),

    /**
     * Only pauses Pause 1.2 s would end, and only if the sentence sounds finished: about as few
     * cut-offs as Pause 1.2 s in every language measured (0-4 %; Japanese 13 %, as Pause 1.2 s),
     * fewer on AMI (4 % of long turns vs 12 %), and a finished sentence ends after about 1.3 s.
     * Relaxed until #64, which found the old Normal (0.5 s / 0.7 / 2 s) cut off one single
     * sentence in five to three.
     */
    NORMAL(proposeSeconds = 1.2f, threshold = 0.7f, ceilingSeconds = 2.5f),

    /** For long thinking pauses: the model is first asked after 1.5 s of silence, and 3 s ends the turn. */
    RELAXED(proposeSeconds = 1.5f, threshold = 0.7f, ceilingSeconds = 3.0f),
    ;

    /** [ceilingSeconds] in mic samples. */
    val ceilingSamples: Int get() = (ceilingSeconds * MIC_SAMPLE_RATE).toInt()
}

/**
 * The hybrid end-of-turn gate of [EndOfDictation.SMART_TURN] (#43), ported
 * from marmalade-stt-cli's `streaming.py`: a short pause only *proposes* the
 * end of a turn, and the Smart Turn model decides whether the speaker
 * finished. Its numbers come from the [patience].
 *
 * - The VAD closes a segment after [Patience.proposeSeconds] of silence: the
 *   proposal ([propose]). The detector scores the last 8 s of the turn up to
 *   now, **trailing silence included** (pipecat trains on windows that end in
 *   silence; cut it off and the model calls almost everything incomplete,
 *   and every re-check would score the same audio).
 * - Probability above [Patience.threshold]: the turn ends now. Otherwise it
 *   is checked again after every further [RECHECK_SAMPLES] of silence
 *   ([poll]), and at [Patience.ceilingSeconds] it ends regardless.
 * - Speech before that ([resume]): the turn goes on.
 *
 * The session feeds it every VAD window of the turn ([append]), so "now" is
 * where the VAD is. One gate per session; not thread-safe. The detector runs
 * synchronously on the session's consumer thread (tens of ms per check).
 * A detector that throws is logged, and for the rest of the session a turn
 * ends after [fallbackSilenceSamples] of silence (the Pause option's
 * slider), as Pause would end it, instead of at every short pause.
 *
 * The numbers come from the #43 AMI benchmark
 * (`docs/benchmarks/2026-09-smart-turn-ami.md`), not the CLI's agent-turn
 * defaults (propose 0.2 s, threshold 0.5, ceiling 3 s): on dictation those
 * cut 54 % of long sentences off mid-way, and a 0.2 s pause was scored
 * "finished" as often as a long one. #64
 * (`docs/benchmarks/2026-10-smart-turn-defaults.md`) checked them on nine
 * more languages and on quieter, phone-like pauses: the model calls a
 * mid-sentence pause "finished" in every language, and more often when the
 * pause is digital silence, so Normal only asks it once the pause reaches
 * 1.2 s.
 */
internal class SmartTurnGate(
    private val patience: Patience = Patience.NORMAL,
    private val fallbackSilenceSamples: Int = patience.ceilingSamples,
    private val detector: TurnDetector,
) {

    companion object {
        /** Further silence between checks (0.5 s). */
        const val RECHECK_SAMPLES = MIC_SAMPLE_RATE / 2

        /** The detector's window: the last 8 s of the turn. */
        const val WINDOW_SAMPLES = 8 * MIC_SAMPLE_RATE

        private const val TAG = "SmartTurnGate"
    }

    private val ceiling = patience.ceilingSamples

    private val tail = SampleBuffer() // the turn's last WINDOW_SAMPLES, up to where the VAD is
    private var silence = 0 // samples of silence since the proposed end (while waiting)
    private var nextCheck = 0 // silence at which to check again
    private var failed = false

    /** A segment closed and the turn waits for the verdict. */
    var waiting: Boolean = false
        private set

    /** A new turn: forget the last one and start the window with [audio] (the pre-roll up to the onset). */
    fun start(audio: FloatArray) {
        tail.dropFirst(tail.size)
        waiting = false
        append(audio)
    }

    /** Audio the VAD has just seen; while [waiting], it counts as silence. */
    fun append(audio: FloatArray) {
        tail.append(audio)
        if (tail.size > WINDOW_SAMPLES) tail.dropFirst(tail.size - WINDOW_SAMPLES)
        if (waiting) silence += audio.size
    }

    /**
     * The VAD closed a segment [trailingSilence] samples ago (where the VAD
     * is now). Returns true when the turn has ended.
     */
    fun propose(trailingSilence: Int): Boolean {
        waiting = true
        silence = trailingSilence
        return check()
    }

    /** Called once per mic hop while [waiting]. Returns true when the turn has ended. */
    fun poll(): Boolean = waiting && (silence >= ceiling || (silence >= nextCheck && check()))

    /** The VAD heard speech again before the verdict: the turn goes on. */
    fun resume() {
        waiting = false
    }

    private fun check(): Boolean {
        if (silence >= ceiling) return true
        if (failed) return fallback()
        val started = System.nanoTime()
        val p = try {
            detector.completeProbability(tail.copyRange(0, tail.size))
        } catch (e: Exception) {
            Log.w(TAG, "Smart Turn failed; the rest of this session ends turns at the Pause setting's silence", e)
            failed = true
            return fallback()
        }
        Log.d(
            StreamingSession.TIMING_TAG,
            "smart turn ${(System.nanoTime() - started) / 1_000_000} ms: p=$p after ${silence * 1000L / MIC_SAMPLE_RATE} ms of silence",
        )
        if (p > patience.threshold) return true
        nextCheck = silence + RECHECK_SAMPLES
        return false
    }

    /** Without a working detector: end once the silence reaches the fallback, checking every hop until then. */
    private fun fallback(): Boolean {
        nextCheck = 0
        return silence >= fallbackSilenceSamples
    }
}
