package app.marmalade.stt.speech

/*
 * Data flow of one listening session (ported from the Marmalade client's
 * WhisperStreamingEngine; ARCHITECTURE.md "Stage-1 pipeline"):
 *
 *   MicCapture.hops (100 ms, Dispatchers.IO, producer coroutine; from the
 *   engine, an EarlyMic: the hops kept while the model loaded come first)
 *     -> AudioPreprocessor (100 Hz high-pass, in place)
 *     -> SoftAgc (on a copy), only when the platform AGC isn't running
 *     -> SttEvent.Level (dBFS of the hop after gain, sent straight from the
 *        producer so the meter keeps moving while Whisper decodes)
 *     -> Channel.UNLIMITED of MicHop (the hop after gain, and before it;
 *        a caller's stream, not live: NON_LIVE_QUEUED_HOPS, #90)
 *   consumer coroutine (Dispatchers.Default):
 *     -> SampleBuffer (this utterance's audio, plus PREROLL_SAMPLES before it)
 *        and PreGainAudio (the same audio before gain, for nearSilent)
 *     -> SpeechVad in 512-sample windows
 *          onset: SttEvent.SpeechStarted; the utterance starts 0.6 s earlier
 *     -> VAD segment complete: transcribe the pre-roll + the VAD's segment,
 *        then reset the buffer for the next utterance; FinishedUtterances
 *        decides per end mode (#43):
 *          Pause:      SttEvent.Final(text, nearSilent)
 *          Tap stop:   keep it, SttEvent.Partial(all kept utterances)
 *          End word:   keep it, or on an end word standing alone,
 *                      SttEvent.Final(kept + text before it, endWord = true)
 *        Smart Turn instead: the segment only proposes the end of the turn
 *        (SmartTurnGate; the VAD's min silence and the gate's numbers come
 *        from the Patience setting, Normal: 1.2 s). The turn's audio stays in
 *        the buffer; the model scores its last 8 s (trailing silence
 *        included) now and after every further 0.5 s of silence, up to the
 *        patience's ceiling (Normal: 2.5 s). Complete:
 *        transcribe the whole turn once, SttEvent.Final. Speech first: the
 *        turn goes on. A turn reaching 28 s ends at its last segment.
 *     -> an utterance (or a turn with no segment closed yet) still going at
 *        the cap (28 s plus the pre-roll, #48, UtteranceCap; 21 s for Whisper
 *        in Hindi, #54) is cut at its quietest window in the last 1.5 s:
 *        that part is decoded and handled like a segment (End word holds it
 *        until the utterance's real end), and the rest goes on as the next
 *        one. Silero's own 28 s limit only makes it eager to end the
 *        segment, so it can run longer.
 *     -> otherwise, during speech (livePartials), at most every 200 ms and
 *        never sooner than the last partial took: transcribe the utterance
 *        (its last few seconds once it is long) -> PartialStabilizer
 *        (stable words, then the latest tail) -> SttEvent.Partial
 *        (trailing punctuation stripped)
 *   stop(), or the capture ending (SherpaSttEngine.stop closes the mic, and
 *   its EarlyMic ends once the audio kept during the load has been read)
 *     -> producer ends the channel -> consumer drains it, flushes the
 *     VAD so the speech in progress becomes a segment and is transcribed the
 *     same way (a Smart Turn in progress ends), emits the kept utterances as
 *     one Final, or NoSpeech if the session transcribed nothing.
 *
 * Audio lives only in memory: the buffer holds at most the utterance in
 * progress (cut at the session's decode cap, UtteranceCap; a Smart Turn is
 * split at its last segment once it reaches that cap less the pre-roll) and
 * is dropped when the flow ends.
 */

import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * One listening session: simulated streaming recognition from an offline
 * transcriber (Whisper, Parakeet, Canary, Qwen3-ASR) segmented by a VAD
 * (Silero). Pure JVM apart from logging; [SherpaSttEngine] supplies the
 * sherpa-backed pieces. [OnlineSession] is the true-streaming counterpart.
 *
 * Create one per listen and collect [run] once. The session doesn't own
 * [transcriber] or [vad] (it calls [SpeechVad.reset] at the start and never
 * releases either). It does own the mic, which it always closes when the
 * flow ends.
 *
 * Events follow the [SttEvent] contract. Details specific to this engine:
 * - [SttEvent.Level] comes once per 100 ms mic hop. It is sent with
 *   `trySend`, so a collector that falls far behind loses levels rather than
 *   stalling the mic.
 * - [SttEvent.SpeechStarted] comes at each utterance onset, so more than once
 *   per session when the user pauses between utterances.
 * - [SttEvent.Partial] text has Whisper's trailing punctuation removed with
 *   [stripTrailingPunctuation]. Whisper punctuates every chunk as if it were
 *   a finished sentence, so the full stop would flicker. With utterances kept
 *   (Tap stop, End word) only the utterance in progress is stripped; kept
 *   ones keep theirs.
 *   [SttEvent.Final] text is never stripped.
 * - Live partials of the utterance in progress go through a
 *   [PartialStabilizer] (#28): words two partials in a row agreed on stay
 *   as they are until the Final, and a repeat at the end is cut to one copy.
 * - [SttEvent.Final.nearSilent] and [SttEvent.Final.silent] are
 *   [NearSilence.quiet] of the RMS of the utterance's VAD segment (the quiet
 *   pre-roll is left out), measured before [SoftAgc]'s gain. A Final that
 *   joins utterances (Tap stop, End word, a Smart Turn's segments) is only as
 *   quiet as its loudest one.
 * - Partials never delay a Final: a segment the VAD has closed is decoded
 *   before any partial. A partial decode can't be interrupted, so partials
 *   are spaced at least as far apart as the last one took (a slow model
 *   spends at most half its time on them) and decode at most
 *   [PARTIAL_WINDOW_SAMPLES] of audio: once the utterance is longer, the
 *   audio up to a quiet point near its end is transcribed once and its text
 *   kept, and later partials decode only what follows. The Final still
 *   decodes the whole utterance.
 * - While the session is behind the mic ([LagMonitor], #30) it skips partials
 *   until it has caught up, so a slow device gets its Finals on time rather
 *   than ever later partials. [SttEvent.CatchingUp] and [SttEvent.TooSlow]
 *   report it.
 * - [SttEvent.Error]: [SttErrorKind.MIC] if the mic can't be opened or fails
 *   mid-session, [SttErrorKind.RECOGNITION] if decoding throws. It is always
 *   the last event. Cancelling the collector emits nothing, and stops a
 *   decode in progress at its next decoder step (#49: Whisper, Qwen3-ASR and
 *   Canary; [OfflineTranscriber.cancel]) or inside its encoder run (#54), so
 *   the engine is free again about as soon as the flow is.
 *
 * @param options the end-of-dictation mode ([SessionOptions.effectiveEnd]),
 *   its end words and turn detector. The VAD's minimum silence is the
 *   engine's business ([SessionOptions.vadMinSilenceSeconds]).
 * @param openMic mic factory, called with (hop size, hardware effects on).
 * @param livePartials re-decode the utterance in progress as it grows. Off
 *   for models too slow for that: then the only [SttEvent.Partial]s are the
 *   kept utterances of Tap stop and End word.
 * @param nowMs monotonic clock for the partial cadence (tests inject one).
 * @param catchUp skip partials while behind the mic. Only tests whose mic
 *   delivers a long script at once (so it is "behind" from the start) turn
 *   it off; the lag events come either way.
 * @param maxDecodeSamples the most audio one decode gets
 *   ([UtteranceCap.maxDecodeSamples]: shorter for Whisper in Hindi, #54). An
 *   utterance is cut at it, and a Smart Turn ends at its last segment once it
 *   is this long less the pre-roll.
 * @param isPreSpeechPhantom whether a text is what the engine writes for the
 *   noise before speech, dropped when more speech follows
 *   ([FinishedUtterances], [app.marmalade.stt.install.SttEngineDescriptor.isPreSpeechPhantom]).
 * @param earlyVad a second VAD with a shorter minimum silence ([EarlyVad],
 *   #91): when it ends the utterance, the audio the Final would
 *   decode if [vad] ended it there is decoded at once, and that text is the
 *   Final when [vad] ends the same range. No partials until speech resumes.
 * @param trimAtStop with [earlyVad]: a stop in that silence sends the
 *   speculative text (the speech without the silence up to the stop).
 * @param partialAtOnset decode the first partial at the onset, not
 *   [PARTIAL_INTERVAL_MS] later.
 *   [trimAtStop] and [partialAtOnset] are off here; the app's engines pass
 *   [SpeedKnobs]' values (#91).
 * @param verifySpeculation also decode a speculated Final the usual way and
 *   log whether the texts match (measurement only: it costs the decode the
 *   speculation saved).
 */
class StreamingSession(
    private val transcriber: OfflineTranscriber,
    private val vad: SpeechVad,
    private val options: SessionOptions,
    private val openMic: (hopSamples: Int, hardwareEffects: Boolean) -> MicCapture,
    private val livePartials: Boolean = true,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val catchUp: Boolean = true,
    private val maxDecodeSamples: Int = UtteranceCap.MAX_DECODE_SAMPLES,
    private val isPreSpeechPhantom: (String) -> Boolean = { false },
    private val earlyVad: SpeechVad? = null,
    private val trimAtStop: Boolean = false,
    private val partialAtOnset: Boolean = false,
    private val verifySpeculation: Boolean = false,
) : ListeningSession {

    companion object {
        /** Silero's analysis window at 16 kHz (32 ms). */
        const val VAD_WINDOW_SIZE = 512
        internal const val MIC_HOP_SAMPLES = 1_600 // 100 ms

        /**
         * Audio kept before the VAD's onset (0.6 s), prepended to each
         * utterance. Silero detects speech about 0.3 s after its segment
         * starts, and at mic open (cold model state) its segment itself
         * starts up to 0.3 s into the first word. The #15 host benchmark
         * picked 0.6 s: it recovers a first word spoken as the mic opens as
         * well as 0.8-1 s does, while longer leading silence made Parakeet
         * return nothing for some abrupt onsets. With the 28 s utterance cap
         * this stays inside Whisper's ~29.5 s.
         */
        internal const val PREROLL_SAMPLES = 9_600

        /** Shortest gap between two partials; a slow decode makes it longer (see [consume]). */
        internal const val PARTIAL_INTERVAL_MS = 200L

        /**
         * Most audio one partial decodes (6 s). Every partial re-decodes its
         * audio from the start, and a decode can't be interrupted, so a
         * partial running when the VAD closes the segment delays the Final
         * by up to its whole decode. Without a cap, a 22 s utterance on the
         * Pixel 8a (2 threads) re-decoded ~6 s per partial and its Final
         * came 14.7 s after the speech ended (#11, #20). 6 s was the best of
         * 6/8/10 in the #20 latency simulation (`tools/asr-bench`
         * `latency.py`).
         */
        internal const val PARTIAL_WINDOW_SAMPLES = 96_000

        /**
         * Where a long utterance's partial audio is cut: the quietest VAD
         * window between 2.5 s and 0.5 s before the end, most likely a gap
         * between words, so neither side decodes half a word.
         */
        internal const val PARTIAL_CUT_FROM_END = 40_000
        internal const val PARTIAL_CUT_TO_END = 8_000

        /**
         * The longest Smart Turn decoded as one Final (28 s, as Silero caps an
         * utterance: [MAX_UTTERANCE_SECONDS]). A turn that grows past it ends
         * at its last segment boundary; with the pre-roll it stays inside
         * Whisper's ~29.5 s. A session with a shorter decode cap shortens it
         * to match ([maxTurnSamples]).
         */
        internal val MAX_TURN_SAMPLES = (MAX_UTTERANCE_SECONDS * MIC_SAMPLE_RATE).toInt()

        /** Latency numbers (never audio or text), for on-device measurement. */
        const val TIMING_TAG = "SttTiming"
    }

    /** [MAX_TURN_SAMPLES] under this session's decode cap: the cap less the pre-roll. */
    private val maxTurnSamples = maxDecodeSamples - PREROLL_SAMPLES

    @Volatile
    private var stopRequested = false
    private val collected = AtomicBoolean(false)

    /** The collector went away mid-session (#49): no decode starts after this. */
    @Volatile
    private var cancelled = false

    /**
     * Finish: stop reading the mic, transcribe what was heard, then end the
     * flow. Safe from any thread, and before [run] is collected (the session
     * then ends right after [SttEvent.Ready]).
     */
    override fun stop() {
        stopRequested = true
    }

    /** The session's events. Cold; collect it once. */
    override fun run(): Flow<SttEvent> {
        check(collected.compareAndSet(false, true)) { "A StreamingSession can be collected only once" }
        return micSession(openMic, { stopRequested }, onStart = { vad.reset(); earlyVad?.reset() }) { hops, captured ->
            // A native decode doesn't see coroutine cancellation: without
            // this, a cancelled session would hold the engine until its
            // decode ended (a runaway Whisper decode: up to 224 steps, #49).
            // This child is cancelled with the flow, on another thread, and
            // stops the decode at its next step.
            val consumed = AtomicBoolean(false)
            val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (!consumed.get()) {
                        cancelled = true
                        transcriber.cancel()
                    }
                }
            }
            try {
                consume(hops, captured)
            } finally {
                consumed.set(true)
                canceller.cancel()
            }
        }
    }

    /** The consumer: VAD segmentation and offline decoding. Returns when [hops] is closed. */
    private suspend fun ProducerScope<SttEvent>.consume(hops: ReceiveChannel<MicHop>, captured: () -> Int) {
        val mode = options.effectiveEnd
        // Smart Turn only: segments propose the end of a turn, the gate decides.
        val gate = options.turnDetector?.takeIf { mode == EndOfDictation.SMART_TURN }
            ?.let { SmartTurnGate(options.patience, options.fallbackSilenceSamples, it) }
        val texts = FinishedUtterances(mode, options.endWords, isPreSpeechPhantom)
        val buffer = SampleBuffer()
        val lag = LagMonitor()
        val preGain = PreGainAudio()
        var offset = 0 // samples of buffer already fed to the VAD
        var fed = 0 // samples fed to the VAD since reset: VadSegment.start counts in these
        var isSpeechStarted = false
        // Where the utterance starts in buffer: onset minus the pre-roll. In
        // Smart Turn mode, where the turn starts: its audio stays in buffer
        // across its segments until the turn ends.
        var speechStartOffset = 0
        var onsetOffset = 0 // the latest onset in buffer
        var turnOpen = false // Smart Turn: a turn is in progress (speaking, or waiting for the verdict)
        var turnEnd = -1 // Smart Turn: where the turn's last closed segment ends in buffer; -1 before its first
        var turnQuiet = Quiet.SILENT // Smart Turn: as quiet as the turn's loudest segment
        var lastPartialAt = nowMs()
        var lastPartialMs = 0L // how long the last partial's decode took
        var onsetAt = lastPartialAt
        var partialSinceOnset = false
        // Live partials decode buffer[partialFrom, offset); the text of the
        // audio before partialFrom (long utterances only) is in committed.
        var partialFrom = 0
        val committed = mutableListOf<String>()
        val stabilizer = PartialStabilizer()
        // Where the last forced cut was (#48), counted like VadSegment.start:
        // the segment in progress before it has been decoded.
        var cutAt = 0
        // The next segment the VAD closes is the one that was cut: only its
        // rest after cutAt is still to decode. Any later one is a new segment
        // (even one starting a little before cutAt) and gets the usual pre-roll.
        var restPending = false
        var turnFromCut = false // Smart Turn: the turn's audio starts at a forced cut
        val stats = SessionStats()
        // #91: the Final's audio decoded when the early VAD ended the utterance,
        // as [from, to) counted like VadSegment.start, and its text.
        var speculation: Speculation? = null
        var earlySilence = false // the early VAD ended the utterance: no partials until speech again

        suspend fun sendAll(events: List<SttEvent>) {
            for (event in events) {
                if (event is SttEvent.Final) stats.finalSent()
                send(event)
            }
        }

        /**
         * [speechEnd]: where the decoded speech ends, counted like
         * [VadSegment.start], so the log can split end of speech to Final
         * into the wait for the end (the VAD's silence, Smart Turn, a stop)
         * and the backlog behind the mic, then this decode.
         */
        fun decodeFinal(audio: FloatArray, speechEnd: Int, from: Int = -1): String {
            val micPast = samplesMs(captured() * MIC_HOP_SAMPLES - speechEnd)
            val spec = speculation
            speculation = null
            earlySilence = false
            if (spec != null && spec.from == from && spec.to == speechEnd) {
                Log.d(
                    TIMING_TAG,
                    "final decode 0 ms for ${audio.size * 1000L / MIC_SAMPLE_RATE} ms of audio (speculative, decoded ${spec.decodeMs} ms " +
                        "${samplesMs(spec.decodedAt - speechEnd)} ms after the speech's end); " +
                        "at its start the VAD was ${samplesMs(fed - speechEnd)} ms past the speech's end, the mic $micPast ms",
                )
                if (verifySpeculation) {
                    val again = transcribe(audio)
                    Log.d(TIMING_TAG, "speculation parity: ${if (again == spec.text) "same" else "DIFFERENT"}")
                }
                return spec.text
            }
            if (spec != null) Log.d(TIMING_TAG, "speculation missed: ${spec.from}..${spec.to} against $from..$speechEnd")
            val decodeStart = nowMs()
            val text = transcribe(audio)
            // The VAD closes a segment after its minimum silence, so end of
            // speech to Final = that pause (+ Smart Turn's wait) + this decode.
            Log.d(
                TIMING_TAG,
                "final decode ${nowMs() - decodeStart} ms for ${audio.size * 1000L / MIC_SAMPLE_RATE} ms of audio; " +
                    "at its start the VAD was ${samplesMs(fed - speechEnd)} ms past the speech's end, the mic $micPast ms",
            )
            return text
        }

        /** Smart Turn: forget the turn's state (its audio is dropped by the caller). */
        fun resetTurn() {
            turnEnd = -1
            turnQuiet = Quiet.SILENT
            turnFromCut = false
        }

        /** Smart Turn: the turn is over. Decode it once, from its pre-roll to its last segment's end, and send it. */
        suspend fun endTurn() {
            val length = turnEnd - speechStartOffset
            // A turn that is only a cut segment's rest is decoded only if it holds speech.
            if (length > 0 && (!turnFromCut || UtteranceCap.restHasSpeech(length, turnQuiet.nearSilent))) {
                sendAll(texts.finished(decodeFinal(buffer.copyRange(speechStartOffset, turnEnd), fed - offset + turnEnd, fed - offset + speechStartOffset), turnQuiet))
            }
            // As after an utterance: samples the VAD hasn't seen yet are kept.
            buffer.dropFirst(offset)
            offset = 0
            speechStartOffset = 0
            turnOpen = false
            resetTurn()
        }

        /** Handle the VAD's completed segments. [atStop]: flushed by stop, so the session is ending. */
        suspend fun drainSegments(atStop: Boolean) {
            while (!vad.empty()) {
                val segment = vad.front()
                val segmentStart = segment.start - (fed - offset) // index into buffer
                // The cut segment's audio before the cut was decoded then (in buffer, it ends there).
                val segmentEnd = (segmentStart + segment.samples.size).coerceAtLeast(0)
                val isRest = restPending
                restPending = false
                // The cut segment: only its rest after the cut is still to judge and decode.
                val restFrom = if (isRest) (cutAt - segment.start).coerceIn(0, segment.samples.size) else 0
                val rest = segment.samples.size - restFrom
                val quiet = judgeQuiet(preGain.rmsDb(segment.start + restFrom, rest), rest)
                isSpeechStarted = false

                if (gate != null) {
                    vad.pop()
                    // A closed segment only proposes the end of the turn; it isn't decoded yet.
                    if (!turnOpen) {
                        // (Silero reports speech before it closes a segment; this is for safety.)
                        turnOpen = true
                        gate.start(buffer.copyRange(speechStartOffset, offset))
                    }
                    if (turnEnd < 0) speechStartOffset = minOf(speechStartOffset, segmentStart.coerceAtLeast(0))
                    turnEnd = segmentEnd
                    turnQuiet = turnQuiet and quiet
                    val ended = atStop || turnEnd - speechStartOffset >= maxTurnSamples ||
                        gate.propose(trailingSilence = offset - segmentEnd)
                    if (ended) endTurn()
                    continue
                }

                // Final text comes from the VAD's own segment (its end is
                // trimmed to the speech), with the pre-roll in front of it.
                // The cut segment: its rest, if that holds speech.
                val text = when {
                    !isRest -> {
                        val audio = capped(withPreroll(buffer, speechStartOffset, segmentStart, segment.samples))
                        val end = segment.start + segment.samples.size
                        val spec = speculation
                        if (atStop && trimAtStop && earlySilence && spec != null && spec.from == end - audio.size &&
                            preGain.rmsDb(spec.to, fed - spec.to) < NearSilence.THRESHOLD_DB
                        ) {
                            // Stopped in the early VAD's silence, and nothing but near-silence
                            // came since: the speech without the silence up to the stop.
                            decodeFinal(audio, spec.to, spec.from)
                        } else {
                            decodeFinal(audio, end, end - audio.size)
                        }
                    }
                    UtteranceCap.restHasSpeech(rest, quiet.nearSilent) ->
                        decodeFinal(segment.samples.copyOfRange(restFrom, segment.samples.size), segment.start + segment.samples.size)
                    else -> ""
                }
                vad.pop()

                // Per-utterance reset: the next utterance's buffer starts
                // empty, which keeps each decode inside Whisper's 30 s window.
                // Samples the VAD hasn't seen yet are kept.
                buffer.dropFirst(offset)
                offset = 0
                speechStartOffset = 0

                sendAll(texts.finished(text, quiet, atStop))
            }
        }

        /**
         * The utterance (or a Smart Turn with no segment closed yet) is at
         * the cap: decode it up to a quiet point near its end and handle that
         * as a finished utterance; the rest goes on as the next one (#48).
         */
        suspend fun cutAtCap() {
            val cut = UtteranceCap.cutPoint(buffer, offset)
            val bufferStart = fed - offset // buffer[0], counted like VadSegment.start
            val length = cut - speechStartOffset
            val quiet = judgeQuiet(preGain.rmsDb(bufferStart + speechStartOffset, length), length)
            sendAll(texts.cut(decodeFinal(buffer.copyRange(speechStartOffset, cut), bufferStart + cut), quiet))
            buffer.dropFirst(cut)
            offset -= cut
            onsetOffset -= cut
            cutAt = bufferStart + cut
            restPending = true
            preGain.dropBefore(cutAt - PreGainAudio.LOOKBACK_SAMPLES)
            speechStartOffset = 0
            partialFrom = 0
            committed.clear()
            stabilizer.reset()
            if (gate != null) {
                resetTurn()
                turnFromCut = true
                gate.start(buffer.copyRange(0, offset))
            }
        }

        /**
         * #91: the early VAD ended the utterance while [vad] still holds it.
         * Decode now what [vad]'s Final will decode if it ends at the same
         * sample (see decodeFinal), so its wait for more silence hides this
         * decode. Not after a forced cut (that Final decodes only a rest).
         */
        fun speculate() {
            val early = earlyVad ?: return
            while (!early.empty()) {
                val segment = early.front()
                early.pop()
                val bufferStart = fed - offset
                val end = segment.start + segment.samples.size
                val endInBuffer = end - bufferStart
                if (!(isSpeechStarted || turnOpen) || restPending || turnFromCut || endInBuffer > offset) continue
                val from: Int
                val audio: FloatArray
                if (gate != null) {
                    // A turn decodes from its start (the first segment may reach before the pre-roll).
                    val start = if (turnEnd < 0) minOf(speechStartOffset, (segment.start - bufferStart).coerceAtLeast(0)) else speechStartOffset
                    if (endInBuffer <= start) continue
                    from = bufferStart + start
                    audio = buffer.copyRange(start, endInBuffer)
                } else {
                    val segmentStart = segment.start - bufferStart
                    if (segmentStart < 0) continue // reaches back past the buffer: leave it to the Final
                    audio = capped(withPreroll(buffer, speechStartOffset, segmentStart, buffer.copyRange(segmentStart, endInBuffer)))
                    from = end - audio.size
                }
                val decodeStart = nowMs()
                val text = transcribe(audio)
                speculation = Speculation(from, end, text, nowMs() - decodeStart, captured() * MIC_HOP_SAMPLES)
                earlySilence = true
                Log.d(TIMING_TAG, "speculative decode ${nowMs() - decodeStart} ms for ${samplesMs(audio.size)} ms of audio")
            }
        }

        /** Decode the utterance in progress (or, once it is long, commit its older part) and show it. */
        suspend fun partial() {
            val decodeStart = nowMs()
            if (offset - partialFrom > PARTIAL_WINDOW_SAMPLES) {
                // Too long to re-decode: transcribe up to a quiet point once and keep that text.
                val cut = quietestWindow(buffer, offset - PARTIAL_CUT_FROM_END, offset - PARTIAL_CUT_TO_END)
                stripTrailingPunctuation(transcribe(buffer.copyRange(partialFrom, cut)))
                    .takeIf { it.isNotEmpty() }?.let { committed += it }
                partialFrom = cut
            } else {
                val shown = stripTrailingPunctuation(transcribe(buffer.copyRange(partialFrom, offset)))
                stats.partialAudio(offset - partialFrom, shown.isEmpty())
                val hypothesis = (committed + shown).filter { it.isNotEmpty() }.joinToString(" ")
                val utterance = stripTrailingPunctuation(stabilizer.update(hypothesis))
                if (utterance.isNotEmpty()) {
                    send(SttEvent.Partial((texts.kept + utterance).joinToString(" ")))
                    if (!partialSinceOnset) {
                        partialSinceOnset = true
                        Log.d(TIMING_TAG, "first partial ${nowMs() - onsetAt} ms after onset (decode ${nowMs() - decodeStart} ms)")
                    }
                }
            }
            lastPartialAt = nowMs()
            lastPartialMs = lastPartialAt - decodeStart
            stats.partial(lastPartialMs)
        }

        for (hop in hops) {
            // Receiving from a backlog never suspends, so without this a
            // lagging session would ignore cancellation until it caught up.
            ensureActive()
            buffer.append(hop.audio)
            preGain.append(hop.preGain)

            while (offset + VAD_WINDOW_SIZE < buffer.size) {
                val window = buffer.copyRange(offset, offset + VAD_WINDOW_SIZE)
                val vadStart = System.nanoTime()
                vad.acceptWaveform(window)
                earlyVad?.acceptWaveform(window)
                stats.vadWindow(System.nanoTime() - vadStart)
                if (earlySilence && earlyVad?.isSpeechDetected() == true) earlySilence = false
                offset += VAD_WINDOW_SIZE
                fed += VAD_WINDOW_SIZE
                if (turnOpen) gate?.append(window)

                if (!isSpeechStarted && vad.isSpeechDetected()) {
                    isSpeechStarted = true
                    onsetOffset = offset
                    if (turnOpen) {
                        // Smart Turn: speech again before the verdict. The
                        // turn goes on, and so do its partials.
                        gate?.resume()
                    } else {
                        speechStartOffset = (offset - PREROLL_SAMPLES).coerceAtLeast(0)
                        partialFrom = speechStartOffset
                        committed.clear()
                        stabilizer.reset()
                        if (gate != null) {
                            turnOpen = true
                            gate.start(buffer.copyRange(speechStartOffset, offset))
                        }
                    }
                    lastPartialAt = nowMs()
                    lastPartialMs = 0
                    onsetAt = lastPartialAt
                    if (partialAtOnset) lastPartialAt -= PARTIAL_INTERVAL_MS + 1
                    partialSinceOnset = false
                    send(SttEvent.SpeechStarted)
                }
            }

            // Before any partial: a Final never waits behind one.
            drainSegments(atStop = false)
            speculate()

            if (gate != null && turnOpen) {
                if (gate.poll()) {
                    endTurn()
                } else if (isSpeechStarted && turnEnd >= 0 && offset - speechStartOffset > maxTurnSamples) {
                    // The turn has grown to Whisper's window: it ends at its
                    // last segment boundary, and the speech in progress (from
                    // its pre-roll) starts the next turn.
                    sendAll(texts.finished(decodeFinal(buffer.copyRange(speechStartOffset, turnEnd), fed - offset + turnEnd), turnQuiet))
                    val next = maxOf(turnEnd, onsetOffset - PREROLL_SAMPLES)
                    buffer.dropFirst(next)
                    offset -= next
                    onsetOffset -= next
                    speechStartOffset = 0
                    partialFrom = 0
                    committed.clear()
                    stabilizer.reset()
                    resetTurn()
                    gate.start(buffer.copyRange(0, offset))
                }
            }

            // Still speaking at the cap, with no segment boundary to end at.
            if (isSpeechStarted && UtteranceCap.isDue(offset - speechStartOffset, maxDecodeSamples)) cutAtCap()

            for (event in lag.update(captured(), hop.index + 1)) send(event)

            // A partial decode can't be interrupted, and the hops that queue
            // up meanwhile are the VAD's to see first. Waiting as long as the
            // last decode took lets the VAD catch up (a closed segment then
            // goes to drainSegments, not behind another partial) and keeps a
            // slow model's partials from taking all its time.
            // Behind the mic, partials wait until the session has caught up.
            // (A Smart Turn waiting in silence gets none: isSpeechStarted is off.)
            if (livePartials && isSpeechStarted && !earlySilence && !(catchUp && lag.catchingUp) &&
                nowMs() - lastPartialAt > maxOf(PARTIAL_INTERVAL_MS, lastPartialMs)
            ) {
                partial()
            }

            // Between utterances only the pre-roll can still be needed; drop
            // older audio so a long silent session doesn't grow the buffer.
            if (!isSpeechStarted && !turnOpen) {
                if (offset > PREROLL_SAMPLES) {
                    buffer.dropFirst(offset - PREROLL_SAMPLES)
                    offset = PREROLL_SAMPLES
                }
                preGain.dropBefore(fed - PreGainAudio.LOOKBACK_SAMPLES)
            }
        }

        // The capture ended (stop()): the utterance in progress becomes a
        // segment (what the VAD has windowed of it) and is handled as the
        // last one: joined to what is kept, or (Smart Turn) ends its turn.
        if (isSpeechStarted) {
            vad.flush()
            drainSegments(atStop = true)
        }
        // A turn waiting for its verdict ends with the session.
        if (turnOpen) endTurn()

        lag.logSummary()
        sendAll(texts.ended())
        stats.log()
    }

    /**
     * [audio] cut to its last [maxDecodeSamples]. A segment longer than
     * the buffer's part of the utterance (only next to the previous one: it
     * reaches back into that one's closing silence, which Silero keeps) can
     * pass the cap by a little; the silence goes first.
     */
    private fun capped(audio: FloatArray): FloatArray {
        val cap = maxDecodeSamples
        return if (audio.size <= cap) audio else audio.copyOfRange(audio.size - cap, audio.size)
    }

    /**
     * The model's text, trimmed (sherpa returns Whisper's with a leading
     * space). Once the session is cancelled, none: the decode would be
     * nobody's, and could only hold the engine.
     */
    private fun transcribe(samples: FloatArray): String {
        if (cancelled) throw CancellationException("Session cancelled")
        return transcriber.transcribe(samples, MIC_SAMPLE_RATE) { cancelled }.trim()
    }
}

/**
 * One 100 ms mic hop as the consumer gets it: [audio] after the high-pass and
 * any [SoftAgc], for the VAD and the model; [preGain] the same hop before the
 * gain (the same array when there is no SoftAgc), for [NearSilence]. [index]
 * counts hops from 0 since the mic opened, for the [LagMonitor].
 */
internal class MicHop(val audio: FloatArray, val preGain: FloatArray, val index: Int)

/**
 * The part every [ListeningSession] shares: open the mic, run a producer that
 * high-pass filters each 100 ms hop (then applies [SoftAgc] to a copy if the
 * capture has no platform AGC), sends [SttEvent.Level] and queues the hop,
 * and hand the queue to [consume] on [Dispatchers.Default], with how many hops
 * the mic has captured so far (for the [LagMonitor]). A capture with a
 * backlog ([MicCapture.backlogHops], the engine's [EarlyMic]) gets no Level
 * for it, and counts it as captured from the start. Maps failures to
 * [SttEvent.Error] ([SttErrorKind.MIC] for the mic, [SttErrorKind.RECOGNITION]
 * for anything [consume] throws) as the last event, and always closes the mic.
 *
 * The queue is unbounded for a live mic, which won't wait. A non-live capture
 * ([MicCapture.live], a caller's stream, #90) gets [NON_LIVE_QUEUED_HOPS]
 * instead: the producer then reads it only as fast as [consume] takes hops,
 * so the captured count stays within a few hops of what was consumed, and
 * the [LagMonitor] never sees the session as behind (no partials skipped,
 * no [SttEvent.CatchingUp], above all no [SttEvent.TooSlow], which would
 * mark the engine too slow for the phone). Its backlog isn't added up front
 * either: its hops are counted as they pass.
 *
 * @param isStopRequested polled per hop; true ends capture and closes the queue,
 *   so [consume] can flush and return.
 * @param onStart runs before the mic opens (reset the VAD).
 */
internal fun micSession(
    openMic: (hopSamples: Int, hardwareEffects: Boolean) -> MicCapture,
    isStopRequested: () -> Boolean,
    onStart: () -> Unit = {},
    consume: suspend ProducerScope<SttEvent>.(hops: ReceiveChannel<MicHop>, captured: () -> Int) -> Unit,
): Flow<SttEvent> = channelFlow {
    onStart()

    val capture = try {
        openMic(StreamingSession.MIC_HOP_SAMPLES, true)
    } catch (e: MicCaptureException) {
        Log.w(SESSION_TAG, "Couldn't open the microphone", e)
        send(SttEvent.Error(SttErrorKind.MIC, e.message ?: "Couldn't open the microphone"))
        return@channelFlow
    }

    var producer: Job? = null
    val failure: SttEvent.Error? = try {
        send(SttEvent.Ready)
        val hops = Channel<MicHop>(if (capture.live) Channel.UNLIMITED else NON_LIVE_QUEUED_HOPS)
        val captured = AtomicInteger()
        producer = launch(Dispatchers.IO) {
            val preprocessor = AudioPreprocessor()
            val softAgc = if (capture.hasPlatformAgc) null else SoftAgc()
            if (softAgc != null) Log.i(SESSION_TAG, "No platform AGC: software gain control on")
            try {
                capture.hops.takeWhile { !isStopRequested() }.collect { raw ->
                    val filtered = preprocessor.process(raw)
                    val hop = MicHop(softAgc?.process(filtered.copyOf()) ?: filtered, preGain = filtered, index = captured.get())
                    // The backlog's levels went to the meter while the model loaded.
                    if (hop.index >= capture.backlogHops) trySend(SttEvent.Level(NearSilence.rmsDb(hop.audio)))
                    captured.incrementAndGet()
                    hops.send(hop)
                }
                hops.close()
            } catch (e: CancellationException) {
                hops.close()
                throw e
            } catch (e: Exception) {
                // Hand the failure to the consumer, which reports it.
                hops.close(e as? MicCaptureException ?: MicCaptureException("Microphone failed", e))
            }
        }
        // A live backlog was captured before the session started, so it counts from the start.
        consume(hops) { if (capture.live) maxOf(captured.get(), capture.backlogHops) else captured.get() }
        null
    } catch (e: CancellationException) {
        throw e // the collector went away: no Error, just clean up
    } catch (e: MicCaptureException) {
        Log.e(SESSION_TAG, "Microphone failed mid-session", e)
        SttEvent.Error(SttErrorKind.MIC, e.message ?: "Microphone failed")
    } catch (e: Exception) {
        Log.e(SESSION_TAG, "Recognition failed", e)
        SttEvent.Error(SttErrorKind.RECOGNITION, e.message ?: "Recognition failed")
    } finally {
        producer?.cancel()
        capture.close() // also unblocks a mic read in progress
    }

    if (failure != null) {
        producer?.join() // no Level may follow the Error
        send(failure)
    }
}.flowOn(Dispatchers.Default)

private const val SESSION_TAG = "StreamingSession"

/**
 * [micSession]'s queue for a non-live capture: enough to overlap a read with
 * a decode, and far below [LagMonitor.BEHIND_SECONDS] (15 hops), so the lag
 * it allows (this plus the hop in hand) never reads as falling behind.
 */
internal const val NON_LIVE_QUEUED_HOPS = 4

/**
 * Log how quiet an utterance was, from its pre-gain level [rmsDb], and
 * return it. Numbers only, never audio: this is what calibrates
 * [NearSilence.THRESHOLD_DB].
 */
internal fun judgeQuiet(rmsDb: Float, samples: Int): Quiet {
    val quiet = NearSilence.quiet(rmsDb)
    Log.d(
        NearSilence.LOG_TAG,
        String.format(
            Locale.ROOT,
            "utterance rms=%.1f dBFS samples=%d nearSilent=%b silent=%b",
            rmsDb, samples, quiet.nearSilent, quiet.silent,
        ),
    )
    return quiet
}

/**
 * The session's audio before [SoftAgc], addressed like [VadSegment.start]:
 * by sample index since the VAD's reset. Near-silence is judged on this
 * (#20): the gain lifts quiet noise by up to 30 dB, after which almost
 * nothing reads as near-silent. Holds the utterance in progress plus
 * [LOOKBACK_SAMPLES] before it; not thread-safe.
 */
internal class PreGainAudio {
    private val audio = SampleBuffer()
    private var base = 0 // index of audio's first sample

    fun append(samples: FloatArray) = audio.append(samples)

    /** RMS in dBFS of [count] samples from index [start], clipped to what is held. */
    fun rmsDb(start: Int, count: Int): Float {
        val from = (start - base).coerceIn(0, audio.size)
        val to = (start + count - base).coerceIn(from, audio.size)
        return NearSilence.rmsDb(audio.copyRange(from, to))
    }

    /** Forget the audio before index [index]. */
    fun dropBefore(index: Int) {
        val n = (index - base).coerceIn(0, audio.size)
        audio.dropFirst(n)
        base += n
    }

    companion object {
        /**
         * Audio kept before the utterance (2 s): Silero's segment starts a
         * little before it reports speech (by its min speech duration, 0.25
         * s, and a few windows), so this is ample.
         */
        const val LOOKBACK_SAMPLES = 2 * MIC_SAMPLE_RATE
    }
}

/**
 * Where to cut a long utterance's partial audio: the start of the quietest
 * VAD window (by energy, in half-window steps) that fits in [buffer] from
 * [from] to [to]. Returns [to] if no window fits.
 */
internal fun quietestWindow(buffer: SampleBuffer, from: Int, to: Int): Int {
    val window = StreamingSession.VAD_WINDOW_SIZE
    var best = Double.MAX_VALUE
    var at = to
    var i = from.coerceAtLeast(0)
    while (i + window <= to) {
        val w = buffer.copyRange(i, i + window)
        var energy = 0.0
        for (x in w) energy += x.toDouble() * x
        if (energy < best) {
            best = energy
            at = i
        }
        i += window / 2
    }
    return at
}

/**
 * An utterance's audio: [buffer] from [start] up to where the VAD's segment
 * begins ([segmentStart], an index into [buffer]), then the [segment]. If the
 * segment begins at or before [start] (or before the buffer, just after the
 * previous utterance), it is used as is: the pre-roll never reaches back into
 * audio the previous utterance already had.
 */
internal fun withPreroll(buffer: SampleBuffer, start: Int, segmentStart: Int, segment: FloatArray): FloatArray =
    if (segmentStart <= start) segment else buffer.copyRange(start, segmentStart) + segment

/** A growable float buffer: unboxed audio, amortised appends. Not thread-safe. */
internal class SampleBuffer {
    private var data = FloatArray(MIC_SAMPLE_RATE * 2)

    var size = 0
        private set

    fun append(samples: FloatArray) {
        val needed = size + samples.size
        if (needed > data.size) data = data.copyOf(maxOf(needed, data.size * 2))
        samples.copyInto(data, destinationOffset = size)
        size = needed
    }

    fun copyRange(from: Int, to: Int): FloatArray {
        require(from in 0..to && to <= size) { "range $from..$to outside 0..$size" }
        return data.copyOfRange(from, to)
    }

    /** Remove the first [count] samples. */
    fun dropFirst(count: Int) {
        require(count in 0..size) { "can't drop $count of $size samples" }
        data.copyInto(data, destinationOffset = 0, startIndex = count, endIndex = size)
        size -= count
    }
}

private fun samplesMs(samples: Int): Long = samples * 1000L / MIC_SAMPLE_RATE

/**
 * Where one session's time went (#91), logged once at its end under
 * [StreamingSession.TIMING_TAG] at debug level: numbers only. The VAD's cost
 * (Silero's [SpeechVad.acceptWaveform] per 32 ms window) and each partial's
 * decode. Finals log their own line ([StreamingSession]'s `decodeFinal`)
 * and a `final sent` stamp in [android.os.SystemClock.elapsedRealtime], which
 * the surfaces' `delivered` stamps pair with.
 */
internal class SessionStats {
    private var vadNanos = 0L
    private var vadWindows = 0
    private var vadMaxNanos = 0L
    private val partialMs = mutableListOf<Long>()
    private val partialAudio = mutableListOf<String>() // ms of audio, "e" when the text was empty

    fun vadWindow(nanos: Long) {
        vadNanos += nanos
        vadWindows++
        if (nanos > vadMaxNanos) vadMaxNanos = nanos
    }

    fun partial(ms: Long) {
        partialMs += ms
    }

    fun partialAudio(samples: Int, empty: Boolean) {
        partialAudio += samplesMs(samples).toString() + if (empty) "e" else ""
    }

    fun finalSent() {
        Log.d(StreamingSession.TIMING_TAG, "final sent t=${android.os.SystemClock.elapsedRealtime()}")
    }

    fun log() {
        val sorted = partialMs.sorted()
        Log.d(
            StreamingSession.TIMING_TAG,
            "session vad=${vadNanos / 1_000_000}ms/${vadWindows}win max=${vadMaxNanos / 1000}us " +
                "partials=${partialMs.size} total=${partialMs.sum()}ms median=${sorted.getOrNull(sorted.size / 2) ?: 0}ms " +
                "max=${sorted.lastOrNull() ?: 0}ms each=${partialMs.joinToString(",")} audio=${partialAudio.joinToString(",")}",
        )
    }
}

/** #91: a Final decoded ahead ([from], [to]) counted like [VadSegment.start]; [decodedAt] is the mic's position when it finished. */
private class Speculation(val from: Int, val to: Int, val text: String, val decodeMs: Long, val decodedAt: Int)
