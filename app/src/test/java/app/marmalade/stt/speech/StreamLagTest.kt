package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A caller's stream written as it records (#118, Waze): its lag is the
 * engine's [EarlyMic] queue, so partials are skipped to catch up as for the
 * mic; a file's isn't; and no stream marks the engine too slow.
 */
class StreamLagTest {

    private companion object {
        const val HOPS = 40
    }

    /** A caller's stream: [script] as fast as it is read, then [afterScript], then its end. */
    private class FakeStream(
        private val script: List<FloatArray>,
        override val paced: Boolean,
        private val afterScript: () -> Unit = {},
    ) : MicCapture {
        override val live: Boolean get() = false
        override val hops: Flow<FloatArray> = flow {
            for (hop in script) emit(hop.copyOf())
            afterScript()
        }
        override fun close() = Unit
    }

    @Test
    fun `a paced stream's queue in EarlyMic counts as lag, a file's doesn't`() = runTest(timeout = TEST_TIMEOUT) {
        suspend fun maxAhead(paced: Boolean): Int {
            val queued = CountDownLatch(1)
            val early = EarlyMic(FakeStream(List(HOPS) { loudHop }, paced) { queued.countDown() })
            launch(Dispatchers.IO) { early.record() }
            var ahead = 0
            micSession({ _, _ -> early }, isStopRequested = { false }) { hops, captured ->
                for (h in hops) {
                    // The first hop is in hand once the whole stream has been read into the queue.
                    if (h.index == 0) queued.await(5, TimeUnit.SECONDS)
                    ahead = maxOf(ahead, captured() - h.index)
                }
            }.toList()
            return ahead
        }
        assertTrue(maxAhead(paced = true) > HOPS - NON_LIVE_QUEUED_HOPS - 3)
        assertTrue(maxAhead(paced = false) <= NON_LIVE_QUEUED_HOPS + 2)
    }

    /** StreamingSession over [stream] in an EarlyMic; its first decode waits until the stream is read. */
    private suspend fun kotlinx.coroutines.CoroutineScope.session(paced: Boolean): Pair<List<SttEvent>, FakeTranscriber> {
        val queued = CountDownLatch(1)
        var calls = 0
        val transcriber = FakeTranscriber { samples ->
            if (calls++ == 0) queued.await(5, TimeUnit.SECONDS)
            "h${samples.size / HOP}"
        }
        val early = EarlyMic(FakeStream(List(HOPS) { loudHop }, paced) { queued.countDown() })
        launch(Dispatchers.IO) { early.record() }
        val s = StreamingSession(
            transcriber, FakeVad(speechFromSample = 1), options = SessionOptions(), openMic = { _, _ -> early },
            nowMs = fastClock(),
        )
        return s.run().toList().filterNot { it is SttEvent.Level } to transcriber
    }

    @Test
    fun `behind a paced stream, partials are skipped and every word still comes`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, transcriber) = session(paced = true)
        // The first partial, a few once caught up, and the Final (as CatchUpTest's mic).
        assertTrue("${transcriber.calls.size} decodes: $events", transcriber.calls.size <= 8)
        // The Final decodes the whole stream (but the last VAD window, which is never filled):
        // nothing was dropped to catch up.
        val whole = SttEvent.Final("h${(HOPS * HOP - StreamingSession.VAD_WINDOW_SIZE) / HOP}", nearSilent = false)
        assertEquals(whole, events.last())
        assertFalse(SttEvent.TooSlow in events)

        // A file (not paced) is never behind: the fast clock opens the partial gate every other hop.
        val (fileEvents, fileTranscriber) = session(paced = false)
        assertTrue("${fileTranscriber.calls.size} decodes for a file", fileTranscriber.calls.size >= 15)
        assertEquals(whole, fileEvents.last())
        assertTrue(fileEvents.none { it is SttEvent.CatchingUp })
    }

    /** A session that only says the engine is too slow, then ends. */
    private class TooSlowSession : ListeningSession {
        override fun stop() = Unit
        override fun run(): Flow<SttEvent> = flow {
            emit(SttEvent.TooSlow)
            emit(SttEvent.Final("done", nearSilent = false))
        }
    }

    private fun engine(mic: () -> MicCapture) = SherpaSttEngine<OfflineTranscriber>(
        descriptor = SttEngineCatalog.default,
        loadModel = { FakeTranscriber(listOf("unused")) },
        createVad = { FakeVad(speechFromSample = Int.MAX_VALUE) },
        openMic = { _, _ -> mic() },
        newSession = { _, _, _, _, _ -> TooSlowSession() },
    )

    @Test
    fun `only the mic marks the engine too slow, never a caller's stream`() = runTest(timeout = TEST_TIMEOUT) {
        val fromMic = engine { FakeMic(afterScript = {}) }.listen(SessionOptions()).toList()
        assertTrue(fromMic.toString(), SttEvent.TooSlow in fromMic)

        val stream = SessionOptions(audioInput = { FakeStream(emptyList(), paced = true) })
        val fromStream = engine { FakeMic() }.listen(stream).toList()
        assertFalse(fromStream.toString(), SttEvent.TooSlow in fromStream)
        assertEquals(SttEvent.Final("done", nearSilent = false), fromStream.last())
    }
}
