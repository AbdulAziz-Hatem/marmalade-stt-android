package app.marmalade.stt.recognizer

import android.speech.SpeechRecognizer
import app.marmalade.stt.speech.ListenTimeouts
import app.marmalade.stt.speech.PcmFormat
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

    // ---- A caller's stream (#116) ------------------------------------------

    @Test
    fun `a live stream held open after the speech - results at the end of speech, the engine stopped`() = runTest {
        // Waze: the pipe stays open (the flow goes on) until it closes it 10 s in.
        start(RecognitionRequest(audioSource = PcmFormat()))
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("navigate home.", nearSilent = false))
        runCurrent()
        assertEquals(1, stops)
        assertEquals(listOf("ready", "begin", "end", "results [Navigate home.]"), client.calls)
    }

    @Test
    fun `a stream that closes before the end of speech - its flushed words are the results`() = runTest {
        // A caller that stops writing mid-sentence: the engine flushes at the end of the audio, which
        // is the request's first end (before #116 nothing ended a stream request but its stream).
        start(RecognitionRequest(audioSource = PcmFormat()))
        send(SttEvent.Ready, SttEvent.SpeechStarted)
        runCurrent()
        events.trySend(SttEvent.Final("navigate", nearSilent = false))
        runCurrent()
        assertEquals(1, stops)
        assertEquals(listOf("ready", "begin", "end", "results [Navigate]"), client.calls)
    }

    @Test
    fun `a file runs to its end - every utterance, nothing after the first stops it`() = runTest {
        // A recording handed over whole (a regular-file descriptor) wants all of it, as before #116.
        service = ServiceSession(RecognitionRequest(audioSource = PcmFormat()), client)
        val oneShot = OneShotSession(
            listen = { events.receiveAsFlow() },
            stopEngine = {
                stops++
                events.close()
            },
            pipeline = { TextPipeline(TextProcConfig()) },
            end = RecognitionRequest(audioSource = PcmFormat()).end(fromFile = true),
        )
        launch { service.run(oneShot) }
        send(
            SttEvent.Ready,
            SttEvent.SpeechStarted,
            SttEvent.Final("navigate home.", nearSilent = false),
            SttEvent.SpeechStarted,
            SttEvent.Final("then call Bob.", nearSilent = false),
        )
        testScheduler.advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 10)
        runCurrent()
        assertEquals(0, stops)
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "results [Navigate home. Then call Bob.]"), client.calls)
    }

    @Test
    fun `a fast stream whose flush holds the next sentence - only the first is the result`() = runTest {
        // #116 review: a pipe read ahead of the session; the engine's flush at the stop has begun
        // the next utterance.
        service = ServiceSession(RecognitionRequest(audioSource = PcmFormat()), client)
        val oneShot = OneShotSession(
            listen = { events.receiveAsFlow() },
            stopEngine = {
                stops++
                send(SttEvent.SpeechStarted, SttEvent.Final("then", nearSilent = false))
                events.close()
            },
            pipeline = { TextPipeline(TextProcConfig()) },
            end = RecognitionRequest(audioSource = PcmFormat()).end(),
        )
        launch { service.run(oneShot) }
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Final("navigate home.", nearSilent = false))
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "results [Navigate home.]"), client.calls)
    }

    @Test
    fun `a stream with nobody speaking - a speech timeout, not a wait for the stream`() = runTest {
        start(RecognitionRequest(audioSource = PcmFormat()))
        send(SttEvent.Ready)
        testScheduler.advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(1, stops)
        assertEquals(listOf("ready", "error ${SpeechRecognizer.ERROR_SPEECH_TIMEOUT}"), client.calls)
    }

    @Test
    fun `a session segmented by the stream - every utterance, until the stream ends`() = runTest {
        start(RecognitionRequest(audioSource = PcmFormat(), segmented = RecognitionRequest.Segmented.AUDIO_SOURCE))
        send(
            SttEvent.Ready,
            SttEvent.SpeechStarted,
            SttEvent.SpeechEnded,
            SttEvent.Final("one.", nearSilent = false),
            SttEvent.SpeechStarted,
            SttEvent.SpeechEnded,
            SttEvent.Final("two.", nearSilent = false),
        )
        testScheduler.advanceTimeBy(ListenTimeouts.NO_SPEECH_TIMEOUT_MS * 10)
        runCurrent()
        assertEquals(0, stops)
        events.close()
        runCurrent()
        // #124: each utterance's end goes out at once, before its result (Waze stops recording on it).
        assertEquals(
            listOf("ready", "begin", "end", "segment [One.]", "begin", "end", "segment [Two.]", "endOfSegmentedSession"),
            client.calls,
        )
    }

    @Test
    fun `segmented - an utterance's end goes out when it is decided, not after its decode`() = runTest {
        start(RecognitionRequest(audioSource = PcmFormat(), segmented = RecognitionRequest.Segmented.AUDIO_SOURCE))
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.SpeechEnded)
        runCurrent()
        assertEquals(listOf("ready", "begin", "end"), client.calls)
        events.close()
        runCurrent()
    }

    @Test
    fun `segmented - the caller's stop after an utterance ended sends no second end`() = runTest {
        start(RecognitionRequest(audioSource = PcmFormat(), segmented = RecognitionRequest.Segmented.AUDIO_SOURCE))
        send(SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.SpeechEnded, SttEvent.Final("one.", nearSilent = false))
        runCurrent()
        service.stopped()
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "segment [One.]", "endOfSegmentedSession"), client.calls)
    }

    @Test
    fun `segmented - a stop mid-utterance ends it once, and the flush begins no other`() = runTest {
        start(RecognitionRequest(audioSource = PcmFormat(), segmented = RecognitionRequest.Segmented.AUDIO_SOURCE))
        send(SttEvent.Ready, SttEvent.SpeechStarted)
        runCurrent()
        service.stopped()
        // The engine flushes what it had read: the utterance in progress, and one after it.
        send(
            SttEvent.SpeechEnded,
            SttEvent.Final("one.", nearSilent = false),
            SttEvent.SpeechStarted,
            SttEvent.SpeechEnded,
            SttEvent.Final("two.", nearSilent = false),
        )
        events.close()
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "segment [One.]", "segment [Two.]", "endOfSegmentedSession"), client.calls)
    }

    @Test
    fun `a plain request - one beginning and one end, also over utterances that leave no text`() = runTest {
        start(RecognitionRequest(audioSource = PcmFormat()))
        send(
            SttEvent.Ready,
            SttEvent.SpeechStarted,
            SttEvent.SpeechEnded,
            // Noise: no text, so listening goes on.
            SttEvent.Final(" ", nearSilent = true),
            SttEvent.SpeechStarted,
            SttEvent.SpeechEnded,
            SttEvent.Final("navigate home.", nearSilent = false),
        )
        runCurrent()
        assertEquals(listOf("ready", "begin", "end", "results [Navigate home.]"), client.calls)
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
