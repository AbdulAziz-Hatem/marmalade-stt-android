package app.marmalade.stt.recognizer

import android.speech.SpeechRecognizer
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The RecognitionService's callbacks for one request (#90), in Android's order. */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceSessionTest {

    /** Records each callback as a line. */
    private class Recorder : RecognitionClient {
        val calls = mutableListOf<String>()
        override fun readyForSpeech() { calls += "ready" }
        override fun beginningOfSpeech() { calls += "begin" }
        override fun rmsChanged(rmsDb: Float) { calls += "rms $rmsDb" }
        override fun partialResults(hypotheses: List<String>) { calls += "partial $hypotheses" }
        override fun endOfSpeech() { calls += "end" }
        override fun results(hypotheses: List<String>) { calls += "results $hypotheses" }
        override fun segmentResults(hypotheses: List<String>) { calls += "segment $hypotheses" }
        override fun endOfSegmentedSession() { calls += "endOfSegmentedSession" }
        override fun error(code: Int) { calls += "error $code" }
    }

    private val events = Channel<SttEvent>(Channel.UNLIMITED)
    private var stops = 0
    private val client = Recorder()
    private lateinit var service: ServiceSession

    private fun send(vararg e: SttEvent) = e.forEach { events.trySend(it) }

    /** Runs [request] like the service does; the engine's stop() ends its flow. */
    private fun TestScope.start(request: RecognitionRequest = RecognitionRequest()): Job {
        service = ServiceSession(request, client)
        val oneShot = OneShotSession(
            listen = { events.receiveAsFlow() },
            stopEngine = {
                stops++
                events.close()
            },
            pipeline = { TextPipeline(TextProcConfig()) },
            end = request.end(),
            minimumLengthMs = request.minimumLengthMs ?: 0,
        )
        return launch { service.run(oneShot) }
    }

    private val hello = arrayOf(
        SttEvent.Listening,
        SttEvent.Level(-40f),
        SttEvent.Ready,
        SttEvent.SpeechStarted,
        SttEvent.Level(-20f),
        SttEvent.Partial("um, hello"),
        SttEvent.SpeechStarted,
        SttEvent.Final("um, hello world.", nearSilent = false),
    )

    @Test
    fun `a plain request - ready once, beginning once, levels, no partials, end of speech before results`() = runTest {
        start()
        send(*hello)
        runCurrent()
        assertEquals(
            listOf("ready", "rms -40.0", "begin", "rms -20.0", "end", "results [Hello world.]"),
            client.calls,
        )
        assertEquals(1, stops)
    }

    @Test
    fun `partials when asked for, cleaned and without trailing punctuation if asked`() = runTest {
        start(RecognitionRequest(partials = true, hidePartialTrailingPunctuation = true))
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Partial("hello there,"), SttEvent.Final("hello there.", nearSilent = false))
        runCurrent()
        assertEquals(listOf("ready", "begin", "partial [hello there]", "end", "results [Hello there.]"), client.calls)
    }

    @Test
    fun `a blank partial is still sent - the last one was noise`() = runTest {
        start(RecognitionRequest(partials = true))
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Partial("so"), SttEvent.Partial("[BLANK_AUDIO]"))
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "begin", "partial [so]", "partial []"), client.calls.take(4))
    }

    @Test
    fun `formatting - results carry the text, then the raw words`() = runTest {
        start(RecognitionRequest(formatting = true))
        send(*hello)
        runCurrent()
        assertEquals("results [Hello world., um, hello world.]", client.calls.last())
    }

    @Test
    fun `formatting with one result allowed - the text only`() = runTest {
        start(RecognitionRequest(formatting = true, maxResults = 1))
        send(*hello)
        runCurrent()
        assertEquals("results [Hello world.]", client.calls.last())
    }

    @Test
    fun `segmented - results per utterance, then the end of the session`() = runTest {
        val silence = 3_000L
        start(RecognitionRequest(segmented = RecognitionRequest.Segmented.COMPLETE_SILENCE, completeSilenceMs = silence, formatting = true))
        send(
            SttEvent.Ready,
            SttEvent.SpeechStarted,
            SttEvent.Final("um, hello world.", nearSilent = false),
            SttEvent.SpeechStarted,
            SttEvent.Final("second one.", nearSilent = false),
        )
        runCurrent()
        assertEquals(0, stops)
        testScheduler.advanceTimeBy(silence + 1)
        runCurrent()
        assertEquals(1, stops)
        assertEquals(
            listOf(
                "ready",
                "begin",
                "segment [Hello world., um, hello world.]",
                "segment [Second one., second one.]",
                "end",
                "endOfSegmentedSession",
            ),
            client.calls,
        )
    }

    @Test
    fun `segmented - nothing heard still ends the session normally`() = runTest {
        start(RecognitionRequest(segmented = RecognitionRequest.Segmented.MINIMUM_LENGTH))
        send(SttEvent.Ready)
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "endOfSegmentedSession"), client.calls)
    }

    @Test
    fun `segmented - a failure is an error`() = runTest {
        start(RecognitionRequest(segmented = RecognitionRequest.Segmented.MINIMUM_LENGTH))
        send(SttEvent.Error(SttErrorKind.MIC, "mic"))
        events.close()
        runCurrent()
        assertEquals(listOf("error ${SpeechRecognizer.ERROR_AUDIO}"), client.calls)
    }

    @Test
    fun `nobody speaking is a speech timeout`() = runTest {
        start()
        send(SttEvent.Ready)
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "error ${SpeechRecognizer.ERROR_SPEECH_TIMEOUT}"), client.calls)
    }

    @Test
    fun `no match after speech - end of speech, then no match`() = runTest {
        start()
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("", nearSilent = true, endWord = true))
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "error ${SpeechRecognizer.ERROR_NO_MATCH}"), client.calls)
    }

    @Test
    fun `a mic failure is an audio error`() = runTest {
        start()
        send(SttEvent.Listening, SttEvent.Error(SttErrorKind.MIC, "mic"))
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "error ${SpeechRecognizer.ERROR_AUDIO}"), client.calls)
    }

    @Test
    fun `the caller's stop sends end of speech at once, and not again at the end`() = runTest {
        start()
        send(SttEvent.Ready, SttEvent.SpeechStarted)
        runCurrent()
        service.stopped()
        assertEquals(listOf("ready", "begin", "end"), client.calls)
        // What the engine flushes still comes back.
        send(SttEvent.Final("hello.", nearSilent = false))
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "results [Hello.]"), client.calls)
    }

    @Test
    fun `a stop before any speech sends no end of speech`() = runTest {
        start()
        send(SttEvent.Ready)
        runCurrent()
        service.stopped()
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "error ${SpeechRecognizer.ERROR_SPEECH_TIMEOUT}"), client.calls)
    }

    @Test
    fun `beginning of speech goes out once, also when speech is flushed after the stop`() = runTest {
        start()
        send(SttEvent.Listening, SttEvent.SpeechStarted)
        runCurrent()
        service.stopped()
        // The audio kept while the model loaded (#78) is transcribed after the stop.
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("hello.", nearSilent = false))
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "results [Hello.]"), client.calls)
    }
}
