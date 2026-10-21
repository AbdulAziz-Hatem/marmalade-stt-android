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
 * | each utterance, segmented   | segmentResults                                   |
 * | the end                     | endOfSpeech (if speech was heard), then results, endOfSegmentedSession or error |
 *
 * Result lists come from [RecognitionRequest.hypotheses]: the text after
 * `:textproc`, and its raw words after it when the caller asked for
 * formatting. A segmented session ends with `endOfSegmentedSession` when it
 * heard nothing too; only a failure (mic, model) is an error there.
 */
class ServiceSession(private val request: RecognitionRequest, private val client: RecognitionClient) {

    /** `beginningOfSpeech` went out: once per request, also when speech is flushed after a stop. */
    private var begun = false

    /** Speech was heard and `endOfSpeech` hasn't been sent. */
    private var speechOpen = false

    suspend fun run(oneShot: OneShotSession) {
        val ready = ReadyForSpeech()
        val segmented = request.segmented != null
        val outcome = oneShot.run(
            onSegment = { text, raw -> if (segmented) client.segmentResults(request.hypotheses(text, raw)) },
        ) { event ->
            if (ready.isDue(event)) client.readyForSpeech()
            when (event) {
                SttEvent.SpeechStarted -> if (!begun) {
                    begun = true
                    speechOpen = true
                    client.beginningOfSpeech()
                }
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
    fun stopped() = endOfSpeech()

    private fun endOfSpeech() {
        if (!speechOpen) return
        speechOpen = false
        client.endOfSpeech()
    }
}
