package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cancelling a session mid-decode (#49): a native decode doesn't see
 * coroutine cancellation, so the session stops it through
 * [OfflineTranscriber.cancel], and the engine is free for the next listen
 * at once instead of answering BUSY until a runaway decode ends.
 */
class DecodeCancelTest {

    /** A decode that only ends when cancelled, like a Whisper runaway; the real ones run on real threads. */
    private class RunawayTranscriber : OfflineTranscriber {
        val started = CountDownLatch(1)
        private val stopped = CountDownLatch(1)
        val calls = AtomicInteger()
        val cancels = AtomicInteger()

        override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean): String {
            calls.incrementAndGet()
            started.countDown()
            check(stopped.await(60, TimeUnit.SECONDS)) { "the decode was never cancelled" }
            return "loop loop loop"
        }

        override fun cancel() {
            cancels.incrementAndGet()
            stopped.countDown()
        }

        override fun release() = Unit
    }

    /** One utterance of 0.3 s, then the mic's endless silence: the session decodes once and keeps listening. */
    private fun oneSegmentVad() = FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(3 * HOP))

    @Test
    fun `cancelling the collector stops the decode in progress and starts no other`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = RunawayTranscriber()
        val session = StreamingSession(transcriber, oneSegmentVad(), SessionOptions(), openMic = { _, _ -> FakeMic(List(4) { loudHop }) })
        val job = launch(Dispatchers.Default) { session.run().collect {} }
        withContext(Dispatchers.IO) { assertTrue(transcriber.started.await(5, TimeUnit.SECONDS)) }

        val cancelledAt = System.nanoTime()
        job.cancelAndJoin()

        assertTrue("took ${(System.nanoTime() - cancelledAt) / 1_000_000} ms", System.nanoTime() - cancelledAt < 2_000_000_000L)
        assertEquals(1, transcriber.cancels.get())
        assertEquals(1, transcriber.calls.get())
    }

    @Test
    fun `a cancel that lands before the decode's stream exists is not missed`() = runTest(timeout = TEST_TIMEOUT) {
        // The session has checked its flag and called transcribe, but the
        // stream doesn't exist yet when the canceller runs: its cancel()
        // finds nothing to stop. The flag the transcriber reads once the
        // stream exists (InFlightDecode.begin) must already be set then.
        val entered = CountDownLatch(1)
        val cancelCalled = CountDownLatch(1)
        val flagWhenStreamExists = AtomicInteger(-1)
        val transcriber = object : OfflineTranscriber {
            override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean): String {
                entered.countDown()
                check(cancelCalled.await(5, TimeUnit.SECONDS)) { "the canceller never ran" }
                flagWhenStreamExists.set(if (isCancelled()) 1 else 0)
                return ""
            }
            override fun cancel() = cancelCalled.countDown()
            override fun release() = Unit
        }
        val session = StreamingSession(transcriber, oneSegmentVad(), SessionOptions(), openMic = { _, _ -> FakeMic(List(4) { loudHop }) })
        val job = launch(Dispatchers.Default) { session.run().collect {} }
        withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }

        job.cancelAndJoin()

        assertEquals(1, flagWhenStreamExists.get())
    }

    @Test
    fun `a session that ends normally cancels nothing`() = runTest(timeout = TEST_TIMEOUT) {
        val cancels = AtomicInteger()
        val transcriber = object : OfflineTranscriber {
            override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean) = "hello"
            override fun cancel() {
                cancels.incrementAndGet()
            }
            override fun release() = Unit
        }
        lateinit var session: StreamingSession
        val mic = FakeMic(List(4) { loudHop }, afterScript = { session.stop() })
        session = StreamingSession(transcriber, oneSegmentVad(), SessionOptions(), openMic = { _, _ -> mic })

        val events = session.run().toList()

        assertTrue(events.contains(SttEvent.Final("hello", nearSilent = false)))
        assertEquals(0, cancels.get())
    }

    @Test
    fun `after a cancel mid-decode the engine takes the next listen instead of answering BUSY`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = RunawayTranscriber()
        val mics = AtomicInteger()
        lateinit var engine: SherpaSttEngine<OfflineTranscriber>
        engine = SherpaSttEngine(
            descriptor = SttEngineCatalog.default,
            loadModel = { transcriber },
            createVad = { oneSegmentVad() },
            // The first listen hears an utterance; the second stops as soon as its mic opens.
            openMic = { _, _ -> if (mics.getAndIncrement() == 0) FakeMic(List(4) { loudHop }) else FakeMic(afterScript = { engine.stop() }) },
            newSession = { model, vad, options, openMic, _ -> StreamingSession(model, vad, options, openMic) },
        )
        val first = launch(Dispatchers.Default) { engine.listen(SessionOptions()).collect {} }
        withContext(Dispatchers.IO) { assertTrue(transcriber.started.await(5, TimeUnit.SECONDS)) }

        first.cancelAndJoin()
        val events = withContext(Dispatchers.Default) { engine.listen(SessionOptions()).toList() }

        assertTrue("$events", events.none { it is SttEvent.Error })
        assertEquals(SttEvent.NoSpeech, events.last())
    }
}
