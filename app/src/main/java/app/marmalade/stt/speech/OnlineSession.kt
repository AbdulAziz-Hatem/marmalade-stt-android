package app.marmalade.stt.speech

/*
 * Data flow of one true-streaming session (Nemotron via sherpa-onnx's
 * OnlineRecognizer; #13):
 *
 *   micSession (shared with StreamingSession): mic -> high-pass -> SoftAgc
 *     (without a platform AGC) -> Level -> hop queue
 *   consumer:
 *     -> SampleBuffer (audio the VAD hasn't windowed yet, plus 1 s of pre-roll)
 *        and PreGainAudio (the utterance before gain, for nearSilent)
 *     -> SpeechVad in 512-sample windows
 *          onset: SttEvent.SpeechStarted; a new RecognizerStream is fed the
 *          pre-roll (silence first if the mic opened less than 1 s before)
 *          and everything buffered since
 *     -> while an utterance runs: every hop goes to the stream, decode(),
 *          changed text -> SttEvent.Partial (straight from the model)
 *     -> VAD segment complete: inputFinished() + decode() flushes the model's
 *          lookahead -> FinishedUtterances per end mode (#43; see
 *          StreamingSession) -> Final, or keep it
 *          the stream is released; the next onset opens a new one
 *        Smart Turn instead: the segment proposes the end of the turn
 *          (SmartTurnGate) and the stream stays open, fed the pause; speech
 *          again continues the turn in the same stream. Complete (or the
 *          patience's ceiling of silence): flush the stream -> one Final for the turn
 *   stop() -> queue closes -> flush the VAD and the utterance in progress
 *     the same way (a Smart Turn in progress ends) -> Final of the kept
 *     utterances, or NoSpeech
 *
 * Endpointing is Silero's, not the model's own endpoint rules: see OnlineSession.
 */

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/**
 * A listening session over a true streaming recognizer: the model decodes the
 * audio as it arrives and its own running text is the [SttEvent.Partial].
 *
 * **Endpointing comes from Silero VAD, not the model's endpoint rules.** The
 * same VAD ends utterances for every engine, so the silence slider,
 * [SttEvent.SpeechStarted] (which the voice keyboard's idle timeout and the
 * one-shot recognizer rely on), [SttEvent.Final.nearSilent] and the end of
 * dictation modes ([SessionOptions.end]) behave identically whichever engine
 * runs. And the model only runs while
 * someone speaks: between utterances no audio is decoded, which saves the
 * battery a 0.6B encoder would otherwise burn on silence.
 *
 * Each utterance gets a fresh [RecognizerStream], started [PREROLL_SAMPLES]
 * (1 s) before the VAD's onset, padded with silence near mic open; at the
 * segment end [RecognizerStream.inputFinished] flushes
 * the model's right context so the last word isn't lost. In Smart Turn mode
 * a stream lasts a whole turn instead: it stays open (and keeps getting the
 * audio) while the gate waits for the verdict, so a turn that goes on after
 * a pause is decoded in one piece. A turn that has lasted 28 s
 * ([StreamingSession.MAX_TURN_SAMPLES]) ends at its next segment end. Partials are the
 * model's text as is (it punctuates as it goes, so nothing is stripped).
 * While the session is behind the mic ([LagMonitor], #30), it only feeds
 * the stream until the queue is drained, then decodes the whole backlog at
 * once and shows one partial for it.
 *
 * Shares the mic, error and threading contract of [StreamingSession] (see
 * [micSession]). Doesn't own [recognizer] or [vad]; does own the mic and the
 * streams it creates.
 *
 * @param options the end-of-dictation mode, end words and turn detector; see
 *   [StreamingSession].
 * @param catchUp batch decodes while behind the mic; see [StreamingSession]'s
 *   parameter of the same name.
 */
class OnlineSession(
    private val recognizer: StreamingRecognizer,
    private val vad: SpeechVad,
    private val options: SessionOptions,
    private val catchUp: Boolean = true,
    private val openMic: (hopSamples: Int, hardwareEffects: Boolean) -> MicCapture,
) : ListeningSession {

    companion object {
        /**
         * Audio fed to a new stream from before the VAD's onset (1 s). The
         * cache-aware encoder needs that much left context: with 0.4 s,
         * Nemotron dropped the first word of 15–20 % of utterances in the
         * #14 host benchmark; with 1 s, none (and WER fell 1–3 points).
         * Near mic open (or just after the previous utterance, whose audio
         * isn't reused) there is less than that before the onset, so silence
         * makes up the rest (#15: without it, speech from the mic's first
         * sample still lost its first word two times in three).
         */
        internal const val PREROLL_SAMPLES = 16_000
    }

    @Volatile
    private var stopRequested = false
    private val collected = AtomicBoolean(false)

    override fun stop() {
        stopRequested = true
    }

    override fun run(): Flow<SttEvent> {
        check(collected.compareAndSet(false, true)) { "An OnlineSession can be collected only once" }
        return micSession(openMic, { stopRequested }, onStart = vad::reset) { hops, captured -> consume(hops, captured) }
    }

    private suspend fun ProducerScope<SttEvent>.consume(hops: ReceiveChannel<MicHop>, captured: () -> Int) {
        val mode = options.effectiveEnd
        // Smart Turn only: segments propose the end of a turn, the gate decides.
        val gate = options.turnDetector?.takeIf { mode == EndOfDictation.SMART_TURN }
            ?.let { SmartTurnGate(options.patience, options.fallbackSilenceSamples, it) }
        val texts = FinishedUtterances(mode, options.endWords)
        val buffer = SampleBuffer()
        val lag = LagMonitor()
        var decodedThrough = 0 // hops whose audio the stream has decoded (all of them between utterances)
        val preGain = PreGainAudio()
        var offset = 0 // samples of buffer already fed to the VAD
        var fed = 0 // samples fed to the VAD since reset: VadSegment.start counts in these
        // The utterance's stream; in Smart Turn mode the turn's, open across its segments.
        var stream: RecognizerStream? = null
        var inSpeech = false // the VAD is in an utterance (Smart Turn: the stream may outlive it)
        var fedTo = 0 // samples of buffer already fed to the stream
        var shown = "" // the last Partial's text for the utterance in progress
        var turnStartFed = 0 // Smart Turn: where the turn's onset was, in VAD samples
        var turnQuiet = Quiet.SILENT // Smart Turn: as quiet as the turn's loudest segment

        suspend fun sendAll(events: List<SttEvent>) {
            for (event in events) send(event)
        }

        /** Flush and free the stream of the utterance in progress; its text, trimmed. */
        fun finishUtterance(): String {
            val s = stream ?: return ""
            stream = null
            shown = ""
            return try {
                s.inputFinished()
                s.decode()
                s.text().trim()
            } finally {
                s.release()
            }
        }

        /** Smart Turn: the turn is over; its stream's text is one Final. */
        suspend fun endTurn() {
            sendAll(texts.finished(finishUtterance(), turnQuiet))
            turnQuiet = Quiet.SILENT
        }

        /** Handle each completed VAD segment. [atStop]: flushed by stop, so the session is ending. */
        suspend fun drainSegments(atStop: Boolean) {
            while (!vad.empty()) {
                val segment = vad.front()
                vad.pop()
                inSpeech = false
                // Judged before SoftAgc's gain, as in StreamingSession.
                val quiet = judgeQuiet(preGain.rmsDb(segment.start, segment.samples.size), segment.samples.size)
                if (gate != null && stream != null) {
                    // The segment only proposes the end of the turn: the stream stays open.
                    turnQuiet = turnQuiet and quiet
                    val segmentEnd = segment.start + segment.samples.size
                    val ended = atStop || fed - turnStartFed >= StreamingSession.MAX_TURN_SAMPLES ||
                        gate.propose(trailingSilence = fed - segmentEnd)
                    if (ended) endTurn()
                    continue
                }
                sendAll(texts.finished(finishUtterance(), quiet, atStop))
            }
        }

        try {
            for (hop in hops) {
                // Receiving from a backlog never suspends; see StreamingSession.
                ensureActive()
                buffer.append(hop.audio)
                preGain.append(hop.preGain)

                while (offset + StreamingSession.VAD_WINDOW_SIZE < buffer.size) {
                    val window = buffer.copyRange(offset, offset + StreamingSession.VAD_WINDOW_SIZE)
                    vad.acceptWaveform(window)
                    offset += StreamingSession.VAD_WINDOW_SIZE
                    fed += StreamingSession.VAD_WINDOW_SIZE
                    if (stream != null) gate?.append(window)
                    if (!inSpeech && vad.isSpeechDetected()) {
                        inSpeech = true
                        if (stream != null) {
                            // Smart Turn: speech again before the verdict; the turn goes on in its stream.
                            gate?.resume()
                        } else {
                            val start = (offset - PREROLL_SAMPLES).coerceAtLeast(0)
                            buffer.dropFirst(start)
                            offset -= start
                            fedTo = 0
                            stream = recognizer.createStream().also { s ->
                                if (offset < PREROLL_SAMPLES) s.acceptWaveform(FloatArray(PREROLL_SAMPLES - offset))
                            }
                            turnStartFed = fed
                            gate?.start(buffer.copyRange(0, offset))
                        }
                        send(SttEvent.SpeechStarted)
                    }
                }

                stream?.let { s ->
                    if (fedTo < buffer.size) {
                        s.acceptWaveform(buffer.copyRange(fedTo, buffer.size))
                        fedTo = buffer.size
                    }
                    // Behind the mic, decode once the queue is drained: the
                    // whole backlog in one go, and one partial for it.
                    if (!(catchUp && lag.catchingUp) || captured() <= hop.index + 1) {
                        s.decode()
                        decodedThrough = hop.index + 1
                        val text = s.text().trim()
                        if (text.isNotEmpty() && text != shown) {
                            shown = text
                            send(SttEvent.Partial((texts.kept + text).joinToString(" ")))
                        }
                    }
                }

                drainSegments(atStop = false)
                if (gate != null && stream != null && gate.poll()) endTurn()
                if (stream == null) decodedThrough = hop.index + 1
                for (event in lag.update(captured(), decodedThrough)) send(event)

                if (stream == null) {
                    // Between utterances only the pre-roll can still be needed.
                    if (offset > PREROLL_SAMPLES) {
                        buffer.dropFirst(offset - PREROLL_SAMPLES)
                        offset = PREROLL_SAMPLES
                    }
                    preGain.dropBefore(fed - PreGainAudio.LOOKBACK_SAMPLES)
                } else {
                    // The stream has everything; keep only what the VAD hasn't windowed.
                    buffer.dropFirst(offset)
                    fedTo -= offset
                    offset = 0
                }
            }

            // The capture ended (stop()): the utterance in progress becomes a
            // VAD segment (for the near-silence judgement) and is flushed.
            if (inSpeech) {
                vad.flush()
                drainSegments(atStop = true)
            }
            // A Smart Turn waiting for its verdict ends with the session.
            if (gate != null && stream != null) endTurn()
        } finally {
            // Cancelled or failed mid-utterance: free the native stream anyway.
            stream?.release()
        }

        lag.logSummary()
        sendAll(texts.ended())
    }
}
