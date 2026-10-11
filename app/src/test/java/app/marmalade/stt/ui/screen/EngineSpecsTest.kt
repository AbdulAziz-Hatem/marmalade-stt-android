package app.marmalade.stt.ui.screen

import app.marmalade.stt.R
import app.marmalade.stt.install.QualityRating
import app.marmalade.stt.install.SpeedTier
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineSpecsTest {

    @Test
    fun `stars map to one word each`() {
        val words = (1..5).map { qualityLabel(QualityRating(it, "en"), "en-US").word }
        assertEquals(
            listOf(
                R.string.engine_quality_basic,
                R.string.engine_quality_fair,
                R.string.engine_quality_good,
                R.string.engine_quality_great,
                R.string.engine_quality_best,
            ),
            words,
        )
        assertEquals(QualityLabel(R.string.engine_quality_not_rated, null), qualityLabel(null, "en-US"))
    }

    /** #67: quality is a five-segment meter in speed's traffic-light colours, by the share lit; #80: speed has five too. */
    @Test
    fun `meter colour follows the share of segments lit`() {
        assertEquals(
            listOf(MeterBand.RED, MeterBand.AMBER, MeterBand.LIME, MeterBand.LIME, MeterBand.GREEN),
            SpeedTier.entries.reversed().map { meterBand(it.meterFill, 5) },
        )
        assertEquals(
            listOf(MeterBand.RED, MeterBand.AMBER, MeterBand.LIME, MeterBand.LIME, MeterBand.GREEN),
            (1..5).map { meterBand(it, 5) },
        )
    }

    @Test
    fun `a rating measured in another language says which`() {
        // Measured in the user's language (any region): no suffix.
        assertEquals(null, qualityLabel(QualityRating(4, "en"), "en-GB").measuredIn)
        assertEquals(null, qualityLabel(QualityRating(4, "de"), "de-AT").measuredIn)
        // A German user shown an English-measured rating: "Great (English)".
        assertEquals(
            QualityLabel(R.string.engine_quality_great, "en"),
            qualityLabel(QualityRating(4, "en"), "de-DE"),
        )
        // Android's old code for Hebrew still matches.
        assertEquals(null, qualityLabel(QualityRating(3, "he"), "iw-IL").measuredIn)
        // An engine's main-language fallback (#76): Parakeet Japanese for an English user, "Great (Japanese)".
        assertEquals(QualityLabel(R.string.engine_quality_great, "ja"), qualityLabel(QualityRating(4, "ja"), "en"))
    }

    @Test
    fun `the quality source names the tests behind the rating`() {
        assertEquals(QualitySource(R.string.engine_quality_source_english, null), qualitySource(QualityRating(5, "en")))
        assertEquals(QualitySource(R.string.engine_quality_source_fleurs, "fr"), qualitySource(QualityRating(3, "fr")))
        assertEquals(QualitySource(R.string.engine_quality_source_fleurs, "hi"), qualitySource(QualityRating(2, "hi")))
        // Chinese and Korean name their second set (#50).
        assertEquals(
            QualitySource(R.string.engine_quality_source_two_sets, "zh", listOf("FLEURS", "AISHELL-1")),
            qualitySource(QualityRating(4, "zh")),
        )
        assertEquals(
            QualitySource(R.string.engine_quality_source_two_sets, "ko", listOf("FLEURS", "Zeroth-Korean")),
            qualitySource(QualityRating(4, "ko")),
        )
        assertEquals(QualitySource(R.string.engine_quality_source_none, null), qualitySource(null))
    }

    @Test
    fun `one language is named, several are counted`() {
        assertEquals(LanguagesLabel.One("en"), languagesLabel(setOf("en")))
        assertEquals(LanguagesLabel.Many(4), languagesLabel(setOf("en", "es", "de", "fr")))
    }

    @Test
    fun `the languages list is by name, alphabetically`() {
        assertEquals(listOf("English", "French", "German", "Spanish"), sortedLanguageNames(setOf("fr", "en", "de", "es"), Locale.US))
        assertEquals(listOf("Deutsch", "Englisch"), sortedLanguageNames(setOf("en", "de"), Locale.GERMANY))
    }

    /** #51: the UI locale's collation, not a code-point compare ("Árabe" sorted after "Vietnamita"). */
    @Test
    fun `language names sort by the UI locale's collation`() {
        val es = Locale.forLanguageTag("es")
        assertEquals(listOf("Alemán", "Árabe", "Inglés", "Vietnamita"), sortedLanguageNames(setOf("vi", "ar", "en", "de"), es))
        assertEquals(listOf("de", "ar", "en", "vi"), sortedLanguageCodes(setOf("vi", "ar", "en", "de"), es))
        // Japanese collation ignores the voicing mark first: ドイツ before トルコ, ベトナム before
        // ヘブライ. Code points put the unvoiced ト and ヘ first.
        assertEquals(
            listOf("アラビア語", "ドイツ語", "トルコ語", "ベトナム語", "ヘブライ語"),
            sortedLanguageNames(setOf("he", "vi", "tr", "de", "ar"), Locale.JAPANESE),
        )
    }

    /** Names in the UI's language: an English UI on a Russian phone says "German", not "немецкий". */
    @Test
    fun `language names are in the given locale, not the phone's`() {
        val phone = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            assertEquals("German", languageName("de", Locale.ENGLISH))
            assertEquals("alemán", languageNameInText("de", Locale.forLanguageTag("es")))
        } finally {
            Locale.setDefault(phone)
        }
    }

    @Test
    fun `each speed tier has its phrase, slow is the may-be-slow one`() {
        assertEquals(R.string.engine_speed_tier_fastest, speedTierRes(SpeedTier.FASTEST))
        assertEquals(R.string.engine_speed_tier_very_fast, speedTierRes(SpeedTier.VERY_FAST))
        assertEquals(R.string.engine_speed_tier_fast, speedTierRes(SpeedTier.FAST))
        assertEquals(R.string.engine_speed_tier_moderate, speedTierRes(SpeedTier.MODERATE))
        assertEquals(R.string.engine_badge_may_be_slow_long, speedTierRes(SpeedTier.SLOW))
        assertEquals(R.string.engine_speed_unmeasured, speedTierRes(null))
    }

    @Test
    fun `each speed tier has its own word`() {
        val words = SpeedTier.entries.map { speedWordRes(it) }
        assertEquals(SpeedTier.entries.size, words.toSet().size)
        assertEquals(R.string.engine_speed_word_very_fast, speedWordRes(SpeedTier.VERY_FAST))
    }

    /** #68: the slow meter says just "Slow"; the pill or fact beside it has the phrase. */
    @Test
    fun `the speed meter says the phrase, but only the word when slow`() {
        assertEquals(R.string.engine_speed_tier_fast, speedMeterRes(SpeedTier.FAST))
        assertEquals(R.string.engine_speed_word_slow, speedMeterRes(SpeedTier.SLOW))
    }
}
