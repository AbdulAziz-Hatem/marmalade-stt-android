package app.marmalade.stt.recognizer

import android.app.Application
import android.os.Bundle
import android.speech.RecognizerIntent
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.speech.PcmFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the speech-input sheet returns, and the request it runs (#90). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecognizerResultsTest {

    @Test
    fun `results carry the hypotheses and an unavailable confidence for each`() {
        val intent = recognizerResults(arrayListOf("Hello there.", "hello there"))
        assertEquals(listOf("Hello there.", "hello there"), intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        assertArrayEquals(floatArrayOf(-1f, -1f), intent.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES), 0f)
    }

    @Test
    fun `the pending intent's fill-in keeps the caller's bundle`() {
        val callerBundle = Bundle().apply { putString("query_id", "42") }
        val fillIn = recognizerResults(arrayListOf("Hi."), callerBundle)
        assertEquals("42", fillIn.getStringExtra("query_id"))
        assertEquals(listOf("Hi."), fillIn.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        assertArrayEquals(floatArrayOf(-1f), fillIn.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES), 0f)
    }

    @Test
    fun `the results follow max results and formatting`() {
        val formatting = RecognitionRequest(formatting = true)
        assertEquals(listOf("Hi.", "hi"), recognizerResults(formatting.hypotheses("Hi.", "hi")).getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        val one = formatting.copy(maxResults = 1)
        assertEquals(listOf("Hi."), recognizerResults(one.hypotheses("Hi.", "hi")).getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
    }

    @Test
    fun `the sheet runs a plain one-shot, keeping the caller's pause`() {
        val segmented = RecognitionRequest(
            completeSilenceMs = 2000,
            minimumLengthMs = 3000,
            segmented = RecognitionRequest.Segmented.COMPLETE_SILENCE,
            audioSource = PcmFormat(),
        ).forSheet()
        assertNull(segmented.segmented)
        assertNull(segmented.audioSource)
        assertEquals(OneShotEnd.FirstEnd, segmented.end())
        assertEquals(2f, segmented.enginePauseSeconds())
        assertEquals(3000L, segmented.minimumLengthMs)
    }

    @Test
    fun `no request language and no other engine keep the stored cleanup language`() {
        assertNull(RecognitionRequest().cleanupLanguage(SpeechPrefs(), engine = null))
    }
}
