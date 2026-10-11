package app.marmalade.stt.speech

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals

/** Every session test finishes in well under a second; a hang is a failure, not a 60 s wait. */
internal val TEST_TIMEOUT = 10.seconds

/** The session's 100 ms mic hop. */
internal const val HOP = StreamingSession.MIC_HOP_SAMPLES

/** 100 ms of an 8 kHz square wave: passes the 100 Hz high-pass unchanged. */
internal fun squareHop(amplitude: Float) = FloatArray(HOP) { if (it % 2 == 0) amplitude else -amplitude }

internal val loudHop = squareHop(0.5f) // about -6 dBFS
internal val quietHop = squareHop(0.001f) // about -60 dBFS: near-silent and silent
internal val softHop = squareHop(0.0018f) // about -55 dBFS: near-silent, not silent (#57)

/**
 * Mic that plays [script] (a copy of each hop: the session filters in place),
 * then runs [afterScript] (typically a stop()), then either throws
 * [failAfterScript] or emits silent hops every 5 ms until it is closed. By
 * default it reports a platform AGC, so the session applies no [SoftAgc]. As
 * with a real mic, a stop (the session's flag, or the engine closing the
 * mic), not the flow running dry, is what ends capture. [emitted] counts the
 * hops it has delivered.
 */
internal class FakeMic(
    private val script: List<FloatArray> = emptyList(),
    private val afterScript: (() -> Unit)? = null,
    private val failAfterScript: Exception? = null,
    override val hasPlatformAgc: Boolean = true,
) : MicCapture {
    @Volatile
    var closed = false
        private set

    val emitted = java.util.concurrent.atomic.AtomicInteger()

    override val hops: Flow<FloatArray> = flow {
        for (hop in script) {
            if (closed) return@flow
            emitted.incrementAndGet()
            emit(hop.copyOf())
        }
        afterScript?.invoke()
        failAfterScript?.let { throw it }
        while (!closed) {
            delay(5)
            if (closed) break
            emitted.incrementAndGet()
            emit(FloatArray(HOP))
        }
    }

    override fun close() {
        closed = true
    }
}

/**
 * Deterministic VAD, a pure function of the number of samples accepted:
 * speech is "detected" while that count is in [speechFromSample,
 * speechUntilSample). A segment completes each time the count reaches the
 * next entry of [segmentsAt]; it holds every window accepted since the
 * previous segment, except windows starting before [segmentFrom] (Silero
 * starts its segment only shortly before it detects speech, and at mic open
 * after the first word has begun). [VadSegment.start] is its first window's
 * sample index.
 */
internal class FakeVad(
    private val speechFromSample: Int,
    private val speechUntilSample: Int = Int.MAX_VALUE,
    segmentsAt: List<Int> = emptyList(),
    private val segmentFrom: Int = 0,
) : SpeechVad {
    private val boundaries = ArrayDeque(segmentsAt)
    private var accepted = 0
    private var collectedFrom = -1 // sample index of the first collected window
    private val collected = mutableListOf<FloatArray>() // windows since the last segment
    private val segments = ArrayDeque<VadSegment>()

    @Volatile
    var resetCalls = 0
        private set

    @Volatile
    var releaseCalls = 0
        private set

    override fun acceptWaveform(samples: FloatArray) {
        assertEquals(StreamingSession.VAD_WINDOW_SIZE, samples.size)
        if (accepted >= segmentFrom) {
            if (collected.isEmpty()) collectedFrom = accepted
            collected += samples.copyOf()
        }
        accepted += samples.size
        if (boundaries.isNotEmpty() && accepted >= boundaries.first()) {
            boundaries.removeFirst()
            completeSegment()
        }
    }

    private fun completeSegment() {
        if (collected.isEmpty()) return
        segments.addLast(VadSegment(collectedFrom, collected.reduce { a, b -> a + b }))
        collected.clear()
    }

    override fun isSpeechDetected() = accepted in speechFromSample until speechUntilSample
    override fun empty() = segments.isEmpty()
    override fun front() = segments.first()
    override fun pop() {
        segments.removeFirst()
    }

    override fun flush() = completeSegment()

    override fun reset() {
        resetCalls++
    }

    override fun release() {
        releaseCalls++
    }
}

/** Transcriber answering with [respond]; records every call's samples. */
internal class FakeTranscriber(private val respond: (FloatArray) -> String) : OfflineTranscriber {
    /** Answers [texts] in call order, then the last one for ever. */
    constructor(texts: List<String>) : this(ScriptedAnswers(texts)::next)

    val calls: MutableList<FloatArray> = java.util.Collections.synchronizedList(mutableListOf())

    @Volatile
    var releaseCalls = 0
        private set

    override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean): String {
        assertEquals(MIC_SAMPLE_RATE, sampleRate)
        calls += samples
        return respond(samples)
    }

    override fun release() {
        releaseCalls++
    }

    private class ScriptedAnswers(private val texts: List<String>) {
        private var n = 0
        fun next(@Suppress("UNUSED_PARAMETER") samples: FloatArray): String = texts[minOf(n++, texts.lastIndex)]
    }
}

/** Clock that never crosses the 200 ms partial gate (1 ms per reading). */
internal fun slowClock(): () -> Long {
    var t = 0L
    return { ++t }
}

/** Clock that crosses the 200 ms partial gate at every check (300 ms per reading). */
internal fun fastClock(): () -> Long {
    var t = 0L
    return { t += 300; t }
}
