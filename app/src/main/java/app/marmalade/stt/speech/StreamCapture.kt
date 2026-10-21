package app.marmalade.stt.speech

import android.media.AudioFormat
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * The format of a caller's raw PCM stream (`RecognizerIntent.EXTRA_AUDIO_SOURCE_*`,
 * #90), with Android's documented defaults: mono, 16-bit PCM, 16 kHz.
 *
 * @property encoding an `AudioFormat.ENCODING_*` value.
 */
data class PcmFormat(
    val sampleRate: Int = MIC_SAMPLE_RATE,
    val channels: Int = 1,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
)

/**
 * A [MicCapture] over a caller's audio stream instead of the mic (#90,
 * `EXTRA_AUDIO_SOURCE`): raw interleaved PCM in [format], read until the
 * stream ends. Each frame is downmixed to mono (the channels' mean), and
 * resampled to [MIC_SAMPLE_RATE] by a [Resampler] unless it is already
 * there; 16 kHz input comes through unchanged (16-bit: sample / 32768,
 * exactly as [openMicCapture]'s). [hops] yields `hopSamples` at a time, the
 * last hop shorter, and ends at the end of the stream. Reads block, so like
 * the mic it adds no `flowOn`. Frames split across reads (a pipe delivers
 * any byte count) are put back together.
 *
 * Not [live][MicCapture.live]: a caller may hand over a file, read as fast
 * as the session consumes it.
 *
 * No platform AGC ([hasPlatformAgc] false), so the session runs [SoftAgc]:
 * the caller's audio may be anything (a quiet file, or its own mic, which on
 * the VOICE_RECOGNITION path has no gain control either), and [SoftAgc] is
 * what brings an unprocessed mic to the level the models expect.
 *
 * Bounded, so no caller holds the engine for ever (#90). The stream ends as
 * if the caller had closed it:
 * - when we have waited [idleTimeoutMs] for audio without a tenth of that
 *   arriving (1 s of audio in 10 s): a writer that stalled, a pipe it never
 *   closes, or one that trickles bytes to keep it open. Only time spent
 *   waiting on the caller counts, not time the session spends consuming;
 * - after [maxAudioMs] of audio (a caller handing us `/dev/zero`);
 * - [maxWallMs] after the first read, whatever the stream does.
 *
 * What came before is still transcribed. A read that returns no bytes (a
 * non-blocking descriptor) is retried after [EMPTY_READ_BACKOFF_MS], not at
 * once.
 *
 * The audio stays in memory: nothing is written, and no sample is logged.
 *
 * @param input the stream; owned: [close] closes it.
 * @param format must pass [supports].
 */
class StreamCapture(
    private val input: InputStream,
    private val format: PcmFormat,
    private val hopSamples: Int,
    private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    private val maxAudioMs: Long = MAX_AUDIO_MS,
    private val maxWallMs: Long = MAX_WALL_MS,
) : MicCapture {

    companion object {
        /** Waiting this long for audio without a tenth of it arriving ends the stream: 10 s. */
        const val IDLE_TIMEOUT_MS = 10_000L

        /** The most audio one stream may carry: an hour, generous for transcribing a file. */
        const val MAX_AUDIO_MS = 3_600_000L

        /** The longest a stream is read, from its first read: an hour. */
        const val MAX_WALL_MS = 3_600_000L

        /** The wait before reading again after a read that returned no bytes. */
        const val EMPTY_READ_BACKOFF_MS = 20L

        /** Frames per read: 64 ms at 16 kHz, a few KB at most. */
        private const val READ_FRAMES = 1024

        private fun sampleBytes(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            else -> 0
        }

        /**
         * Whether [format] can be read: 8-, 16-, 24- (packed) or 32-bit
         * integer PCM or float PCM, 1 to 8 channels, 8–192 kHz.
         */
        fun supports(format: PcmFormat): Boolean =
            sampleBytes(format.encoding) > 0 && format.channels in 1..8 && format.sampleRate in 8_000..192_000
    }

    init {
        require(supports(format)) { "unsupported PCM format $format" }
        require(hopSamples > 0) { "hopSamples $hopSamples" }
        require(idleTimeoutMs > 0 && maxAudioMs > 0 && maxWallMs > 0) { "limits $idleTimeoutMs, $maxAudioMs, $maxWallMs" }
    }

    private val closed = AtomicBoolean(false)
    private val sampleBytes = sampleBytes(format.encoding)
    private val frameBytes = sampleBytes * format.channels

    /** The audio that counts as progress: a tenth of [idleTimeoutMs], in frames (1 s at the defaults). */
    private val progressFrames = maxOf(1L, format.sampleRate * idleTimeoutMs / 10 / 1000)

    // Waiting on the caller since the last progress; the read loop and the watchdog share it.
    private val progressLock = Any()
    private var waitingSince = 0L // System.nanoTime() when the current wait began, 0 while not waiting
    private var waitedNanos = 0L // earlier waits since the last progress
    private var framesSinceProgress = 0L

    private fun startWaiting() = synchronized(progressLock) {
        if (waitingSince == 0L) waitingSince = System.nanoTime()
    }

    /** A read brought [frames] whole frames (maybe none): the wait ends; enough audio restarts the count. */
    private fun received(frames: Int) = synchronized(progressLock) {
        waitedNanos += System.nanoTime() - waitingSince
        waitingSince = 0L
        framesSinceProgress += frames
        if (framesSinceProgress >= progressFrames) {
            framesSinceProgress = 0L
            waitedNanos = 0L
        }
    }

    /** How long we have waited on the caller since the last progress, in ms. */
    private fun waitedMs(): Long = synchronized(progressLock) {
        (waitedNanos + if (waitingSince != 0L) System.nanoTime() - waitingSince else 0L) / 1_000_000
    }

    override val live: Boolean get() = false

    override val hasPlatformAgc: Boolean get() = false

    override val hops: Flow<FloatArray> = flow {
        val resampler = if (format.sampleRate == MIC_SAMPLE_RATE) null else Resampler(format.sampleRate, MIC_SAMPLE_RATE)
        val bytes = ByteArray(frameBytes * READ_FRAMES)
        var pending = 0 // bytes of an incomplete frame, kept at the start of bytes
        val hop = FloatArray(hopSamples)
        var filled = 0

        suspend fun emitHops(samples: FloatArray) {
            var from = 0
            while (from < samples.size) {
                val n = minOf(hopSamples - filled, samples.size - from)
                samples.copyInto(hop, destinationOffset = filled, startIndex = from, endIndex = from + n)
                filled += n
                from += n
                if (filled == hopSamples) {
                    emit(hop.copyOf()) // the session filters its hops in place
                    filled = 0
                }
            }
        }

        val maxFrames = format.sampleRate * maxAudioMs / 1000
        var frames = 0L
        coroutineScope {
            // On its own thread: the read below blocks the collector's.
            val watchdog = launch(Dispatchers.Default) { watch(startedAt = System.nanoTime()) }
            try {
                while (frames < maxFrames) {
                    currentCoroutineContext().ensureActive()
                    startWaiting()
                    val read = try {
                        input.read(bytes, pending, bytes.size - pending)
                    } catch (e: IOException) {
                        // close() closing the stream is what unblocks a read.
                        if (closed.get()) break
                        throw MicCaptureException("Couldn't read the audio stream", e)
                    }
                    if (closed.get() || read < 0) break
                    if (read == 0) {
                        // A non-blocking descriptor with nothing yet; still waiting.
                        delay(EMPTY_READ_BACKOFF_MS)
                        continue
                    }
                    val available = pending + read
                    val whole = minOf((available / frameBytes).toLong(), maxFrames - frames).toInt()
                    received(whole) // the session consuming it doesn't count as waiting
                    frames += whole
                    val mono = toMono(bytes, whole)
                    pending = available - whole * frameBytes
                    bytes.copyInto(bytes, destinationOffset = 0, startIndex = whole * frameBytes, endIndex = available)
                    emitHops(resampler?.process(mono) ?: mono)
                }
            } finally {
                watchdog.cancel()
            }
        }
        // The end of the stream, or of what we take of it (a trailing partial frame is dropped).
        resampler?.let { emitHops(it.flush()) }
        if (filled > 0) emit(hop.copyOf(filled))
    }

    /**
     * Closes the stream, which ends a blocked read, once we have waited
     * [idleTimeoutMs] without progress or [maxWallMs] have passed since
     * [startedAt].
     */
    private suspend fun watch(startedAt: Long) {
        while (true) {
            val idleLeft = idleTimeoutMs - waitedMs()
            val wallLeft = maxWallMs - (System.nanoTime() - startedAt) / 1_000_000
            if (idleLeft <= 0 || wallLeft <= 0) return close()
            // Waiting accrues no faster than the clock, so neither limit can pass before this.
            delay(minOf(idleLeft, wallLeft))
        }
    }

    /** The first [frames] frames of [bytes], each the mean of its channels. */
    private fun toMono(bytes: ByteArray, frames: Int): FloatArray {
        val channels = format.channels
        var at = 0
        return FloatArray(frames) {
            var sum = 0f
            repeat(channels) {
                sum += sample(bytes, at)
                at += sampleBytes
            }
            sum / channels
        }
    }

    /** The sample at byte [at], in [-1, 1]. Little-endian; 8-bit PCM is unsigned. */
    private fun sample(b: ByteArray, at: Int): Float = when (format.encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> ((b[at].toInt() and 0xff) or (b[at + 1].toInt() shl 8)).toShort() / 32768f
        AudioFormat.ENCODING_PCM_8BIT -> ((b[at].toInt() and 0xff) - 128) / 128f
        AudioFormat.ENCODING_PCM_24BIT_PACKED ->
            ((b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or (b[at + 2].toInt() shl 16)) / 8_388_608f
        AudioFormat.ENCODING_PCM_32BIT -> int32(b, at) / 2_147_483_648f
        // Float PCM may stray past full scale, or hold NaN; the models expect neither.
        else -> Float.fromBits(int32(b, at)).let { if (it.isNaN()) 0f else it.coerceIn(-1f, 1f) }
    }

    private fun int32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or
            ((b[at + 2].toInt() and 0xff) shl 16) or (b[at + 3].toInt() shl 24)

    /** Idempotent, from any thread: closes the stream, which unblocks a pending read, and ends [hops]. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { input.close() }
    }
}
