package app.marmalade.stt.benchmark

import app.marmalade.stt.speech.SttEvent
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedBenchTest {

    private fun wav(samples: ShortArray, rate: Int = 16_000, channels: Int = 1, extraChunk: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(n: Int, bytes: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array().copyOf(bytes)
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        out.write("RIFF".toByteArray()); out.write(le(0, 4)); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); out.write(le(16, 4))
        out.write(le(1, 2)); out.write(le(channels, 2)); out.write(le(rate, 4))
        out.write(le(rate * 2 * channels, 4)); out.write(le(2 * channels, 2)); out.write(le(16, 2))
        if (extraChunk) {
            // An odd-sized LIST chunk, as ffmpeg writes, plus its pad byte.
            out.write("LIST".toByteArray()); out.write(le(3, 4)); out.write(byteArrayOf(1, 2, 3, 0))
        }
        out.write("data".toByteArray()); out.write(le(samples.size * 2, 4)); out.write(data.array())
        return out.toByteArray()
    }

    @Test
    fun `decodes 16 kHz mono PCM, skipping other chunks`() {
        val samples = shortArrayOf(0, 16384, -32768, 32767)
        val expected = floatArrayOf(0f, 0.5f, -1f, 32767 / 32768f)
        assertArrayEquals(expected, Wav16k.decode(wav(samples)), 0f)
        assertArrayEquals(expected, Wav16k.decode(wav(samples, extraChunk = true)), 0f)
    }

    @Test
    fun `rejects other formats`() {
        assertThrows(IllegalArgumentException::class.java) { Wav16k.decode(wav(shortArrayOf(1), rate = 24_000)) }
        assertThrows(IllegalArgumentException::class.java) { Wav16k.decode(wav(shortArrayOf(1, 2), channels = 2)) }
        assertThrows(IllegalArgumentException::class.java) { Wav16k.decode(ByteArray(40)) }
    }

    @Test
    fun `speech ends after the last frame within 35 dB of the loudest`() {
        val audio = FloatArray(16_000) // 1 s
        for (i in 3_200 until 8_000) audio[i] = if (i % 2 == 0) 0.5f else -0.5f // loud: 0.2 s to 0.5 s
        for (i in 8_000 until 9_600) audio[i] = if (i % 2 == 0) 0.001f else -0.001f // -54 dB below it: not speech
        assertEquals(8_000, speechEndSample(audio))
        for (i in 8_000 until 9_600) audio[i] = if (i % 2 == 0) 0.02f else -0.02f // -28 dB: still speech
        assertEquals(9_600, speechEndSample(audio))
    }

    @Test
    fun `the paced mic delivers each hop when a real mic would have recorded it`() = runTest {
        val clip = FloatArray(4_000) { 0.25f }
        val mic = PacedClipMic(clip, leadSamples = 1_600, tailSamples = 1_000, nowNanos = { currentTime * 1_000_000L })
        val arrivals = mutableListOf<Long>()
        val hops = mutableListOf<FloatArray>()
        mic.hops.collect {
            arrivals += currentTime
            hops += it
        }
        // 1600 + 4000 + 1000 = 6600 samples: hops end at 100, 200, 300, 400 ms and 412.5 ms.
        assertEquals(listOf(100L, 200L, 300L, 400L, 412L), arrivals)
        assertEquals(listOf(1_600, 1_600, 1_600, 1_600, 200), hops.map { it.size })
        assertEquals(0f, hops[0].max()) // lead silence
        assertEquals(0.25f, hops[1][0]) // the clip starts with hop 1
        assertEquals(0L, mic.startNanos)
    }

    @Test
    fun `close ends the paced mic early`() = runTest {
        val mic = PacedClipMic(FloatArray(16_000), 0, 0, nowNanos = { currentTime * 1_000_000L })
        val got = mutableListOf<FloatArray>()
        mic.hops.collect {
            got += it
            if (got.size == 2) mic.close()
        }
        assertEquals(2, got.size)
    }

    @Test
    fun `the recorder times the first partial from the onset and the Final from the end of speech`() = runTest {
        val clip = FloatArray(16_000) { if (it < 12_800) 0.3f else 0f } // speech ends at 0.8 s
        val recorder = SessionRecorder(clip, nowNanos = { currentTime * 1_000_000L })
        val events = flow {
            recorder.mic.hops.toList() // plays in virtual time: 0.5 s lead + 1 s clip + 3 s tail = 4.5 s
            emit(SttEvent.Ready)
            delay(100)
            emit(SttEvent.SpeechStarted) // 4.6 s
            delay(700)
            emit(SttEvent.Partial("hel")) // 5.3 s
            delay(300)
            emit(SttEvent.Partial("hello"))
            emit(SttEvent.Final("Hello.", nearSilent = false)) // 5.6 s
        }
        val t = recorder.record(events)
        assertEquals(700L, t.firstPartialMs)
        // Speech ended at 0.5 s lead + 0.8 s = 1.3 s after the mic started.
        assertEquals(5_600L - 1_300L, t.endToFinalMs)
        assertEquals(2, t.partials)
        assertEquals(1, t.finals)
        assertEquals("Hello.", t.text)
        assertEquals(1_000L, t.audioMs)
        assertNull(t.error)
    }

    @Test
    fun `no partial or Final leaves the latencies null`() = runTest {
        val recorder = SessionRecorder(FloatArray(1_600), nowNanos = { currentTime * 1_000_000L })
        val t = recorder.record(
            flow {
                recorder.mic.hops.toList()
                emit(SttEvent.NoSpeech)
            },
        )
        assertNull(t.firstPartialMs)
        assertNull(t.endToFinalMs)
    }

    @Test
    fun `median of odd and even lists`() {
        assertEquals(2.0, median(listOf(3.0, 1.0, 2.0))!!, 0.0)
        assertEquals(2.5, median(listOf(4.0, 1.0, 2.0, 3.0))!!, 0.0)
        assertNull(median(emptyList()))
    }
}
