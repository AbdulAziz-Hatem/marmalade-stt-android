package app.marmalade.stt.speech

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [EarlyMic]: the mic's audio kept while the model loads (#78), and a session reading it. */
@OptIn(ExperimentalCoroutinesApi::class)
class EarlyMicTest {

    /** A hop tagged with [n] in its first sample, so order can be checked. */
    private fun hop(n: Int) = FloatArray(HOP) { if (it == 0) n.toFloat() else 0.5f }

    private fun List<FloatArray>.tags() = map { it[0].toInt() }

    /** Plays [script], then whatever the test sends to [live], until closed (as a real mic). */
    private class ScriptMic(
        private val script: List<FloatArray>,
        private val failure: Exception? = null,
        override val hasPlatformAgc: Boolean = true,
    ) : MicCapture {
        val live = Channel<FloatArray>(Channel.UNLIMITED)

        @Volatile
        var closed = false
            private set

        override val hops: Flow<FloatArray> = flow {
            for (h in script) emit(h)
            failure?.let { throw it }
            for (h in live) emit(h)
        }

        override fun close() {
            closed = true
            live.close()
        }
    }

    @Test
    fun `the hops kept during the load come first, then live ones, and a stop ends them once all are read`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = ScriptMic(listOf(hop(1), hop(2), hop(3)))
        val early = EarlyMic(mic)
        launch { early.record() }
        runCurrent() // the model is still loading: three hops wait

        val got = async { early.hops.toList() }
        runCurrent()
        assertEquals(3, early.backlogHops)
        mic.live.send(hop(4))
        runCurrent()
        early.stopRecording()

        assertEquals(listOf(1, 2, 3, 4), got.await().tags())
        assertTrue(mic.closed)
    }

    @Test
    fun `a stop before the session reads anything keeps every hop`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = ScriptMic(listOf(hop(1), hop(2)))
        val early = EarlyMic(mic)
        launch { early.record() }
        runCurrent()

        early.stopRecording() // the user tapped stop while the model loaded
        runCurrent()

        assertEquals(listOf(1, 2), early.hops.toList().tags())
    }

    @Test
    fun `levels are reported only for the hops captured before the session reads them`() = runTest(timeout = TEST_TIMEOUT) {
        val levels = mutableListOf<Float>()
        val mic = ScriptMic(listOf(loudHop, quietHop))
        val early = EarlyMic(mic, onLevel = { levels += it })
        launch { early.record() }
        runCurrent()
        assertEquals(listOf(NearSilence.rmsDb(loudHop), NearSilence.rmsDb(quietHop)), levels)

        val got = async { early.hops.toList() }
        runCurrent()
        mic.live.send(loudHop) // the session meters this one itself
        runCurrent()
        early.stopRecording()

        assertEquals(3, got.await().size)
        assertEquals(2, levels.size)
    }

    @Test
    fun `close drops what is kept and ends the hops at once`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = ScriptMic(listOf(hop(1), hop(2)))
        val early = EarlyMic(mic)
        launch { early.record() }
        runCurrent()

        early.close()

        assertTrue(early.hops.toList().isEmpty())
        assertTrue(mic.closed)
    }

    @Test
    fun `close ends a session waiting for the next hop`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = ScriptMic(listOf(hop(1)))
        val early = EarlyMic(mic)
        launch { early.record() }
        val got = async { early.hops.toList() }
        runCurrent()

        early.close()

        assertEquals(listOf(1), got.await().tags())
    }

    @Test
    fun `past the cap the oldest hops are dropped`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = ScriptMic(List(5) { hop(it + 1) })
        val early = EarlyMic(mic, maxQueuedHops = 2)
        launch { early.record() }
        runCurrent()
        early.stopRecording()
        runCurrent()

        assertEquals(listOf(4, 5), early.hops.toList().tags())
        assertEquals(2, early.backlogHops)
    }

    @Test
    fun `a mic failure comes after the hops kept before it`() = runTest(timeout = TEST_TIMEOUT) {
        val early = EarlyMic(ScriptMic(listOf(hop(1)), failure = MicCaptureException("Microphone read failed")))
        launch { early.record() }
        runCurrent()

        val got = mutableListOf<FloatArray>()
        val failure = runCatching { early.hops.collect { got += it } }.exceptionOrNull()

        assertEquals(listOf(1), got.tags())
        assertTrue("$failure", failure is MicCaptureException)
    }

    @Test
    fun `it reports the mic's gain control`() {
        assertFalse(EarlyMic(ScriptMic(emptyList(), hasPlatformAgc = false)).hasPlatformAgc)
        assertTrue(EarlyMic(ScriptMic(emptyList())).hasPlatformAgc)
    }

    @Test
    fun `a session sends no Level for the backlog and counts it as captured from the start`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = ScriptMic(List(5) { loudHop.copyOf() })
        val early = EarlyMic(mic)
        launch { early.record() }
        runCurrent() // five hops kept while the model loaded

        var capturedAtFirstHop = -1
        val indexes = mutableListOf<Int>()
        val events = micSession({ _, _ -> early }, isStopRequested = { false }) { hops, captured ->
            for (h in hops) {
                if (capturedAtFirstHop < 0) capturedAtFirstHop = captured()
                indexes += h.index
                when (h.index) {
                    4 -> mic.live.trySend(quietHop.copyOf()) // a live hop, after the backlog
                    5 -> early.stopRecording()
                }
            }
        }.toList()

        // The burst of five isn't five hops of lag: they were captured before the session started.
        assertEquals(5, capturedAtFirstHop)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), indexes)
        // Only the live hop's level: the backlog's went to the meter during the load.
        assertEquals(SttEvent.Ready, events.first())
        assertEquals(1, events.count { it is SttEvent.Level })
        assertEquals(2, events.size)
    }
}
