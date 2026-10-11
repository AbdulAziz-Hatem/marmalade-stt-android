package app.marmalade.stt.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRatingsTest {

    private fun engine(id: String) = SttEngineCatalog.byId(id)!!

    @Test
    fun `locale tags normalise to the language part and current codes`() {
        assertEquals("pt", EngineLanguages.normalize("pt-BR"))
        assertEquals("zh", EngineLanguages.normalize("zh_Hant_TW"))
        assertEquals("he", EngineLanguages.normalize("iw"))
        assertEquals("id", EngineLanguages.normalize("in-ID"))
        assertEquals("nb", EngineLanguages.normalize("no"))
        assertEquals("fil", EngineLanguages.normalize("tl-PH"))
        assertEquals("en", EngineLanguages.normalize("EN"))
    }

    @Test
    fun `language support`() {
        val parakeet = engine(SttEngineCatalog.PARAKEET_ULTRA)
        assertTrue(EngineLanguages.supports(parakeet, "de-DE"))
        assertFalse(EngineLanguages.supports(parakeet, "ja-JP"))
        // Nemotron 3.5: Hindi, Arabic and Vietnamese only (#37).
        val nemotron = engine(SttEngineCatalog.NEMOTRON_35_1120MS)
        for (code in listOf("hi-IN", "ar-EG", "vi-VN")) assertTrue(code, EngineLanguages.supports(nemotron, code))
        for (code in listOf("en", "ja-JP", "zh-CN", "ko", "tr", "nb", "es", "it", "el")) {
            assertFalse(code, EngineLanguages.supports(nemotron, code))
        }
        assertTrue(EngineLanguages.supports(engine("parakeet-tdt-110m-en"), "en-AU"))
        assertFalse(EngineLanguages.supports(engine("parakeet-tdt-110m-en"), "de"))
        assertTrue(EngineLanguages.supports(engine("canary-180m-flash"), "fr-CA"))
        assertFalse(EngineLanguages.supports(engine("canary-180m-flash"), "it"))
        // Qwen3-ASR: its 30 languages as a developer engine, Chinese and Korean as a user one (#50).
        assertTrue(EngineLanguages.supports(SttEngineCatalog.qwen3Asr(forUsers = false), "th"))
        val userQwen3 = SttEngineCatalog.qwen3Asr(forUsers = true)
        assertEquals(setOf("zh", "ko"), EngineLanguages.of(userQwen3))
        assertTrue(EngineLanguages.supports(userQwen3, "zh-Hant-TW"))
        assertFalse(EngineLanguages.supports(userQwen3, "th"))
        assertTrue(EngineLanguages.supports(engine("whisper-turbo"), "sw"))
    }

    @Test
    fun `every catalog engine has languages`() {
        for (e in SttEngineCatalog.engines) assertTrue(e.id, EngineLanguages.of(e).isNotEmpty())
    }

    @Test
    fun `English WER maps to stars`() {
        val table = listOf(6.7 to 5, 7.0 to 5, 7.01 to 4, 8.0 to 4, 8.4 to 3, 9.5 to 3, 10.3 to 2, 11.0 to 2, 13.0 to 1)
        for ((wer, stars) in table) assertEquals("WER $wer", stars, EngineQuality.englishStars(wer))
    }

    @Test
    fun `FLEURS WER maps to stars`() {
        val table = listOf(3.1 to 5, 5.0 to 5, 5.9 to 4, 8.0 to 4, 10.1 to 3, 13.8 to 2, 15.0 to 2, 16.2 to 1)
        for ((wer, stars) in table) assertEquals("WER $wer", stars, EngineQuality.fleursStars(wer))
    }

    @Test
    fun `CER maps to stars`() {
        val table = listOf(2.7 to 5, 4.0 to 5, 4.65 to 4, 5.95 to 4, 7.0 to 4, 8.1 to 3, 9.45 to 3, 10.0 to 3, 14.6 to 2, 23.3 to 1)
        for ((cer, stars) in table) assertEquals("CER $cer", stars, EngineQuality.cerStars(cer))
    }

    @Test
    fun `the rating is in the user's language when measured, else English`() {
        val ultra = engine(SttEngineCatalog.PARAKEET_ULTRA)
        assertEquals(QualityRating(5, "en"), EngineQuality.rating(ultra, "en-US"))
        assertEquals(QualityRating(5, "es"), EngineQuality.rating(ultra, "es-ES"))
        assertEquals(QualityRating(4, "de"), EngineQuality.rating(ultra, "de-DE"))
        assertEquals(QualityRating(5, "en"), EngineQuality.rating(engine("parakeet-tdt-110m-en"), "en-GB"))
        assertEquals(QualityRating(5, "en"), EngineQuality.rating(ultra, "pl-PL"))
        assertEquals(QualityRating(3, "en"), EngineQuality.rating(engine("canary-180m-flash"), "en"))
        assertEquals(QualityRating(3, "de"), EngineQuality.rating(engine("canary-180m-flash"), "de"))
        // English engines on a Japanese phone: English.
        assertEquals(QualityRating(5, "en"), EngineQuality.rating(ultra, "ja-JP"))
    }

    @Test
    fun `markers are per language, with CER for Japanese, Chinese and Korean (#50)`() {
        val turbo = engine("whisper-turbo")
        assertEquals(QualityRating(3, "ja"), EngineQuality.rating(turbo, "ja-JP")) // 7.2 (#52)
        assertEquals(QualityRating(4, "ja"), EngineQuality.rating(engine(SttEngineCatalog.PARAKEET_JA), "ja-JP")) // 5.4
        assertEquals(QualityRating(3, "zh"), EngineQuality.rating(turbo, "zh-CN")) // 9.6 / 9.3
        assertEquals(QualityRating(4, "ko"), EngineQuality.rating(turbo, "ko-KR")) // 4.4 / 4.9
        assertEquals(QualityRating(1, "hi"), EngineQuality.rating(turbo, "hi-IN")) // 31.2
        assertEquals(QualityRating(3, "tr"), EngineQuality.rating(turbo, "tr-TR")) // 8.3
        assertEquals(QualityRating(3, "nb"), EngineQuality.rating(turbo, "no-NO")) // 9.2
        // Not measured in Thai: English.
        assertEquals(QualityRating(5, "en"), EngineQuality.rating(turbo, "th-TH"))
        val nemotron = engine(SttEngineCatalog.NEMOTRON_35_1120MS)
        assertEquals(QualityRating(3, "hi"), EngineQuality.rating(nemotron, "hi-IN")) // 14.4, the best (#80)
        assertEquals(QualityRating(3, "ar"), EngineQuality.rating(nemotron, "ar-EG")) // 10.6
        assertEquals(QualityRating(3, "vi"), EngineQuality.rating(nemotron, "vi-VN")) // 15.0, near turbo's 14.1 (#80)
    }

    @Test
    fun `only languages the engine is offered for get a marker`() {
        val nemotron = engine(SttEngineCatalog.NEMOTRON_35_1120MS)
        // Measured in English, Spanish, German, French, Japanese, Chinese, Korean, Turkish and Norwegian,
        // but offered for Hindi, Arabic and Vietnamese only (#37): elsewhere its Hindi marker (#76).
        for (code in listOf("en", "es", "de", "fr", "ja", "zh", "ko", "tr", "nb", "it")) {
            assertEquals(code, QualityRating(3, "hi"), EngineQuality.rating(nemotron, code))
        }
        // Qwen3-ASR as a developer engine: all it was measured in.
        val devQwen3 = SttEngineCatalog.qwen3Asr(forUsers = false)
        assertEquals(QualityRating(5, "en"), EngineQuality.rating(devQwen3, "en"))
        assertEquals(QualityRating(1, "fr"), EngineQuality.rating(devQwen3, "fr")) // 16.2
        assertEquals(QualityRating(2, "ja"), EngineQuality.rating(devQwen3, "ja")) // 13.4 (#52)
        // As a user engine, Chinese and Korean only.
        val userQwen3 = SttEngineCatalog.qwen3Asr(forUsers = true)
        assertEquals(QualityRating(4, "zh"), EngineQuality.rating(userQwen3, "zh-TW")) // 9.2 / 2.7
        assertEquals(QualityRating(4, "ko"), EngineQuality.rating(userQwen3, "ko")) // 6.5 / 4.9
        for (code in listOf("en", "fr", "ja", "hi")) {
            assertEquals(code, QualityRating(4, "zh"), EngineQuality.rating(userQwen3, code))
        }
    }

    @Test
    fun `an engine offered for neither the user's language nor English shows its main language (#76)`() {
        val parakeetJa = engine(SttEngineCatalog.PARAKEET_JA)
        // Was "Not rated" on an English phone; now "Tested in Japanese".
        assertEquals(QualityRating(4, "ja"), EngineQuality.rating(parakeetJa, "en-US"))
        assertEquals(QualityRating(4, "ja"), EngineQuality.rating(parakeetJa, "de"))
        // Nemotron 3.5's main language is Hindi (the first it is offered for), not its best-scoring Arabic.
        assertEquals(QualityRating(3, "hi"), EngineQuality.rating(engine(SttEngineCatalog.NEMOTRON_35_1120MS), "en"))
        // English still comes before the main language when the engine is offered for it.
        assertEquals(QualityRating(5, "en"), EngineQuality.rating(engine("whisper-turbo"), "th"))
        assertEquals(QualityRating(3, "en"), EngineQuality.rating(engine("canary-180m-flash"), "it"))
    }

    /** #80 (Max): an engine about as good as the best measured in a language gets at least 3 stars. */
    @Test
    fun `a near-best engine gets at least three stars`() {
        assertEquals(3, EngineQuality.stars("hi", 14.4)) // the best Hindi: 2 on the FLEURS scale
        assertEquals(3, EngineQuality.stars("vi", 15.0)) // within 2 points of turbo's 14.1
        assertEquals(3, EngineQuality.stars("vi", 14.1))
        assertEquals(3, EngineQuality.stars("vi", 16.3)) // 1.16x the best: inside the 1.2x margin
        assertEquals(1, EngineQuality.stars("hi", 27.3)) // Qwen3's Hindi: nowhere near
        assertEquals(1, EngineQuality.stars("vi", 17.0)) // just past the margin (16.92)
        // It only raises: near-best engines already above 3 keep their stars.
        assertEquals(5, EngineQuality.stars("es", 3.8))
        // Arabic was 3 already; a language with no measurements keeps its scale.
        assertEquals(QualityRating(3, "ar"), EngineQuality.rating(engine(SttEngineCatalog.NEMOTRON_35_1120MS), "ar"))
        assertEquals(2, EngineQuality.stars("th", 14.0))
        val turbo = engine("whisper-turbo")
        assertEquals(QualityRating(3, "vi"), EngineQuality.rating(turbo, "vi-VN"))
        assertEquals(QualityRating(1, "hi"), EngineQuality.rating(turbo, "hi-IN")) // 31.2, twice Nemotron's
    }

    @Test
    fun `an engine measured in none of its languages is not rated`() {
        val unmeasured = engine(SttEngineCatalog.PARAKEET_JA).copy(id = "unmeasured-ja")
        assertNull(EngineQuality.rating(unmeasured, "ja"))
        assertNull(EngineQuality.rating(unmeasured, "en"))
    }

    @Test
    fun `scores are means over the language's sets`() {
        assertEquals(5.95, EngineQuality.error("qwen3-asr-0-6b", "zh")!!, 1e-9)
        assertEquals(4.65, EngineQuality.error("whisper-turbo", "ko")!!, 1e-9)
        assertEquals(5.5, EngineQuality.error(SttEngineCatalog.PARAKEET_ULTRA, "en")!!, 1e-9)
        assertNull(EngineQuality.error("whisper-turbo", "it"))
        assertNull(EngineQuality.error("qwen3-asr-0-6b", "nb"))
    }

    @Test
    fun `the margin is 2 points or 1_2x, whichever is looser`() {
        assertTrue(EngineQuality.nearBest(3.8, 3.1)) // Ultra vs turbo, Spanish
        assertTrue(EngineQuality.nearBest(10.6, 10.3)) // Nemotron vs turbo, Arabic
        assertTrue(EngineQuality.nearBest(5.7, 4.65)) // Qwen3 vs turbo, Korean
        assertFalse(EngineQuality.nearBest(9.45, 5.95)) // turbo vs Qwen3, Chinese
        assertFalse(EngineQuality.nearBest(31.2, 14.4)) // turbo vs Nemotron, Hindi
        assertTrue(EngineQuality.nearBest(17.2, 14.4)) // 1.2x: 17.28
        assertFalse(EngineQuality.nearBest(17.3, 14.4))
        assertTrue(EngineQuality.nearBest(7.0, 5.0)) // 2 points: 7.0
        assertFalse(EngineQuality.nearBest(7.01, 5.0))
    }

    @Test
    fun `only catalog engines have scores, and every one has an English score`() {
        for ((language, scores) in EngineQuality.SCORES) {
            for (id in scores.scores.keys) assertTrue("$language $id", SttEngineCatalog.byId(id) != null)
        }
        assertTrue(EngineLanguages.BY_ENGINE.keys.all { SttEngineCatalog.byId(it) != null })
        // Every engine was measured in English, bar the Japanese-only Parakeet-ja.
        assertEquals(
            SttEngineCatalog.engines.map { it.id }.toSet() - SttEngineCatalog.PARAKEET_JA,
            EngineQuality.SCORES.getValue("en").scores.keys,
        )
    }

    @Test
    fun `Recognition language offers every language a user engine transcribes`() {
        val choices = EngineLanguages.userChoices()
        // Parakeet Ultra's and Nemotron 3.5's languages, and Whisper turbo's ~100 (a user engine since #45),
        // which keeps Japanese, Chinese, Korean, Turkish and Norwegian now Nemotron dropped them (#37).
        for (code in listOf("en", "de", "el", "mt", "lv", "ja", "zh", "ko", "hi", "ar", "vi", "tr", "nb", "th", "id", "he", "sw")) {
            assertTrue(code, code in choices)
        }
        assertEquals(EngineLanguages.of(engine("whisper-turbo")) + EngineLanguages.of(engine(SttEngineCatalog.PARAKEET_ULTRA)), choices.toSet())
        assertTrue(EngineLanguages.NEMOTRON_35.all { it in choices })
        assertEquals(choices.sorted(), choices)
        assertEquals(choices.distinct(), choices)
    }
}
