package app.marmalade.stt.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedProbeTest {

    /** Counts windows; each window advances [clock] by 1 ms, or [slowMs] while [slow] says so. */
    private class CountingVad(private val clock: LongArray, private val slow: (window: Int) -> Boolean = { false }) : SpeechVad {
        var windows = 0
        var resets = 0
        override fun acceptWaveform(samples: FloatArray) {
            clock[0] += if (slow(windows)) 3_000_000 else 1_000_000
            windows++
        }
        override fun isSpeechDetected() = false
        override fun empty() = true
        override fun front(): VadSegment = error("no segments")
        override fun pop() = Unit
        override fun flush() = Unit
        override fun reset() {
            resets++
        }
        override fun release() = Unit
    }

    @Test
    fun `a warm-up pass, then the fastest of the timed runs`() {
        val clock = longArrayOf(0)
        val vad = CountingVad(clock)
        val clip = FloatArray(StreamingSession.VAD_WINDOW_SIZE * 10 + 100) // 10 whole windows
        val ms = SpeedProbe.time(vad, clip, nanoTime = { clock[0] })
        assertEquals(10L * SpeedProbe.PASSES, ms)
        assertEquals(10 * (1 + SpeedProbe.RUNS * SpeedProbe.PASSES), vad.windows)
        // Reset before every pass and once at the end.
        assertEquals(1 + SpeedProbe.RUNS * SpeedProbe.PASSES + 1, vad.resets)
    }

    @Test
    fun `a run slowed by other work doesn't count`() {
        val clock = longArrayOf(0)
        val perRun = 10 * SpeedProbe.PASSES
        // Every timed run but the last is 3× slower (after the 10-window warm-up).
        val vad = CountingVad(clock) { w -> w >= 10 && w < 10 + perRun * (SpeedProbe.RUNS - 1) }
        val clip = FloatArray(StreamingSession.VAD_WINDOW_SIZE * 10)
        assertEquals(perRun.toLong(), SpeedProbe.time(vad, clip, nanoTime = { clock[0] }))
    }
}
