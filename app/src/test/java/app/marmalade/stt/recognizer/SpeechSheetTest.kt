package app.marmalade.stt.recognizer

import android.app.Activity
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSheetTest {

    @Test
    fun `listening phases follow the engine`() {
        var s = SpeechSheet.started(modelLoaded = false)
        assertEquals(SheetPhase.LOADING, s.phase)
        s = SpeechSheet.reduce(s, SttEvent.Ready)
        assertEquals(SheetPhase.LISTENING, s.phase)
        s = SpeechSheet.reduce(s, SttEvent.SpeechStarted)
        assertEquals(SheetPhase.HEARING, s.phase)
        s = SpeechSheet.reduce(s, SttEvent.Partial("hello"))
        assertEquals("hello", s.partial)
        s = SpeechSheet.reduce(s, SttEvent.Final("", nearSilent = true))
        assertEquals(SheetPhase.LISTENING, s.phase)
        assertEquals("", s.partial)
        assertEquals(Activity.RESULT_CANCELED, s.resultCode)
    }

    @Test
    fun `the mic opening ends Loading model, and Ready after it changes nothing (#78)`() {
        var s = SpeechSheet.reduce(SpeechSheet.started(modelLoaded = false), SttEvent.Listening)
        assertEquals(SheetPhase.LISTENING, s.phase)
        assertEquals(SheetPhase.LISTENING, SpeechSheet.reduce(s, SttEvent.Ready).phase)

        // Done pressed while the model loads: Ready doesn't undo "Finishing".
        s = SpeechSheet.reduce(SpeechSheet.stopping(s), SttEvent.Ready)
        assertEquals(SheetPhase.FINISHING, s.phase)
    }

    @Test
    fun `done keeps the sheet finishing even if speech starts again`() {
        var s = SpeechSheet.stopping(SpeechSheet.started(modelLoaded = true))
        assertEquals(SheetPhase.FINISHING, s.phase)
        s = SpeechSheet.reduce(s, SttEvent.SpeechStarted)
        assertEquals(SheetPhase.FINISHING, s.phase)
    }

    @Test
    fun `problems carry their message and result code`() {
        val noSpeech = SpeechSheet.ended(OneShotOutcome.NoMatch(heardSpeech = false))
        assertEquals(SheetProblem.NO_SPEECH, noSpeech.problem)
        assertEquals(RecognizerIntent.RESULT_NO_MATCH, noSpeech.resultCode)
        assertTrue(noSpeech.problem!!.canRetry)

        val noModel = SpeechSheet.ended(OneShotOutcome.Failed(SttErrorKind.MODEL))
        assertEquals(SheetProblem.NO_MODEL, noModel.problem)
        assertEquals(RecognizerIntent.RESULT_CLIENT_ERROR, noModel.resultCode)
        assertFalse(noModel.problem!!.canRetry)

        val mic = SpeechSheet.ended(OneShotOutcome.Failed(SttErrorKind.MIC))
        assertEquals(RecognizerIntent.RESULT_AUDIO_ERROR, mic.resultCode)

        val denied = SpeechSheet.micDenied()
        assertEquals(SheetProblem.MIC_BLOCKED, denied.problem)
        assertEquals(RecognizerIntent.RESULT_AUDIO_ERROR, denied.resultCode)
    }

    @Test
    fun `a language no installed engine transcribes is a problem without retry (#90)`() {
        val unavailable = SpeechSheet.languageUnavailable(LanguageRoute.Unavailable(downloadable = true), "fr-FR")
        assertEquals(SheetPhase.PROBLEM, unavailable.phase)
        assertEquals(SheetProblem.LANGUAGE_UNAVAILABLE, unavailable.problem)
        assertEquals("fr-FR", unavailable.language)
        assertEquals(RecognizerIntent.RESULT_CLIENT_ERROR, unavailable.resultCode)
        assertFalse(unavailable.problem!!.canRetry)
        assertFalse(unavailable.canLock)

        val unsupported = SpeechSheet.languageUnavailable(LanguageRoute.Unavailable(downloadable = false), "xx")
        assertEquals(SheetProblem.LANGUAGE_NOT_SUPPORTED, unsupported.problem)
        assertEquals(RecognizerIntent.RESULT_CLIENT_ERROR, unsupported.resultCode)
        assertFalse(unsupported.problem!!.canRetry)
    }

    @Test
    fun `speech recognizer error codes`() {
        assertEquals(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, speechRecognizerError(OneShotOutcome.NoMatch(false), 34))
        assertEquals(SpeechRecognizer.ERROR_NO_MATCH, speechRecognizerError(OneShotOutcome.NoMatch(true), 34))
        assertEquals(SpeechRecognizer.ERROR_AUDIO, speechRecognizerError(OneShotOutcome.Failed(SttErrorKind.MIC), 34))
        assertEquals(
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            speechRecognizerError(OneShotOutcome.Failed(SttErrorKind.MODEL), 31),
        )
        assertEquals(SpeechRecognizer.ERROR_CLIENT, speechRecognizerError(OneShotOutcome.Failed(SttErrorKind.MODEL), 30))
        assertEquals(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            speechRecognizerError(OneShotOutcome.Failed(SttErrorKind.BUSY), 34),
        )
        assertEquals(Activity.RESULT_OK, OneShotOutcome.Text("hi").recognizerResultCode())
    }

    // ---- The lock (#43) ----------------------------------------------------

    @Test
    fun `press and hold locks a listening sheet, and Finals leave it locked`() {
        var s = SpeechSheet.reduce(SpeechSheet.started(modelLoaded = true), SttEvent.SpeechStarted)
        assertTrue(s.canLock)
        s = SpeechSheet.locking(s)
        assertTrue(s.locked)
        assertFalse(s.canLock)
        s = SpeechSheet.reduce(s, SttEvent.Final("first one.", nearSilent = false))
        assertTrue(s.locked)
        assertEquals(SheetPhase.LISTENING, s.phase)
        // Loading can be locked too: the request is already running.
        assertTrue(SpeechSheet.locking(SpeechSheet.started(modelLoaded = false)).locked)
    }

    @Test
    fun `a tap unlocks and finishes, like Done`() {
        val locked = SpeechSheet.locking(SpeechSheet.started(modelLoaded = true))
        val s = SpeechSheet.stopping(locked)
        assertFalse(s.locked)
        assertEquals(SheetPhase.FINISHING, s.phase)
        assertFalse(s.canLock)
    }

    @Test
    fun `nothing to lock while finishing or after a problem`() {
        val finishing = SpeechSheet.stopping(SpeechSheet.started(modelLoaded = true))
        assertEquals(finishing, SpeechSheet.locking(finishing))
        val problem = SpeechSheet.ended(OneShotOutcome.NoMatch(heardSpeech = false))
        assertEquals(problem, SpeechSheet.locking(problem))
        // Unlocked, a sheet behaves as before.
        assertFalse(SpeechSheet.started(modelLoaded = true).locked)
    }
}
