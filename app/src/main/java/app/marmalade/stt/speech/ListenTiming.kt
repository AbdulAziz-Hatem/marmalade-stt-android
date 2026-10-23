package app.marmalade.stt.speech

import android.util.Log

/**
 * How long one listen took from the user's request to each stage (#78):
 * the mic open ([SttEvent.Listening]), the model loaded and the session
 * running ([SttEvent.Ready]), the first level, speech heard, the first
 * partial and the first Final. Numbers only, never audio or
 * text. Logged at info level under [StreamingSession.TIMING_TAG], so release
 * builds keep it (R8 strips debug lines): one line when the listen ends.
 *
 * @param requestedAt when the user asked to listen (the mic tap, the panel or
 *   sheet opening), in [nowMs]'s clock.
 */
internal class ListenTiming(
    private val label: String,
    private val requestedAt: Long,
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private val stages = LinkedHashMap<String, Long>()

    /** Records [event]'s stage the first time it happens. */
    fun on(event: SttEvent) {
        val stage = when (event) {
            SttEvent.Listening -> "mic"
            SttEvent.Ready -> "ready"
            is SttEvent.Level -> "level"
            SttEvent.SpeechStarted -> "speech"
            is SttEvent.Partial -> if (event.text.isNotBlank()) "partial" else return
            is SttEvent.Final -> "final"
            else -> return
        }
        mark(stage)
    }

    /** Records [stage] the first time it is reached. */
    fun mark(stage: String) {
        stages.putIfAbsent(stage, nowMs() - requestedAt)
    }

    /** The stages reached, in order, as "stage +ms" pairs. */
    fun summary(): String = stages.entries.joinToString(" ") { (stage, ms) -> "$stage=$ms" }

    fun log() {
        Log.i(StreamingSession.TIMING_TAG, "listen $label at=$requestedAt: ${summary()}")
    }

    companion object {
        /**
         * A surface has applied a Final (#91): its cleanup and commit, which
         * began at [startedAt], are done. Debug level, stamped in
         * [android.os.SystemClock.elapsedRealtime] like the session's
         * `final sent` line.
         */
        fun delivered(surface: String, startedAt: Long) {
            val now = android.os.SystemClock.elapsedRealtime()
            Log.d(StreamingSession.TIMING_TAG, "$surface delivered t=$now took=${now - startedAt}ms")
        }
    }
}
