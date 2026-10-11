package app.marmalade.stt.speech

import kotlinx.coroutines.flow.Flow

/**
 * A model loaded into native memory, held by an engine between sessions.
 * The interfaces below keep the sessions free of native code, so they run in
 * plain JVM tests with fakes. Production implementations are in
 * SherpaBackends.kt.
 */
interface LoadedModel {
    /** Free native memory. The model is unusable afterwards. */
    fun release()
}

/**
 * An offline (whole-utterance) transcriber: Whisper, Parakeet, Canary or
 * Qwen3-ASR ([SherpaOfflineTranscriber]). Run by [StreamingSession].
 */
interface OfflineTranscriber : LoadedModel {
    /**
     * Transcribe a complete chunk of mono float PCM in [-1, 1].
     * [isCancelled] is the caller's cancel flag: read once the decode can be
     * stopped, so a [cancel] that came just before that isn't missed (#49).
     */
    fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean = { false }): String

    /**
     * Stop the [transcribe] running on another thread, if any: it returns
     * early, with text nobody should use. Safe from any thread (#49). Set
     * the flag [transcribe] reads first. Whisper, Qwen3-ASR and Canary stop
     * at their next decoder step, or inside their encoder run (#54); the
     * others' decodes have no steps to stop between and are quick.
     */
    fun cancel() {}
}

/**
 * A true streaming recognizer (Nemotron, [SherpaStreamingRecognizer]). Run by
 * [OnlineSession]. Each utterance gets its own [RecognizerStream].
 */
interface StreamingRecognizer : LoadedModel {
    fun createStream(): RecognizerStream
}

/** One utterance's decoding state inside a [StreamingRecognizer]. Not thread-safe. */
interface RecognizerStream {
    /** Queue 16 kHz mono float PCM. */
    fun acceptWaveform(samples: FloatArray)

    /** Decode every complete chunk queued so far. Blocking; may take a while. */
    fun decode()

    /** The text decoded so far (the whole utterance, not a delta). */
    fun text(): String

    /** No more audio is coming: [decode] then flushes the model's lookahead. */
    fun inputFinished()

    /** Free the stream's native memory. */
    fun release()
}

/** Length of [warmUpDecode]'s silence: 0.5 s. */
internal const val WARM_UP_SAMPLES = MIC_SAMPLE_RATE / 2

/**
 * Decodes [WARM_UP_SAMPLES] of silence and throws the text away (#31). ONNX
 * Runtime's first run after a load allocates and plans its buffers; doing it
 * here takes that cost off the first real decode. Blocking.
 */
internal fun warmUpDecode(model: LoadedModel) {
    val silence = FloatArray(WARM_UP_SAMPLES)
    when (model) {
        is OfflineTranscriber -> model.transcribe(silence, MIC_SAMPLE_RATE)
        is StreamingRecognizer -> {
            val stream = model.createStream()
            try {
                stream.acceptWaveform(silence)
                stream.inputFinished()
                stream.decode()
            } finally {
                stream.release()
            }
        }
    }
}

/**
 * One speech segment from a [SpeechVad].
 *
 * @property start index of its first sample, counted in samples accepted
 *   since the last [SpeechVad.reset]. Silero starts a segment only about
 *   0.3 s before it detects speech, so the sessions prepend their own
 *   pre-roll from this point back.
 */
class VadSegment(val start: Int, val samples: FloatArray)

/**
 * A streaming voice-activity detector that assembles speech segments, shaped
 * after the sherpa-onnx `Vad` surface. Production: Silero ([SherpaSileroVad]).
 */
interface SpeechVad {
    /** Feed one analysis window of [StreamingSession.VAD_WINDOW_SIZE] samples. */
    fun acceptWaveform(samples: FloatArray)

    /** True while the VAD believes speech is in progress. */
    fun isSpeechDetected(): Boolean

    /** True when no completed speech segment is queued. */
    fun empty(): Boolean

    /** The oldest completed segment. Only valid when not [empty]. */
    fun front(): VadSegment

    /** Drop the oldest completed segment. */
    fun pop()

    /** Samples seen since the last completed segment become a segment (end of input). */
    fun flush()

    /** Clear all state for a fresh session. */
    fun reset()

    /** Free native memory. The VAD is unusable afterwards. */
    fun release()
}

/**
 * One listening session: mic in, [SttEvent]s out. [StreamingSession] runs an
 * offline model over VAD segments; [OnlineSession] runs a streaming one.
 * Create one per listen and collect [run] once.
 */
interface ListeningSession {
    /**
     * Finish: stop reading the mic, flush what was heard, then end the flow.
     * Safe from any thread. Hops the mic has delivered but the session
     * hasn't read yet are dropped, so [SherpaSttEngine] doesn't call it: it
     * closes the mic instead, and the session ends once it has read the
     * audio kept while the model loaded (#78).
     */
    fun stop()

    fun run(): Flow<SttEvent>
}
