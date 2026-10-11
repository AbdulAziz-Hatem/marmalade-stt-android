package app.marmalade.stt.ui.screen

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.CorruptProblem
import app.marmalade.stt.install.EngineQuality
import app.marmalade.stt.install.InstallFailure
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.QualityRating
import app.marmalade.stt.install.SpeedBudget
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.ui.screen.EngineAction.CANCEL
import app.marmalade.stt.ui.screen.EngineAction.DOWNLOAD
import app.marmalade.stt.ui.screen.EngineAction.DOWNLOAD_AGAIN
import app.marmalade.stt.ui.screen.EngineAction.REMOVE
import app.marmalade.stt.ui.screen.EngineAction.REMOVE_DOWNLOAD
import app.marmalade.stt.ui.screen.EngineAction.RESUME
import app.marmalade.stt.ui.screen.EngineAction.RETRY
import app.marmalade.stt.ui.screen.EngineSelection.NONE
import app.marmalade.stt.ui.screen.EngineSelection.SELECTABLE
import app.marmalade.stt.ui.screen.EngineSelection.SELECTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineCardStateTest {

    private val default = SttEngineCatalog.default
    private val canary = SttEngineCatalog.byId("canary-180m-flash")!!
    private val nemotron = SttEngineCatalog.byId(SttEngineCatalog.NEMOTRON_35_1120MS)!!
    private val turbo = SttEngineCatalog.byId("whisper-turbo")!!
    private val installed = InstallState.Installed

    @Test
    fun `an engine that isn't installed shows its install actions, no radio`() {
        val prefs = SpeechPrefs()
        assertEquals(EngineCardUi(NONE, listOf(DOWNLOAD)), engineCardUi(canary, InstallState.NotInstalled, prefs))
        assertEquals(EngineCardUi(NONE, listOf(CANCEL)), engineCardUi(canary, InstallState.Downloading(1L, 10L, "x"), prefs))
        assertEquals(EngineCardUi(NONE, listOf(REMOVE_DOWNLOAD, RESUME)), engineCardUi(canary, InstallState.Paused(1L, 10L), prefs))
        assertEquals(
            EngineCardUi(NONE, listOf(RETRY)),
            engineCardUi(canary, InstallState.Failed("net", InstallFailure.NETWORK), prefs),
        )
        // The active engine too: it can't run without its files.
        assertEquals(EngineCardUi(NONE, listOf(DOWNLOAD)), engineCardUi(default, InstallState.NotInstalled, prefs))
    }

    @Test
    fun `a damaged model can be downloaded again or removed from the card`() {
        val corrupt = InstallState.Corrupt(CorruptProblem.MISSING, "x")
        assertEquals(EngineCardUi(NONE, listOf(REMOVE, DOWNLOAD_AGAIN)), engineCardUi(canary, corrupt, SpeechPrefs()))
        // Even the active one: it can't be picked while damaged.
        assertEquals(EngineCardUi(NONE, listOf(REMOVE, DOWNLOAD_AGAIN)), engineCardUi(default, corrupt, SpeechPrefs()))
    }

    @Test
    fun `an installed engine is selected when in use, else one tap picks it`() {
        val onCanary = SpeechPrefs(selectedEngineId = canary.id)
        assertEquals(EngineCardUi(SELECTED, listOf(REMOVE)), engineCardUi(canary, installed, onCanary))
        assertEquals(EngineCardUi(SELECTABLE, listOf(REMOVE)), engineCardUi(default, installed, onCanary))
        // Nothing picked and nothing recommended: the default runs.
        assertEquals(EngineCardUi(SELECTED, listOf(REMOVE)), engineCardUi(default, installed, SpeechPrefs()))
        // Nothing picked: the recommended engine runs.
        assertEquals(
            EngineCardUi(SELECTED, listOf(REMOVE)),
            engineCardUi(turbo, installed, SpeechPrefs(recommendedEngineId = turbo.id)),
        )
    }

    @Test
    fun `no radio until the settings are read, or for an engine that can't be picked`() {
        assertEquals(EngineCardUi(NONE, listOf(REMOVE)), engineCardUi(canary, installed, prefs = null))
        // A hidden developer engine: picking it would be ignored.
        assertEquals(EngineCardUi(NONE, listOf(REMOVE)), engineCardUi(granite, installed, SpeechPrefs()))
        // Recommended makes it visible, and so pickable.
        assertEquals(
            EngineCardUi(SELECTABLE, listOf(REMOVE)),
            engineCardUi(granite, installed, SpeechPrefs(selectedEngineId = canary.id, recommendedEngineId = granite.id)),
        )
    }

    private val p110 = SttEngineCatalog.byId("parakeet-tdt-110m-en")!!
    private val pja = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_JA)!!
    private val granite = SttEngineCatalog.byId("granite-speech-5-470m")!! // a developer engine
    private val qwen = SttEngineCatalog.byId(SttEngineCatalog.QWEN3)!!

    /** Qwen3-ASR when it is a user engine (#50): over a Pixel 8a's budget, so under Other engines. */
    private val qwenIfUser = listOfNotNull(qwen.takeIf { !it.developer })

    /** A phone [factor]× a Pixel 8a, from its probe time. */
    private fun phone(factor: Double) = SpeechPrefs(deviceProbeMs = (SpeedBudget.PROBE_REFERENCE_MS / factor).toLong())

    @Test
    fun `a Pixel 8a gets five user engines as cards and turbo under Other engines`() {
        // The budget is 3.57 Parakeet Ultras; with the 1.25 band Nemotron 3.5 (2.86) fits, turbo (9.79) doesn't.
        assertEquals(
            EngineGroups(main = listOf(default, p110, pja, canary, nemotron), other = listOf(turbo) + qwenIfUser),
            engineGroups(SpeechPrefs(), emptySet()),
        )
        // The recommended engine leads.
        assertEquals(
            listOf(nemotron, default, p110, pja, canary),
            engineGroups(SpeechPrefs(recommendedEngineId = nemotron.id), emptySet()).main,
        )
    }

    @Test
    fun `a slow phone has Nemotron under Other engines when it is over the budget`() {
        // Half a Pixel, 1.79 x 1.25 = 2.23: Nemotron (2.86) is over; Canary (1.92, measured on the
        // Pixel in #47) isn't.
        assertEquals(
            EngineGroups(main = listOf(default, p110, pja, canary), other = listOf(nemotron, turbo) + qwenIfUser),
            engineGroups(phone(0.5), emptySet()),
        )
        // At 0.7 x a Pixel (3.12) Canary and Nemotron fit and get cards.
        assertEquals(listOf(default, p110, pja, canary, nemotron), engineGroups(phone(0.7), emptySet()).main)
    }

    @Test
    fun `an engine that fell behind on this phone moves under Other engines`() {
        val groups = engineGroups(SpeechPrefs(tooSlowEngineIds = setOf(p110.id, nemotron.id)), emptySet())
        assertEquals(EngineGroups(main = listOf(default, pja, canary), other = listOf(p110, nemotron, turbo) + qwenIfUser), groups)
    }

    @Test
    fun `installed engines left out of the cards are under Other engines, so they can be removed`() {
        val groups = engineGroups(SpeechPrefs(), setOf(granite.id, default.id))
        assertEquals(listOf(default, p110, pja, canary, nemotron), groups.main)
        // Developer engines after the rest.
        assertEquals(listOf(turbo) + qwenIfUser + granite, groups.other)
    }

    @Test
    fun `the engine in use and the recommended one always get a card`() {
        // Turbo in use, though over the budget.
        assertEquals(
            listOf(default, p110, pja, canary, nemotron, turbo),
            engineGroups(SpeechPrefs(selectedEngineId = turbo.id), emptySet()).main,
        )
        // A developer engine recommended for the phone (as Qwen3 was before #37) leads, however slow.
        val recommendedGranite = SpeechPrefs(recommendedEngineId = granite.id)
        assertEquals(listOf(granite, default, p110, pja, canary, nemotron), engineGroups(recommendedGranite, emptySet()).main)
    }

    @Test
    fun `a Pixel 8a speaking Japanese is recommended Parakeet-ja, one speaking Hindi Nemotron 3_5`() {
        val eightGb = 8L * 1024 * 1024 * 1024
        fun recommended(language: String): SpeechPrefs {
            val pixel = SpeechPrefs(recognitionLanguage = language, deviceProbeMs = SpeedBudget.PROBE_REFERENCE_MS)
            return pixel.copy(recommendedEngineId = pixel.recommendation(eightGb).engine.id)
        }
        // Japanese (#60): Parakeet-ja leads; turbo stays under Other engines.
        val japanese = recommended("ja")
        assertEquals(pja.id, japanese.recommendedEngineId)
        assertEquals(
            EngineGroups(main = listOf(pja, default, p110, canary, nemotron), other = listOf(turbo) + qwenIfUser),
            engineGroups(japanese, emptySet()),
        )
        assertEquals(pja, japanese.activeEngine)
        // Hindi: Nemotron 3.5 leads, turbo stays under Other engines.
        val hindi = recommended("hi")
        assertEquals(nemotron.id, hindi.recommendedEngineId)
        assertEquals(EngineGroups(main = listOf(nemotron, default, p110, pja, canary), other = listOf(turbo) + qwenIfUser), engineGroups(hindi, emptySet()))
    }

    @Test
    fun `with developer engines on, every other engine is under Other engines, developer engines last`() {
        val groups = engineGroups(SpeechPrefs(developerEngines = true), emptySet())
        assertEquals(listOf(default, p110, pja, canary, nemotron), groups.main)
        assertEquals((SttEngineCatalog.engines - groups.main.toSet()).toSet(), groups.other.toSet())
        // Turbo isn't a developer engine: it comes before them.
        assertEquals(turbo, groups.other.first())
        assertEquals(groups.other.sortedBy { it.developer }, groups.other)
    }

    @Test
    fun `a card says when its engine doesn't transcribe the spoken language`() {
        // Japanese system language, Parakeet in use, Whisper turbo recommended (#37).
        val prefs = SpeechPrefs(selectedEngineId = default.id, recommendedEngineId = turbo.id)
        assertEquals(LanguageNote("ja", turbo), languageNote(default, prefs, "ja-JP"))
        // Other cards only say it, without the suggestion; Nemotron 3.5 has no Japanese since #37.
        assertEquals(LanguageNote("ja", null), languageNote(p110, prefs, "ja-JP"))
        assertEquals(LanguageNote("ja", null), languageNote(nemotron, prefs, "ja-JP"))
        assertNull(languageNote(turbo, prefs, "ja-JP"))
        // A chosen recognition language beats the system's.
        assertNull(languageNote(default, prefs.copy(recognitionLanguage = "de"), "ja-JP"))
        assertEquals(LanguageNote("ko", turbo), languageNote(default, prefs.copy(recognitionLanguage = "ko"), "en-US"))
        // Hindi: Nemotron 3.5 recommended, and its card has no note.
        val hindi = SpeechPrefs(selectedEngineId = default.id, recommendedEngineId = nemotron.id, recognitionLanguage = "hi")
        assertEquals(LanguageNote("hi", nemotron), languageNote(default, hindi, "en-US"))
        assertNull(languageNote(nemotron, hindi, "en-US"))
        // No suggestion when the recommended engine lacks it too.
        assertEquals(
            LanguageNote("ja", null),
            languageNote(default, SpeechPrefs(selectedEngineId = default.id, recommendedEngineId = p110.id), "ja-JP"),
        )
    }

    @Test
    fun `an engine that can't detect the language asks for one while Recognition language is Auto`() {
        val canaryLanguages = listOf("en", "es", "de", "fr")
        // Auto on a Japanese phone: Canary would fall back to English, so it asks for a language.
        assertEquals(LanguageNote("ja", null, canaryLanguages), languageNote(canary, SpeechPrefs(), "ja-JP"))
        // Even when no engine covers the system language: Canary would still run in the wrong one.
        assertEquals(LanguageNote("zu", null, canaryLanguages), languageNote(canary, SpeechPrefs(), "zu-ZA"))
        // Auto on a German phone: Canary takes the system language.
        assertNull(languageNote(canary, SpeechPrefs(), "de-DE"))
        // A chosen language it has: fine; one it lacks: the usual note.
        assertNull(languageNote(canary, SpeechPrefs(recognitionLanguage = "fr"), "ja-JP"))
        assertEquals(LanguageNote("ko", null), languageNote(canary, SpeechPrefs(recognitionLanguage = "ko"), "en-US"))
        // Its developer language override set: it runs in German, so it doesn't ask; Japanese
        // still isn't one of its languages, so the usual note stays.
        val germanCanary = SpeechPrefs(developerEngines = true, engineLanguages = mapOf(canary.id to "de"))
        assertEquals("de", germanCanary.languageFor(canary, "ja-JP"))
        assertEquals(LanguageNote("ja", null), languageNote(canary, germanCanary, "ja-JP"))
        assertNull(languageNote(canary, germanCanary, "zu-ZA"))
        // The override applies only with Developer engines on.
        assertEquals(LanguageNote("ja", null, canaryLanguages), languageNote(canary, germanCanary.copy(developerEngines = false), "ja-JP"))
        // Engines that detect the language never ask.
        assertNull(languageNote(turbo, SpeechPrefs(), "ja-JP"))
        assertEquals(LanguageNote("ja", null), languageNote(p110, SpeechPrefs(), "ja-JP"))
    }

    @Test
    fun `no language note when nothing listed transcribes the language, or before the settings are read`() {
        // Zulu: no engine at all.
        assertNull(languageNote(default, SpeechPrefs(), "zu-ZA"))
        // Thai: Whisper turbo, a user engine since #45, has it; on the engine in use it names turbo when recommended.
        assertEquals(LanguageNote("th", null), languageNote(default, SpeechPrefs(), "th-TH"))
        assertEquals(LanguageNote("th", turbo), languageNote(default, SpeechPrefs(selectedEngineId = default.id, recommendedEngineId = turbo.id), "th-TH"))
        assertNull(languageNote(default, null, "ja-JP"))
    }

    @Test
    fun `quality rates in the Recognition language, which beats the UI locale (#76)`() {
        val japanese = SpeechPrefs(recognitionLanguage = "ja")
        assertEquals("ja", ratingLanguage(japanese, "en-US"))
        val parakeetJa = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_JA)!!
        val rating = EngineQuality.rating(parakeetJa, ratingLanguage(japanese, "en-US"))
        assertEquals(QualityRating(4, "ja"), rating)
        // Rated in the language the user dictates in: no "Tested in Japanese" under it.
        assertNull(qualityLabel(rating, ratingLanguage(japanese, "en-US")).measuredIn)
        // Turbo on that English phone: its Japanese marker, not its English one.
        assertEquals(QualityRating(3, "ja"), EngineQuality.rating(turbo, ratingLanguage(japanese, "en-US")))
        assertEquals("zh", ratingLanguage(SpeechPrefs(recognitionLanguage = "zh"), "de-DE"))
    }

    @Test
    fun `on Auto, or before the settings are read, quality rates in the UI locale`() {
        assertEquals("en", ratingLanguage(SpeechPrefs(), "en-US"))
        assertEquals("de", ratingLanguage(SpeechPrefs(recognitionLanguage = SpeechPrefs.AUTO), "de-AT"))
        assertEquals("he", ratingLanguage(null, "iw-IL"))
        assertEquals(QualityRating(4, "de"), EngineQuality.rating(turbo, ratingLanguage(SpeechPrefs(), "de-DE"))) // 5.8
    }
}
