package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineCatalog
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SessionArbiter] (#90, "your own use wins"): the user's own session takes
 * the engine from another app's, never the other way round. Run against a
 * real [SherpaSttEngine] with a fake model, VAD and mic.
 */
class SessionArbiterTest {

    private val loads = AtomicInteger()
    private val arbiter = SessionArbiter()

    private fun engine(mic: (SttEngine) -> MicCapture): SherpaSttEngine<OfflineTranscriber> {
        lateinit var engine: SherpaSttEngine<OfflineTranscriber>
        engine = SherpaSttEngine(
            descriptor = SttEngineCatalog.default,
            loadModel = {
                loads.incrementAndGet()
                FakeTranscriber(listOf("unused"))
            },
            createVad = { FakeVad(speechFromSample = Int.MAX_VALUE) },
            openMic = { _, _ -> mic(engine) },
            newSession = { model, vad, options, openMic, _ -> StreamingSession(model, vad, options, openMic) },
        )
        return engine
    }

    /** Another app's session, collected in the background; [ready] completes once it runs. */
    private fun kotlinx.coroutines.CoroutineScope.appSession(engine: SttEngine, options: SessionOptions = SessionOptions()) =
        CompletableDeferred<Unit>().let { ready ->
            ready to async(Dispatchers.Default) {
                arbiter.session(preemptible = true, engine.listen(options))
                    .onEach { if (it == SttEvent.Ready) ready.complete(Unit) }
                    .toList()
            }
        }

    /** The user's own session, the way SpeechEngineManager.listen runs it; its mic stops itself. */
    private suspend fun ownSession(engine: SttEngine): List<SttEvent> {
        engine.keep()
        arbiter.makeRoom(preemptible = false)
        return arbiter.session(preemptible = false, engine.listen(SessionOptions())).toList()
    }

    @Test
    fun `the user's own session pre-empts another app's mic session, without a model reload`() = runTest(timeout = TEST_TIMEOUT) {
        val appMic = FakeMic() // listens until closed
        var own = false
        val engine = engine { e -> if (own) FakeMic(afterScript = { e.stop() }) else appMic }
        val (ready, app) = appSession(engine)
        ready.await()

        own = true
        val mine = ownSession(engine)

        // The user's session ran from start to end: no busy.
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), mine.filterNot { it is SttEvent.Level })
        // The app's ended busy, its mic closed.
        assertEquals(SttEvent.Error(SttErrorKind.BUSY, SessionArbiter.PREEMPTED_MESSAGE), app.await().last())
        assertTrue(appMic.closed)
        assertEquals(1, loads.get())
        assertTrue(engine.isLoaded)
    }

    @Test
    fun `a release deferred behind the pre-empted session doesn't unload the model the user's session takes over`() = runTest(timeout = TEST_TIMEOUT) {
        var own = false
        val engine = engine { e -> if (own) FakeMic(afterScript = { e.stop() }) else FakeMic() }
        val (ready, app) = appSession(engine)
        ready.await()
        // The user left Dictate while the app listened: its hold closed, the release waits for the session.
        engine.release()

        own = true
        ownSession(engine)

        app.await()
        assertEquals(1, loads.get())
        assertTrue(engine.isLoaded)
    }

    /** A caller's pipe that never sends anything: a read blocks until it is closed. */
    private class SilentPipe : InputStream() {
        val closed = CountDownLatch(1)
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            // Sends a little silence now and then, like a live caller.
            if (closed.await(20, TimeUnit.MILLISECONDS)) throw IOException("closed")
            b.fill(0, off, off + minOf(len, 640))
            return minOf(len, 640)
        }

        override fun close() = closed.countDown()
    }

    @Test
    fun `pre-empting another app's stream session closes its stream`() = runTest(timeout = TEST_TIMEOUT) {
        val pipe = SilentPipe()
        val engine = engine { e -> FakeMic(afterScript = { e.stop() }) }
        val streamOptions = SessionOptions(audioInput = { hop -> StreamCapture(pipe, PcmFormat(), hop) })
        val (ready, app) = appSession(engine, streamOptions)
        ready.await()

        val mine = ownSession(engine)

        assertFalse(mine.any { it is SttEvent.Error })
        assertEquals(SttEvent.Error(SttErrorKind.BUSY, SessionArbiter.PREEMPTED_MESSAGE), app.await().last())
        assertEquals(0L, pipe.closed.count)
    }

    @Test
    fun `another app's request during the user's own session is still turned away busy`() = runTest(timeout = TEST_TIMEOUT) {
        val ownMic = FakeMic()
        val engine = engine { ownMic }
        val ownReady = CompletableDeferred<Unit>()
        val mine = async(Dispatchers.Default) {
            arbiter.makeRoom(preemptible = false)
            arbiter.session(preemptible = false, engine.listen(SessionOptions()))
                .onEach { if (it == SttEvent.Ready) ownReady.complete(Unit) }
                .toList()
        }
        ownReady.await()

        arbiter.makeRoom(preemptible = true) // nothing to make room for
        val app = arbiter.session(preemptible = true, engine.listen(SessionOptions())).toList()

        assertEquals(listOf(SttEvent.Error(SttErrorKind.BUSY, SherpaSttEngine.BUSY_MESSAGE)), app)
        assertFalse(ownMic.closed)
        engine.stop()
        assertFalse(mine.await().any { it is SttEvent.Error })
    }

    @Test
    fun `with nothing running the user's session starts at once`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine { e -> FakeMic(afterScript = { e.stop() }) }
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), ownSession(engine).filterNot { it is SttEvent.Level })
    }

    @Test
    fun `another app's session that ended on its own isn't pre-empted afterwards`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine { e -> FakeMic(afterScript = { e.stop() }) }
        val app = arbiter.session(preemptible = true, engine.listen(SessionOptions())).toList()
        assertEquals(SttEvent.NoSpeech, app.last())
        ownSession(engine) // returns: nothing left to wait for
    }

    @Test
    fun `another app's request routed to another engine is turned away before it picks one`() = runTest(timeout = TEST_TIMEOUT) {
        // The user dictates on engine A; the app's language would route it to engine B.
        val userMic = FakeMic()
        val engineA = engine { userMic }
        val bMics = AtomicInteger()
        val engineB = engine { bMics.incrementAndGet(); FakeMic() }
        val userReady = CompletableDeferred<Unit>()
        val mine = async(Dispatchers.Default) {
            arbiter.userStarting()
            try {
                arbiter.makeRoom(preemptible = false)
                arbiter.session(preemptible = false, engineA.listen(SessionOptions()))
                    .onEach { if (it == SttEvent.Ready) userReady.complete(Unit) }
                    .toList()
            } finally {
                arbiter.userEnded()
            }
        }
        userReady.await()

        // SpeechEngineManager.listen(preemptible = true) checks this before choosing an engine.
        assertTrue(arbiter.userSessionRunning())
        assertEquals(0, bMics.get())
        assertFalse(userMic.closed)

        // The user's stop reaches the user's session, which ends normally.
        engineA.stop()
        assertFalse(mine.await().any { it is SttEvent.Error })
        assertFalse(arbiter.userSessionRunning())
    }
    @Test
    fun `a pre-empted session drops its speculative decode`() = runTest(timeout = TEST_TIMEOUT) {
        // Another app's Tap stop session (#91): its early VAD has ended the
        // utterance, and the speculative decode of it is running when the
        // user's own session starts.
        val decoding = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val calls = AtomicInteger()
        val cancels = AtomicInteger()
        val transcriber = object : OfflineTranscriber {
            override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean): String {
                calls.incrementAndGet()
                decoding.countDown()
                check(stopped.await(60, TimeUnit.SECONDS)) { "the decode was never cancelled" }
                return "speculative"
            }

            override fun cancel() {
                cancels.incrementAndGet()
                stopped.countDown()
            }

            override fun release() = Unit
        }
        var own = false
        lateinit var engine: SherpaSttEngine<OfflineTranscriber>
        engine = SherpaSttEngine(
            descriptor = SttEngineCatalog.default,
            loadModel = { transcriber },
            // The main VAD (1.2 s) is still in the utterance; the early one (~0.6 s) has closed it.
            createVad = { minSilence ->
                if (minSilence < 1f) FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP, segmentsAt = listOf(5 * HOP))
                else FakeVad(speechFromSample = 1, speechUntilSample = 3 * HOP)
            },
            openMic = { _, _ -> if (own) FakeMic(afterScript = { engine.stop() }) else FakeMic(List(3) { loudHop }) },
            newSession = { model, vad, options, openMic, earlyVad ->
                StreamingSession(model, vad, options, openMic, livePartials = false, earlyVad = earlyVad)
            },
        )
        val (_, app) = appSession(engine, SessionOptions(end = EndOfDictation.TAP_STOP))
        withContext(Dispatchers.IO) { assertTrue(decoding.await(5, TimeUnit.SECONDS)) }

        own = true
        val mine = ownSession(engine)
        val theirs = app.await()

        assertTrue("$mine", mine.none { it is SttEvent.Error })
        assertEquals(SttEvent.Error(SttErrorKind.BUSY, SessionArbiter.PREEMPTED_MESSAGE), theirs.last())
        assertTrue("$theirs", theirs.none { it is SttEvent.Final })
        assertEquals(1, cancels.get())
        assertEquals(1, calls.get())
    }
}
