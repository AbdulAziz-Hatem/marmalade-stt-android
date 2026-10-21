package app.marmalade.stt.recognizer

import app.marmalade.stt.recognizer.RecognitionRequest.Segmented
import app.marmalade.stt.speech.EndOfDictation
import app.marmalade.stt.speech.PcmFormat
import app.marmalade.stt.speech.SessionOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What ends a RecognitionService request, and the engine's pause for it (#90). */
class RequestEndTest {

    @Test
    fun `a plain request ends at its first end`() {
        assertEquals(OneShotEnd.FirstEnd, RecognitionRequest().end())
        // A plain request's pause is the engine's end of utterance instead.
        assertEquals(OneShotEnd.FirstEnd, RecognitionRequest(completeSilenceMs = 3_000).end())
        assertEquals(OneShotEnd.FirstEnd, RecognitionRequest(segmented = Segmented.MINIMUM_LENGTH, minimumLengthMs = 9_000).end())
    }

    @Test
    fun `a session segmented by the pause ends at that pause`() {
        assertEquals(OneShotEnd.Pause(3_000), RecognitionRequest(segmented = Segmented.COMPLETE_SILENCE, completeSilenceMs = 3_000).end())
        // Without the pause it names, the first end.
        assertEquals(OneShotEnd.FirstEnd, RecognitionRequest(segmented = Segmented.COMPLETE_SILENCE).end())
    }

    @Test
    fun `a caller's stream ends with the stream, whatever else it asks`() {
        assertEquals(OneShotEnd.AudioEnd, RecognitionRequest(audioSource = PcmFormat()).end())
        assertEquals(
            OneShotEnd.AudioEnd,
            RecognitionRequest(audioSource = PcmFormat(), segmented = Segmented.COMPLETE_SILENCE, completeSilenceMs = 3_000).end(),
        )
        assertEquals(OneShotEnd.AudioEnd, RecognitionRequest(audioSource = PcmFormat(), segmented = Segmented.AUDIO_SOURCE).end())
    }

    @Test
    fun `the engine's pause - a caller's silence in seconds, except where it ends a segmented session`() {
        assertNull(RecognitionRequest().enginePauseSeconds())
        assertEquals(2.5f, RecognitionRequest(completeSilenceMs = 2_500).enginePauseSeconds())
        assertEquals(2.5f, RecognitionRequest(completeSilenceMs = 2_500, segmented = Segmented.MINIMUM_LENGTH).enginePauseSeconds())
        assertEquals(2.5f, RecognitionRequest(completeSilenceMs = 2_500, audioSource = PcmFormat()).enginePauseSeconds())
        assertNull(RecognitionRequest(completeSilenceMs = 2_500, segmented = Segmented.COMPLETE_SILENCE).enginePauseSeconds())
    }

    @Test
    fun `a caller's pause reaches the VAD, up to five seconds`() {
        fun vad(seconds: Float) = SessionOptions(end = EndOfDictation.PAUSE, minSilenceSeconds = seconds).vadMinSilenceSeconds
        assertEquals(2.5f, vad(2.5f))
        assertEquals(SessionOptions.MAX_CALLER_SILENCE_SECONDS, vad(30f))
        assertEquals(SessionOptions.MIN_SILENCE_RANGE_START, vad(0.05f))
    }
}
