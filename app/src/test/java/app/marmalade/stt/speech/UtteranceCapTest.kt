package app.marmalade.stt.speech

import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.speech.StreamingSession.Companion.VAD_WINDOW_SIZE
import app.marmalade.stt.speech.UtteranceCap.MAX_DECODE_SAMPLES
import app.marmalade.stt.speech.turn.TurnDetector
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hard cap on one decode (#48): Silero's `max_speech_duration` isn't a
 * hard cut, so [StreamingSession] cuts continuous speech itself at a quiet
 * point before [MAX_DECODE_SAMPLES] ([UtteranceCap]), and the benchmarks'
 * splitter ([vadUtterances]) cuts it the same way. [FakeVad] here never
 * closes a segment on its own unless told to (speech from the first window
 * on), as Silero with no pause to end at; stop() flushes what is left.
 *
 * With 100 ms hops of continuous speech the cut is due after hop 285 and
 * searches hops ~270-285; a quiet hop there is the gap it cuts in. Under
 * Whisper's Hindi cap (21 s, #54) it is due after hop 208 and searches hops
 * ~194-209.
 */
class UtteranceCapTest {

    private suspend fun run(
        script: List<FloatArray>,
        transcriber: OfflineTranscriber,
        vad: SpeechVad,
        options: SessionOptions,
        maxDecodeSamples: Int = MAX_DECODE_SAMPLES,
    ): List<SttEvent> {
        lateinit var s: StreamingSession
        val mic = FakeMic(script, afterScript = { s.stop() })
        // A stopped clock: no live partials, so every decode is a Final's.
        s = StreamingSession(transcriber, vad, options, openMic = { _, _ -> mic }, nowMs = { 0L }, maxDecodeSamples = maxDecodeSamples)
        return s.run().toList().filterNot { it is SttEvent.Level }
    }

    private fun List<SttEvent>.finals() = filterIsInstance<SttEvent.Final>()

    /** [hops] of continuous loud speech, with a quiet hop (a gap between words) at each of [quietAt]. */
    private fun speech(hops: Int, vararg quietAt: Int) = List(hops) { if (it in quietAt) quietHop else loudHop }

    private fun assertWithinCap(transcriber: FakeTranscriber, cap: Int = MAX_DECODE_SAMPLES) {
        for (call in transcriber.calls) assertTrue("decoded ${call.size} samples, cap $cap", call.size <= cap)
    }

    /** Nothing lost or decoded twice: the decodes add up to the [hops] the VAD saw (all but its last part-window). */
    private fun assertCovers(transcriber: FakeTranscriber, hops: Int) {
        val total = transcriber.calls.sumOf { it.size }
        assertTrue("decoded $total of ${hops * HOP}", total in hops * HOP - VAD_WINDOW_SIZE..hops * HOP)
    }

    @Test
    fun `40 s of continuous speech is cut at the gap before the cap, and the tail is decoded`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("first part", "the tail"))

        val events = run(speech(400, 278), transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.PAUSE))

        assertEquals(listOf(SttEvent.Final("first part", false), SttEvent.Final("the tail", false)), events.finals())
        assertEquals(2, transcriber.calls.size)
        assertWithinCap(transcriber)
        assertTrue("cut at ${transcriber.calls[0].size}", transcriber.calls[0].size in 278 * HOP..279 * HOP)
        assertCovers(transcriber, 400)
    }

    @Test
    fun `Tap stop joins the cut parts into one Final`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("first part", "the tail"))

        val events = run(speech(400, 278), transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.TAP_STOP))

        assertEquals(listOf(SttEvent.Final("first part the tail", false)), events.finals())
        assertTrue(events.contains(SttEvent.Partial("first part")))
        assertWithinCap(transcriber)
        assertCovers(transcriber, 400)
    }

    @Test
    fun `speech longer than two caps is cut twice`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("one", "two", "three"))

        val events = run(speech(700), transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.TAP_STOP))

        assertEquals(listOf(SttEvent.Final("one two three", false)), events.finals())
        assertEquals(3, transcriber.calls.size)
        assertWithinCap(transcriber)
        assertCovers(transcriber, 700)
    }

    @Test
    fun `a short word left after the cut is decoded`() = runTest(timeout = TEST_TIMEOUT) {
        // The gap is at the end of the search window and the VAD closes the
        // segment a window after the cut: ~0.17 s of speech is left
        // ("…and that's it"). Speech then goes on as a new segment.
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(457_216))
        val transcriber = FakeTranscriber(listOf("and that's", "it.", "next"))

        val events = run(speech(300, 284), transcriber, vad, SessionOptions(end = EndOfDictation.PAUSE))

        assertEquals(listOf("and that's", "it.", "next"), events.finals().map { it.text })
        assertTrue("rest of ${transcriber.calls[1].size}", transcriber.calls[1].size < 4_000)
        assertWithinCap(transcriber)
    }

    @Test
    fun `a near-silent rest after the cut is not decoded`() = runTest(timeout = TEST_TIMEOUT) {
        // The speech stopped at the gap; what the VAD's segment has after the cut is quiet.
        val vad = FakeVad(speechFromSample = 1, segmentsAt = listOf(457_216))
        val transcriber = FakeTranscriber(listOf("first part", "next"))

        val events = run(speech(300, 284, 285, 286), transcriber, vad, SessionOptions(end = EndOfDictation.PAUSE))

        assertEquals(listOf("first part", "next"), events.finals().map { it.text })
        assertEquals(2, transcriber.calls.size)
    }

    @Test
    fun `a new segment that starts just before the cut is a whole utterance, not a rest`() = runTest(timeout = TEST_TIMEOUT) {
        // Silero ended the cut segment at 444 000, before the cut (~445 000),
        // and the next segment starts at 444 500: it reaches back over the
        // cut, and is decoded whole, as any segment next to the previous one.
        val vad = ScriptedVad(closeAt = 457_216, firstEnd = 444_000, secondStart = 444_500)
        val transcriber = FakeTranscriber(listOf("first part", "next"))

        val events = run(speech(300, 278), transcriber, vad, SessionOptions(end = EndOfDictation.PAUSE))

        assertEquals(listOf("first part", "next"), events.finals().map { it.text })
        assertEquals(2, transcriber.calls.size)
        assertEquals(vad.accepted - 444_500, transcriber.calls[1].size)
    }

    @Test
    fun `End word - a cut just after a mid-sentence end word doesn't end the dictation`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("Game over. Over.", "And the rest."))

        val events = run(speech(400, 278), transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.END_WORD))

        assertEquals(listOf(SttEvent.Final("Game over. Over. And the rest.", false)), events.finals())
    }

    @Test
    fun `End word - an end word just after the cut still ends the dictation`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("Lock the bridge.", "Over."))

        val events = run(speech(400, 278), transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.END_WORD))

        assertEquals(listOf(SttEvent.Final("Lock the bridge.", false, endWord = true)), events.finals())
    }

    @Test
    fun `End word - an end word ending the cut part ends the dictation when nothing follows it`() = runTest(timeout = TEST_TIMEOUT) {
        val transcriber = FakeTranscriber(listOf("Lock the bridge. Over.", ""))

        val events = run(speech(400, 278), transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.END_WORD))

        assertEquals(listOf(SttEvent.Final("Lock the bridge.", false, endWord = true)), events.finals())
    }

    @Test
    fun `Smart Turn - one segment longer than the cap is cut too`() = runTest(timeout = TEST_TIMEOUT) {
        // No segment closes, so there is no boundary to end the turn at.
        val neverComplete = object : TurnDetector {
            override fun completeProbability(audio: FloatArray) = 0f
        }
        val transcriber = FakeTranscriber(listOf("first part", "the tail"))
        val options = SessionOptions(end = EndOfDictation.SMART_TURN, turnDetector = neverComplete)

        val events = run(speech(400, 278), transcriber, FakeVad(speechFromSample = 1), options)

        assertEquals(listOf(SttEvent.Final("first part", false), SttEvent.Final("the tail", false)), events.finals())
        assertWithinCap(transcriber)
        assertCovers(transcriber, 400)
    }

    @Test
    fun `Whisper gets a 21 s cap in Hindi only`() {
        assertEquals(21 * MIC_SAMPLE_RATE, UtteranceCap.maxDecodeSamples(EngineFamily.WHISPER, "hi"))
        for (language in listOf("", "en", "ja", "zh", "ar")) {
            assertEquals(language, MAX_DECODE_SAMPLES, UtteranceCap.maxDecodeSamples(EngineFamily.WHISPER, language))
        }
        for (family in EngineFamily.entries - EngineFamily.WHISPER) {
            assertEquals(family.name, MAX_DECODE_SAMPLES, UtteranceCap.maxDecodeSamples(family, "hi"))
        }
    }

    @Test
    fun `under Whisper's Hindi cap, 35 s of speech is cut at the gap before 21 s`() = runTest(timeout = TEST_TIMEOUT) {
        val cap = UtteranceCap.maxDecodeSamples(EngineFamily.WHISPER, "hi")
        val transcriber = FakeTranscriber(listOf("first part", "the tail"))

        val events = run(speech(350, 200), transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.PAUSE), cap)

        assertEquals(listOf("first part", "the tail"), events.finals().map { it.text })
        assertWithinCap(transcriber, cap)
        assertTrue("cut at ${transcriber.calls[0].size}", transcriber.calls[0].size in 200 * HOP..201 * HOP)
        assertCovers(transcriber, 350)
    }

    @Test
    fun `under Whisper's Hindi cap, a Smart Turn ends at its last segment before 21 s`() = runTest(timeout = TEST_TIMEOUT) {
        // Speech throughout, a segment every 5 s, a model that never calls the turn complete.
        val cap = UtteranceCap.maxDecodeSamples(EngineFamily.WHISPER, "hi")
        val neverComplete = object : TurnDetector {
            override fun completeProbability(audio: FloatArray) = 0f
        }
        val vad = FakeVad(speechFromSample = 1, segmentsAt = (1..5).map { it * 5 * MIC_SAMPLE_RATE })
        val transcriber = FakeTranscriber(listOf("first turn", "second turn", "third turn"))
        val options = SessionOptions(end = EndOfDictation.SMART_TURN, turnDetector = neverComplete)

        run(speech(260), transcriber, vad, options, cap)

        // 0-20 s (four segments), then 20-26 s: no decode over the cap, and nothing lost.
        assertEquals(2, transcriber.calls.size)
        assertWithinCap(transcriber, cap)
        val first = transcriber.calls[0].size
        assertTrue("first turn is $first samples", first in 20 * MIC_SAMPLE_RATE..cap - StreamingSession.PREROLL_SAMPLES)
        assertCovers(transcriber, 260)
    }

    @Test
    fun `the benchmarks' splitter cuts where the live session cuts`() = runTest(timeout = TEST_TIMEOUT) {
        for (script in listOf(speech(400, 278), speech(700), speech(300, 284))) {
            val transcriber = FakeTranscriber(listOf("x"))
            run(script, transcriber, FakeVad(speechFromSample = 1), SessionOptions(end = EndOfDictation.TAP_STOP))
            // The same audio as the session got it: high-passed hop by hop.
            val preprocessor = AudioPreprocessor()
            val take = script.map { preprocessor.process(it.copyOf()) }.reduce { a, b -> a + b }

            val utterances = vadUtterances(FakeVad(speechFromSample = 1), take, StreamingSession.PREROLL_SAMPLES, maxDecodeSamples = MAX_DECODE_SAMPLES)

            // The same cuts. (The splitter also feeds the take's last full
            // window, which the live session leaves for a hop that never comes.)
            val live = transcriber.calls.map { it.size }
            assertEquals(live.dropLast(1), utterances.map { it.size }.dropLast(1))
            assertTrue("$live vs ${utterances.map { it.size }}", utterances.last().size - live.last() in 0..VAD_WINDOW_SIZE)
        }
    }

    /**
     * Speech detected throughout; once [closeAt] samples are accepted, one
     * segment [0, [firstEnd]) closes and the next starts at [secondStart]
     * (flushed by stop). Segments hold the samples accepted there.
     */
    private class ScriptedVad(private val closeAt: Int, private val firstEnd: Int, private val secondStart: Int) : SpeechVad {
        private val audio = SampleBuffer()
        private val segments = ArrayDeque<VadSegment>()
        private var closed = false
        val accepted get() = audio.size

        override fun acceptWaveform(samples: FloatArray) {
            audio.append(samples)
            if (!closed && audio.size >= closeAt) {
                closed = true
                segments.addLast(VadSegment(0, audio.copyRange(0, firstEnd)))
            }
        }

        override fun isSpeechDetected() = true
        override fun empty() = segments.isEmpty()
        override fun front() = segments.first()
        override fun pop() {
            segments.removeFirst()
        }

        override fun flush() {
            segments.addLast(VadSegment(secondStart, audio.copyRange(secondStart, audio.size)))
        }

        override fun reset() = Unit
        override fun release() = Unit
    }
}
