package app.marmalade.stt.speech

import android.media.AudioFormat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [StreamCapture]: a caller's raw PCM (#90, `EXTRA_AUDIO_SOURCE`) as 16 kHz
 * mono hops, and a session reading such a non-live source without falling
 * "behind" it.
 */
class StreamCaptureTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun capture(bytes: ByteArray, format: PcmFormat, input: InputStream = ByteArrayInputStream(bytes)) =
        StreamCapture(input, format, HOP)

    private suspend fun StreamCapture.samples(): FloatArray {
        val hops = hops.toList()
        return FloatArray(hops.sumOf { it.size }).also { out ->
            var at = 0
            for (h in hops) {
                h.copyInto(out, at)
                at += h.size
            }
        }
    }

    private fun pcm16(vararg samples: Int): ByteArray =
        ByteArray(samples.size * 2).also { b ->
            samples.forEachIndexed { i, s ->
                b[2 * i] = s.toByte()
                b[2 * i + 1] = (s shr 8).toByte()
            }
        }

    private fun int32(vararg values: Int): ByteArray =
        ByteArray(values.size * 4).also { b ->
            values.forEachIndexed { i, v -> for (k in 0..3) b[4 * i + k] = (v shr (8 * k)).toByte() }
        }

    /** [seconds] of a [hz] sine at [rate], 16-bit, amplitude [amplitude] of full scale. */
    private fun sine16(hz: Double, rate: Int, seconds: Double, amplitude: Double = 0.5): ByteArray {
        val n = (rate * seconds).roundToInt()
        return pcm16(*IntArray(n) { (amplitude * 32767 * sin(2 * PI * hz * it / rate)).roundToInt() })
    }

    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (to - from))
    }

    /** Frequency in Hz of a 16 kHz tone, from its rising zero crossings (linearly interpolated) over [from, to). */
    private fun frequency(x: FloatArray, from: Int, to: Int): Double {
        var first = -1.0
        var last = -1.0
        var crossings = 0
        for (i in from + 1 until to) {
            if (x[i - 1] < 0 && x[i] >= 0) {
                val t = i - 1 + x[i - 1] / (x[i - 1] - x[i]).toDouble()
                if (first < 0) first = t else crossings++
                last = t
            }
        }
        return crossings * MIC_SAMPLE_RATE / (last - first)
    }

    /** Returns 1–3 bytes per read: frames arrive split, as from a pipe. */
    private class TrickleStream(private val data: ByteArray) : InputStream() {
        private var at = 0
        private val random = Random(7)

        override fun read(): Int = if (at < data.size) data[at++].toInt() and 0xff else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (at >= data.size) return -1
            val n = minOf(len, random.nextInt(1, 4), data.size - at)
            data.copyInto(b, off, at, at + n)
            at += n
            return n
        }
    }

    /**
     * Gives [first], then blocks like an idle pipe until closed, when the
     * blocked read fails, as a closed file descriptor's does on Android.
     * (A PipedInputStream won't do: closing its read end doesn't wake a
     * reader waiting for its writer.)
     */
    private class BlockingStream(first: ByteArray) : InputStream() {
        private val first = ByteArrayInputStream(first)
        val blocked = CountDownLatch(1)
        private val closedLatch = CountDownLatch(1)

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = first.read(b, off, len)
            if (n > 0) return n
            blocked.countDown()
            closedLatch.await(5, TimeUnit.SECONDS)
            throw IOException("Stream closed")
        }

        override fun close() = closedLatch.countDown()
    }

    // ── encodings ────────────────────────────────────────────────────────────

    @Test
    fun `16-bit mono at 16 kHz comes through bit-exact, as the mic's samples`() = runTest(timeout = TEST_TIMEOUT) {
        val random = Random(1)
        val values = IntArray(3 * HOP + 100) { random.nextInt(-32768, 32768) }
        values[0] = -32768
        values[1] = 32767
        val hops = capture(pcm16(*values), PcmFormat()).hops.toList()

        assertEquals(listOf(HOP, HOP, HOP, 100), hops.map { it.size })
        val expected = FloatArray(values.size) { values[it].toShort() / 32768.0f }
        assertArrayEquals(expected, hops.reduce { a, b -> a + b }, 0f)
    }

    @Test
    fun `8-bit PCM is unsigned, 128 the zero`() = runTest(timeout = TEST_TIMEOUT) {
        val bytes = byteArrayOf(128.toByte(), 0, 255.toByte(), 192.toByte())
        val got = capture(bytes, PcmFormat(encoding = AudioFormat.ENCODING_PCM_8BIT)).samples()
        assertArrayEquals(floatArrayOf(0f, -1f, 127 / 128f, 0.5f), got, 0f)
    }

    @Test
    fun `float PCM is read little-endian, clamped to full scale, NaN silenced`() = runTest(timeout = TEST_TIMEOUT) {
        val values = floatArrayOf(0.25f, -0.5f, 1.5f, Float.NaN, -3f)
        val bytes = int32(*IntArray(values.size) { values[it].toRawBits() })
        val got = capture(bytes, PcmFormat(encoding = AudioFormat.ENCODING_PCM_FLOAT)).samples()
        assertArrayEquals(floatArrayOf(0.25f, -0.5f, 1f, 0f, -1f), got, 0f)
    }

    @Test
    fun `24-bit packed and 32-bit integer PCM are read signed, little-endian`() = runTest(timeout = TEST_TIMEOUT) {
        // 0x400000 (half scale), -0x800000 (negative full scale), -1.
        val packed = byteArrayOf(0, 0, 0x40, 0, 0, 0x80.toByte(), -1, -1, -1)
        val got24 = capture(packed, PcmFormat(encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED)).samples()
        assertArrayEquals(floatArrayOf(0.5f, -1f, -1f / 8_388_608), got24, 0f)

        val got32 = capture(int32(0x40000000, Int.MIN_VALUE, -0x20000000), PcmFormat(encoding = AudioFormat.ENCODING_PCM_32BIT)).samples()
        assertArrayEquals(floatArrayOf(0.5f, -1f, -0.25f), got32, 0f)
    }

    @Test
    fun `channels are averaged to mono`() = runTest(timeout = TEST_TIMEOUT) {
        val stereo = pcm16(16384, 0, -16384, -16384, 8192, -8192)
        assertArrayEquals(floatArrayOf(0.25f, -0.5f, 0f), capture(stereo, PcmFormat(channels = 2)).samples(), 0f)

        val six = pcm16(6 * 1024, 0, 0, 0, 0, 0)
        assertArrayEquals(floatArrayOf(1024f / 32768), capture(six, PcmFormat(channels = 6)).samples(), 1e-7f)
    }

    @Test
    fun `frames split across reads are put back together`() = runTest(timeout = TEST_TIMEOUT) {
        val random = Random(3)
        // 24-bit stereo: 6-byte frames. 48 kHz, so the resampler also gets odd chunk sizes.
        val bytes = ByteArray(6 * 4800) { random.nextInt(256).toByte() }
        val format = PcmFormat(sampleRate = 48_000, channels = 2, encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED)

        val whole = capture(bytes, format).samples()
        val trickled = capture(bytes, format, TrickleStream(bytes)).samples()

        assertEquals(1600, whole.size)
        assertArrayEquals(whole, trickled, 0f)
    }

    @Test
    fun `the end of the stream ends the hops, the last one short`() = runTest(timeout = TEST_TIMEOUT) {
        val hops = capture(pcm16(*IntArray(2 * HOP + HOP / 2) { 100 }), PcmFormat()).hops.toList()
        assertEquals(listOf(HOP, HOP, HOP / 2), hops.map { it.size })

        assertTrue(capture(ByteArray(0), PcmFormat()).hops.toList().isEmpty())
        // Half a frame: nothing to read.
        assertTrue(capture(ByteArray(1), PcmFormat()).hops.toList().isEmpty())
    }

    // ── resampling ───────────────────────────────────────────────────────────

    @Test
    fun `other rates become 16 kHz, a 440 Hz tone keeping its pitch and level`() = runTest(timeout = TEST_TIMEOUT) {
        for (rate in listOf(8_000, 22_050, 24_000, 44_100, 48_000)) {
            val out = capture(sine16(440.0, rate, 2.0), PcmFormat(sampleRate = rate)).samples()

            assertTrue("$rate Hz: ${out.size} samples", abs(out.size - 32_000) <= 2)
            // Away from the edges, where the input starts and stops abruptly.
            val hz = frequency(out, 2_000, 30_000)
            assertEquals("$rate Hz", 440.0, hz, 0.5)
            assertEquals("$rate Hz", 0.5 / sqrt(2.0), rms(out, 2_000, 30_000), 0.005)
        }
    }

    @Test
    fun `downsampling low-passes first, so what is above 8 kHz doesn't alias into speech`() = runTest(timeout = TEST_TIMEOUT) {
        for ((rate, hz) in listOf(48_000 to 10_000.0, 48_000 to 12_000.0, 44_100 to 9_000.0, 24_000 to 9_000.0)) {
            val out = capture(sine16(hz, rate, 1.0), PcmFormat(sampleRate = rate)).samples()
            // Plain decimation would fold it to 16 kHz − f at full level (−9 dBFS rms).
            val level = rms(out, 1_000, 15_000) / (0.5 / sqrt(2.0))
            assertTrue("$hz Hz at $rate Hz kept ${"%.4f".format(level)} of its level", level < 0.003) // −50 dB
        }
        // The speech band passes.
        val speech = capture(sine16(3_000.0, 48_000, 1.0), PcmFormat(sampleRate = 48_000)).samples()
        assertEquals(0.5 / sqrt(2.0), rms(speech, 1_000, 15_000), 0.005)
    }

    // ── closing and failing ──────────────────────────────────────────────────

    @Test
    fun `close from another thread ends a blocked read`() = runTest(timeout = TEST_TIMEOUT) {
        val stream = BlockingStream(pcm16(*IntArray(HOP) { 1000 }))
        val capture = capture(ByteArray(0), PcmFormat(), stream)
        val got = async(Dispatchers.IO) { capture.hops.toList() }

        assertTrue(stream.blocked.await(5, TimeUnit.SECONDS))
        Thread { capture.close() }.apply { start() }.join()
        capture.close() // idempotent

        assertEquals(listOf(HOP), got.await().map { it.size })
    }

    @Test
    fun `a read failure before close is a mic error`() = runTest(timeout = TEST_TIMEOUT) {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("EIO")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("EIO")
        }
        try {
            capture(ByteArray(0), PcmFormat(), broken).hops.toList()
            fail("expected a MicCaptureException")
        } catch (e: MicCaptureException) {
            // Coroutines' stack-trace recovery may wrap the original in a copy: look down the chain.
            assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it is IOException })
        }
    }

    @Test
    fun `supported formats`() {
        for (encoding in listOf(
            AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT,
        )) {
            assertTrue("$encoding", StreamCapture.supports(PcmFormat(encoding = encoding)))
        }
        assertTrue(StreamCapture.supports(PcmFormat(sampleRate = 8_000, channels = 8)))
        assertTrue(StreamCapture.supports(PcmFormat(sampleRate = 192_000)))

        assertFalse(StreamCapture.supports(PcmFormat(encoding = AudioFormat.ENCODING_MP3)))
        assertFalse(StreamCapture.supports(PcmFormat(encoding = AudioFormat.ENCODING_INVALID)))
        assertFalse(StreamCapture.supports(PcmFormat(channels = 0)))
        assertFalse(StreamCapture.supports(PcmFormat(channels = 9)))
        assertFalse(StreamCapture.supports(PcmFormat(sampleRate = 4_000)))
        assertFalse(StreamCapture.supports(PcmFormat(sampleRate = 384_000)))
    }

    @Test
    fun `a stream is not live and has no platform AGC`() {
        val capture = capture(ByteArray(0), PcmFormat())
        assertFalse(capture.live)
        assertFalse(capture.hasPlatformAgc)
    }

    // ── limits ───────────────────────────────────────────────────────────────

    @Test
    fun `a stream that sends nothing for the idle timeout ends, keeping what came`() = runTest(timeout = TEST_TIMEOUT) {
        val stream = BlockingStream(pcm16(*IntArray(HOP + 10) { 1000 }))
        val capture = StreamCapture(stream, PcmFormat(), HOP, idleTimeoutMs = 200)
        val started = System.nanoTime()

        val got = withContext(Dispatchers.IO) { capture.hops.toList() }

        // Nobody closed it: the watchdog did, and the short last hop still came.
        assertEquals(listOf(HOP, 10), got.map { it.size })
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("ended after $ms ms", ms in 200..3_000)
    }

    @Test
    fun `time the session spends consuming is not idle`() = runTest(timeout = TEST_TIMEOUT) {
        val hops = 6
        val capture = StreamCapture(ByteArrayInputStream(pcm16(*IntArray(hops * HOP) { 1000 })), PcmFormat(), HOP, idleTimeoutMs = 100)
        var got = 0
        withContext(Dispatchers.IO) {
            capture.hops.collect {
                Thread.sleep(150) // a slow decode, longer than the idle timeout
                got++
            }
        }
        assertEquals(hops, got)
    }

    @Test
    fun `a stream ends after the most audio it may carry`() = runTest(timeout = TEST_TIMEOUT) {
        // /dev/zero: never ends, and always has bytes.
        val zeros = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(0, off, off + len)
                return len
            }
        }
        val capture = StreamCapture(zeros, PcmFormat(sampleRate = 48_000, channels = 2), HOP, maxAudioMs = 1_500)
        // 1.5 s at 48 kHz stereo is 1.5 s at 16 kHz mono.
        assertEquals(24_000.0, capture.samples().size.toDouble(), 2.0)
    }

    @Test
    fun `reads that return no bytes back off, and the idle timeout ends them`() = runTest(timeout = TEST_TIMEOUT) {
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val empty = object : InputStream() {
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads.incrementAndGet()
                return 0 // a non-blocking descriptor with nothing in it
            }
        }
        val capture = StreamCapture(empty, PcmFormat(), HOP, idleTimeoutMs = 300)

        val got = withContext(Dispatchers.IO) { capture.hops.toList() }

        assertTrue(got.isEmpty())
        // About 300 / EMPTY_READ_BACKOFF_MS reads; spinning would make millions.
        assertTrue("${reads.get()} reads", reads.get() in 5..40)
    }

    /** Never ends: [bytes] bytes of silence per read, each read taking [everyMs] (a slow writer). */
    private class SlowStream(private val bytes: Int, private val everyMs: Long) : InputStream() {
        val reads = java.util.concurrent.atomic.AtomicInteger()
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            Thread.sleep(everyMs)
            reads.incrementAndGet()
            val n = minOf(bytes, len)
            b.fill(0, off, off + n)
            return n
        }
    }

    /** How long collecting [capture] took, in ms, and the samples it gave. */
    private suspend fun timed(capture: StreamCapture): Pair<Long, Int> {
        val started = System.nanoTime()
        val samples = withContext(Dispatchers.IO) { capture.hops.toList() }.sumOf { it.size }
        return (System.nanoTime() - started) / 1_000_000 to samples
    }

    @Test
    fun `a stream trickling single bytes ends at the idle timeout`() = runTest(timeout = TEST_TIMEOUT) {
        // A byte every 50 ms keeps a pipe busy but carries almost no audio.
        val (ms, _) = timed(StreamCapture(SlowStream(bytes = 1, everyMs = 50), PcmFormat(), HOP, idleTimeoutMs = 400))
        assertTrue("ended after $ms ms", ms in 400..2_000)
    }

    @Test
    fun `a stream trickling whole frames at a hundredth of real time ends at the idle timeout`() = runTest(timeout = TEST_TIMEOUT) {
        // 16 frames every 10 ms: 1.6 kframes/s against 16 kHz.
        val (ms, samples) = timed(StreamCapture(SlowStream(bytes = 32, everyMs = 10), PcmFormat(), HOP, idleTimeoutMs = 400))
        assertTrue("ended after $ms ms", ms in 400..2_000)
        assertTrue("$samples samples", samples < MIC_SAMPLE_RATE / 10)
    }

    @Test
    fun `a stream at a fifth of real time keeps going`() = runTest(timeout = TEST_TIMEOUT) {
        // 64 frames (4 ms) every 20 ms: a tenth of the idle timeout's worth of audio arrives in time.
        val stream = SlowStream(bytes = 128, everyMs = 20)
        val capture = StreamCapture(stream, PcmFormat(), HOP, idleTimeoutMs = 400)
        val got = async(Dispatchers.IO) { capture.hops.toList() }
        Thread.sleep(1_500)
        assertFalse("ended on its own", got.isCompleted)
        capture.close()
        got.await()
    }

    @Test
    fun `no stream outlives the wall-clock cap, however much audio it sends`() = runTest(timeout = TEST_TIMEOUT) {
        // Faster than real time, so neither the idle rule nor the audio cap (an hour) stops it.
        val (ms, _) = timed(StreamCapture(SlowStream(bytes = 2048, everyMs = 5), PcmFormat(), HOP, maxWallMs = 600))
        assertTrue("ended after $ms ms", ms in 600..2_500)
    }

    // ── a session on a stream ────────────────────────────────────────────────

    /** Yields [count] loud hops as fast as they are read, then ends. */
    private class InstantCapture(private val count: Int, override val live: Boolean) : MicCapture {
        override val hops: Flow<FloatArray> = flow { repeat(count) { emit(loudHop.copyOf()) } }
        override fun close() = Unit
    }

    /** The most a slow consumer saw captured() run ahead of the hop in hand, and the indexes it got. */
    private suspend fun lagOf(capture: MicCapture): Pair<Int, List<Int>> {
        var maxAhead = 0
        val indexes = mutableListOf<Int>()
        micSession({ _, _ -> capture }, isStopRequested = { false }) { hops, captured ->
            for (h in hops) {
                Thread.sleep(1) // a decode
                maxAhead = maxOf(maxAhead, captured() - h.index)
                indexes += h.index
            }
        }.toList()
        return maxAhead to indexes
    }

    @Test
    fun `micSession reads a non-live capture in step with its consumer`() = runTest(timeout = TEST_TIMEOUT) {
        val (ahead, indexes) = lagOf(InstantCapture(150, live = false))
        assertEquals((0 until 150).toList(), indexes)
        assertTrue("$ahead hops ahead", ahead <= NON_LIVE_QUEUED_HOPS + 2)

        // A live mic's hops are all taken at once (this one, unlike a mic, delivers them instantly).
        val (liveAhead, _) = lagOf(InstantCapture(150, live = true))
        assertTrue("$liveAhead hops ahead", liveAhead > 15)
    }

    @Test
    fun `15 s of a stream read at once through EarlyMic into a session never looks too slow`() = runTest(timeout = TEST_TIMEOUT) {
        val stream = capture(sine16(440.0, 48_000, 15.0), PcmFormat(sampleRate = 48_000))
        // A small queue, so the backpressure is exercised.
        val early = EarlyMic(stream, maxQueuedHops = 20)
        launch(Dispatchers.IO) { early.record() }

        val fake = FakeVad(speechFromSample = 1, segmentsAt = List(7) { (it + 1) * 2 * MIC_SAMPLE_RATE })
        var accepted = 0
        val vad = object : SpeechVad by fake {
            override fun acceptWaveform(samples: FloatArray) {
                // Slower than reading and resampling the stream (and catch-up can't skip it):
                // a session that read ahead of itself would fall behind and report it.
                Thread.sleep(2)
                accepted += samples.size
                fake.acceptWaveform(samples)
            }
        }
        val transcriber = FakeTranscriber { _ ->
            Thread.sleep(1)
            "words"
        }
        val session = StreamingSession(
            transcriber, vad, options = SessionOptions(end = EndOfDictation.PAUSE), openMic = { _, _ -> early },
            nowMs = fastClock(),
        )

        val events = session.run().toList()

        assertTrue(events.filterIsInstance<SttEvent.CatchingUp>().toString(), events.none { it is SttEvent.CatchingUp })
        assertFalse(SttEvent.TooSlow in events)
        assertTrue(events.toString(), events.none { it is SttEvent.Error })
        // Every hop reached the VAD (but the last window's worth, which it never fills).
        assertTrue("$accepted samples", accepted > 15 * MIC_SAMPLE_RATE - 2 * StreamingSession.VAD_WINDOW_SIZE)
        assertTrue(events.filterIsInstance<SttEvent.Final>().size >= 7)
    }
}
