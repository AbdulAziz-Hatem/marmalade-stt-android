package app.marmalade.stt.recognizer

import app.marmalade.stt.speech.SttEvent

/**
 * The callbacks a RecognitionService client receives, as plain values: the
 * service wraps `RecognitionService.Callback` in one (bundles, binder
 * errors), and tests record them.
 */
interface RecognitionClient {
    fun readyForSpeech()
    fun beginningOfSpeech()

    /** [rmsDb]: the hop's level in dBFS; the service maps it onto the scale apps expect. */
    fun rmsChanged(rmsDb: Float)
    fun partialResults(hypotheses: List<String>)
    fun endOfSpeech()
    fun results(hypotheses: List<String>)
    fun segmentResults(hypotheses: List<String>)
    fun endOfSegmentedSession()
    fun error(code: Int)
}

/**
 * One RecognitionService request (#90): runs its [OneShotSession] and tells
 * [client] what happens, in the order Android documents.
 *
 * | Session                     | Callback                                         |
 * |-----------------------------|--------------------------------------------------|
 * | Listening (or Ready), once  | readyForSpeech                                   |
 * | first SpeechStarted         | beginningOfSpeech                                |
 * | Level                       | rmsChanged                                       |
 * | Partial                     | partialResults, if asked for                     |
 * | segmented: SpeechStarted    | beginningOfSpeech, for each utterance            |
 * | segmented: SpeechEnded      | endOfSpeech, for each utterance (#124)           |
 * | each utterance, segmented   | segmentResults                                   |
 * | the end                     | endOfSpeech (if speech was heard and it hasn't gone out), then results, endOfSegmentedSession or error |
 *
 * A segmented session reports each utterance's end as soon as the engine
 * decides it, before its decode (#124): Waze asks for one and stops its
 * recording at the first `onEndOfSpeech`, so waiting for the session's end
 * (its own `stopListening`, 10 s in) delayed its command by that long. A
 * plain request keeps one begin and one end: its first utterance may turn
 * out to be noise, and listening goes on.
 *
 * Result lists come from [RecognitionRequest.hypotheses]: the text after
 * `:textproc`, and its raw words after it when the caller asked for
 * formatting. A segmented session ends with `endOfSegmentedSession` when it
 * heard nothing too; only a failure (mic, model) is an error there.
 */
class ServiceSession(private val request: RecognitionRequest, private val client: RecognitionClient) {

    /**
     * `beginningOfSpeech` went out: once per request (per utterance when
     * segmented), also when speech is flushed after a stop.
     */
    private var begun = false

    /** Speech was heard and `endOfSpeech` hasn't been sent. */
    private var speechOpen = false

    /** The caller stopped ([stopped]): what the engine flushes after it begins no new utterance. */
    private var callerStopped = false

    suspend fun run(oneShot: OneShotSession) {
        val ready = ReadyForSpeech()
        val segmented = request.segmented != null
        val outcome = oneShot.run(
            onSegment = { text, raw -> if (segmented) client.segmentResults(request.hypotheses(text, raw)) },
        ) { event ->
            if (ready.isDue(event)) client.readyForSpeech()
            when (event) {
                SttEvent.SpeechStarted -> if (!begun || (segmented && !speechOpen && !callerStopped)) {
                    begun = true
                    speechOpen = true
                    client.beginningOfSpeech()
                }
                SttEvent.SpeechEnded -> if (segmented) endOfSpeech()
                is SttEvent.Level -> client.rmsChanged(event.rmsDb)
                // A blank partial too: the last one turned out to be noise (`cleanPartial`). With
                // formatting the raw hypothesis repeats it: the engine's own partial isn't kept.
                is SttEvent.Partial -> if (request.partials) request.partial(event.text).let { client.partialResults(request.hypotheses(it, it)) }
                else -> Unit
            }
        }
        endOfSpeech()
        when {
            outcome is OneShotOutcome.Failed -> client.error(speechRecognizerError(outcome))
            segmented -> client.endOfSegmentedSession()
            outcome is OneShotOutcome.Text -> client.results(request.hypotheses(outcome.text, outcome.raw))
            else -> client.error(speechRecognizerError(outcome))
        }
    }

    /** The caller stopped: no more speech is coming (`onStopListening`). The result follows. */
    fun stopped() {
        callerStopped = true
        endOfSpeech()
    }

    private fun endOfSpeech() {
        if (!speechOpen) return
        speechOpen = false
        client.endOfSpeech()
    }
}
