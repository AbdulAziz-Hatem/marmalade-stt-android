package app.marmalade.stt.recognizer

import app.marmalade.stt.speech.ListenTimeouts
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OneShotSessionTest {

    /** An engine session fed by the test. By default stop() ends it at once (nothing left to flush). */
    private class FakeEngine {
        val events = Channel<SttEvent>(Channel.UNLIMITED)
        var stops = 0
        var onStop: () -> Unit = { events.close() }

        fun stop() {
            stops++
            onStop()
        }

        fun send(vararg e: SttEvent) = e.forEach { events.trySend(it) }
    }

    private val engine = FakeEngine()
    private val seen = mutableListOf<SttEvent>()

    private lateinit var session: OneShotSession

    private fun TestScope.start(): Deferred<OneShotOutcome> {
        session = OneShotSession(
            listen = { engine.events.receiveAsFlow() },
            stopEngine = engine::stop,
            pipeline = { TextPipeline(TextProcConfig()) },
        )
        return async { session.run { seen += it } }
    }

    @Test
    fun `endpointing stops after the first utterance and returns it cleaned up`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("um, hello world.", nearSilent = false))
        assertEquals(OneShotOutcome.Text("Hello world."), result.await())
        assertEquals(1, engine.stops)
        assertEquals(3, seen.size)
    }

    @Test
    fun `what the engine flushes after the stop joins the result`() = runTest {
        engine.onStop = {
            engine.send(SttEvent.Final("second one.", nearSilent = false))
            engine.events.close()
        }
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("first one.", nearSilent = false))
        assertEquals(OneShotOutcome.Text("First one. Second one."), result.await())
    }

    @Test
    fun `nobody speaking times out and reports no match without speech`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready)
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(0, engine.stops)
        advanceTimeBy(2)
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = false), result.await())
        assertEquals(1, engine.stops)
    }

    @Test
    fun `the no-speech timeout counts from Ready, not from the mic opening (#78)`() = runTest {
        val result = start()
        engine.send(SttEvent.Listening)
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 2) // a slow model load
        runCurrent()
        assertEquals(0, engine.stops)
        engine.send(SttEvent.Ready)
        runCurrent()
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(0, engine.stops)
        advanceTimeBy(2)
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = false), result.await())
        assertEquals(1, engine.stops)
        assertEquals(SttEvent.Listening, seen.first())
    }

    @Test
    fun `speech disarms the timeout`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted)
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 3)
        runCurrent()
        assertEquals(0, engine.stops)
        engine.send(SttEvent.Final("okay then.", nearSilent = false))
        assertEquals(OneShotOutcome.Text("Okay then."), result.await())
    }

    @Test
    fun `a filtered hallucination does not end the request and re-arms the timeout`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("Thank you.", nearSilent = true, silent = true))
        runCurrent()
        assertEquals(0, engine.stops)
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS + 1)
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = true), result.await())
    }

    @Test
    fun `partials reach the caller cleaned`() = runTest {
        val result = start()
        engine.send(
            SttEvent.Ready,
            SttEvent.SpeechStarted,
            SttEvent.Partial("so it was " + "E ".repeat(12)),
            SttEvent.Partial("[BLANK_AUDIO]"),
            SttEvent.Final("so it was fine.", nearSilent = false),
        )
        assertEquals(OneShotOutcome.Text("So it was fine."), result.await())
        assertEquals(listOf(SttEvent.Partial("so it was"), SttEvent.Partial("")), seen.filterIsInstance<SttEvent.Partial>())
    }

    @Test
    fun `an engine error is reported`() = runTest {
        val result = start()
        engine.send(SttEvent.Error(SttErrorKind.MODEL, "No speech model is installed."))
        engine.events.close()
        assertEquals(OneShotOutcome.Failed(SttErrorKind.MODEL), result.await())
    }

    @Test
    fun `text already heard wins over a later error`() = runTest {
        val result = start()
        engine.send(
            SttEvent.Ready,
            SttEvent.Final("keep this.", nearSilent = false),
            SttEvent.Error(SttErrorKind.RECOGNITION, "boom"),
        )
        engine.events.close()
        assertEquals(OneShotOutcome.Text("Keep this."), result.await())
    }

    @Test
    fun `cancelling the caller aborts without stopping gracefully`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted)
        runCurrent()
        result.cancel()
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 2)
        assertTrue(result.isCancelled)
        assertEquals(0, engine.stops)
        assertFalse(seen.isEmpty())
    }

    @Test
    fun `the end word alone ends the request with no match`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("", nearSilent = true, endWord = true))
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = true), result.await())
        assertEquals(1, engine.stops)
    }

    @Test
    fun `an end word after text returns the text before it`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("call mom.", nearSilent = false, endWord = true))
        assertEquals(OneShotOutcome.Text("Call mom."), result.await())
        assertEquals(1, engine.stops)
    }

    // ---- The lock (#43) ----------------------------------------------------

    @Test
    fun `locked - Finals, an end word included, only join the text, and the tap returns all of it`() = runTest {
        engine.onStop = {
            engine.send(SttEvent.Final("third one.", nearSilent = false))
            engine.events.close()
        }
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted)
        runCurrent()
        session.lock()
        engine.send(SttEvent.Final("first one.", nearSilent = false), SttEvent.Final("second one.", nearSilent = false, endWord = true))
        runCurrent()
        assertEquals(0, engine.stops)
        // No timeout either, however long it stays quiet.
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 3)
        runCurrent()
        assertEquals(0, engine.stops)
        // The sheet's tap (or Done) stops the engine; what it flushes joins the text.
        engine.stop()
        assertEquals(OneShotOutcome.Text("First one. Second one. Third one."), result.await())
    }

    @Test
    fun `locked before anyone speaks - the no-speech timeout is off`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready)
        runCurrent()
        session.lock()
        advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 3)
        runCurrent()
        assertEquals(0, engine.stops)
        engine.stop()
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = false), result.await())
    }
}
