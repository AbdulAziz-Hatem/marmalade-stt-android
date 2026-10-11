package app.marmalade.stt.speech

import java.util.Collections
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamingSession]'s live-partial schedule (#20) on virtual time: the VAD
 * advances the clock 32 ms per window (the audio's own duration) and every
 * decode advances it by its cost, both on the consumer thread, so the
 * schedule is deterministic. The mic queues the whole script at once, as a
 * real mic's hops queue up behind a slow decode.
 */
class PartialSchedulingTest {

    private class VirtualClock {
        @Volatile
        var ms = 0L
    }

    private class ClockedVad(private val inner: FakeVad, private val clock: VirtualClock) : SpeechVad by inner {
        override fun acceptWaveform(samples: FloatArray) {
            inner.acceptWaveform(samples)
            clock.ms += samples.size * 1000L / MIC_SAMPLE_RATE
        }
    }

    /** One decode: when it started (virtual ms), its cost, its audio, and whether a closed segment was waiting (then it is a Final). */
    private data class Decode(val startMs: Long, val costMs: Long, val samples: Int, val final: Boolean)

    private class CostTranscriber(
        private val clock: VirtualClock,
        private val vad: SpeechVad,
        private val costMs: Long,
    ) : OfflineTranscriber {
        val decodes: MutableList<Decode> = Collections.synchronizedList(mutableListOf())

        override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean): String {
            decodes += Decode(clock.ms, costMs, samples.size, final = !vad.empty())
            clock.ms += costMs
            return "w${decodes.size}"
        }

        override fun release() = Unit
    }

    private suspend fun run(hops: Int, fakeVad: FakeVad, costMs: Long): Pair<List<SttEvent>, List<Decode>> {
        val clock = VirtualClock()
        val vad = ClockedVad(fakeVad, clock)
        val transcriber = CostTranscriber(clock, vad, costMs)
        lateinit var s: StreamingSession
        val mic = FakeMic(List(hops) { loudHop }, afterScript = { s.stop() })
        // The script is queued at once, so the session starts far "behind" the
        // mic; catch-up mode (#30) would skip the partials under test.
        s = StreamingSession(
            transcriber, vad, options = SessionOptions(), openMic = { _, _ -> mic }, nowMs = { clock.ms }, catchUp = false,
        )
        return s.run().toList() to transcriber.decodes
    }

    @Test
    fun `a segment the VAD has closed is decoded before any partial`() = runTest(timeout = TEST_TIMEOUT) {
        // Continuous speech cut into segments of uneven length, so segments
        // close at every phase of the partial cadence.
        val window = StreamingSession.VAD_WINDOW_SIZE
        val gaps = listOf(23, 29, 31, 37, 41, 43, 47, 53)
        val boundaries = gaps.runningReduce(Int::plus).map { it * window }
        val (events, decodes) = run(hops = 110, FakeVad(speechFromSample = 1, segmentsAt = boundaries), costMs = 60)

        // One decode per segment ran while it waited: its Final. None was a partial.
        assertEquals(gaps.size + 1, events.count { it is SttEvent.Final }) // + the flush on stop
        assertEquals(gaps.size + 1, decodes.count { it.final })
        assertTrue("partials ran", decodes.count { !it.final } > gaps.size)
    }

    @Test
    fun `partials are spaced at least as far apart as the last one took`() = runTest(timeout = TEST_TIMEOUT) {
        val (_, decodes) = run(hops = 60, FakeVad(speechFromSample = 1), costMs = 1_000)

        val partials = decodes.filterNot { it.final }
        assertTrue(partials.size >= 2)
        for ((a, b) in partials.zipWithNext()) {
            assertTrue("gap ${b.startMs - (a.startMs + a.costMs)} ms after a ${a.costMs} ms decode", b.startMs - (a.startMs + a.costMs) >= a.costMs)
        }
    }

    @Test
    fun `a long utterance's partials decode at most the window, the Final all of it`() = runTest(timeout = TEST_TIMEOUT) {
        val (events, decodes) = run(hops = 250, FakeVad(speechFromSample = 1), costMs = 10) // 25 s, flushed on stop

        val partials = decodes.filterNot { it.final }
        assertTrue(partials.all { it.samples <= StreamingSession.PARTIAL_WINDOW_SAMPLES + 2 * HOP })
        assertTrue(decodes.single { it.final }.samples >= 24 * MIC_SAMPLE_RATE)
        // The last partial shows the committed text of the older audio, then the window's.
        val shown = events.filterIsInstance<SttEvent.Partial>().last().text.split(" ")
        assertTrue("partial was $shown", shown.size >= 3)
        assertEquals(shown, shown.distinct())
    }
}
