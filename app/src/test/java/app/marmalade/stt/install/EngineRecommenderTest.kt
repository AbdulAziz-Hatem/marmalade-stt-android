package app.marmalade.stt.install

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineRecommender.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locale → engine (#27), on phones of different memory and speed. The
 * reference table is pinned here (#27's costs in Parakeet Ultra units since
 * #45; the app's peak memory since #31) so a new measurement changes
 * [SpeedBudget.REFERENCE] without silently changing what these tests check.
 * Phones are given as a speed factor against the reference phone
 * ([SpeedBudget.budget]) and their RAM as `totalMem` reports it.
 *
 * Since #37 (Max, 2026-10-02) Nemotron 3.5 (the 1120 ms export) is
 * recommended for Hindi, Arabic and Vietnamese only, Whisper turbo for
 * Turkish and Norwegian, and Parakeet TDT_CTC 0.6B ja Japanese since #60.
 * Since #50 an engine wins only where it is about as accurate as the best
 * that fits ([EngineQuality.nearBest]); Qwen3-ASR takes Chinese and Korean
 * where it fits, and nothing else though it is offered for 30 languages
 * (#88).
 */
class EngineRecommenderTest {

    private val reference = mapOf(
        "whisper-turbo" to EngineSpeed(9.79, 2170),
        SttEngineCatalog.PARAKEET_ULTRA to EngineSpeed(1.00, 1930),
        "canary-180m-flash" to EngineSpeed(2.46, 1090),
        SttEngineCatalog.NEMOTRON_35_1120MS to EngineSpeed(2.68, 940),
        "qwen3-asr-0-6b" to EngineSpeed(5.96, 4110),
        "parakeet-tdt-110m-en" to EngineSpeed(0.321, 810),
        SttEngineCatalog.PARAKEET_JA to EngineSpeed(0.98, 1240),
    )

    private val gib = 1024L * 1024L * 1024L
    private val pixel8a = 8 * gib // speed factor 1.0 by definition

    private fun pick(
        language: String,
        ram: Long = pixel8a,
        factor: Double = 1.0,
        table: Map<String, EngineSpeed> = reference,
    ): EngineRecommender.Recommendation =
        EngineRecommender.recommend(language, ram, SpeedBudget.budget(factor), table)

    private val parakeet = SttEngineCatalog.PARAKEET_ULTRA
    private val nemotron = SttEngineCatalog.NEMOTRON_35_1120MS
    private val turbo = "whisper-turbo"
    private val qwen3 = SttEngineCatalog.QWEN3
    private val parakeetJa = SttEngineCatalog.PARAKEET_JA

    @Test
    fun `on a Pixel 8a, by locale`() {
        val table = listOf(
            "en-US" to parakeet,
            "en-GB" to parakeet,
            "de-DE" to parakeet,
            "fr-FR" to parakeet,
            "es-MX" to parakeet,
            "pt-BR" to parakeet,
            "pl-PL" to parakeet,
            "uk-UA" to parakeet,
            "hi-IN" to nemotron,
            "ar-EG" to nemotron,
            "vi-VN" to nemotron,
            "ja-JP" to parakeetJa,
        )
        for ((locale, engine) in table) {
            val r = pick(locale)
            assertEquals(locale, engine, r.engine.id)
            assertEquals(locale, Reason.BEST_FIT, r.reason)
            assertTrue(locale, r.languageCovered)
        }
    }

    @Test
    fun `Chinese, Korean, Turkish and Norwegian get turbo, over a Pixel 8a's budget`() {
        // Turbo (2.2 GB) fits 8 GB, but its 9.79 is over 3.57 x 1.25: marked slow. The only candidate
        // for Turkish and Norwegian; for Chinese and Korean the pinned table's Qwen3 (4.1 GB) doesn't fit.
        for (locale in listOf("zh-CN", "zh-TW", "ko-KR", "tr-TR", "nb-NO", "no-NO")) {
            val r = pick(locale)
            assertEquals(locale, turbo to Reason.MAY_BE_SLOW, r.engine.id to r.reason)
            assertTrue(locale, r.languageCovered)
        }
        // Three times a Pixel's speed (10.71 x 1.25) affords it.
        assertEquals(turbo to Reason.BEST_FIT, pick("tr-TR", factor = 3.0).let { it.engine.id to it.reason })
    }

    @Test
    fun `Japanese gets Parakeet-ja, with turbo only where Parakeet-ja fell behind and turbo fits (#60)`() {
        // The most accurate (5.4 against turbo's 7.2, inside the margin) and first in order: on every
        // phone from 3 GB to 16 GB, slow or fast, where it fits.
        for ((ram, factor) in listOf(pixel8a to 1.0, 4 * gib to 0.25, 16 * gib to 4.0, (3.1 * gib).toLong() to 1.0)) {
            assertEquals("$ram $factor", parakeetJa to Reason.BEST_FIT, pick("ja-JP", ram, factor).let { it.engine.id to it.reason })
        }
        // It fell behind on a fast phone where turbo fits the budget: turbo, the lighter-for-phone pick.
        val fast = EngineRecommender.recommend("ja-JP", 16 * gib, SpeedBudget.budget(4.0), reference, setOf(parakeetJa))
        assertEquals(turbo to Reason.LIGHTER_FOR_PHONE, fast.engine.id to fast.reason)
        // It fell behind on a Pixel 8a, where turbo is over budget too: Parakeet-ja is still far cheaper.
        val pixel = EngineRecommender.recommend("ja-JP", pixel8a, SpeedBudget.budget(1.0), reference, setOf(parakeetJa))
        assertEquals(parakeetJa to Reason.MAY_BE_SLOW, pixel.engine.id to pixel.reason)
        // 2 GB: neither fits (1.24 GB needs 3.1 GB); the one needing the least memory.
        assertEquals(parakeetJa to Reason.LOW_MEMORY, pick("ja-JP", 2 * gib).let { it.engine.id to it.reason })
    }

    @Test
    fun `languages only turbo covers get it on a Pixel 8a, marked slow`() {
        for (locale in listOf("th-TH", "id-ID", "fa-IR", "he-IL", "iw-IL", "sw-KE")) {
            val r = pick(locale)
            assertEquals(locale, turbo, r.engine.id)
            assertEquals(locale, Reason.MAY_BE_SLOW, r.reason)
            assertTrue(locale, r.languageCovered)
        }
        // 4 GB: turbo doesn't fit, but it is all there is.
        assertEquals(turbo to Reason.LOW_MEMORY, pick("th-TH", 4 * gib).let { it.engine.id to it.reason })
    }

    @Test
    fun `Qwen3 takes Chinese and Korean wherever it fits memory, nothing else (#50, #88)`() {
        for (locale in listOf("zh-CN", "zh-TW", "ko-KR")) {
            // 16 GB at four times a Pixel's speed: both fit; Qwen3 is first of those near the best.
            assertEquals(locale, qwen3 to Reason.BEST_FIT, pick(locale, 16 * gib, 4.0).let { it.engine.id to it.reason })
            // 12 GB at a Pixel's speed: both over budget; Qwen3 (5.96) is cheaper than turbo (9.79).
            assertEquals(locale, qwen3 to Reason.MAY_BE_SLOW, pick(locale, 12 * gib).let { it.engine.id to it.reason })
        }
        // A Pixel 8a: the pinned table's Qwen3 (4.1 GB, no lean mode) doesn't fit; turbo does.
        assertEquals(turbo to Reason.MAY_BE_SLOW, pick("zh-CN").let { it.engine.id to it.reason })
        // Not for the languages it lost elsewhere or wasn't measured in.
        for (locale in listOf("ja-JP", "th-TH", "id-ID", "en-US", "hi-IN", "tr-TR")) {
            for (ram in listOf(pixel8a, 16 * gib)) {
                assertTrue("$locale $ram", pick(locale, ram, 4.0).engine.id != qwen3)
            }
        }
    }

    @Test
    fun `Korean on a fast phone gets Qwen3, about as good as turbo and before it`() {
        // 4.65 (turbo) is the best; Qwen3's 5.7 is inside 4.65 + 2, and Qwen3 comes before turbo.
        assertEquals(qwen3 to Reason.BEST_FIT, pick("ko-KR", 16 * gib, 4.0).let { it.engine.id to it.reason })
        // Chinese: turbo's 9.45 is outside Qwen3's 5.95 + 2, so turbo never wins there while Qwen3 fits.
        val qwen3Pricier = reference + (qwen3 to EngineSpeed(12.0, 4110))
        assertEquals(qwen3, pick("zh-CN", 16 * gib, 0.25, table = qwen3Pricier).engine.id)
        // Korean with Qwen3 the pricier: both are near the best, both over budget: the cheaper, turbo.
        assertEquals(turbo, pick("ko-KR", 16 * gib, 0.25, table = qwen3Pricier).engine.id)
    }

    @Test
    fun `a costlier engine wins only where it is clearly more accurate (#50)`() {
        // Sixteen GB at four times a Pixel's speed: everything fits, so only accuracy and order decide.
        fun best(locale: String) = pick(locale, 16 * gib, 4.0).let { it.engine.id to it.reason }
        // Spanish and German: turbo is the best measured, Ultra within 2 points; French: Ultra is the best.
        for (locale in listOf("es-ES", "de-DE", "fr-FR", "en-US")) assertEquals(locale, parakeet to Reason.BEST_FIT, best(locale))
        // Arabic and Vietnamese are a tie, Hindi is Nemotron's by far.
        for (locale in listOf("hi-IN", "ar-EG", "vi-VN")) assertEquals(locale, nemotron to Reason.BEST_FIT, best(locale))
        // Turkish, Norwegian: turbo, the only candidate. Japanese: Parakeet-ja, turbo within the margin.
        for (locale in listOf("tr-TR", "nb-NO")) assertEquals(locale, turbo to Reason.BEST_FIT, best(locale))
        assertEquals(parakeetJa to Reason.BEST_FIT, best("ja-JP"))
        // A Spanish phone where Ultra fell behind: turbo (3.1) rather than Canary (8.0, outside 3.1 + 2).
        val es = EngineRecommender.recommend("es-ES", 16 * gib, SpeedBudget.budget(4.0), reference, setOf(parakeet))
        assertEquals(turbo to Reason.LIGHTER_FOR_PHONE, es.engine.id to es.reason)
        // When turbo doesn't fit (4 GB), Canary is the best that does.
        assertEquals("canary-180m-flash", pick("es-ES", 4 * gib, 4.0).engine.id)
    }

    @Test
    fun `nearBest keeps the engines within the margin of the best measured, in order`() {
        val engines = EngineRecommender.preference.mapNotNull(SttEngineCatalog::byId)
        fun ids(language: String, of: List<SttEngineDescriptor> = engines) =
            EngineRecommender.nearBest(of.filter { EngineLanguages.supports(it, language) }, language).map { it.id }
        // 5.5, 6.9, 6.7, 7.0 (Canary 8.4 out). Canary isn't excluded here, nor Qwen3 outside Chinese and Korean (#88).
        assertEquals(listOf(parakeet, "parakeet-tdt-110m-en", qwen3, turbo), ids("en"))
        assertEquals(listOf(parakeet, turbo), ids("es"))
        assertEquals(listOf(parakeet, turbo), ids("fr")) // 7.9, 9.4
        assertEquals(listOf(nemotron), ids("hi"))
        assertEquals(listOf(nemotron, turbo), ids("ar"))
        assertEquals(listOf(qwen3), ids("zh"))
        assertEquals(listOf(qwen3, turbo), ids("ko"))
        assertEquals(listOf(parakeetJa, turbo), ids("ja")) // 5.4, 7.2 (inside 5.4 + 2)
        // Nothing measured: all of them, in order.
        assertEquals(listOf(parakeet, qwen3, turbo), ids("it"))
    }

    @Test
    fun `an engine not measured in a measured language is kept, after the measured near-best`() {
        val nemotronHi = SttEngineCatalog.byId(nemotron)!!
        val turboHi = SttEngineCatalog.byId(turbo)!!
        // A Hindi engine nobody measured, ahead of the others in order.
        val unmeasured = SttEngineCatalog.byId("canary-180m-flash")!!.copy(id = "unmeasured-hi", languages = listOf("hi"))
        assertEquals(
            listOf(nemotronHi, unmeasured), // turbo (31.2) is outside Nemotron's 14.4 + margin
            EngineRecommender.nearBest(listOf(unmeasured, nemotronHi, turboHi), "hi"),
        )
        // Only unmeasured ones: all of them.
        assertEquals(listOf(unmeasured), EngineRecommender.nearBest(listOf(unmeasured), "hi"))
    }

    @Test
    fun `a language no engine covers falls back to English`() {
        val r = pick("zu-ZA")
        assertEquals(parakeet, r.engine.id)
        assertFalse(r.languageCovered)
        assertEquals("zu", r.language)
    }

    @Test
    fun `a 4 GB phone gets a small engine for English, Nemotron for Hindi, Parakeet-ja for Japanese`() {
        val en = pick("en-US", 4 * gib)
        assertEquals("parakeet-tdt-110m-en", en.engine.id)
        assertEquals(Reason.LIGHTER_FOR_PHONE, en.reason)
        // Nemotron's 0.94 GB needs 2.3 GB of RAM.
        assertEquals(nemotron to Reason.BEST_FIT, pick("hi-IN", 4 * gib).let { it.engine.id to it.reason })
        // Parakeet-ja's 1.24 GB needs 3.1 GB (#60); before it, turbo, which didn't fit.
        assertEquals(parakeetJa to Reason.BEST_FIT, pick("ja-JP", 4 * gib).let { it.engine.id to it.reason })
        // Turkish: turbo's 2.17 GB needs 5.4 GB, but nothing else has the language.
        assertEquals(turbo to Reason.LOW_MEMORY, pick("tr-TR", 4 * gib).let { it.engine.id to it.reason })
        // 2 GB: Parakeet 110M (810 MB) fits, just.
        assertEquals("parakeet-tdt-110m-en" to Reason.LIGHTER_FOR_PHONE, pick("en-US", 2 * gib).let { it.engine.id to it.reason })
        // 1 GB: nothing fits; the leanest, Parakeet 110M. No Whisper model is offered any more.
        assertEquals("parakeet-tdt-110m-en" to Reason.LOW_MEMORY, pick("en-US", gib).let { it.engine.id to it.reason })
    }

    @Test
    fun `a slow phone keeps Parakeet for English, Nemotron for Hindi and Parakeet-ja for Japanese`() {
        // A quarter of the reference phone, budget 0.89: Ultra (1.0) and Parakeet-ja (0.98) are within
        // 0.89 × 1.25; Nemotron (2.68) isn't, but it's cheaper than turbo; turbo is the only Turkish engine.
        assertEquals(parakeet to Reason.BEST_FIT, pick("en-US", factor = 0.25).let { it.engine.id to it.reason })
        assertEquals(nemotron to Reason.MAY_BE_SLOW, pick("hi-IN", factor = 0.25).let { it.engine.id to it.reason })
        assertEquals(parakeetJa to Reason.BEST_FIT, pick("ja-JP", factor = 0.25).let { it.engine.id to it.reason })
        assertEquals(turbo to Reason.MAY_BE_SLOW, pick("tr-TR", factor = 0.25).let { it.engine.id to it.reason })
        // 0.7 × the reference phone, budget 2.5: Nemotron (2.68) fits.
        assertEquals(nemotron to Reason.BEST_FIT, pick("hi-IN", factor = 0.7).let { it.engine.id to it.reason })
        // The slowest phone with 2 recognizer threads: Ultra costs 1.45 there, over 0.89 x 1.25,
        // so English gets Parakeet 110M (approved as the small option, 2026-09-28).
        val twoThreads = SpeedBudget.costsAt(2, reference)
        val slowest = SpeedBudget.budget(SpeedBudget.MIN_FACTOR)
        assertEquals(
            "parakeet-tdt-110m-en" to Reason.LIGHTER_FOR_PHONE,
            EngineRecommender.recommend("en-US", pixel8a, slowest, twoThreads).let { it.engine.id to it.reason },
        )
        // Half the reference phone with 4 GB: Parakeet 110M (0.321) fits memory and speed.
        assertEquals("parakeet-tdt-110m-en" to Reason.LIGHTER_FOR_PHONE, pick("en-US", 4 * gib, 0.5).let { it.engine.id to it.reason })
    }

    @Test
    fun `memory check uses the engine's peak against 40 percent of RAM`() {
        val ultra = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA)!!
        val mb = 1024L * 1024L
        val edge = (1930 * mb / EngineRecommender.RAM_FRACTION).toLong() + 1
        assertTrue(EngineRecommender.fitsMemory(ultra, edge, reference))
        assertFalse(EngineRecommender.fitsMemory(ultra, edge - 10 * mb, reference))
        // No measurement: assumed to fit.
        assertTrue(EngineRecommender.fitsMemory(ultra, 1, emptyMap()))
    }

    @Test
    fun `the preference is Ultra, 110M, Parakeet-ja, Canary, Nemotron, Qwen3, turbo, all in the catalog`() {
        assertEquals(
            listOf(SttEngineCatalog.PARAKEET_ULTRA, "parakeet-tdt-110m-en", parakeetJa, "canary-180m-flash", nemotron, qwen3, turbo),
            EngineRecommender.preference,
        )
        // All user engines.
        for (id in EngineRecommender.preference) assertEquals(id, false, SttEngineCatalog.byId(id)?.developer)
        // Cheapest first after Ultra, by the shipped costs.
        val costs = EngineRecommender.preference.drop(1).map { SpeedBudget.REFERENCE.getValue(it).relativeCost }
        assertEquals(costs.sorted(), costs)
    }

    /**
     * #88: Qwen3-ASR is offered for all 30 of its languages but still recommended for Chinese and
     * Korean only ([EngineRecommender.QWEN3_RECOMMENDED]). So every other language gets what it got
     * when Qwen3 was offered for those two alone: the pick with Qwen3 left out of the catalog. Checked
     * on every phone class, speed and engine that fell behind here, with both cost tables.
     */
    @Test
    fun `outside Chinese and Korean the recommendation is the one without Qwen3`() {
        val withoutQwen3: (String) -> SttEngineDescriptor? = { id -> if (id == qwen3) null else SttEngineCatalog.byId(id) }
        val languages = EngineLanguages.userChoices() - EngineRecommender.QWEN3_RECOMMENDED + listOf("xx", "en-GB", "pt-BR")
        assertTrue(EngineLanguages.of(SttEngineCatalog.byId(qwen3)!!).all { it in languages || it in EngineRecommender.QWEN3_RECOMMENDED })
        val rams = listOf(1.8, 2.7, 3.6, 5.5, 7.4, 11.3, 15.0).map { (it * gib).toLong() }
        val factors = listOf(SpeedBudget.MIN_FACTOR, 0.5, 1.0, 2.0)
        val slow = listOf(emptySet(), setOf(nemotron), setOf(parakeet), setOf(parakeetJa), setOf("canary-180m-flash"), setOf(nemotron, turbo))
        for (table in listOf(reference, SpeedBudget.REFERENCE)) for (language in languages) for (ram in rams) {
            for (factor in factors) for (tooSlow in slow) {
                fun run(catalog: (String) -> SttEngineDescriptor?) =
                    EngineRecommender.recommend(language, ram, SpeedBudget.budget(factor), table, tooSlow, catalog)
                val now = run(SttEngineCatalog::byId)
                assertEquals("$language $ram $factor $tooSlow", run(withoutQwen3), now)
                assertFalse(now.engine.id == qwen3)
            }
        }
    }

    /** #88 offers Qwen3 for more languages but recommends nothing new, so phones keep their recommendation. */
    @Test
    fun `the rules version is unchanged by #88`() {
        assertEquals("2+qwen3", EngineRecommender.RULES_VERSION)
    }

    @Test
    fun `a 6 GB phone gets Parakeet Ultra, a 4 GB one doesn't`() {
        // Phones report a little under their nominal RAM (totalMem). Ultra's 1.93 GB needs 4.7 GB.
        val sixGb = (5.5 * gib).toLong()
        for (locale in listOf("en-US", "de-DE", "fr-FR", "es-ES", "it-IT", "pt-BR")) {
            assertEquals(locale, parakeet to Reason.BEST_FIT, pick(locale, sixGb).let { it.engine.id to it.reason })
        }
        val fourGb = (3.6 * gib).toLong()
        assertEquals("parakeet-tdt-110m-en", pick("en-US", fourGb).engine.id)
        assertEquals("canary-180m-flash", pick("de-DE", fourGb).engine.id)
    }

    /** #45's and #37's acceptance, with the shipped costs rather than the pinned table. */
    @Test
    fun `a fresh install recommends Ultra, 110M on small or slow English phones, Nemotron, Parakeet-ja or turbo elsewhere`() {
        fun shipped(language: String, ram: Long = pixel8a, factor: Double = 1.0, threads: Int = 4): EngineRecommender.Recommendation =
            EngineRecommender.recommend(language, ram, SpeedBudget.budget(factor), SpeedBudget.costsAt(threads))
        for (locale in listOf("en-US", "de-DE", "es-ES", "fr-FR", "it-IT", "pt-BR", "pl-PL")) {
            assertEquals(locale, parakeet, shipped(locale).engine.id)
        }
        val threeGb = (2.7 * gib).toLong() // as a 3 GB phone reports it
        assertEquals("parakeet-tdt-110m-en", shipped("en-US", ram = threeGb).engine.id)
        assertEquals("parakeet-tdt-110m-en", shipped("en-US", factor = SpeedBudget.MIN_FACTOR, threads = 2).engine.id)
        // Hindi, Arabic and Vietnamese: Nemotron 3.5, on a 3 GB phone too.
        for (locale in listOf("hi-IN", "ar-SA", "vi-VN")) {
            assertEquals(locale, nemotron to Reason.BEST_FIT, shipped(locale).let { it.engine.id to it.reason })
            assertEquals(locale, nemotron to Reason.BEST_FIT, shipped(locale, ram = threeGb).let { it.engine.id to it.reason })
        }
        // Japanese: Parakeet-ja (#60), on a 3 GB phone too, there without ORT's arena (1.03 GB).
        assertEquals(parakeetJa to Reason.BEST_FIT, shipped("ja-JP").let { it.engine.id to it.reason })
        assertEquals(parakeetJa to Reason.BEST_FIT, shipped("ja-JP", ram = threeGb).let { it.engine.id to it.reason })
        assertTrue(SpeedBudget.runsLean(parakeetJa, threeGb))
        // Turkish, Norwegian: turbo, never Nemotron or Qwen3 (#37). Too big for a 3 GB
        // phone, which still gets it: nothing else has the language.
        for (locale in listOf("tr-TR", "nb-NO")) {
            assertEquals(locale, turbo to Reason.MAY_BE_SLOW, shipped(locale).let { it.engine.id to it.reason })
            assertEquals(locale, turbo to Reason.LOW_MEMORY, shipped(locale, ram = threeGb).let { it.engine.id to it.reason })
        }
        // Chinese and Korean: Qwen3 (#50), lean on a Pixel 8a (9.71 against turbo's 9.79) and on a
        // 3 GB phone (1.88 GB, the least memory).
        for (locale in listOf("zh-CN", "ko-KR")) {
            assertEquals(locale, qwen3 to Reason.MAY_BE_SLOW, shipped(locale, ram = (7.4 * gib).toLong()).let { it.engine.id to it.reason })
            assertEquals(locale, qwen3 to Reason.LOW_MEMORY, shipped(locale, ram = threeGb).let { it.engine.id to it.reason })
        }
        // Swahili: only turbo has it.
        assertEquals(turbo, shipped("sw-KE", ram = 12 * gib).engine.id)
        // A 3 GB German or French phone: Ultra doesn't fit, Canary does (#45 fix round).
        assertEquals("canary-180m-flash", shipped("de-DE", ram = threeGb).engine.id)
        assertEquals("canary-180m-flash", shipped("fr-FR", ram = threeGb).engine.id)
        // A 3 GB Italian phone got Nemotron before #37; now nothing Italian fits it, and Ultra
        // without the arena (1.18 GB) needs the least memory.
        assertEquals(parakeet to Reason.LOW_MEMORY, shipped("it-IT", ram = threeGb).let { it.engine.id to it.reason })
        // A 4 GB phone (3.6 GB reported) fits Ultra since #47, without ORT's memory arena (6 GB since
        // #31, 8 GB before); a 6 GB one keeps the arena, which is faster.
        val fourGb = (3.6 * gib).toLong()
        for (locale in listOf("de-DE", "en-US", "it-IT")) assertEquals(locale, parakeet, shipped(locale, ram = fourGb).engine.id)
        assertTrue(SpeedBudget.runsLean(parakeet, fourGb))
        assertFalse(SpeedBudget.runsLean(parakeet, (5.5 * gib).toLong()))
        assertFalse(SpeedBudget.runsLean("parakeet-tdt-110m-en", threeGb))
    }

    @Test
    fun `a 4 GB German, French or Spanish phone gets Canary, never an English phone`() {
        for (locale in listOf("de-DE", "fr-FR", "es-ES")) {
            assertEquals(locale, "canary-180m-flash" to Reason.LIGHTER_FOR_PHONE, pick(locale, 4 * gib).let { it.engine.id to it.reason })
        }
        // 8 GB: Ultra, as before.
        assertEquals(parakeet to Reason.BEST_FIT, pick("de-DE").let { it.engine.id to it.reason })
        // English: 110M, even when 110M fell behind on this phone (Canary is slower still).
        assertEquals("parakeet-tdt-110m-en", pick("en-US", 4 * gib).engine.id)
        val en = EngineRecommender.recommend("en-US", 4 * gib, SpeedBudget.budget(1.0), reference, setOf("parakeet-tdt-110m-en"))
        assertTrue(en.engine.id != "canary-180m-flash")
        // Italian is neither Canary's nor, since #37, Nemotron's: Ultra and turbo are too big for
        // the pinned table's 4 GB phone, and Ultra needs the less memory.
        assertEquals(parakeet to Reason.LOW_MEMORY, pick("it-IT", 4 * gib).let { it.engine.id to it.reason })
        // A language nothing covers falls back to the English candidates, still without Canary.
        assertEquals("parakeet-tdt-110m-en", pick("zu-ZA", 4 * gib).engine.id)
    }

    @Test
    fun `Recognition language on Auto follows the system locale, a chosen one overrides it`() {
        fun forPrefs(prefs: SpeechPrefs, systemLocale: String) =
            EngineRecommender.recommend(prefs.spokenLanguage(systemLocale), pixel8a, SpeedBudget.budget(1.0), reference).engine.id
        assertEquals(parakeet, forPrefs(SpeechPrefs(), "de-DE"))
        assertEquals(parakeetJa, forPrefs(SpeechPrefs(), "ja-JP"))
        assertEquals(nemotron, forPrefs(SpeechPrefs(), "hi-IN"))
        // A German phone whose user speaks Japanese, and the other way round.
        assertEquals(parakeetJa, forPrefs(SpeechPrefs(recognitionLanguage = "ja"), "de-DE"))
        assertEquals(parakeet, forPrefs(SpeechPrefs(recognitionLanguage = "de"), "ja-JP"))
        assertEquals(turbo, forPrefs(SpeechPrefs(recognitionLanguage = "ko"), "en-US"))
        assertEquals(nemotron, forPrefs(SpeechPrefs(recognitionLanguage = "vi"), "en-US"))
    }

    @Test
    fun `an engine that fell behind on this phone is passed over`() {
        val budget = SpeedBudget.budget(1.0)
        fun pick(language: String, tooSlow: Set<String>, ram: Long = pixel8a) =
            EngineRecommender.recommend(language, ram, budget, reference, tooSlow).let { it.engine.id to it.reason }
        // Parakeet Ultra fell behind: the next English candidate, Parakeet 110M.
        assertEquals("parakeet-tdt-110m-en" to Reason.LIGHTER_FOR_PHONE, pick("en-US", setOf(parakeet)))
        // Both Parakeets fell behind: turbo is the only other English candidate and over budget, so
        // the cheapest, 110M, marked slow (Nemotron has no English since #37).
        assertEquals("parakeet-tdt-110m-en" to Reason.MAY_BE_SLOW, pick("en-US", setOf(parakeet, "parakeet-tdt-110m-en")))
        // Nemotron fell behind (#47): it stays, marked slow. Turbo fits memory from 8 GB, but its
        // estimate is 3.6x Nemotron's: it would fall behind too.
        for (ram in listOf(4 * gib, pixel8a, 12 * gib)) {
            assertEquals("$ram", nemotron to Reason.MAY_BE_SLOW, pick("hi-IN", setOf(nemotron), ram))
        }
    }

    @Test
    fun `an unproven engine beats one that fell behind only at no higher estimated cost`() {
        // A slow phone (a quarter of the Pixel's speed): Nemotron and turbo are both over budget.
        val budget = SpeedBudget.budget(0.25)
        val nemotronCost = reference.getValue(nemotron).relativeCost
        // Arabic, where the two are a tie (#50: in Hindi turbo is never near Nemotron's accuracy).
        fun pick(turboCost: Double, tooSlow: Set<String>) = EngineRecommender.recommend(
            "ar-EG", pixel8a, budget, reference + (turbo to EngineSpeed(turboCost, 2170)), tooSlow,
        ).let { it.engine.id to it.reason }
        // Nothing has fallen behind yet: the cheaper of the two.
        assertEquals(turbo to Reason.MAY_BE_SLOW, pick(2.46, emptySet()))
        // Nemotron fell behind; turbo, unproven, costs less, the same, or more than it.
        assertEquals(turbo to Reason.MAY_BE_SLOW, pick(nemotronCost - 0.1, setOf(nemotron)))
        assertEquals(turbo to Reason.MAY_BE_SLOW, pick(nemotronCost, setOf(nemotron)))
        assertEquals(nemotron to Reason.MAY_BE_SLOW, pick(nemotronCost + 0.1, setOf(nemotron)))
        // Turbo fell behind instead: Nemotron, unproven and cheaper, wins.
        assertEquals(nemotron to Reason.MAY_BE_SLOW, pick(2.9, setOf(turbo)))
    }

    @Test
    fun `a phone Ultra fits only without the arena gets it lean, counted at its lean memory and cost`() {
        // Ultra 1700 MB with the arena, 1200 MB and 1.4x the cost without.
        val lean = reference + (parakeet to EngineSpeed(1.0, 1700, LeanMode(peakRssMb = 1200, costFactor = 1.4)))
        fun pick(table: Map<String, EngineSpeed>, ram: Long, locale: String = "de-DE", factor: Double = 1.0): String =
            EngineRecommender.recommend(locale, ram, SpeedBudget.budget(factor), table).engine.id
        val fourGb = (3.6 * gib).toLong() // as a 4 GB phone reports it: 1475 MB allowed
        assertFalse(SpeedBudget.runsLean(parakeet, pixel8a, lean))
        assertEquals(parakeet, pick(lean, pixel8a))
        assertTrue(SpeedBudget.runsLean(parakeet, fourGb, lean))
        assertEquals(parakeet, pick(lean, fourGb))
        // Without a lean mode Ultra doesn't fit 4 GB: Canary.
        assertEquals("canary-180m-flash", pick(lean + (parakeet to EngineSpeed(1.0, 1700)), fourGb))
        // At 0.3x a Pixel's speed the budget is 1.07 x 1.25 = 1.34: Ultra with the arena (1.0) is within it,
        // lean (1.4) isn't, so the 4 GB English phone gets 110M instead.
        assertEquals(parakeet, pick(lean, pixel8a, "en-US", factor = 0.3))
        assertEquals("parakeet-tdt-110m-en", pick(lean, fourGb, "en-US", factor = 0.3))
    }

    @Test
    fun `real 6, 8 and 12 GB phones get turbo for Thai and Qwen3 for Chinese`() {
        // Phones report less than their nominal RAM: 6 GB about 5.5 GiB, 8 GB (a Pixel 8a) about 7.4.
        fun shipped(ram: Double, locale: String = "th-TH") =
            SpeechPrefs(deviceProbeMs = SpeedBudget.PROBE_REFERENCE_MS).recommendation((ram * gib).toLong(), locale)
                .let { it.engine.id to it.reason }
        // 8 GB: turbo fits (2.27 GB), over the Pixel's budget.
        assertEquals(turbo to Reason.MAY_BE_SLOW, shipped(7.4))
        // 6 GB: turbo doesn't fit with the arena (2.25 GB allowed). Qwen3 would, lean, but isn't
        // recommended for Thai (#88): turbo, the least memory of the rest.
        assertEquals(turbo to Reason.LOW_MEMORY, shipped(5.5))
        // 12 GB fit Qwen3, which was recommended here before #37.
        assertEquals(turbo to Reason.MAY_BE_SLOW, shipped(11.3))
        assertEquals(qwen3 to Reason.MAY_BE_SLOW, shipped(11.3, "zh-CN"))
        // 6 GB: turbo doesn't fit; Qwen3 does, lean (#47).
        assertEquals(qwen3 to Reason.MAY_BE_SLOW, shipped(5.5, "zh-CN"))
        // A user engine does run lean in the recommendation: 4 GB gets Parakeet Ultra.
        assertEquals(parakeet, shipped(3.6, "de-DE").first)
    }
}
