package app.marmalade.stt.ui.components

import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #63: the jar's frame schedule. A settled idle jar sleeps until the next blink edge
 * instead of drawing every frame; a moving one draws at about 30 fps at any refresh rate.
 */
class JarMascotTest {

    /**
     * Runs JarMascot's loop against a display refreshing at [hz]: each frame lands on
     * the first vsync after the loop wakes up. Returns the frame times, in ms since the
     * state began.
     */
    private fun frames(state: JarMascotState, hz: Int, untilMs: Long): List<Long> {
        val vsync = 1000.0 / hz
        val schedule = JarFrames(state)
        val out = mutableListOf<Long>()
        var wake = 0.0
        while (true) {
            val frame = (floor(wake / vsync + 1e-9) + 1) * vsync // 1e-9: k * vsync / vsync may be k - ε
            val elapsed = frame.toLong()
            if (elapsed > untilMs) return out
            val (drawn, sleep) = schedule.onFrame(elapsed)
            out += drawn
            wake = frame + sleep
        }
    }

    private fun List<Long>.countIn(fromMs: Long, toMs: Long) = count { it in fromMs until toMs }

    @Test
    fun `a moving jar draws about 30 frames a second at any refresh rate`() {
        for (state in listOf(JarMascotState.LISTENING, JarMascotState.THINKING)) {
            for (hz in listOf(60, 90, 120)) {
                val perSecond = frames(state, hz, 61_000).countIn(1_000, 61_000) / 60.0
                assertTrue("$state at $hz Hz: $perSecond fps", perSecond in 27.0..31.0)
            }
        }
    }

    @Test
    fun `an idle jar breathes at 30 fps, then draws only at blink edges`() {
        for (hz in listOf(60, 120)) {
            val f = frames(JarMascotState.IDLE, hz, 70_000)
            val breathing = f.countIn(1_000, 7_000) / 6.0
            assertTrue("breathing at $hz Hz: $breathing fps", breathing in 27.0..31.0)
            // After the breaths: one frame as the eyes close and one as they open, every 3.4 s.
            val settled = f.countIn(10_000, 70_000)
            assertTrue("settled at $hz Hz: $settled frames in 60 s", settled in 34..37)
        }
    }

    @Test
    fun `sleeps until the blink starts, then until it ends`() {
        assertEquals(3_270L, untilBlinkEdge(130L)) // eyes just opened: next blink at 3400
        assertEquals(3_000L, untilBlinkEdge(400L))
        assertEquals(130L, untilBlinkEdge(3_400L)) // blink starts: wake when it ends
        assertEquals(30L, untilBlinkEdge(3_500L))
        assertEquals(3_270L, untilBlinkEdge(3_530L))
    }

    @Test
    fun `never sleeps for zero`() {
        for (t in 0L..10_000L) {
            val wait = untilBlinkEdge(t)
            assertTrue("t=$t wait=$wait", wait in 1L..3_400L)
        }
    }
}
