package app.marmalade.stt.benchmark

import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.MicCapture
import app.marmalade.stt.speech.SttEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/*
 * Pure pieces of the headless speed benchmark (#23). The debug-only
 * `SpeedBenchmarkService` drives them on a phone: it decodes test WAVs with
 * each engine for load time and RTF, then plays each WAV through a real
 * listening session in real time ([PacedClipMic]) for the latencies a user
 * feels ([SessionRecorder]).
 */

/** Reads 16 kHz mono 16-bit PCM WAV files, the only format the benchmark takes. */
object Wav16k {

    /** The samples of [bytes] in [-1, 1]. Throws [IllegalArgumentException] for any other format. */
    fun decode(bytes: ByteArray): FloatArray {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && tag(b, 0) == "RIFF" && tag(b, 8) == "WAVE") { "not a WAV file" }
        var pos = 12
        var formatOk = false
        while (pos + 8 <= bytes.size) {
            val id = tag(b, pos)
            val size = b.getInt(pos + 4)
            val body = pos + 8
            // A streamed WAV may leave the data size at -1 or too large: the data runs to the end.
            require(id == "data" || size >= 0 && body + size <= bytes.size) { "chunk $id runs past the end" }
            when (id) {
                "fmt " -> {
                    val format = b.getShort(body).toInt()
                    val channels = b.getShort(body + 2).toInt()
                    val rate = b.getInt(body + 4)
                    val bits = b.getShort(body + 14).toInt()
                    require(format == 1 && channels == 1 && rate == MIC_SAMPLE_RATE && bits == 16) {
                        "need 16 kHz mono 16-bit PCM, got format=$format channels=$channels rate=$rate bits=$bits"
                    }
                    formatOk = true
                }
                "data" -> {
                    require(formatOk) { "data before fmt" }
                    val avail = bytes.size - body
                    val n = (if (size < 0 || size > avail) avail else size) / 2
                    return FloatArray(n) { b.getShort(body + 2 * it) / 32768f }
                }
            }
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        throw IllegalArgumentException("no data chunk")
    }

    private fun tag(b: ByteBuffer, at: Int) = String(ByteArray(4) { b.get(at + it) }, Charsets.US_ASCII)
}

/**
 * Where the speech in [audio] ends: just past the last 20 ms frame within
 * 35 dB of the loudest frame. The same definition as `asrbench.latency`'s
 * `speech_end`, so phone and host latencies compare.
 */
fun speechEndSample(audio: FloatArray): Int {
    val frame = MIC_SAMPLE_RATE / 50
    val n = audio.size / frame
    if (n == 0) return audio.size
    val db = DoubleArray(n) { f ->
        var sum = 0.0
        for (i in f * frame until (f + 1) * frame) sum += audio[i].toDouble() * audio[i]
        20 * log10(sqrt(sum / frame) + 1e-12)
    }
    val threshold = db.max() - 35
    val last = db.indices.last { db[it] > threshold }
    return (last + 1) * frame
}

/**
 * A mic that "hears" [clip] in real time: [leadSamples] of silence, the clip,
 * then [tailSamples] of silence, in 100 ms hops. Hop k (samples k·hop to
 * (k+1)·hop) is delivered at [startNanos] + (k+1) × 100 ms, when a real mic
 * would have finished recording it, so audio sample s happens at
 * [startNanos] + s / 16 kHz. The flow ends after the tail, which ends the
 * session as a stop would.
 *
 * Reports no platform AGC, like the Pixel 8a (`agc=unavailable`, #11), so the
 * session runs [app.marmalade.stt.speech.SoftAgc] as it does there.
 */
class PacedClipMic(
    private val clip: FloatArray,
    private val leadSamples: Int,
    private val tailSamples: Int,
    private val nowNanos: () -> Long = System::nanoTime,
) : MicCapture {

    /** When the capture started (hop 0 began), or -1 before the flow is collected. */
    @Volatile
    var startNanos: Long = -1L
        private set

    @Volatile
    private var closed = false

    override val hasPlatformAgc: Boolean get() = false

    override val hops: Flow<FloatArray> = flow {
        val total = leadSamples + clip.size + tailSamples
        val start = nowNanos()
        startNanos = start
        var from = 0
        while (from < total && !closed) {
            val to = minOf(from + HOP_SAMPLES, total)
            val due = start + to * NANOS_PER_SAMPLE
            val wait = (due - nowNanos()) / 1_000_000L
            if (wait > 0) delay(wait)
            emit(FloatArray(to - from) { i -> sampleAt(from + i) })
            from = to
        }
    }

    private fun sampleAt(i: Int): Float {
        val c = i - leadSamples
        return if (c in clip.indices) clip[c] else 0f
    }

    override fun close() {
        closed = true
    }

    companion object {
        const val HOP_SAMPLES = 1_600
        const val NANOS_PER_SAMPLE = 1_000_000_000L / MIC_SAMPLE_RATE
    }
}

/** What one real-time session run showed (all times in ms). */
data class SessionTiming(
    /** The clip's length. */
    val audioMs: Long,
    /** From the VAD's first onset to the first partial; null when no partial came. */
    val firstPartialMs: Long?,
    /** From the end of speech ([speechEndSample]) to the last Final; null when no Final came. */
    val endToFinalMs: Long?,
    val partials: Int,
    val finals: Int,
    /** The Finals, joined with spaces. */
    val text: String,
    /** The session's [SttEvent.Error] message, if it ended with one. */
    val error: String?,
)

/**
 * Plays one clip through a listening session and times it. Pass [openMic] as
 * the engine's mic factory, then collect the engine's `listen()` with
 * [record]. Leading silence ([LEAD_SAMPLES]) lets the VAD settle, as when a
 * user taps the mic and starts talking; the trailing silence ([TAIL_SAMPLES])
 * outlasts the longest pause setting so the VAD closes the last segment
 * before the mic ends. The same lead and tail as `asrbench.latency`.
 */
class SessionRecorder(
    private val clip: FloatArray,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    val mic = PacedClipMic(clip, LEAD_SAMPLES, TAIL_SAMPLES, nowNanos)

    @Suppress("UNUSED_PARAMETER")
    fun openMic(hopSamples: Int, hardwareEffects: Boolean): MicCapture = mic

    suspend fun record(events: Flow<SttEvent>): SessionTiming {
        var onset = -1L
        var firstPartial = -1L
        var lastFinal = -1L
        var partials = 0
        val finals = mutableListOf<String>()
        var error: String? = null
        events.collect { e ->
            val now = nowNanos()
            when (e) {
                SttEvent.SpeechStarted -> if (onset < 0) onset = now
                is SttEvent.Partial -> {
                    partials++
                    if (firstPartial < 0 && onset >= 0) firstPartial = now
                }
                is SttEvent.Final -> {
                    finals += e.text
                    lastFinal = now
                }
                is SttEvent.Error -> error = e.message
                else -> Unit
            }
        }
        val speechEnd = mic.startNanos + (LEAD_SAMPLES + speechEndSample(clip)) * PacedClipMic.NANOS_PER_SAMPLE
        return SessionTiming(
            audioMs = clip.size * 1_000L / MIC_SAMPLE_RATE,
            firstPartialMs = if (firstPartial >= 0) (firstPartial - onset) / 1_000_000L else null,
            endToFinalMs = if (lastFinal >= 0 && mic.startNanos >= 0) (lastFinal - speechEnd) / 1_000_000L else null,
            partials = partials,
            finals = finals.size,
            text = finals.joinToString(" "),
            error = error,
        )
    }

    companion object {
        const val LEAD_SAMPLES = MIC_SAMPLE_RATE / 2 // 0.5 s
        const val TAIL_SAMPLES = 3 * MIC_SAMPLE_RATE // 3 s
    }
}

/** Median of [values] (the mean of the middle two for an even count); null when empty. */
fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
}
