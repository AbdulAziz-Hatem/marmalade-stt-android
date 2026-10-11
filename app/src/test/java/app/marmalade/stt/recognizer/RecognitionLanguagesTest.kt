package app.marmalade.stt.recognizer

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the RecognitionService reports as supported languages (#41). */
class RecognitionLanguagesTest {

    private val dev = SpeechPrefs(developerEngines = true)

    @Test
    fun `the default engine reports all its languages, the spoken one first`() {
        val languages = recognitionLanguages(SpeechPrefs(), "de-DE")
        assertEquals(listOf("de", "de-DE"), languages.take(2))
        assertEquals(EngineLanguages.of(SttEngineCatalog.default) + "de-DE", languages.toSet())
        assertTrue("en" in languages)
    }

    @Test
    fun `a chosen recognition language leads the list`() {
        val languages = recognitionLanguages(SpeechPrefs(recognitionLanguage = "fr"), "en-US")
        assertEquals("fr", languages.first())
        // The phone's own tag still follows its language.
        assertEquals("en-US", languages[languages.indexOf("en") + 1])
    }

    @Test
    fun `a blank or missing EXTRA_LANGUAGE is no request language`() {
        assertEquals(null, requestedLanguage(null))
        assertEquals(null, requestedLanguage("  "))
        assertEquals("de-DE", requestedLanguage(" de-DE "))
    }

    @Test
    fun `an English-only engine reports English only`() {
        val small = SpeechPrefs(selectedEngineId = "parakeet-tdt-110m-en")
        assertEquals(listOf("en", "en-US"), recognitionLanguages(small, "en-US"))
        assertEquals(listOf("en"), recognitionLanguages(small, "de-DE"))
    }

    @Test
    fun `an engine held to one language by its hint reports only that one`() {
        val canary = dev.copy(selectedEngineId = "canary-180m-flash")
        assertEquals(listOf("de", "de-AT"), recognitionLanguages(canary, "de-AT"))
        assertEquals(listOf("es"), recognitionLanguages(canary.copy(recognitionLanguage = "es"), "en-US"))
    }

    @Test
    fun `an auto-detecting engine on a chosen language it takes is held to it`() {
        val nemotron = SpeechPrefs(selectedEngineId = SttEngineCatalog.NEMOTRON_35_1120MS, recognitionLanguage = "hi")
        assertEquals(listOf("hi"), recognitionLanguages(nemotron, "en-US"))
        // On Auto it detects, so it reports everything it knows: Hindi, Arabic and Vietnamese (#37).
        assertEquals(listOf("ar", "hi", "vi"), recognitionLanguages(nemotron.copy(recognitionLanguage = ""), "en-US"))
        assertEquals(listOf("vi", "vi-VN", "ar", "hi"), recognitionLanguages(nemotron.copy(recognitionLanguage = ""), "vi-VN"))
        // A chosen language it doesn't take (Japanese, turbo's since #37) leaves it detecting.
        assertEquals(listOf("ar", "hi", "vi"), recognitionLanguages(nemotron.copy(recognitionLanguage = "ja"), "en-US"))
    }
}
