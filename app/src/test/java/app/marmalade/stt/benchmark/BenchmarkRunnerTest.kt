package app.marmalade.stt.benchmark

import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.TakeTranscriber
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkRunnerTest {

    private fun engine(id: String) = SttEngineDescriptor(
        id = id,
        displayName = "Engine $id",
        description = 0,
        family = EngineFamily.WHISPER,
        languages = listOf("en"),
        licenseId = "MIT",
        licenseKey = "test",
        files = emptyList(),
    )

    private val a = engine("a")
    private val b = engine("b")
    private val c = engine("c")

    /** 2 s of audio. */
    private val take = FloatArray(2 * MIC_SAMPLE_RATE)

    /**
     * Records every load / transcribe / release in order, tracks how many
     * engines are loaded at once, and advances a fake clock by the per-engine
     * load and decode times.
     */
    private inner class Harness(
        val failLoad: Set<String> = emptySet(),
        val failTranscribe: Set<String> = emptySet(),
        val loadMs: Map<String, Long> = emptyMap(),
        val decodeMs: Map<String, Long> = emptyMap(),
        val cleanMs: Long = 0L,
    ) {
        val calls = mutableListOf<String>()
        val takesSeen = mutableListOf<FloatArray>()
        var loaded = 0
        var maxLoaded = 0
        var clockNanos = 0L

        fun load(d: SttEngineDescriptor): TakeTranscriber {
            calls += "load ${d.id}"
            clockNanos += (loadMs[d.id] ?: 0L) * 1_000_000L
            if (d.id in failLoad) throw IllegalStateException("no model for ${d.id}")
            loaded++
            maxLoaded = maxOf(maxLoaded, loaded)
            return object : TakeTranscriber {
                override fun utterances(take: FloatArray) = listOf(take)

                override fun transcribeUtterance(utterance: FloatArray): String {
                    calls += "transcribe ${d.id}"
                    takesSeen += utterance
                    clockNanos += (decodeMs[d.id] ?: 0L) * 1_000_000L
                    if (d.id in failTranscribe) throw RuntimeException("decode blew up")
                    return "text ${d.id}"
                }

                override fun release() {
                    calls += "release ${d.id}"
                    loaded--
                }
            }
        }

        /** Records the call, advances the clock by [cleanMs], and upper-cases the text. */
        fun clean(d: SttEngineDescriptor, utterances: List<String>): String {
            calls += "clean ${d.id}"
            clockNanos += cleanMs * 1_000_000L
            return utterances.joinToString(" ").uppercase()
        }

        fun runner(rss: () -> Long? = { null }) = BenchmarkRunner(
            load = ::load,
            clean = ::clean,
            nanoTime = { clockNanos },
            rssProbe = rss,
            // Single-threaded and never idle during the blocking fakes, so
            // only the runner's fixed checkpoints read the probe.
            workContext = Dispatchers.Unconfined,
            samplerContext = Dispatchers.Unconfined,
            rssSampleIntervalMs = Long.MAX_VALUE,
        )
    }

    private fun results(events: List<BenchmarkEvent>) = events.filterIsInstance<BenchmarkEvent.Finished>().map { it.result }

    @Test
    fun `engines run strictly one at a time, each released before the next loads`() = runTest {
        val h = Harness()
        val events = h.runner().run(take, listOf(a, b, c)).toList()

        assertEquals(
            listOf(
                "load a", "transcribe a", "release a", "clean a",
                "load b", "transcribe b", "release b", "clean b",
                "load c", "transcribe c", "release c", "clean c",
            ),
            h.calls,
        )
        assertEquals(1, h.maxLoaded)
        assertEquals(0, h.loaded)
        assertEquals(
            listOf("Started a", "Finished a", "Started b", "Finished b", "Started c", "Finished c"),
            events.map {
                when (it) {
                    is BenchmarkEvent.Started -> "Started ${it.engine.id}"
                    is BenchmarkEvent.Finished -> "Finished ${it.engineId}"
                    is BenchmarkEvent.Failed -> "Failed ${it.engineId}"
                }
            },
        )
        assertEquals(listOf("TEXT A", "TEXT B", "TEXT C"), results(events).map { it.text })
        assertEquals(listOf("text a", "text b", "text c"), results(events).map { it.rawText })
    }

    @Test
    fun `the cleanup runs after the release and counts in no measurement`() = runTest {
        val readings = ArrayDeque(listOf<Long?>(100L, 200L, 300L))
        val h = Harness(loadMs = mapOf("a" to 1_000L), decodeMs = mapOf("a" to 500L), cleanMs = 60_000L)
        val r = results(h.runner(rss = { readings.removeFirst() }).run(take, listOf(a)).toList()).single()

        assertEquals(listOf("load a", "transcribe a", "release a", "clean a"), h.calls)
        assertEquals(1_000L, r.loadMs)
        assertEquals(500L, r.decodeMs)
        assertEquals(0.25, r.rtf, 1e-9)
        assertEquals(300L, r.peakRssBytes)
        assertTrue(readings.isEmpty())
        assertEquals("TEXT A", r.text)
    }

    @Test
    fun `the cleanup gets each utterance's text, blanks skipped`() = runTest {
        val seen = mutableListOf<List<String>>()
        val split = object : TakeTranscriber {
            override fun utterances(take: FloatArray) = listOf(FloatArray(1), FloatArray(2), FloatArray(3))
            override fun transcribeUtterance(utterance: FloatArray) = listOf("um, hello.", "", "world")[utterance.size - 1]
            override fun release() = Unit
        }
        val runner = BenchmarkRunner(
            load = { split },
            clean = { _, u -> seen += u; "cleaned" },
            nanoTime = { 0L },
            rssProbe = { null },
            workContext = Dispatchers.Unconfined,
            samplerContext = Dispatchers.Unconfined,
        )
        val r = results(runner.run(take, listOf(a)).toList()).single()

        assertEquals(listOf(listOf("um, hello.", "world")), seen)
        assertEquals("um, hello. world", r.rawText)
        assertEquals("cleaned", r.text)
    }

    @Test
    fun `a failed engine is not cleaned`() = runTest {
        val h = Harness(failTranscribe = setOf("a"), failLoad = setOf("b"))
        h.runner().run(take, listOf(a, b)).collect()
        assertTrue(h.calls.none { it.startsWith("clean") })
    }

    @Test
    fun `a load failure is reported and the next engine still runs`() = runTest {
        val h = Harness(failLoad = setOf("b"))
        val events = h.runner().run(take, listOf(a, b, c)).toList()

        assertEquals(
            listOf("load a", "transcribe a", "release a", "clean a", "load b", "load c", "transcribe c", "release c", "clean c"),
            h.calls,
        )
        val failed = events.filterIsInstance<BenchmarkEvent.Failed>().single()
        assertEquals("b", failed.engineId)
        assertEquals(BenchmarkEvent.Stage.LOAD, failed.stage)
        assertEquals("no model for b", failed.message)
        assertEquals(listOf("a", "c"), results(events).map { it.engineId })
    }

    @Test
    fun `a transcription failure still releases the engine`() = runTest {
        val h = Harness(failTranscribe = setOf("a"))
        val events = h.runner().run(take, listOf(a, b)).toList()

        assertEquals(listOf("load a", "transcribe a", "release a", "load b", "transcribe b", "release b", "clean b"), h.calls)
        assertEquals(0, h.loaded)
        val failed = events.filterIsInstance<BenchmarkEvent.Failed>().single()
        assertEquals(BenchmarkEvent.Stage.TRANSCRIBE, failed.stage)
        assertEquals("decode blew up", failed.message)
    }

    @Test
    fun `every engine gets the very same take array, unmodified`() = runTest {
        val h = Harness()
        val original = take.copyOf()
        h.runner().run(take, listOf(a, b, c)).collect()

        assertEquals(3, h.takesSeen.size)
        h.takesSeen.forEach { assertSame(take, it) }
        assertTrue(original.contentEquals(take))
    }

    @Test
    fun `load and decode times come from the clock and RTF is decode over audio`() = runTest {
        val h = Harness(loadMs = mapOf("a" to 1_200L, "b" to 300L), decodeMs = mapOf("a" to 500L, "b" to 3_000L))
        val r = results(h.runner().run(take, listOf(a, b)).toList())

        assertEquals(1_200L, r[0].loadMs)
        assertEquals(500L, r[0].decodeMs)
        assertEquals(2_000L, r[0].audioMs)
        assertEquals(0.25, r[0].rtf, 1e-9)
        assertEquals(300L, r[1].loadMs)
        assertEquals(3_000L, r[1].decodeMs)
        assertEquals(1.5, r[1].rtf, 1e-9)
    }

    @Test
    fun `peak RSS is the largest probe reading, skipping unreadable ones`() = runTest {
        // Engine a: readings at load start, after load, after transcribe.
        val readings = ArrayDeque(listOf<Long?>(100L, 900L, null, 200L, 300L, 250L))
        val h = Harness()
        val r = results(h.runner(rss = { readings.removeFirst() }).run(take, listOf(a, b)).toList())

        assertEquals(900L, r[0].peakRssBytes)
        assertEquals(300L, r[1].peakRssBytes)
        assertTrue(readings.isEmpty())
    }

    @Test
    fun `peak RSS is null when the probe never reads`() = runTest {
        val r = results(Harness().runner(rss = { null }).run(take, listOf(a)).toList())
        assertNull(r.single().peakRssBytes)
    }

    @Test
    fun `the periodic sampler catches a peak inside a long transcription`() = runBlocking {
        // Real threads: the probe reports 800 only while "decoding"; the
        // checkpoints before and after see 100.
        val rss = AtomicLong(100L)
        val slow = object : TakeTranscriber {
            override fun utterances(take: FloatArray) = listOf(take)

            override fun transcribeUtterance(utterance: FloatArray): String {
                rss.set(800L)
                Thread.sleep(200)
                rss.set(100L)
                return "x"
            }

            override fun release() = Unit
        }
        val runner = BenchmarkRunner(load = { slow }, clean = { _, u -> u.joinToString(" ") }, rssProbe = { rss.get() }, rssSampleIntervalMs = 10)
        val r = withTimeout(5_000) { results(runner.run(take, listOf(a)).toList()) }
        assertEquals(800L, r.single().peakRssBytes)
    }

    @Test
    fun `cancelling during a load releases that engine and loads no other`() {
        val h = Harness()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val runner = BenchmarkRunner(
            load = { d ->
                val t = h.load(d)
                scope.cancel() // the screen is left while this engine loads
                t
            },
            clean = h::clean,
            nanoTime = { 0L },
            rssProbe = { null },
            workContext = Dispatchers.Unconfined,
            samplerContext = Dispatchers.Unconfined,
        )
        runBlocking {
            scope.launch { runner.run(take, listOf(a, b)).collect() }.join()
        }
        assertEquals(listOf("load a", "release a"), h.calls)
        assertEquals(0, h.loaded)
    }
}
