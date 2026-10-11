package app.marmalade.stt.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechServiceTest {

    private val pkg = "app.marmalade.stt"
    private val cls = "app.marmalade.stt.recognizer.MarmaladeRecognitionService"

    @Test
    fun `our service is recognized in long and short form`() {
        assertTrue(SpeechService.isOurs("$pkg/$cls", pkg, cls))
        assertTrue(SpeechService.isOurs("$pkg/.recognizer.MarmaladeRecognitionService", pkg, cls))
        // The debug build: its package differs from the class's.
        assertTrue(SpeechService.isOurs("$pkg.debug/$cls", "$pkg.debug", cls))
    }

    @Test
    fun `anything else is not ours`() {
        assertFalse(SpeechService.isOurs(null, pkg, cls))
        assertFalse(SpeechService.isOurs("", pkg, cls))
        assertFalse(SpeechService.isOurs("com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService", pkg, cls))
        assertFalse(SpeechService.isOurs("$pkg.debug/$cls", pkg, cls)) // our other build
        assertFalse(SpeechService.isOurs(pkg, pkg, cls))
    }
}
