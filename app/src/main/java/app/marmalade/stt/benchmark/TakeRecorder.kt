package app.marmalade.stt.benchmark

import app.marmalade.stt.speech.AudioPreprocessor
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.MicCapture
import app.marmalade.stt.speech.NearSilence
import app.marmalade.stt.speech.SoftAgc
import app.marmalade.stt.speech.StreamingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext

/**
 * A recorded take: 16 kHz mono float PCM, in memory only.
 *
 * @property softAgc true when [SoftAgc] ran on it because the mic had no
 *   platform AGC, as a live session would on this phone.
 */
class RecordedTake(val samples: FloatArray, val softAgc: Boolean)

/**
 * Records one take for the A/B benchmark, **into memory only**: the samples
 * live in a FloatArray and are never written to disk or logged
 * (ARCHITECTURE.md D10).
 *
 * The mic is opened as a live session opens it (100 ms hops, platform
 * NoiseSuppressor/AGC on) and every hop goes through the same
 * [AudioPreprocessor] high-pass, then [SoftAgc] when the mic has no platform
 * AGC, exactly as `micSession` does. So the engines hear what they would
 * hear live. One recorder per take.
 *
 * @param openMic mic factory, called with (hop size, hardware effects on);
 *   production passes `::openMicCapture`.
 * @param maxSamples the cap; [MAX_TAKE_SECONDS] of audio by default.
 */
class TakeRecorder(
    private val openMic: (hopSamples: Int, hardwareEffects: Boolean) -> MicCapture,
    private val maxSamples: Int = MAX_TAKE_SECONDS * MIC_SAMPLE_RATE,
) {
    @Volatile
    private var stopRequested = false

    /** End the take after the hop being read. Safe from any thread, also before [record] starts. */
    fun stop() {
        stopRequested = true
    }

    /**
     * Record until [stop] or [maxSamples], then return the preprocessed take
     * (possibly empty). Reads the mic on [Dispatchers.IO]; the mic is closed
     * however this ends.
     *
     * @param onProgress per hop: the hop's level in dBFS ([NearSilence.rmsDb])
     *   and the seconds recorded so far. Called on the recording thread.
     * @throws app.marmalade.stt.speech.MicCaptureException when the mic can't
     *   be opened or fails mid-take.
     */
    suspend fun record(onProgress: (levelDb: Float, elapsedSeconds: Float) -> Unit): RecordedTake =
        withContext(Dispatchers.IO) {
            val buffer = FloatArray(maxSamples)
            var count = 0
            val mic = openMic(StreamingSession.MIC_HOP_SAMPLES, true)
            val softAgc = if (mic.hasPlatformAgc) null else SoftAgc()
            try {
                val preprocessor = AudioPreprocessor()
                mic.hops
                    .takeWhile { !stopRequested }
                    // Ends right after the hop that fills the take, without another mic read.
                    .transformWhile<FloatArray, Nothing> { raw ->
                        val filtered = preprocessor.process(raw)
                        val hop = softAgc?.process(filtered) ?: filtered
                        val n = minOf(hop.size, maxSamples - count)
                        hop.copyInto(buffer, destinationOffset = count, endIndex = n)
                        count += n
                        onProgress(NearSilence.rmsDb(hop), count.toFloat() / MIC_SAMPLE_RATE)
                        count < maxSamples
                    }
                    .collect()
            } finally {
                mic.close()
            }
            RecordedTake(buffer.copyOf(count), softAgc = softAgc != null)
        }

    companion object {
        /** The longest take. Long enough for a few sentences, short enough to keep slow engines bearable. */
        const val MAX_TAKE_SECONDS = 30
    }
}
