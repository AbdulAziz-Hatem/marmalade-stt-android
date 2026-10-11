package app.marmalade.stt.benchmark

import app.marmalade.stt.speech.FakeMic
import app.marmalade.stt.speech.HOP
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.MicCapture
import app.marmalade.stt.speech.MicCaptureException
import app.marmalade.stt.speech.TEST_TIMEOUT
import app.marmalade.stt.speech.NearSilence
import app.marmalade.stt.speech.loudHop
import app.marmalade.stt.speech.quietHop
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TakeRecorderTest {

    /** A mic that yields [hop]-filled hops as fast as they are read, for ever, until closed. */
    private class EndlessMic(
        private val hop: FloatArray,
        override val hasPlatformAgc: Boolean = true,
    ) : MicCapture {
        @Volatile
        var closed = false
        var hopsRead = 0

        override val hops: Flow<FloatArray> = flow {
            while (!closed) {
                hopsRead++
                emit(hop.copyOf())
            }
        }

        override fun close() {
            closed = true
        }
    }

    private fun record(recorder: TakeRecorder, onProgress: (Float, Float) -> Unit = { _, _ -> }) =
        runBlocking { withTimeout(TEST_TIMEOUT) { recorder.record(onProgress) } }.samples

    @Test
    fun `a take is capped at 30 seconds of samples and the mic is closed`() {
        val mic = EndlessMic(loudHop)
        val elapsed = mutableListOf<Float>()
        val take = record(TakeRecorder({ _, _ -> mic })) { _, e -> elapsed += e }

        assertEquals(TakeRecorder.MAX_TAKE_SECONDS * MIC_SAMPLE_RATE, take.size)
        // Stops on the hop that fills the take, without reading another.
        assertEquals(TakeRecorder.MAX_TAKE_SECONDS * MIC_SAMPLE_RATE / HOP, mic.hopsRead)
        assertTrue(mic.closed)
        assertEquals(TakeRecorder.MAX_TAKE_SECONDS.toFloat(), elapsed.last(), 1e-3f)
    }

    @Test
    fun `a partial last hop is cut to the cap`() {
        val mic = EndlessMic(loudHop)
        val take = record(TakeRecorder({ _, _ -> mic }, maxSamples = 2 * HOP + 100))
        assertEquals(2 * HOP + 100, take.size)
        assertEquals(3, mic.hopsRead)
    }

    @Test
    fun `stop ends the take early with what was heard`() {
        lateinit var recorder: TakeRecorder
        val mic = FakeMic(script = List(5) { loudHop }, afterScript = { recorder.stop() })
        recorder = TakeRecorder({ _, _ -> mic })
        val take = record(recorder)

        // The five scripted hops; the hop read after stop() is dropped.
        assertEquals(5 * HOP, take.size)
        assertTrue(mic.closed)
    }

    @Test
    fun `the mic is opened like a live session and closed when it fails`() {
        var opened: Pair<Int, Boolean>? = null
        val mic = FakeMic(script = listOf(loudHop), failAfterScript = MicCaptureException("gone"))
        val recorder = TakeRecorder({ hop, effects ->
            opened = hop to effects
            mic
        })
        try {
            record(recorder)
            fail("expected the mic failure")
        } catch (e: MicCaptureException) {
            assertEquals("gone", e.message)
        }
        assertEquals(HOP to true, opened)
        assertTrue(mic.closed)
    }

    @Test
    fun `hops are high-passed like the live session, so a DC offset decays`() {
        val dc = FloatArray(HOP) { 0.5f }
        val mic = EndlessMic(dc)
        val take = record(TakeRecorder({ _, _ -> mic }, maxSamples = 5 * HOP))

        // The raw input is a constant 0.5; after the 100 Hz high-pass the
        // tail (half a second in) is essentially zero.
        assertFalse(take.all { it == 0.5f })
        val tail = take.copyOfRange(take.size - HOP, take.size)
        assertTrue("tail should be ~0, max was ${tail.maxOf { abs(it) }}", tail.all { abs(it) < 1e-3f })
    }

    @Test
    fun `progress reports the level in dBFS and the seconds so far`() {
        val mic = EndlessMic(loudHop)
        val progress = mutableListOf<Pair<Float, Float>>()
        record(TakeRecorder({ _, _ -> mic }, maxSamples = 3 * HOP)) { db, e -> progress += db to e }

        assertEquals(listOf(0.1f, 0.2f, 0.3f), progress.map { it.second })
        // The 8 kHz square at 0.5 passes the high-pass: about -6 dBFS.
        progress.forEach { assertEquals(-6f, it.first, 0.5f) }
    }

    @Test
    fun `without a platform AGC the take gets the live session's soft AGC and says so`() {
        fun take(platformAgc: Boolean) = runBlocking {
            withTimeout(TEST_TIMEOUT) {
                TakeRecorder({ _, _ -> EndlessMic(quietHop, hasPlatformAgc = platformAgc) }, maxSamples = 3 * HOP)
                    .record { _, _ -> }
            }
        }
        val plain = take(platformAgc = true)
        val gained = take(platformAgc = false)

        assertFalse(plain.softAgc)
        assertTrue(gained.softAgc)
        // A -60 dBFS hop at mic open gets the full +30 dB, as in SoftAgcTest's session case.
        val first = { t: RecordedTake -> NearSilence.rmsDb(t.samples.copyOfRange(0, HOP)) }
        assertEquals(30f, first(gained) - first(plain), 0.5f)
    }
}
