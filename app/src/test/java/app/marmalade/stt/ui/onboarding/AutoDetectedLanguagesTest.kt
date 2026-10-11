package app.marmalade.stt.ui.onboarding

import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoDetectedLanguagesTest {

    private fun engine(id: String) = checkNotNull(SttEngineCatalog.byId(id))

    @Test
    fun `a multilingual engine that detects the language itself gives its count`() {
        val parakeet = SttEngineCatalog.default
        assertEquals(EngineLanguages.of(parakeet).size, autoDetectedLanguages(parakeet))
        // Nemotron 3.5: Hindi, Arabic and Vietnamese since #37.
        assertEquals(3, autoDetectedLanguages(engine(SttEngineCatalog.NEMOTRON_35_1120MS)))
        // Whisper turbo carries Japanese, Chinese, Korean, Turkish and Norwegian now.
        val turbo = engine("whisper-turbo")
        assertEquals(EngineLanguages.of(turbo).size, autoDetectedLanguages(turbo))
    }

    @Test
    fun `a one-language engine gets no sentence`() {
        assertNull(autoDetectedLanguages(engine("parakeet-tdt-110m-en")))
    }

    @Test
    fun `an engine told the language by default gets no sentence`() {
        // Canary 180M Flash starts on English until the user picks another.
        assertNull(autoDetectedLanguages(engine("canary-180m-flash")))
    }
}
