package app.marmalade.stt.speech

import app.marmalade.stt.speech.turn.TurnDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SmartTurnGate]'s schedule on its own; the sessions' use of it is in EndOfDictationSessionTest. */
class SmartTurnGateTest {

    private val window = FloatArray(StreamingSession.VAD_WINDOW_SIZE)

    private fun SmartTurnGate.silence(samples: Int) {
        repeat(samples / window.size) { append(window) }
    }

    @Test
    fun `exactly the patience's threshold is not complete, above it is`() {
        for (patience in Patience.entries) {
            assertFalse(patience.name, SmartTurnGate(patience) { patience.threshold }.apply { start(window) }.propose(0))
            assertTrue(patience.name, SmartTurnGate(patience) { patience.threshold + 0.01f }.apply { start(window) }.propose(0))
        }
    }

    @Test
    fun `each patience ends the turn at its own ceiling`() {
        for (patience in Patience.entries) {
            val gate = SmartTurnGate(patience) { 0f }
            gate.start(window)
            assertFalse(gate.propose(trailingSilence = 0))
            // The last VAD window before the ceiling, then the one that reaches it.
            val windows = (patience.ceilingSamples + window.size - 1) / window.size
            gate.silence((windows - 1) * window.size)
            assertFalse(patience.name, gate.poll())
            gate.silence(window.size)
            assertTrue(patience.name, gate.poll())
        }
    }

    @Test
    fun `Normal is the #64 setting, and patience grows from Quick to Relaxed`() {
        assertEquals(1.2f, Patience.NORMAL.proposeSeconds)
        assertEquals(0.7f, Patience.NORMAL.threshold)
        assertEquals(2.5f, Patience.NORMAL.ceilingSeconds)
        assertEquals(5 * MIC_SAMPLE_RATE / 2, Patience.NORMAL.ceilingSamples)
        assertEquals(0.5f, Patience.QUICK.proposeSeconds)
        assertEquals(1.5f, Patience.RELAXED.proposeSeconds)
        assertEquals(3.0f, Patience.RELAXED.ceilingSeconds)
        for ((less, more) in Patience.entries.zipWithNext()) {
            assertTrue("$less/$more propose", less.proposeSeconds <= more.proposeSeconds)
            assertTrue("$less/$more threshold", less.threshold <= more.threshold)
            assertTrue("$less/$more ceiling", less.ceilingSeconds <= more.ceilingSeconds)
        }
    }

    @Test
    fun `rechecks every half second of further silence, ends at the ceiling`() {
        val seen = mutableListOf<Int>()
        val gate = SmartTurnGate { seen += it.size; 0f }
        gate.start(FloatArray(MIC_SAMPLE_RATE))
        assertFalse(gate.propose(trailingSilence = 0))
        gate.silence(15 * window.size) // 7680 samples: not yet 0.5 s
        assertFalse(gate.poll())
        assertEquals(1, seen.size)
        gate.silence(window.size)
        assertFalse(gate.poll())
        assertEquals(2, seen.size)
        gate.silence(Patience.NORMAL.ceilingSamples)
        assertTrue(gate.poll())
    }

    @Test
    fun `the detector sees at most the last 8 s, trailing silence included`() {
        var last = 0
        val gate = SmartTurnGate { last = it.size; 0f }
        gate.start(FloatArray(10 * MIC_SAMPLE_RATE))
        gate.propose(0)
        assertEquals(SmartTurnGate.WINDOW_SAMPLES, last)
    }

    @Test
    fun `speech resuming stops the checks`() {
        var calls = 0
        val gate = SmartTurnGate { calls++; 0f }
        gate.start(window)
        gate.propose(0)
        gate.resume()
        gate.silence(Patience.NORMAL.ceilingSamples)
        assertFalse(gate.poll())
        assertEquals(1, calls)
    }

    @Test
    fun `a detector that throws falls back to the Pause setting's silence`() {
        val fallback = 38 * window.size // about 1.2 s
        val gate = SmartTurnGate(fallbackSilenceSamples = fallback) { error("broken model") }
        gate.start(window)
        assertFalse(gate.propose(0))
        gate.silence(fallback - window.size)
        assertFalse(gate.poll())
        gate.silence(window.size)
        assertTrue(gate.poll())
        // The next turn doesn't ask the broken detector again, and waits the same.
        gate.start(window)
        assertFalse(gate.propose(0))
        gate.silence(fallback)
        assertTrue(gate.poll())
    }
}
