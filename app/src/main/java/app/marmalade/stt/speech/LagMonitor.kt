package app.marmalade.stt.speech

import android.util.Log
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How far a session is behind the mic (#30). The mic queue is unbounded, so
 * an engine slower than real time falls further and further behind the
 * speaker. Measured in 100 ms mic hops, with no clock: the mic delivers hops
 * in real time, so hops captured is time, and the consumer reports how many
 * of them it has dealt with (VAD'd and, for a streaming engine, decoded).
 * A non-live capture (a caller's stream, #90) isn't time: [micSession]
 * reads it in step with the consumer, so it never registers as lag.
 *
 * - Catching up: behind by more than [BEHIND_SECONDS], until back under
 *   [CAUGHT_UP_SECONDS]. The sessions then skip or batch partials so the
 *   Final isn't held up. [SttEvent.CatchingUp] tells the UI only once it has
 *   lasted [REPORT_AFTER_SECONDS]: after any decode longer than 1.5 s (a long
 *   Final) the session is briefly that far behind and drains the backlog in
 *   milliseconds, which isn't worth a "Catching up…" flash.
 * - Too slow: still catching up [TOO_SLOW_AFTER_SECONDS] after it started,
 *   and at least [TOO_SLOW_GROWTH_SECONDS] further behind than then (so at
 *   least 10 % slower than real time). Even without partials the engine
 *   can't keep up on this device. Reported once per session.
 *
 * Numbers only are logged (under [StreamingSession.TIMING_TAG]), never audio
 * or text. Not thread-safe: the consumer owns it.
 */
internal class LagMonitor {
    companion object {
        const val BEHIND_SECONDS = 1.5f
        const val CAUGHT_UP_SECONDS = 0.5f
        const val TOO_SLOW_AFTER_SECONDS = 10f
        const val TOO_SLOW_GROWTH_SECONDS = 1f
        const val REPORT_AFTER_SECONDS = 1f
        private const val HOP_SECONDS = StreamingSession.MIC_HOP_SAMPLES.toFloat() / MIC_SAMPLE_RATE

        // The thresholds in whole hops, so the harness's port compares exactly the same.
        private fun hops(seconds: Float) = (seconds / HOP_SECONDS).roundToInt()
        private val BEHIND_HOPS = hops(BEHIND_SECONDS)
        private val CAUGHT_UP_HOPS = hops(CAUGHT_UP_SECONDS)
        private val TOO_SLOW_AFTER_HOPS = hops(TOO_SLOW_AFTER_SECONDS)
        private val TOO_SLOW_GROWTH_HOPS = hops(TOO_SLOW_GROWTH_SECONDS)
        private val REPORT_AFTER_HOPS = hops(REPORT_AFTER_SECONDS)
    }

    /** True while behind; see the class comment. */
    var catchingUp = false
        private set

    /** The most the session has been behind, in seconds. */
    var maxLagSeconds = 0f
        private set

    private var behindSince = 0 // hops captured when catching up started
    private var lagAtStart = 0 // hops behind then
    private var tooSlowReported = false
    private var behindReported = false // CatchingUp(true) was sent

    /**
     * [captured] hops have come from the mic, and the consumer has dealt with
     * the first [processed]. Returns what to tell the collector: a
     * [SttEvent.CatchingUp] when the reported state changes, [SttEvent.TooSlow] once.
     */
    fun update(captured: Int, processed: Int): List<SttEvent> {
        val lag = (captured - processed).coerceAtLeast(0)
        val lagSeconds = lag * HOP_SECONDS
        if (lagSeconds > maxLagSeconds) maxLagSeconds = lagSeconds
        val events = mutableListOf<SttEvent>()
        if (!catchingUp && lag > BEHIND_HOPS) {
            catchingUp = true
            behindSince = captured
            lagAtStart = lag
            Log.d(StreamingSession.TIMING_TAG, format("behind %.1f s: catching up", lagSeconds))
        } else if (catchingUp && lag < CAUGHT_UP_HOPS) {
            catchingUp = false
            Log.d(StreamingSession.TIMING_TAG, format("caught up after %.1f s", (captured - behindSince) * HOP_SECONDS))
            if (behindReported) events += SttEvent.CatchingUp(false)
            behindReported = false
        }
        if (catchingUp && !behindReported && captured - behindSince >= REPORT_AFTER_HOPS) {
            behindReported = true
            events += SttEvent.CatchingUp(true)
        }
        if (catchingUp && !tooSlowReported &&
            captured - behindSince >= TOO_SLOW_AFTER_HOPS && lag - lagAtStart >= TOO_SLOW_GROWTH_HOPS
        ) {
            tooSlowReported = true
            Log.d(StreamingSession.TIMING_TAG, format("too slow: behind %.1f s and growing", lagSeconds))
            events += SttEvent.TooSlow
        }
        return events
    }

    /** Log the session's worst lag. */
    fun logSummary() {
        Log.d(StreamingSession.TIMING_TAG, format("max lag %.1f s", maxLagSeconds))
    }

    private fun format(pattern: String, value: Float) = String.format(Locale.ROOT, pattern, value)
}
