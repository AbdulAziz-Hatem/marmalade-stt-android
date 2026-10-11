package app.marmalade.stt.speech

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catch-up mode (#30) in both sessions. The first decode blocks until the
 * mic has queued its whole script (and stopped), so the session is then
 * about [HOPS] hops behind (or already was, if the mic's producer ran ahead
 * of it). Whether the UI is told depends on that race; [LagMonitorTest]
 * covers the reporting.
 */
class CatchUpTest {

    private companion object {
        const val HOPS = 40
    }

    private fun List<SttEvent>.shown() = filterNot { it is SttEvent.Level }

    /** StreamingSession on [HOPS] loud hops; returns (events, how many decodes ran). */
    private suspend fun offline(catchUp: Boolean): Pair<List<SttEvent>, Int> {
        val queued = CountDownLatch(1)
        var calls = 0
        val transcriber = FakeTranscriber { _ ->
            if (calls++ == 0) queued.await(5, TimeUnit.SECONDS)
            "words"
        }
        lateinit var s: StreamingSession
        val mic = FakeMic(List(HOPS) { loudHop }, afterScript = {
            queued.countDown()
            s.stop()
        })
        s = StreamingSession(
            transcriber, FakeVad(speechFromSample = 1), options = SessionOptions(), openMic = { _, _ -> mic },
            nowMs = fastClock(), catchUp = catchUp,
        )
        return s.run().toList().shown() to calls
    }

    @Test
    fun `behind the mic, StreamingSession skips partials until it has caught up`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, decodes) = offline(catchUp = true)
        // At most the first partial, a few in the last half second once caught up, and the Final.
        // Usually 5; a loaded machine can let the session catch up a hop or two earlier (6 seen
        // with a sibling's emulator running), so the bound only has to stay far below the ≥ 15 without.
        assertTrue("$decodes decodes: $events", decodes <= 8)
        assertEquals(SttEvent.Final("words", nearSilent = false), events.last())

        // Without catch-up the fast clock opens the partial gate every other hop.
        val (_, without) = offline(catchUp = false)
        assertTrue("$without decodes without catch-up", without >= 15)
    }

    /** A stream whose first decode blocks until [queued] opens; its text is the audio fed so far, in hops. */
    private class BlockingStream(private val queued: CountDownLatch) : RecognizerStream {
        var fed = 0
        var decodes = 0

        override fun acceptWaveform(samples: FloatArray) {
            fed += samples.size
        }

        override fun decode() {
            if (decodes++ == 0) queued.await(5, TimeUnit.SECONDS)
        }

        override fun text() = "h${fed / HOP}"
        override fun inputFinished() = Unit
        override fun release() = Unit
    }

    @Test
    fun `behind the mic, OnlineSession decodes the backlog at once and shows one partial for it`() = runTest(timeout = TEST_TIMEOUT) {
        val queued = CountDownLatch(1)
        val stream = BlockingStream(queued)
        val recognizer = object : StreamingRecognizer {
            override fun createStream() = stream
            override fun release() = Unit
        }
        lateinit var s: OnlineSession
        val mic = FakeMic(List(HOPS) { loudHop }, afterScript = {
            queued.countDown()
            s.stop()
        })
        s = OnlineSession(recognizer, FakeVad(speechFromSample = 1), options = SessionOptions()) { _, _ -> mic }

        val events = s.run().toList().shown()

        val partials = events.filterIsInstance<SttEvent.Partial>()
        assertEquals("the first hop's partial, then one for the backlog: $events", 2, partials.size)
        // The first hop, the drained backlog, and the flush on stop.
        assertEquals(3, stream.decodes)
        assertTrue(events.last() is SttEvent.Final)
    }
}
