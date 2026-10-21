package app.marmalade.stt.recognizer

import app.marmalade.stt.speech.SttEvent
import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ServiceRequests] (#90): one request at a time, a stop before the engine
 * listens repeated once it does, and a caller's stream closed however its
 * request ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRequestsTest {

    private class Stream : Closeable {
        var closes = 0
        val closed get() = closes > 0
        override fun close() {
            closes++
        }
    }

    private var engineStops = 0

    private fun CoroutineScope.requests() = ServiceRequests(this, stopEngine = { engineStops++ })

    /**
     * Run everything due in the next minute. (advanceUntilIdle won't do: it
     * ignores the background scope the requests run on.)
     */
    private fun TestScope.settle() {
        advanceTimeBy(60_000)
        runCurrent()
    }

    /** A request that holds the engine until cancelled, then takes [letGoMs] to let go of it. */
    private suspend fun holdEngine(letGoMs: Long) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { delay(letGoMs) }
        }
    }

    // ── one at a time ────────────────────────────────────────────────────────

    @Test
    fun `a new request cancels the running one and waits for it to let go of the engine`() = runTest {
        val requests = backgroundScope.requests()
        val first = requests.start(stream = null) { holdEngine(letGoMs = 1_500) }
        runCurrent()
        var startedAt = -1L
        requests.start(stream = null) { startedAt = currentTime }
        runCurrent()
        assertTrue(first.isCancelled)
        assertEquals(-1L, startedAt)
        settle()
        assertEquals(1_500L, startedAt)
    }

    @Test
    fun `cancel ends the running request`() = runTest {
        val requests = backgroundScope.requests()
        val job = requests.start(stream = null) { awaitCancellation() }
        runCurrent()
        requests.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        requests.cancel() // nothing running: nothing happens
    }

    // ── stop ─────────────────────────────────────────────────────────────────

    @Test
    fun `a stop before the engine listens is repeated once it does`() = runTest {
        val requests = backgroundScope.requests()
        val previous = requests.start(stream = null) { holdEngine(letGoMs = 1_000) }
        runCurrent()
        val events = Channel<SttEvent>(Channel.UNLIMITED)
        var stopped = 0
        val seen = CompletableDeferred<List<SttEvent>>()
        requests.start(stream = null, onStop = { stopped++ }) { seen.complete(repeatingStop(events.receiveAsFlow()).toList()) }
        runCurrent()

        // The caller stops while the new request still waits for the previous one.
        requests.stop()
        assertEquals(1, stopped)
        assertEquals(1, engineStops) // reaches whatever runs now, which forgets it
        settle()
        assertTrue(previous.isCompleted)

        events.trySend(SttEvent.Ready)
        runCurrent()
        assertEquals(1, engineStops)
        events.trySend(SttEvent.Listening) // the engine has the mic now: stop it again
        runCurrent()
        assertEquals(2, engineStops)
        events.close()
        assertEquals(listOf(SttEvent.Ready, SttEvent.Listening), seen.await())
    }

    @Test
    fun `without a stop, listening is left alone`() = runTest {
        val requests = backgroundScope.requests()
        val events = Channel<SttEvent>(Channel.UNLIMITED).apply {
            trySend(SttEvent.Listening)
            close()
        }
        requests.start(stream = null) { repeatingStop(events.receiveAsFlow()).toList() }
        settle()
        assertEquals(0, engineStops)
    }

    @Test
    fun `a stop belongs to its request - the next one listens on`() = runTest {
        val requests = backgroundScope.requests()
        requests.start(stream = null) { holdEngine(letGoMs = 0) }
        runCurrent()
        requests.stop()
        assertEquals(1, engineStops)

        val events = Channel<SttEvent>(Channel.UNLIMITED).apply {
            trySend(SttEvent.Listening)
            close()
        }
        requests.start(stream = null) { repeatingStop(events.receiveAsFlow()).toList() }
        settle()
        assertEquals(1, engineStops)
    }

    @Test
    fun `a stop after the request ended does nothing`() = runTest {
        val requests = backgroundScope.requests()
        var stopped = 0
        requests.start(stream = null, onStop = { stopped++ }) {}
        settle()
        requests.stop()
        assertEquals(0, stopped)
        assertEquals(0, engineStops)
    }

    // ── the caller's stream ──────────────────────────────────────────────────

    @Test
    fun `the stream stays open while the request runs, for the capture to read, and is closed when it ends`() = runTest {
        val requests = backgroundScope.requests()
        val stream = Stream()
        val reading = CompletableDeferred<Unit>()
        var openWhileRunning = false
        requests.start(stream) {
            openWhileRunning = !stream.closed
            reading.await()
        }
        runCurrent()
        assertTrue(openWhileRunning)
        assertFalse(stream.closed)
        reading.complete(Unit)
        runCurrent()
        assertEquals(1, stream.closes)
    }

    @Test
    fun `a request that ends before the engine opened the stream closes it`() = runTest {
        val requests = backgroundScope.requests()
        val stream = Stream()
        requests.start(stream) { /* answered with an error: language unavailable, no model, busy */ }
        runCurrent()
        assertEquals(1, stream.closes)
    }

    @Test
    fun `a failing request closes the stream`() = runTest {
        // The failure goes to the scope's handler, as on the service's scope.
        val scope = CoroutineScope(
            backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, _ -> },
        )
        val stream = Stream()
        scope.requests().start(stream) { error("boom") }
        runCurrent()
        assertEquals(1, stream.closes)
    }

    @Test
    fun `a request cancelled while it waits closes its stream once the previous one let go`() = runTest {
        val requests = backgroundScope.requests()
        requests.start(stream = null) { holdEngine(letGoMs = 2_000) }
        runCurrent()
        val stream = Stream()
        val waiting = requests.start(stream) { error("never runs") }
        runCurrent()
        requests.cancel() // the caller cancels before the previous request let go
        runCurrent()
        assertTrue(waiting.isCancelled)
        advanceTimeBy(1_999)
        runCurrent()
        assertFalse(waiting.isCompleted) // it still waits for the holder
        assertEquals(0, stream.closes)
        settle()
        assertTrue(waiting.isCompleted)
        assertEquals(1, stream.closes)
    }

    @Test
    fun `a request replaced while it waits closes its stream, and the next one still waits for the holder`() = runTest {
        val requests = backgroundScope.requests()
        requests.start(stream = null) { holdEngine(letGoMs = 2_000) }
        runCurrent()
        val stream = Stream()
        val b = requests.start(stream) { error("never runs") }
        runCurrent()
        advanceTimeBy(500)
        var cStartedAt = -1L
        requests.start(stream = null) { cStartedAt = currentTime } // cancels B while B waits for A
        runCurrent()
        assertTrue(b.isCancelled)
        assertEquals(-1L, cStartedAt)
        settle()
        // C started when A let go of the engine, not when B (which held nothing) was cancelled.
        assertEquals(2_000L, cStartedAt)
        assertEquals(1, stream.closes)
    }

    @Test
    fun `a second cancel mid-wait doesn't let the next request start early`() = runTest {
        val requests = backgroundScope.requests()
        requests.start(stream = null) { holdEngine(letGoMs = 3_000) }
        runCurrent()
        requests.start(stream = null) { error("never runs") }
        runCurrent()
        requests.cancel() // B, waiting for A
        runCurrent()
        var cStartedAt = -1L
        requests.start(stream = null) { cStartedAt = currentTime }
        settle()
        assertEquals(3_000L, cStartedAt)
    }

    @Test
    fun `a request on a cancelled scope never runs and still closes its stream`() = runTest {
        val scope = CoroutineScope(Job())
        scope.cancel() // the service was destroyed
        val stream = Stream()
        var ran = false
        scope.requests().start(stream) { ran = true }.join()
        assertFalse(ran)
        assertEquals(1, stream.closes)
    }

    @Test
    fun `a stream that fails to close doesn't break the request`() = runTest {
        val requests = backgroundScope.requests()
        val job = requests.start(Closeable { throw java.io.IOException("EBADF") }) {}
        runCurrent()
        assertTrue(job.isCompleted && !job.isCancelled)
    }
}
