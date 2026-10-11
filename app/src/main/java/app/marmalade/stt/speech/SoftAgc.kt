package app.marmalade.stt.speech

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Software gain control for a mic without the platform `AutomaticGainControl`
 * ([MicCapture.hasPlatformAgc] false). It runs on each hop after the
 * high-pass, before the VAD and the model.
 *
 * Why: the VOICE_RECOGNITION source is calibrated flat, so dictation at arm's
 * length arrives around -40 to -55 dBFS. At that level Silero detects speech
 * late (the first word is lost) and holds segments open, and the models do
 * worse. On the #15 host benchmark (FLEURS-en without AGC, a -85 dBFS mic
 * floor), this gain took Parakeet from 16.1 to 8.0 % WER and the dropped
 * first words from 29 to 1 of 200. Speech already louder than the target is
 * left alone.
 *
 * How: an envelope of hop RMS (instant attack, [RELEASE_DB_PER_SECOND]
 * release) sets the gain [TARGET_DBFS] minus the envelope, between 0 dB (it
 * never attenuates) and [MAX_GAIN_DB], capped so the hop's peak stays at or
 * below [PEAK_DBFS]. The gain is computed with the current hop included, so a
 * loud first hop is never over-amplified. The envelope starts at
 * [START_DBFS], which means full gain at mic open for a quiet first word.
 *
 * `tools/asr-bench` has the same algorithm (`session.SoftAgc`); keep the two
 * in step. Stateful and not thread-safe: one instance per session, called
 * from the producer coroutine.
 */
class SoftAgc {
    private var envelopeDb = START_DBFS

    /** Apply the gain to [samples] **in place**; returns the same array. */
    fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        var sum = 0.0
        var peak = 0f
        for (s in samples) {
            sum += s.toDouble() * s
            peak = max(peak, kotlin.math.abs(s))
        }
        val levelDb = 20.0 * log10(max(sqrt(sum / samples.size), 1e-10))
        val releaseDb = RELEASE_DB_PER_SECOND * samples.size / MIC_SAMPLE_RATE
        envelopeDb = max(levelDb, envelopeDb - releaseDb)
        var gainDb = (TARGET_DBFS - envelopeDb).coerceIn(0.0, MAX_GAIN_DB)
        if (peak > 0f) gainDb = min(gainDb, PEAK_DBFS - 20.0 * log10(peak.toDouble()))
        if (gainDb <= 0.0) return samples
        val gain = 10.0.pow(gainDb / 20.0).toFloat()
        for (i in samples.indices) samples[i] *= gain
        return samples
    }

    companion object {
        const val TARGET_DBFS = -20.0
        const val MAX_GAIN_DB = 30.0
        const val RELEASE_DB_PER_SECOND = 3.0
        const val START_DBFS = -50.0
        const val PEAK_DBFS = -1.0
    }
}
