package app.marmalade.stt.recognizer

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the RecognitionService reports as supported languages (#41, #90). */
class RecognitionLanguagesTest {

    private val ultra = SttEngineCatalog.PARAKEET_ULTRA
    private val p110m = "parakeet-tdt-110m-en"
    private val parakeetJa = SttEngineCatalog.PARAKEET_JA
    private val turbo = "whisper-turbo"
    private val granite = "granite-speech-5-470m"

    private fun languages(systemLocale: String, vararg installed: String, prefs: SpeechPrefs = SpeechPrefs()) =
        recognitionLanguages(prefs, systemLocale) { it in installed }

    private fun codes(id: String) = EngineLanguages.of(checkNotNull(SttEngineCatalog.byId(id)))

    @Test
    fun `the default engine reports all its languages, the spoken one first`() {
        val languages = languages("de-DE", ultra)
        assertEquals(listOf("de", "de-DE"), languages.installed.take(2))
        assertEquals(codes(ultra) + "de-DE", languages.installed.toSet())
        assertEquals(languages.installed.size, languages.installed.toSet().size)
        assertTrue("en" in languages.installed)
    }

    @Test
    fun `every installed engine's languages, the active engine's first`() {
        val small = SpeechPrefs(selectedEngineId = p110m)
        assertEquals(listOf("en", "ja"), languages("ko-KR", p110m, parakeetJa, prefs = small).installed)
        assertEquals(listOf("ja", "en"), languages("ko-KR", p110m, parakeetJa, prefs = small.copy(selectedEngineId = parakeetJa)).installed)
    }

    @Test
    fun `the spoken language leads, and the phone's full tag follows its language`() {
        val japanese = SpeechPrefs(recognitionLanguage = "ja")
        val installed = languages("en-US", ultra, parakeetJa, prefs = japanese).installed
        assertEquals("ja", installed.first())
        assertEquals("en-US", installed[installed.indexOf("en") + 1])
        // The active engine's languages, sorted, come before the others'.
        assertEquals(codes(ultra).sorted(), installed.drop(1).filterNot { it == "en-US" })
    }

    @Test
    fun `downloadable - the visible engines not installed, minus what is installed`() {
        val languages = languages("en-US", ultra)
        val expected = (codes(p110m) + codes(parakeetJa) + codes("canary-180m-flash") +
            codes(SttEngineCatalog.NEMOTRON_35_1120MS) + codes(turbo) + codes(SttEngineCatalog.QWEN3))
            .filterNot { it in codes(ultra) }.distinct().sorted()
        assertEquals(expected, languages.downloadable)
        assertTrue("ja" in languages.downloadable)
        assertTrue("en" !in languages.downloadable)
    }

    @Test
    fun `nothing installed - nothing installed reported, every user language downloadable`() {
        val languages = languages("ja-JP")
        assertEquals(emptyList<String>(), languages.installed)
        assertEquals(EngineLanguages.userChoices(), languages.downloadable.filterNot { it == "ja-JP" })
        assertEquals("ja-JP", languages.downloadable[languages.downloadable.indexOf("ja") + 1])
    }

    @Test
    fun `a developer engine counts only when it may run`() {
        val ja = SpeechPrefs(selectedEngineId = parakeetJa)
        assertEquals(listOf("ja"), languages("ja-JP", parakeetJa, granite, prefs = ja).installed.filterNot { it == "ja-JP" })
        assertEquals(listOf("ja", "en"), languages("ko-KR", parakeetJa, granite, prefs = ja.copy(developerEngines = true)).installed)
    }

    @Test
    fun `the phone's tag isn't repeated when it is a bare language`() {
        val small = SpeechPrefs(selectedEngineId = p110m)
        assertEquals(listOf("en"), languages("en", p110m, prefs = small).installed)
        assertEquals(listOf("en", "en-US"), languages("en-US", p110m, prefs = small).installed)
    }

    @Test
    fun `a blank or missing EXTRA_LANGUAGE is no request language`() {
        assertEquals(null, requestedLanguage(null))
        assertEquals(null, requestedLanguage("  "))
        assertEquals("de-DE", requestedLanguage(" de-DE "))
    }
}
