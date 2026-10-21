package app.marmalade.stt.recognizer

import android.app.Application
import android.speech.RecognizerIntent
import app.marmalade.stt.data.SpeechPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ACTION_GET_LANGUAGE_DETAILS answer (#90). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LanguageDetailsTest {

    private val everythingInstalled: (String) -> Boolean = { true }

    @Test
    fun `on Auto the preference is the phone's locale, and the installed languages are listed`() {
        val prefs = SpeechPrefs()
        val details = languageDetails(prefs, "de-DE", onlyPreference = false, everythingInstalled)
        assertEquals("de-DE", details.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertEquals(
            recognitionLanguages(prefs, "de-DE", everythingInstalled).installed,
            details.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES),
        )
    }

    @Test
    fun `a chosen recognition language is the preference`() {
        val details = languageDetails(SpeechPrefs(recognitionLanguage = "fr"), "en-US", onlyPreference = false, everythingInstalled)
        assertEquals("fr", details.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertEquals("fr", details.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)?.first())
    }

    @Test
    fun `only the preference when that is all the caller asked for`() {
        val details = languageDetails(SpeechPrefs(), "en-US", onlyPreference = true, everythingInstalled)
        assertEquals("en-US", details.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertFalse(details.containsKey(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES))
    }

    @Test
    fun `nothing installed lists no languages`() {
        val details = languageDetails(SpeechPrefs(), "en-US", onlyPreference = false) { false }
        assertEquals(emptyList<String>(), details.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES))
    }
}
