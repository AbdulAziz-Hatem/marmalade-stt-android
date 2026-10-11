package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.turn.TurnDetector
import java.io.FileNotFoundException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SherpaSttEngine]'s lifecycle rules (one session at a time, deferred
 * release, VAD reuse, stop during load) through its internal constructor,
 * with fake model loaders, the offline [StreamingSession] and a fake mic. The
 * sherpa-backed loaders need the native library and are exercised on device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SherpaSttEngineTest {

    private val transcriber = FakeTranscriber(listOf("unused"))
    private val vadSilences: MutableList<Float> = Collections.synchronizedList(mutableListOf())
    private val vads: MutableList<FakeVad> = Collections.synchronizedList(mutableListOf())
    private val micOpens = AtomicInteger()

    private fun engine(
        loadTranscriber: () -> OfflineTranscriber = { transcriber },
        mic: (SttEngine) -> MicCapture = { FakeMic() },
        descriptor: SttEngineDescriptor = SttEngineCatalog.default,
        vad: () -> FakeVad = { FakeVad(speechFromSample = Int.MAX_VALUE) },
    ): SherpaSttEngine<OfflineTranscriber> {
        lateinit var engine: SherpaSttEngine<OfflineTranscriber>
        engine = SherpaSttEngine(
            descriptor = descriptor,
            loadModel = loadTranscriber,
            createVad = { silence ->
                vadSilences += silence
                vad().also { vads += it }
            },
            openMic = { _, _ ->
                micOpens.incrementAndGet()
                mic(engine)
            },
            newSession = { model, vad, options, openMic -> StreamingSession(model, vad, options, openMic) },
        )
        return engine
    }

    /** A mic that stops the engine as soon as it opens: each listen ends with NoSpeech. */
    private val selfStoppingMic: (SttEngine) -> MicCapture = { engine -> FakeMic(afterScript = { engine.stop() }) }

    @Test
    fun `a model load failure closes the mic and emits Error MODEL last`() = runTest(timeout = TEST_TIMEOUT) {
        val mic = FakeMic()
        val engine = engine(loadTranscriber = { throw FileNotFoundException("Model files missing for x") }, mic = { mic })

        val events = engine.listen(SessionOptions()).toList()

        assertEquals(SttEvent.Listening, events.first())
        assertEquals(SttEvent.Error(SttErrorKind.MODEL, "Model files missing for x"), events.last())
        assertTrue(events.drop(1).dropLast(1).all { it is SttEvent.Level })
        assertTrue(mic.closed)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `a mic that can't open is Error MIC, and no model is loaded`() = runTest(timeout = TEST_TIMEOUT) {
        val loads = AtomicInteger()
        var micWorks = false
        val engine = engine(
            loadTranscriber = { loads.incrementAndGet(); transcriber },
            mic = { e -> if (micWorks) FakeMic(afterScript = { e.stop() }) else throw MicCaptureException("Microphone permission not granted") },
        )

        val events = engine.listen(SessionOptions()).toList()

        assertEquals(listOf(SttEvent.Error(SttErrorKind.MIC, "Microphone permission not granted")), events)
        assertEquals(0, loads.get())
        assertFalse(engine.isLoaded)
        // The claim was given back: the next listen runs.
        micWorks = true
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), engine.listen(SessionOptions()).toList())
    }

    /** Hears "hello" in audio holding nearly all of the 1 s of loud hops spoken during the load. */
    private val hearsTheLoad = FakeTranscriber { samples -> if (samples.count { abs(it) > 0.1f } >= 15_000) "hello" else "" }

    @Test
    fun `words spoken while the model loads reach the session, and a stop during the load keeps them (#78)`() = runTest(timeout = TEST_TIMEOUT) {
        val loading = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val spoken = CountDownLatch(1)
        val mic = FakeMic(List(10) { loudHop }, afterScript = { spoken.countDown() })
        val engine = engine(
            loadTranscriber = {
                loading.countDown()
                finishLoad.await(5, TimeUnit.SECONDS)
                hearsTheLoad
            },
            mic = { mic },
            vad = { FakeVad(speechFromSample = 1) }, // speech from the start until the stop flushes it
        )
        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val job = launch { engine.listen(SessionOptions()).collect { events += it } }
        withContext(Dispatchers.IO) {
            assertTrue(loading.await(5, TimeUnit.SECONDS))
            assertTrue(spoken.await(5, TimeUnit.SECONDS))
        }

        engine.stop() // a short phrase, and stop before the model is ready
        runCurrent()
        assertEquals("the model is still loading", listOf(SttEvent.Listening), events.filterNot { it is SttEvent.Level })
        assertTrue("the meter moved during the load", events.count { it is SttEvent.Level } >= 10)
        assertTrue(mic.closed)

        finishLoad.countDown()
        job.join()
        assertEquals(
            listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("hello", nearSilent = false)),
            events.filterNot { it is SttEvent.Level || it is SttEvent.Partial },
        )
        // One level per hop: the session doesn't repeat the ones sent during the load.
        assertEquals(mic.emitted.get(), events.count { it is SttEvent.Level })
    }

    @Test
    fun `levels flow during the load and after it, once per hop`() = runTest(timeout = TEST_TIMEOUT) {
        val finishLoad = CountDownLatch(1)
        val spoken = CountDownLatch(1)
        val mic = FakeMic(List(5) { loudHop }, afterScript = { spoken.countDown() })
        val engine = engine(loadTranscriber = { finishLoad.await(5, TimeUnit.SECONDS); transcriber }, mic = { mic })
        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val ready = CompletableDeferred<Unit>()
        val job = launch {
            engine.listen(SessionOptions()).collect {
                events += it
                if (it == SttEvent.Ready) ready.complete(Unit)
            }
        }
        withContext(Dispatchers.IO) { assertTrue(spoken.await(5, TimeUnit.SECONDS)) }
        runCurrent()
        assertTrue(events.count { it is SttEvent.Level } >= 5)

        finishLoad.countDown()
        ready.await()
        // Live hops (silence every 5 ms) after the backlog.
        val atReady = mic.emitted.get()
        withContext(Dispatchers.IO) { while (mic.emitted.get() < atReady + 20) Thread.sleep(5) }
        engine.stop()
        job.join()

        assertEquals(mic.emitted.get(), events.count { it is SttEvent.Level })
        assertEquals(SttEvent.NoSpeech, events.last())
    }

    @Test
    fun `cancelling during the load closes the mic and emits no Error`() = runTest(timeout = TEST_TIMEOUT) {
        val loading = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val mic = FakeMic()
        val engine = engine(loadTranscriber = {
            loading.countDown()
            finishLoad.await(5, TimeUnit.SECONDS)
            transcriber
        }, mic = { mic })
        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val job = launch { engine.listen(SessionOptions()).collect { events += it } }
        withContext(Dispatchers.IO) { assertTrue(loading.await(5, TimeUnit.SECONDS)) }

        job.cancel()
        finishLoad.countDown()
        job.join()

        assertTrue(mic.closed)
        assertEquals(SttEvent.Listening, events.first())
        assertTrue("$events", events.none { it is SttEvent.Error || it == SttEvent.Ready })
    }

    @Test
    fun `a stop before the mic has opened closes it as soon as it opens`() = runTest(timeout = TEST_TIMEOUT) {
        lateinit var mic: FakeMic
        // The stop arrives while the mic is being opened.
        val engine = engine(mic = { e -> e.stop(); FakeMic(List(3) { loudHop }).also { mic = it } })

        val events = engine.listen(SessionOptions()).toList()

        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), events)
        assertTrue(mic.closed)
    }

    @Test
    fun `warmUp loads the model and decodes half a second of silence once`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine()

        engine.warmUp()
        engine.warmUp()

        assertTrue(engine.isLoaded)
        assertEquals(1, transcriber.calls.size)
        assertEquals(WARM_UP_SAMPLES, transcriber.calls.single().size)
        assertTrue(transcriber.calls.single().all { it == 0f })
    }

    @Test
    fun `load alone decodes nothing, and warmUp after it doesn't either`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine()

        engine.load()
        engine.warmUp()

        assertTrue(engine.isLoaded)
        assertEquals(0, transcriber.calls.size)
    }

    /** A transcriber whose warm-up decode (silence of [WARM_UP_SAMPLES]) signals [started] and holds until [finish]. */
    private fun holdingWarmUp(started: CountDownLatch, finish: CountDownLatch) = FakeTranscriber { samples ->
        if (samples.size == WARM_UP_SAMPLES) {
            started.countDown()
            finish.await(5, TimeUnit.SECONDS)
        }
        ""
    }

    @Test
    fun `a session that starts during the warm-up decode waits for it, then runs`() = runTest(timeout = TEST_TIMEOUT) {
        val decoding = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val warmTranscriber = holdingWarmUp(decoding, finish)
        val engine = engine(loadTranscriber = { warmTranscriber }, mic = selfStoppingMic)
        val warm = launch(Dispatchers.Default) { engine.warmUp() }
        withContext(Dispatchers.IO) { assertTrue(decoding.await(5, TimeUnit.SECONDS)) }

        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val job = launch { engine.listen(SessionOptions()).collect { events += it } }
        runCurrent() // the session claims the engine, opens the mic and suspends on the warm-up
        assertEquals(1, micOpens.get())
        assertEquals(listOf(SttEvent.Listening), events.toList())

        finish.countDown()
        warm.join()
        job.join()
        assertEquals(1, micOpens.get())
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), events.toList())
    }

    @Test
    fun `a session waiting for the load skips the warm-up decode`() = runTest(timeout = TEST_TIMEOUT) {
        val loading = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val engine = engine(loadTranscriber = {
            loading.countDown()
            finishLoad.await(5, TimeUnit.SECONDS)
            transcriber
        }, mic = selfStoppingMic)
        val warm = launch(Dispatchers.Default) { engine.warmUp() }
        withContext(Dispatchers.IO) { assertTrue(loading.await(5, TimeUnit.SECONDS)) }
        val job = launch { engine.listen(SessionOptions()).toList() }
        runCurrent() // the session claims the engine and suspends on the load

        finishLoad.countDown()
        warm.join()
        job.join()
        assertEquals(0, transcriber.calls.size)
    }

    @Test
    fun `release during a load drops the new model instead of keeping it`() = runTest(timeout = TEST_TIMEOUT) {
        val loading = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val loads = AtomicInteger()
        val engine = engine(loadTranscriber = {
            if (loads.incrementAndGet() == 1) {
                loading.countDown()
                finishLoad.await(5, TimeUnit.SECONDS)
            }
            transcriber
        })
        val load = launch(Dispatchers.Default) { engine.warmUp() }
        withContext(Dispatchers.IO) { assertTrue(loading.await(5, TimeUnit.SECONDS)) }

        engine.release() // the manager dropped the engine (another engine was picked)
        finishLoad.countDown()
        load.join()
        assertFalse(engine.isLoaded)
        assertEquals(1, transcriber.releaseCalls)
        assertEquals(0, transcriber.calls.size)

        engine.load()
        assertTrue(engine.isLoaded)
        assertEquals(2, loads.get())
    }

    @Test
    fun `release during the warm-up decode waits for it`() = runTest(timeout = TEST_TIMEOUT) {
        val decoding = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val warmTranscriber = holdingWarmUp(decoding, finish)
        val engine = engine(loadTranscriber = { warmTranscriber })
        val warm = launch(Dispatchers.Default) { engine.warmUp() }
        withContext(Dispatchers.IO) { assertTrue(decoding.await(5, TimeUnit.SECONDS)) }

        engine.release()
        assertEquals(0, warmTranscriber.releaseCalls)
        finish.countDown()
        warm.join()
        assertEquals(1, warmTranscriber.releaseCalls)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `an Error in the warm-up decode leaves the engine loaded and usable`() = runTest(timeout = TEST_TIMEOUT) {
        val failing = FakeTranscriber { samples -> if (samples.size == WARM_UP_SAMPLES) throw OutOfMemoryError("test") else "" }
        val engine = engine(loadTranscriber = { failing }, mic = selfStoppingMic)

        assertTrue(runCatching { engine.warmUp() }.exceptionOrNull() is OutOfMemoryError)
        assertTrue(engine.isLoaded)
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), engine.listen(SessionOptions()).toList())
        engine.release()
        assertEquals(1, failing.releaseCalls)
    }

    @Test
    fun `release while a session waits on the warm-up frees the model after the session`() = runTest(timeout = TEST_TIMEOUT) {
        val decoding = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val warmTranscriber = holdingWarmUp(decoding, finish)
        val engine = engine(loadTranscriber = { warmTranscriber }, mic = selfStoppingMic)
        val warm = launch(Dispatchers.Default) { engine.warmUp() }
        withContext(Dispatchers.IO) { assertTrue(decoding.await(5, TimeUnit.SECONDS)) }
        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val job = launch { engine.listen(SessionOptions()).collect { events += it } }
        runCurrent()

        engine.release()
        finish.countDown()
        warm.join()
        assertEquals("released under a waiting session", 0, warmTranscriber.releaseCalls)
        job.join()
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), events.toList())
        assertEquals(1, warmTranscriber.releaseCalls)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `a waiting session cancelled with a release pending leaves it to the warm-up`() = runTest(timeout = TEST_TIMEOUT) {
        val decoding = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val warmTranscriber = holdingWarmUp(decoding, finish)
        val mic = FakeMic()
        val engine = engine(loadTranscriber = { warmTranscriber }, mic = { mic })
        val warm = launch(Dispatchers.Default) { engine.warmUp() }
        withContext(Dispatchers.IO) { assertTrue(decoding.await(5, TimeUnit.SECONDS)) }
        val job = launch { engine.listen(SessionOptions()).toList() }
        runCurrent()

        engine.release()
        job.cancel()
        job.join()
        assertEquals("released under the warm-up decode", 0, warmTranscriber.releaseCalls)
        finish.countDown()
        warm.join()
        assertEquals(1, warmTranscriber.releaseCalls)
        assertFalse(engine.isLoaded)
        assertTrue("the mic opened before the wait, and closed with the session", mic.closed)
    }

    @Test
    fun `release during a session's load runs the session, then frees the model`() = runTest(timeout = TEST_TIMEOUT) {
        val loading = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val engine = engine(loadTranscriber = {
            loading.countDown()
            finishLoad.await(5, TimeUnit.SECONDS)
            transcriber
        }, mic = selfStoppingMic)
        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val job = launch { engine.listen(SessionOptions()).collect { events += it } }
        withContext(Dispatchers.IO) { assertTrue(loading.await(5, TimeUnit.SECONDS)) }

        engine.release()
        finishLoad.countDown()
        job.join()
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), events.toList())
        assertEquals(1, transcriber.releaseCalls)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `a dropped engine's warm-up loads nothing`() = runTest(timeout = TEST_TIMEOUT) {
        // The manager handed the engine to warmUp, then dropped it before the load started.
        val loads = AtomicInteger()
        val engine = engine(loadTranscriber = { loads.incrementAndGet(); transcriber })

        engine.drop()
        engine.warmUp()
        engine.load()
        assertEquals(0, loads.get())
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `a dropped engine still runs a session it was handed, then frees the model`() = runTest(timeout = TEST_TIMEOUT) {
        // The manager handed the engine to listen, then dropped it before the session claimed it.
        val engine = engine(mic = selfStoppingMic)

        engine.drop()
        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), engine.listen(SessionOptions()).toList())
        assertEquals(1, transcriber.releaseCalls)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `drop during a warm-up's load frees the model, and nothing reloads it`() = runTest(timeout = TEST_TIMEOUT) {
        val loading = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val loads = AtomicInteger()
        val engine = engine(loadTranscriber = {
            loads.incrementAndGet()
            loading.countDown()
            finishLoad.await(5, TimeUnit.SECONDS)
            transcriber
        })
        val warm = launch(Dispatchers.Default) { engine.warmUp() }
        withContext(Dispatchers.IO) { assertTrue(loading.await(5, TimeUnit.SECONDS)) }

        engine.drop()
        finishLoad.countDown()
        warm.join()
        engine.warmUp()
        assertEquals(1, loads.get())
        assertEquals(1, transcriber.releaseCalls)
        assertEquals(0, transcriber.calls.size)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `Whisper loads without the warm-up decode`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine(descriptor = checkNotNull(SttEngineCatalog.byId("whisper-turbo")))

        engine.warmUp()

        assertTrue(engine.isLoaded)
        assertEquals(0, transcriber.calls.size)
    }

    @Test
    fun `a second concurrent listen is refused as busy and the first keeps running`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine()
        val first = Collections.synchronizedList(mutableListOf<SttEvent>())
        val ready = CompletableDeferred<Unit>()
        val job = launch {
            engine.listen(SessionOptions()).collect {
                first += it
                if (it == SttEvent.Ready) ready.complete(Unit)
            }
        }
        ready.await()

        val second = engine.listen(SessionOptions()).toList()
        engine.stop()
        job.join()

        assertEquals(listOf(SttEvent.Error(SttErrorKind.BUSY, SherpaSttEngine.BUSY_MESSAGE)), second)
        assertEquals(SttEvent.NoSpeech, first.last())
        assertEquals(1, micOpens.get())
    }

    @Test
    fun `release during a session waits until the session ends`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine()
        val ready = CompletableDeferred<Unit>()
        val job = launch {
            engine.listen(SessionOptions()).collect { if (it == SttEvent.Ready) ready.complete(Unit) }
        }
        ready.await()

        engine.release()
        assertEquals("released under a running session", 0, transcriber.releaseCalls)
        assertTrue(engine.isLoaded)

        engine.stop()
        job.join()
        assertEquals(1, transcriber.releaseCalls)
        assertEquals(1, vads.single().releaseCalls)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `the VAD is reused for the same silence and rebuilt when it changes`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine(mic = selfStoppingMic)

        engine.listen(SessionOptions(minSilenceSeconds = 0.9f)).toList()
        engine.listen(SessionOptions(minSilenceSeconds = 0.9f)).toList()
        engine.listen(SessionOptions(minSilenceSeconds = 1.2f)).toList()

        assertEquals(listOf(0.9f, 1.2f), vadSilences)
        assertEquals(listOf(1, 0), vads.map { it.releaseCalls })
        assertEquals("each session resets the VAD", listOf(2, 1), vads.map { it.resetCalls })
    }

    @Test
    fun `Smart Turn with a detector proposes at its patience's pause, without one the slider applies`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine(mic = selfStoppingMic)

        for (patience in Patience.entries) {
            engine.listen(SessionOptions(end = EndOfDictation.SMART_TURN, minSilenceSeconds = 1.2f, turnDetector = { 0f }, patience = patience)).toList()
        }
        engine.listen(SessionOptions(end = EndOfDictation.SMART_TURN, minSilenceSeconds = 1.2f, patience = Patience.RELAXED)).toList()
        engine.listen(SessionOptions(end = EndOfDictation.END_WORD, minSilenceSeconds = 0.9f)).toList()

        // A VAD is built only when the pause changes: Quick 0.5 s, Normal 1.2 s,
        // Relaxed 1.5 s, then the slider's 1.2 s without a detector, then 0.9 s.
        assertEquals(listOf(0.5f, 1.2f, 1.5f, 1.2f, 0.9f), vadSilences)
    }

    @Test
    fun `Smart Turn's detector loads after the mic has opened, and a failed load means Pause`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine(mic = selfStoppingMic)
        val opensAtLoad = Collections.synchronizedList(mutableListOf<Int>())

        engine.listen(SessionOptions(end = EndOfDictation.SMART_TURN, loadTurnDetector = {
            opensAtLoad += micOpens.get()
            TurnDetector { 0f }
        })).toList()
        engine.listen(SessionOptions(end = EndOfDictation.SMART_TURN, minSilenceSeconds = 0.9f, loadTurnDetector = { null })).toList()

        assertEquals(listOf(1), opensAtLoad.toList())
        // Normal's proposal pause with the detector; the slider's 0.9 s without one.
        assertEquals(listOf(Patience.NORMAL.proposeSeconds, 0.9f), vadSilences)
    }

    @Test
    fun `a silence outside the slider range is clamped`() = runTest(timeout = TEST_TIMEOUT) {
        val engine = engine(mic = selfStoppingMic)

        engine.listen(SessionOptions(minSilenceSeconds = 5f)).toList()

        assertEquals(listOf(SessionOptions.MIN_SILENCE_RANGE_END), vadSilences)
    }

    @Test
    fun `stop while the model loads ends the session once it has read what was kept`() = runTest(timeout = TEST_TIMEOUT) {
        val loading = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val engine = engine(loadTranscriber = {
            loading.countDown()
            finishLoad.await(5, TimeUnit.SECONDS)
            transcriber
        })
        val events = Collections.synchronizedList(mutableListOf<SttEvent>())
        val job = launch { engine.listen(SessionOptions()).collect { events += it } }

        withContext(Dispatchers.IO) { assertTrue(loading.await(5, TimeUnit.SECONDS)) }
        engine.stop()
        finishLoad.countDown()
        job.join()

        assertEquals(listOf(SttEvent.Listening, SttEvent.Ready, SttEvent.NoSpeech), events.filterNot { it is SttEvent.Level })
    }

    @Test
    fun `concurrent loads build the model once`() = runTest(timeout = TEST_TIMEOUT) {
        val loads = AtomicInteger()
        val engine = engine(loadTranscriber = {
            loads.incrementAndGet()
            Thread.sleep(50)
            transcriber
        })

        List(4) { async(Dispatchers.Default) { engine.load() } }.awaitAll()

        assertEquals(1, loads.get())
        assertTrue(engine.isLoaded)
    }

    @Test
    fun `release when idle frees the model and a later listen reloads it`() = runTest(timeout = TEST_TIMEOUT) {
        val loads = AtomicInteger()
        val engine = engine(loadTranscriber = { loads.incrementAndGet(); transcriber }, mic = selfStoppingMic)

        engine.load()
        engine.release()
        assertFalse(engine.isLoaded)
        assertEquals(1, transcriber.releaseCalls)

        engine.listen(SessionOptions()).toList()
        assertEquals(2, loads.get())
        assertTrue(engine.isLoaded)
    }
}
