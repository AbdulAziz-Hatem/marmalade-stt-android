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
import kotlinx.coroutines.test.currentTime
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

    /** What onSegment got: (text, raw). */
    private val segments = mutableListOf<Pair<String, String>>()

    private lateinit var session: OneShotSession

    private fun TestScope.start(
        end: OneShotEnd = OneShotEnd.FirstEnd,
        minimumLengthMs: Long = 0,
        maxLengthMs: Long? = null,
    ): Deferred<OneShotOutcome> {
        session = OneShotSession(
            listen = { engine.events.receiveAsFlow() },
            stopEngine = engine::stop,
            pipeline = { TextPipeline(TextProcConfig()) },
            end = end,
            minimumLengthMs = minimumLengthMs,
            maxLengthMs = maxLengthMs,
        )
        return async { session.run(onSegment = { text, raw -> segments += text to raw }) { seen += it } }
    }

    /** Advance virtual time by [ms] and run what is due then. */
    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
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

    // ---- Raw text and segments (#90) --------------------------------------

    @Test
    fun `the outcome keeps the engine's raw words, and equality ignores them`() = runTest {
        val result = start()
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("um, hello world.", nearSilent = false))
        val text = result.await() as OneShotOutcome.Text
        assertEquals("Hello world.", text.text)
        assertEquals("um, hello world.", text.raw)
        assertEquals(OneShotOutcome.Text("Hello world.", "something else"), text)
        assertEquals("Hello world.", OneShotOutcome.Text("Hello world.").raw)
    }

    @Test
    fun `each kept utterance goes to onSegment and joins the raw text, phantoms and blank ones don't`() = runTest {
        val result = start(OneShotEnd.AudioEnd)
        engine.send(
            SttEvent.Ready,
            SttEvent.SpeechStarted,
            SttEvent.Final("um, hello world.", nearSilent = false),
            SttEvent.SpeechStarted,
            SttEvent.Final("Thank you.", nearSilent = true, silent = true),
            SttEvent.SpeechStarted,
            SttEvent.Final("second one.", nearSilent = false),
            SttEvent.Final("", nearSilent = true, endWord = true),
        )
        runCurrent()
        assertEquals(listOf("Hello world." to "um, hello world.", "Second one." to "second one."), segments)
        engine.events.close()
        val text = result.await() as OneShotOutcome.Text
        assertEquals("Hello world. Second one.", text.text)
        assertEquals("um, hello world. second one.", text.raw)
    }

    // ---- Pause: a segmented session ended by a long pause (#90) ------------

    @Test
    fun `pause - the first utterance doesn't end it, the pause after the last one does`() = runTest {
        val result = start(OneShotEnd.Pause(silenceMs = 3_000))
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("one.", nearSilent = false))
        wait(2_000)
        assertEquals(0, engine.stops)
        // Speaking again disarms it, however long the utterance.
        engine.send(SttEvent.SpeechStarted)
        wait(10_000)
        assertEquals(0, engine.stops)
        engine.send(SttEvent.Final("two.", nearSilent = false))
        wait(2_999)
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.Text("One. Two."), result.await())
    }

    @Test
    fun `pause - at the start, the no-speech timeout when it is longer`() = runTest {
        val result = start(OneShotEnd.Pause(silenceMs = 1_000))
        engine.send(SttEvent.Ready)
        wait(ListenTimeouts.NO_SPEECH_TIMEOUT_MS - 1)
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = false), result.await())
    }

    @Test
    fun `pause - at the start, the pause when it is longer`() = runTest {
        val silence = ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 2
        val result = start(OneShotEnd.Pause(silence))
        engine.send(SttEvent.Ready)
        wait(silence - 1)
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = false), result.await())
    }

    @Test
    fun `pause - an end word ends it at once`() = runTest {
        val result = start(OneShotEnd.Pause(silenceMs = 3_000))
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("one.", nearSilent = false))
        engine.send(SttEvent.SpeechStarted, SttEvent.Final("two.", nearSilent = false, endWord = true))
        runCurrent()
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.Text("One. Two."), result.await())
    }

    // ---- AudioEnd: a caller's stream (#90) ---------------------------------

    @Test
    fun `audio end - no timeout and no first end, it ends when the engine flow does`() = runTest {
        val result = start(OneShotEnd.AudioEnd)
        engine.send(SttEvent.Ready)
        wait(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 10)
        engine.send(
            SttEvent.SpeechStarted,
            SttEvent.Final("one.", nearSilent = false),
            SttEvent.Final("two.", nearSilent = false, endWord = true),
        )
        wait(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 10)
        assertEquals(0, engine.stops)
        assertFalse(result.isCompleted)
        engine.events.close()
        assertEquals(OneShotOutcome.Text("One. Two."), result.await())
        assertEquals(0, engine.stops)
    }

    // ---- Minimum length (#90) ---------------------------------------------

    @Test
    fun `minimum length - a first end before it is held, and the engine stops exactly at it`() = runTest {
        val result = start(minimumLengthMs = 10_000)
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted)
        wait(1_000)
        engine.send(SttEvent.Final("hello.", nearSilent = false))
        wait(4_000)
        // Still listening while held: later utterances join.
        engine.send(SttEvent.SpeechStarted, SttEvent.Final("more.", nearSilent = false))
        wait(4_999)
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(10_000L, currentTime)
        assertEquals(OneShotOutcome.Text("Hello. More."), result.await())
    }

    @Test
    fun `minimum length - counts from Ready, not from the mic opening`() = runTest {
        val result = start(minimumLengthMs = 10_000)
        engine.send(SttEvent.Listening)
        wait(5_000)
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("hello.", nearSilent = false))
        wait(9_999)
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.Text("Hello."), result.await())
    }

    @Test
    fun `minimum length - a no-speech timeout before it is held too`() = runTest {
        val result = start(minimumLengthMs = 20_000)
        engine.send(SttEvent.Ready)
        wait(19_999)
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = false), result.await())
    }

    @Test
    fun `minimum length - an end after it stops at once`() = runTest {
        val result = start(minimumLengthMs = 5_000)
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted)
        wait(7_000)
        assertEquals(0, engine.stops)
        engine.send(SttEvent.Final("late.", nearSilent = false))
        runCurrent()
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.Text("Late."), result.await())
    }

    @Test
    fun `minimum length - the session returns at once when the engine flow ends before it`() = runTest {
        val result = start(minimumLengthMs = 60_000)
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("hi.", nearSilent = false))
        engine.events.close()
        runCurrent()
        // Nothing (the minimum-length or timeout job) is left running to hold the session's scope open.
        assertTrue(result.isCompleted)
        assertEquals(0L, currentTime)
        assertEquals(0, engine.stops)
        assertEquals(OneShotOutcome.Text("Hi."), result.await())
    }

    @Test
    fun `minimum length - lock during a held stop cancels it`() = runTest {
        val result = start(minimumLengthMs = 10_000)
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("one.", nearSilent = false))
        runCurrent()
        session.lock()
        wait(30_000)
        assertEquals(0, engine.stops)
        engine.stop()
        assertEquals(OneShotOutcome.Text("One."), result.await())
    }

    @Test
    fun `minimum length - speech after a held no-speech timeout drops the held stop`() = runTest {
        start(minimumLengthMs = 20_000)
        engine.send(SttEvent.Ready)
        wait(ListenTimeouts.NO_SPEECH_TIMEOUT_MS + 1_000) // the timeout fired and is held
        engine.send(SttEvent.SpeechStarted) // the user speaks after all
        wait(20_000 - currentTime)
        // Without a minimum, speech disarms the timeout; with one, the stale timeout still shouldn't end it.
        assertEquals(0, engine.stops)
        engine.send(SttEvent.Final("there you go.", nearSilent = false))
        runCurrent()
        assertEquals(1, engine.stops)
    }

    // ---- Max length (#90) -------------------------------------------------

    @Test
    fun `max length - a pause session someone keeps talking in is stopped at the cap`() = runTest {
        val result = start(OneShotEnd.Pause(2_000), maxLengthMs = 30_000)
        engine.send(SttEvent.Ready)
        // An utterance every second: the pause never comes.
        repeat(30) {
            engine.send(SttEvent.SpeechStarted, SttEvent.Final("again.", nearSilent = false))
            wait(if (it < 29) 1_000 else 999)
        }
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(30_000L, currentTime)
        assertTrue((result.await() as OneShotOutcome.Text).text.startsWith("Again."))
    }

    @Test
    fun `max length - counts from the start, and outranks a lock and a minimum length`() = runTest {
        val result = start(minimumLengthMs = 60_000, maxLengthMs = 20_000)
        engine.send(SttEvent.Listening)
        wait(5_000) // a slow model load counts
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("one.", nearSilent = false))
        runCurrent()
        session.lock()
        wait(14_999)
        assertEquals(0, engine.stops)
        wait(1)
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.Text("One."), result.await())
    }

    @Test
    fun `max length - an audio-end session is capped too`() = runTest {
        val capped = start(OneShotEnd.AudioEnd, maxLengthMs = 10_000)
        engine.send(SttEvent.Ready)
        wait(10_000)
        assertEquals(1, engine.stops)
        assertEquals(OneShotOutcome.NoMatch(heardSpeech = false), capped.await())
    }

    @Test
    fun `max length - the session returns at once when the engine flow ends before it`() = runTest {
        val result = start(maxLengthMs = 600_000)
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("hi.", nearSilent = false))
        runCurrent()
        assertTrue(result.isCompleted)
        assertEquals(0L, currentTime)
        assertEquals(OneShotOutcome.Text("Hi."), result.await())
    }

    // ---- Pre-empted (#90) --------------------------------------------------

    @Test
    fun `pre-empted by the user's own surface - busy, even with text heard`() = runTest {
        val result = start(OneShotEnd.Pause(2_000))
        engine.send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("half a sentence", nearSilent = false))
        engine.send(SttEvent.Error(SttErrorKind.BUSY, "busy: the user started listening in Marmalade STT"))
        engine.events.close()
        assertEquals(OneShotOutcome.Failed(SttErrorKind.BUSY), result.await())
    }
}
