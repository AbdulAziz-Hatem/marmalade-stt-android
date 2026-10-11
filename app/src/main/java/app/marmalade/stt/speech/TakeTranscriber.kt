package app.marmalade.stt.speech

/**
 * One engine loaded to transcribe a whole recorded take (the A/B benchmark,
 * #13). The take is split with Silero VAD exactly as a live session would
 * split it, so every engine sees the same utterances, and each utterance is
 * decoded as the live session decodes its Final.
 */
interface TakeTranscriber : LoadedModel {
    /** The take cut into utterances as a live session cuts them ([vadUtterances]). Blocking. */
    fun utterances(take: FloatArray): List<FloatArray>

    /** One utterance's text, trimmed. Blocking. */
    fun transcribeUtterance(utterance: FloatArray): String

    /**
     * The text of each VAD utterance that has any, in order: what a live
     * session would send as Finals. Blocking. The A/B benchmark cleans them
     * one by one, as dictation cleans its Finals (#45). The speed benchmark
     * (#23) calls [utterances] and [transcribeUtterance] itself, to time the
     * decoding without the VAD.
     */
    fun transcribeUtterances(take: FloatArray): List<String> =
        utterances(take).map(::transcribeUtterance).filter { it.isNotEmpty() }
}

/**
 * The take's utterances, as a live session cuts them: each Silero segment
 * (plus whatever is still open at the end) with the audio from [prerollSamples]
 * before its onset in front, never reaching back into the previous utterance.
 * With [padStart] (the streaming family, see [OnlineSession.PREROLL_SAMPLES]),
 * silence makes up a pre-roll cut short by the start of the take or by the
 * previous utterance. With [maxDecodeSamples] (the offline families), an
 * utterance reaching that cap is cut as [StreamingSession] cuts it
 * ([UtteranceCap], #48), checked at the same mic-hop boundaries, so the cut
 * lands on the same window.
 */
internal fun vadUtterances(
    vad: SpeechVad,
    take: FloatArray,
    prerollSamples: Int,
    padStart: Boolean = false,
    maxDecodeSamples: Int? = null,
): List<FloatArray> {
    vad.reset()
    val audio = SampleBuffer().apply { append(take) }
    val window = StreamingSession.VAD_WINDOW_SIZE
    val utterances = mutableListOf<FloatArray>()
    var inSpeech = false
    var start = 0 // where the utterance in progress starts: onset minus the pre-roll
    var floor = 0 // where the previous utterance's audio ended (the live session drops it)
    var pad = 0 // silence in front of the utterance in progress
    var cutAt = 0 // the last cut at the cap
    var restPending = false // the next segment is the one that was cut
    var i = 0
    fun drain() {
        while (!vad.empty()) {
            val segment = vad.front()
            if (restPending) {
                // Only its rest after the cut is left, if that holds speech (as in the live session).
                restPending = false
                val rest = segment.samples.copyOfRange((cutAt - segment.start).coerceIn(0, segment.samples.size), segment.samples.size)
                if (UtteranceCap.restHasSpeech(rest.size, NearSilence.isNearSilent(NearSilence.rmsDb(rest)))) utterances += rest
            } else {
                utterances += FloatArray(pad) + withPreroll(audio, start, segment.start, segment.samples)
            }
            pad = 0
            vad.pop()
            inSpeech = false
            floor = i
        }
    }
    while (i + window <= take.size) {
        vad.acceptWaveform(take.copyOfRange(i, i + window))
        i += window
        if (!inSpeech && vad.isSpeechDetected()) {
            inSpeech = true
            start = maxOf(i - prerollSamples, floor)
            if (padStart) pad = prerollSamples - (i - start)
        }
        drain()
        // The live session checks the cap once per 100 ms hop, after the hop's last window.
        val endOfHop = i + window >= (i / StreamingSession.MIC_HOP_SAMPLES + 1) * StreamingSession.MIC_HOP_SAMPLES
        if (maxDecodeSamples != null && inSpeech && endOfHop && UtteranceCap.isDue(i - start, maxDecodeSamples)) {
            val cut = UtteranceCap.cutPoint(audio, i)
            utterances += audio.copyRange(start, cut)
            start = cut
            floor = cut
            cutAt = cut
            restPending = true
        }
    }
    vad.flush()
    drain()
    return utterances
}

/**
 * Offline families: transcribe each utterance, as a live session transcribes
 * its Finals, cut at [maxDecodeSamples] as the session cuts them
 * ([UtteranceCap.maxDecodeSamples]).
 */
internal class OfflineTakeTranscriber(
    private val model: OfflineTranscriber,
    private val vad: SpeechVad,
    private val maxDecodeSamples: Int = UtteranceCap.MAX_DECODE_SAMPLES,
) : TakeTranscriber {
    override fun utterances(take: FloatArray): List<FloatArray> =
        vadUtterances(vad, take, StreamingSession.PREROLL_SAMPLES, maxDecodeSamples = maxDecodeSamples)

    override fun transcribeUtterance(utterance: FloatArray): String =
        model.transcribe(utterance, MIC_SAMPLE_RATE).trim()

    override fun release() {
        try {
            model.release()
        } finally {
            vad.release()
        }
    }
}

/**
 * The streaming family: each utterance goes to a fresh stream in 100 ms
 * chunks (the live mic hop), decoding after every chunk, **as fast as the
 * model takes them** (not paced to real time), then the stream is flushed.
 */
internal class StreamingTakeTranscriber(
    private val model: StreamingRecognizer,
    private val vad: SpeechVad,
) : TakeTranscriber {
    override fun utterances(take: FloatArray): List<FloatArray> =
        vadUtterances(vad, take, OnlineSession.PREROLL_SAMPLES, padStart = true)

    override fun transcribeUtterance(utterance: FloatArray): String {
        val stream = model.createStream()
        try {
            var i = 0
            while (i < utterance.size) {
                val end = minOf(i + StreamingSession.MIC_HOP_SAMPLES, utterance.size)
                stream.acceptWaveform(utterance.copyOfRange(i, end))
                stream.decode()
                i = end
            }
            stream.inputFinished()
            stream.decode()
            return stream.text().trim()
        } finally {
            stream.release()
        }
    }

    override fun release() {
        try {
            model.release()
        } finally {
            vad.release()
        }
    }
}
