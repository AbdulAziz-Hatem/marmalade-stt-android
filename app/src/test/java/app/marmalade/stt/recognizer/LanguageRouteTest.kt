package app.marmalade.stt.recognizer

import android.speech.SpeechRecognizer
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which engine serves a caller's language (#90), and the error when none can. */
class LanguageRouteTest {

    private val ultra = SttEngineCatalog.PARAKEET_ULTRA
    private val p110m = "parakeet-tdt-110m-en"
    private val parakeetJa = SttEngineCatalog.PARAKEET_JA
    private val turbo = "whisper-turbo"
    private val qwen3 = SttEngineCatalog.QWEN3
    private val nemotron = SttEngineCatalog.NEMOTRON_35_1120MS
    private val canary = "canary-180m-flash"
    private val granite = "granite-speech-5-470m"

    private fun route(language: String?, vararg installed: String, prefs: SpeechPrefs = SpeechPrefs()) =
        routeLanguage(prefs, language) { it in installed }

    private fun engine(id: String) = LanguageRoute.Engine(checkNotNull(SttEngineCatalog.byId(id)))

    @Test
    fun `no language asked is the active engine`() {
        assertEquals(LanguageRoute.Active, route(null, ultra))
        assertEquals(LanguageRoute.Active, route(null))
    }

    @Test
    fun `the active engine keeps a language it transcribes`() {
        assertEquals(LanguageRoute.Active, route("de-DE", ultra, turbo))
        assertEquals(LanguageRoute.Active, route("en", ultra, p110m))
    }

    @Test
    fun `another installed engine takes a language the active one doesn't`() {
        val small = SpeechPrefs(selectedEngineId = p110m)
        assertEquals(engine(ultra), route("es-ES", p110m, ultra, prefs = small))
    }

    @Test
    fun `an active engine that isn't installed doesn't keep it`() {
        assertEquals(engine(p110m), route("en-US", p110m))
    }

    @Test
    fun `several candidates - the near-best measured one, then preference`() {
        // Japanese: Parakeet ja (5.4) is best, turbo (7.2) is near it, Qwen3 (13.4) is not.
        assertEquals(engine(parakeetJa), route("ja-JP", ultra, turbo, qwen3, parakeetJa))
        // Without Parakeet ja, turbo beats Qwen3 although Qwen3 comes first in preference.
        assertEquals(engine(turbo), route("ja", ultra, qwen3, turbo))
        // Hindi: Nemotron is far better than turbo and Qwen3.
        assertEquals(engine(nemotron), route("hi-IN", ultra, turbo, qwen3, nemotron))
        // Spanish on an English-only active engine: Ultra (3.8) is near turbo (3.1) and comes first in preference;
        // Canary (8.0) is out.
        val small = SpeechPrefs(selectedEngineId = p110m)
        assertEquals(engine(ultra), route("es", p110m, turbo, canary, ultra, prefs = small))
        assertEquals(engine(turbo), route("es", p110m, canary, turbo, prefs = small))
    }

    @Test
    fun `an unmeasured candidate comes after the measured near-best ones`() {
        // Polish: nothing measured, so preference order: Ultra before turbo.
        val ja = SpeechPrefs(selectedEngineId = parakeetJa)
        assertEquals(engine(ultra), route("pl", parakeetJa, turbo, ultra, prefs = ja))
    }

    @Test
    fun `a developer engine that may not run is never picked`() {
        val ja = SpeechPrefs(selectedEngineId = parakeetJa)
        assertEquals(LanguageRoute.Unavailable(downloadable = true), route("en", parakeetJa, granite, prefs = ja))
        assertEquals(engine(granite), route("en", parakeetJa, granite, prefs = ja.copy(developerEngines = true)))
    }

    @Test
    fun `nothing installed is the active engine, which then reports no model, unless no engine knows the language`() {
        assertEquals(LanguageRoute.Active, route("ja"))
        assertEquals(LanguageRoute.Unavailable(downloadable = false), route("xx"))
    }

    @Test
    fun `a language only a downloadable engine knows is unavailable for now`() {
        assertEquals(LanguageRoute.Unavailable(downloadable = true), route("ja", ultra))
        assertEquals(LanguageRoute.Unavailable(downloadable = true), route("de", p110m))
    }

    @Test
    fun `a language no engine knows is not supported`() {
        assertEquals(LanguageRoute.Unavailable(downloadable = false), route("xx", ultra))
        assertEquals(LanguageRoute.Unavailable(downloadable = false), route("xx-YY", ultra, turbo))
    }

    // ---- languageError ----------------------------------------------------

    @Test
    fun `before Android 12 a missing language is a client error`() {
        assertEquals(SpeechRecognizer.ERROR_CLIENT, languageError(LanguageRoute.Unavailable(true), sdkInt = 30))
        assertEquals(SpeechRecognizer.ERROR_CLIENT, languageError(LanguageRoute.Unavailable(false), sdkInt = 30))
    }

    @Test
    fun `from Android 12 - unavailable when downloadable, else not supported`() {
        assertEquals(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, languageError(LanguageRoute.Unavailable(true), sdkInt = 31))
        assertEquals(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, languageError(LanguageRoute.Unavailable(false), sdkInt = 31))
        assertEquals(13, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(12, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        assertEquals(5, SpeechRecognizer.ERROR_CLIENT)
    }
}
