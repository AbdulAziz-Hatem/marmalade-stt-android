package app.marmalade.stt.benchmark

import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.TakeTranscriber
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** One engine's measurements on the take. */
data class BenchmarkResult(
    val engineId: String,
    val displayName: String,
    /**
     * The take's transcript after the cleanup dictation runs on its Finals
     * ([TakeCleanup]): what dictation would have written. WER is scored on it.
     */
    val text: String,
    /** The engine's own text: each utterance's ([TakeTranscriber.transcribeUtterances]), joined with spaces. */
    val rawText: String,
    val loadMs: Long,
    val decodeMs: Long,
    /** The take's length. */
    val audioMs: Long,
    /** Real-time factor: decode time / audio time. Below 1 is faster than real time. */
    val rtf: Double,
    /** Highest sampled RSS from load start to release, or null when it couldn't be read. */
    val peakRssBytes: Long?,
)

/** What [BenchmarkRunner.run] reports, in order: per engine, [Started] then one [Outcome]. */
sealed interface BenchmarkEvent {
    data class Started(val engine: SttEngineDescriptor) : BenchmarkEvent

    /** How one engine's turn ended. */
    sealed interface Outcome : BenchmarkEvent {
        val engineId: String
        val displayName: String
    }

    data class Finished(val result: BenchmarkResult) : Outcome {
        override val engineId get() = result.engineId
        override val displayName get() = result.displayName
    }

    data class Failed(val engine: SttEngineDescriptor, val stage: Stage, val message: String) : Outcome {
        override val engineId get() = engine.id
        override val displayName get() = engine.displayName
    }

    /** Where a turn failed. [RELEASE]: the transcript was made, but freeing the model threw. */
    enum class Stage { LOAD, TRANSCRIBE, RELEASE }
}

/**
 * Runs one recorded take through engines **one at a time**: load, transcribe,
 * release, then the next. At most one benchmark model is in memory at any
 * moment, so RAM stays bounded and each engine is measured alone, as a user
 * would run it. A failing engine is reported and the run moves on.
 *
 * Measurements:
 * - load / decode: wall-clock around [load] and [TakeTranscriber.transcribeUtterances].
 * - RTF: decode time / take duration.
 * - peak RSS: [rssProbe] is read at load start, after the load and after the
 *   transcription, and every [rssSampleIntervalMs] in between on
 *   [samplerContext], until release. The maximum is reported. It is a sample,
 *   so a spike shorter than the interval can be missed.
 *
 * The transcript is cleaned with [clean] after the engine is released, so the
 * cleanup counts in none of the measurements.
 *
 * Cancelling the collector can't interrupt a blocking load or transcription
 * (native code); the run stops as soon as that call returns, after releasing
 * the engine.
 *
 * @param load loads an engine for a take. Blocking and slow; the runner calls
 *   it on [workContext]. The runner releases what it returns.
 * @param clean an engine's utterance texts to its cleaned transcript
 *   ([TakeCleanup.clean]); called on [workContext], one engine at a time.
 * @param nanoTime monotonic clock.
 * @param rssProbe the process's RSS in bytes, null when unavailable ([ProcRss.read]).
 * @param workContext where loads and transcriptions run.
 * @param samplerContext where the periodic RSS sampler runs; must not share
 *   a single thread with [workContext], or the blocking calls starve it.
 */
class BenchmarkRunner(
    private val load: (SttEngineDescriptor) -> TakeTranscriber,
    private val clean: (SttEngineDescriptor, List<String>) -> String,
    private val nanoTime: () -> Long = System::nanoTime,
    private val rssProbe: () -> Long? = ProcRss::read,
    private val rssSampleIntervalMs: Long = DEFAULT_RSS_SAMPLE_INTERVAL_MS,
    private val workContext: CoroutineContext = Dispatchers.Default,
    private val samplerContext: CoroutineContext = Dispatchers.IO,
) {
    /**
     * Benchmark [take] (16 kHz mono float PCM, passed to every engine as is,
     * never copied or modified here) on [engines], in list order.
     */
    fun run(take: FloatArray, engines: List<SttEngineDescriptor>): Flow<BenchmarkEvent> {
        require(take.isNotEmpty()) { "The take is empty" }
        val audioMs = take.size * 1_000L / MIC_SAMPLE_RATE
        return flow {
            for (engine in engines) {
                emit(BenchmarkEvent.Started(engine))
                emit(runOne(engine, take, audioMs))
            }
        }.flowOn(workContext)
    }

    private suspend fun runOne(engine: SttEngineDescriptor, take: FloatArray, audioMs: Long): BenchmarkEvent.Outcome {
        val peak = PeakRss(rssProbe)
        var transcriber: TakeTranscriber? = null
        var failure: BenchmarkEvent.Failed? = null
        var result: BenchmarkResult? = null
        var utterances = emptyList<String>()
        // The scope returns once the sampler has stopped, so peak.max() below is final.
        coroutineScope {
            peak.sample()
            val sampler = launch(samplerContext) {
                while (true) {
                    delay(rssSampleIntervalMs)
                    peak.sample()
                }
            }
            try {
                val loadStart = nanoTime()
                transcriber = try {
                    load(engine)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = failed(engine, BenchmarkEvent.Stage.LOAD, e)
                    null
                } catch (e: LinkageError) {
                    // A native library that won't link (UnsatisfiedLinkError) is this engine's failure, not the run's.
                    failure = failed(engine, BenchmarkEvent.Stage.LOAD, e)
                    null
                }
                val loadEnd = nanoTime()
                peak.sample()
                // The screen was left during the load: release (below) without transcribing.
                ensureActive()
                val loaded = transcriber
                if (loaded != null) {
                    try {
                        utterances = loaded.transcribeUtterances(take)
                        val decodeEnd = nanoTime()
                        peak.sample()
                        val decodeNanos = decodeEnd - loadEnd
                        val raw = utterances.joinToString(" ")
                        result = BenchmarkResult(
                            engineId = engine.id,
                            displayName = engine.displayName,
                            text = raw, // cleaned below, after the release
                            rawText = raw,
                            loadMs = (loadEnd - loadStart) / NANOS_PER_MS,
                            decodeMs = decodeNanos / NANOS_PER_MS,
                            audioMs = audioMs,
                            rtf = decodeNanos.toDouble() / (audioMs * NANOS_PER_MS),
                            peakRssBytes = null, // set below, once sampling has stopped
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failure = failed(engine, BenchmarkEvent.Stage.TRANSCRIBE, e)
                    }
                }
            } finally {
                // Not a suspending join here: this also runs when cancelled, and must reach release().
                sampler.cancel()
                try {
                    transcriber?.release()
                } catch (e: Exception) {
                    if (failure == null) failure = failed(engine, BenchmarkEvent.Stage.RELEASE, e)
                }
            }
        }
        failure?.let { return it }
        return BenchmarkEvent.Finished(
            checkNotNull(result).copy(text = clean(engine, utterances), peakRssBytes = peak.max()),
        )
    }

    private fun failed(engine: SttEngineDescriptor, stage: BenchmarkEvent.Stage, e: Throwable) =
        BenchmarkEvent.Failed(engine, stage, e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)

    /** Running maximum of [probe]'s readings; nulls (unreadable) are skipped. Thread-safe. */
    private class PeakRss(private val probe: () -> Long?) {
        private val peak = AtomicLong(NONE)

        fun sample() {
            val v = probe() ?: return
            peak.accumulateAndGet(v) { a, b -> maxOf(a, b) }
        }

        fun max(): Long? = peak.get().takeIf { it != NONE }

        private companion object {
            const val NONE = -1L
        }
    }

    companion object {
        const val DEFAULT_RSS_SAMPLE_INTERVAL_MS = 50L
        private const val NANOS_PER_MS = 1_000_000L
    }
}
