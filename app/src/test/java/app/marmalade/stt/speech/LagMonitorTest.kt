package app.marmalade.stt.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LagMonitorTest {

    /** Feeds (captured, processed) pairs; returns every event, in order. */
    private fun LagMonitor.feed(steps: List<Pair<Int, Int>>): List<SttEvent> = steps.flatMap { (c, p) -> update(c, p) }

    @Test
    fun `a session keeping up reports nothing`() {
        val m = LagMonitor()
        assertEquals(emptyList<SttEvent>(), m.feed((1..300).map { it to it - 10 })) // 1 s behind
        assertFalse(m.catchingUp)
        assertEquals(1.0f, m.maxLagSeconds, 1e-4f)
    }

    @Test
    fun `behind by more than 1_5 s it catches up until under 0_5 s, reported after 1 s`() {
        val m = LagMonitor()
        assertEquals(emptyList<SttEvent>(), m.update(100, 84)) // 1.6 s
        assertTrue(m.catchingUp)
        assertEquals(emptyList<SttEvent>(), m.update(105, 90)) // 0.5 s later
        assertEquals(listOf(SttEvent.CatchingUp(true)), m.update(110, 94)) // 1 s later
        assertEquals(emptyList<SttEvent>(), m.update(111, 101)) // 1.0 s behind: still catching up
        assertEquals(listOf(SttEvent.CatchingUp(false)), m.update(112, 108)) // 0.4 s
        assertFalse(m.catchingUp)
    }

    @Test
    fun `one long decode drained at once catches up without telling the UI`() {
        val m = LagMonitor()
        assertEquals(emptyList<SttEvent>(), m.update(100, 70))
        assertTrue(m.catchingUp)
        assertEquals(emptyList<SttEvent>(), m.update(101, 100))
        assertFalse(m.catchingUp)
        assertEquals(3.0f, m.maxLagSeconds, 1e-4f)
    }

    @Test
    fun `an engine at half real time is too slow, reported once`() {
        val m = LagMonitor()
        val events = m.feed((1..600).map { it to it / 2 })
        assertEquals(listOf(SttEvent.CatchingUp(true), SttEvent.TooSlow), events)
        // Catching up began at 3.1 s captured (1.6 s behind); too slow 10 s later.
        assertTrue(m.maxLagSeconds > 29f)
    }

    @Test
    fun `behind but not falling further behind is not too slow`() {
        val m = LagMonitor()
        // 2 s behind for 20 s: slow to start, but keeping real time since.
        val events = m.feed((1..220).map { it to (it - 20).coerceAtLeast(0) })
        assertEquals(listOf(SttEvent.CatchingUp(true)), events)
    }

    @Test
    fun `a lag that recovers within 10 s is not too slow`() {
        val m = LagMonitor()
        val events = m.feed(listOf(100 to 70, 110 to 80, 150 to 149, 300 to 250, 310 to 260, 320 to 319))
        assertEquals(
            listOf(SttEvent.CatchingUp(true), SttEvent.CatchingUp(false), SttEvent.CatchingUp(true), SttEvent.CatchingUp(false)),
            events,
        )
    }

    @Test
    fun `the audio kept during a long model load is a backlog, never too slow while it shrinks (#78)`() {
        // micSession counts an EarlyMic's backlog as captured from the start:
        // 10 s kept while the model loaded, worked off at three hops per hop of real time.
        val m = LagMonitor()
        var captured = 100
        var processed = 0
        val events = mutableListOf<SttEvent>()
        repeat(80) { // 8 s of real time
            processed = minOf(captured, processed + 3)
            events += m.update(captured, processed)
            captured++
        }
        assertEquals(listOf(SttEvent.CatchingUp(true), SttEvent.CatchingUp(false)), events)
        assertFalse(m.catchingUp)
    }

    @Test
    fun `a backlog drained within a second never reaches the UI`() {
        val m = LagMonitor()
        // 3 s kept while the model loaded; the VAD works through it in a few hops.
        assertEquals(emptyList<SttEvent>(), m.feed(listOf(30 to 1, 31 to 12, 32 to 24, 33 to 33)))
        assertFalse(m.catchingUp)
    }
}
