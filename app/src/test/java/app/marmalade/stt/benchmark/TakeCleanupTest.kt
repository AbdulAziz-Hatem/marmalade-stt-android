package app.marmalade.stt.benchmark

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig
import app.marmalade.stt.ui.screen.Dictation
import app.marmalade.stt.ui.screen.DictationState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The benchmark's cleanup is dictation's, with the user's settings, in each engine's language (#45). */
class TakeCleanupTest {

    private val p110 = SttEngineCatalog.byId("parakeet-tdt-110m-en")!!
    private val canary = SttEngineCatalog.byId("canary-180m-flash")!!
    private val english = SpeechPrefs(recognitionLanguage = "en")

    @Test
    fun `fillers go with fillers on`() {
        val cleanup = TakeCleanup(TextProcConfig(removeFillers = true), english, systemLocale = "en-US")
        assertEquals("Hello", cleanup.clean(p110, listOf("um, hello")))
    }

    @Test
    fun `fillers stay with fillers off`() {
        val cleanup = TakeCleanup(TextProcConfig(removeFillers = false), english, systemLocale = "en-US")
        assertEquals("Um, hello", cleanup.clean(p110, listOf("um, hello")))
    }

    @Test
    fun `the user's dictionary and numbers setting apply`() {
        val said = listOf("I tried pair a keet twenty five times")
        val mine = TextProcConfig(replacements = listOf(Replacement("pair a keet", "Parakeet")), numbersAsDigits = true)
        assertEquals("I tried Parakeet 25 times", TakeCleanup(mine, english, "en-US").clean(p110, said))
        val plain = mine.copy(replacements = emptyList(), numbersAsDigits = false)
        assertEquals("I tried pair a keet twenty five times", TakeCleanup(plain, english, "en-US").clean(p110, said))
    }

    @Test
    fun `utterances are committed one by one exactly as the Dictate screen commits Finals`() {
        val config = TextProcConfig()
        val utterances = listOf("um, hello there.", "uh how are you", "new paragraph fine thanks")
        val dictation = Dictation(TextPipeline(config.copy(language = "en")))
        val dictated = utterances.fold(DictationState()) { s, u -> dictation.reduce(s, SttEvent.Final(u, nearSilent = false)) }

        val cleaned = TakeCleanup(config, english, systemLocale = "en-US").clean(p110, utterances)

        assertEquals(dictated.transcript, cleaned)
        assertEquals("Hello there. How are you\n\nFine thanks", cleaned)
    }

    @Test
    fun `each engine is cleaned in the language it would write as the active engine`() {
        // A German speaker: Canary takes the hint and writes German, so the
        // English-only filler rule stays off; an English-only engine writes English.
        val german = SpeechPrefs(recognitionLanguage = "de")
        val cleanup = TakeCleanup(TextProcConfig(), german, systemLocale = "de-DE")
        assertEquals("Er, hallo", cleanup.clean(canary, listOf("er, hallo")))
        assertEquals("Hello", cleanup.clean(p110, listOf("er, hello")))
    }

    @Test
    fun `Parakeet ja - the pre-speech うん goes, a lone reply stays, acronyms in capitals`() {
        val pja = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_JA)!!
        val cleanup = TakeCleanup(TextProcConfig(), SpeechPrefs(), systemLocale = "ja-JP")
        assertEquals("FBIの捜査官。", cleanup.clean(pja, listOf("うん", "fbiの捜査官")))
        assertEquals("うん。", cleanup.clean(pja, listOf("うん")))
        // Whisper on the same phone writes case itself: its "web" stays.
        assertEquals("webサイト。", cleanup.clean(SttEngineCatalog.byId("whisper-turbo")!!, listOf("webサイト")))
    }

    @Test
    fun `no utterances clean to an empty transcript`() {
        assertEquals("", TakeCleanup(TextProcConfig(), english, "en-US").clean(p110, emptyList()))
    }
}
