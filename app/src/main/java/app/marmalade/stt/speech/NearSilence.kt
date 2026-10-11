package app.marmalade.stt.speech

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Loudness maths for the session: the level meter ([SttEvent.Level]) and the
 * near-silence judgements behind [SttEvent.Final.nearSilent] and
 * [SttEvent.Final.silent], which `:textproc`'s hallucination filter uses to
 * drop stock phrases Whisper "hears" in quiet audio.
 */
object NearSilence {

    /**
     * An utterance whose RMS is below this many dBFS counts as near-silent.
     *
     * Calibrated on the Pixel 8a (#11) from the `SttNearSilence` log
     * ([LOG_TAG]), on raw mic levels (platform noise suppression on, no gain
     * control). Speech measured -34 to -49 dBFS: TTS through the phone's own
     * speaker -34 to -49, and sessions during Max's own tests -35 to -39.
     * The one phantom from room noise ("Nice.") measured -53. The old -45 marked half of the
     * speaker-played sentences near-silent.
     *
     * The level must be judged before any gain: [SoftAgc] lifts quiet noise
     * by up to 30 dB, after which almost nothing reads as near-silent.
     */
    const val THRESHOLD_DB = -50f

    /**
     * An utterance below this many dBFS counts as silent: digital silence or the
     * mic floor. Only then does `:textproc` drop the ordinary phantoms a user
     * may really say quietly ("Thank you.", "Gracias.", 「我」, #57). In #57's noise
     * runs every such phantom from quiet audio came at -60 dBFS or below
     * (digital silence, the -70 dBFS mic floor, -60 dBFS white noise); the
     * quietest speech measured on the Pixel 8a is -49, 9 dB above this.
     */
    const val SILENT_THRESHOLD_DB = -58f

    /** Level reported for digital silence (and empty input) instead of -infinity. */
    const val FLOOR_DB = -100f

    /** Logcat tag for per-utterance RMS lines (numbers only, never audio). */
    const val LOG_TAG = "SttNearSilence"

    /** RMS of [samples] (float PCM, full scale = 1.0) in dBFS, never below [FLOOR_DB]. */
    fun rmsDb(samples: FloatArray): Float {
        if (samples.isEmpty()) return FLOOR_DB
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        val rms = sqrt(sum / samples.size)
        if (rms <= 0.0) return FLOOR_DB
        return (20.0 * log10(rms)).toFloat().coerceAtLeast(FLOOR_DB)
    }

    fun isNearSilent(rmsDb: Float): Boolean = rmsDb < THRESHOLD_DB

    fun isSilent(rmsDb: Float): Boolean = rmsDb < SILENT_THRESHOLD_DB

    /** How quiet an utterance at [rmsDb] was. */
    fun quiet(rmsDb: Float): Quiet = Quiet(isNearSilent(rmsDb), isSilent(rmsDb))
}

/**
 * How quiet an utterance's audio was, for the hallucination filter: below
 * [NearSilence.THRESHOLD_DB] ([nearSilent]) and below
 * [NearSilence.SILENT_THRESHOLD_DB] ([silent]). Text joined from several
 * parts is only as quiet as its loudest part: [and].
 */
data class Quiet(val nearSilent: Boolean, val silent: Boolean = false) {
    infix fun and(other: Quiet): Quiet = Quiet(nearSilent && other.nearSilent, silent && other.silent)

    companion object {
        /** Nothing heard yet: the start of an [and] chain. */
        val SILENT = Quiet(nearSilent = true, silent = true)
        val SPEECH = Quiet(nearSilent = false, silent = false)
    }
}
