package app.marmalade.stt.install

/**
 * The engine the setup guide recommends (#27): by the phone's language,
 * then what the phone can carry, then accuracy against cost (#50). Nothing
 * is bundled (D5); the user downloads the one recommended, or picks another.
 *
 * 1. The candidates are [preference] filtered to user engines that are
 *    offered for the language ([EngineLanguages]), Qwen3-ASR only for
 *    [QWEN3_RECOMMENDED] and Canary never for English. A language no engine
 *    covers falls back to the English candidates.
 * 2. Of those that fit the phone, counted as the phone runs them
 *    ([SpeedBudget.forPhone]: without ONNX Runtime's arena, slower and
 *    leaner, where only that fits), the first in [preference] whose
 *    measured error is about as good as the best fitting one's
 *    ([EngineQuality.nearBest]). Fitting is peak memory within
 *    [RAM_FRACTION] of the phone's RAM and cost within the speed budget
 *    ([SpeedBudget.overBudget]; an engine that fell behind real time on
 *    this phone never is, #30). [preference] runs from cheap to costly
 *    (bar Parakeet Ultra, the main engine, ahead of Parakeet 110M), so a
 *    costly engine wins only where it is clearly more accurate. An engine
 *    not measured in the language comes after the measured near-best ones
 *    ([nearBest]); in a language nothing was measured in, the first that
 *    fits.
 * 3. If none fits, the cheapest of those that fit in memory and are about
 *    as good as the best of them (it will be slow), or failing that the one
 *    needing the least memory. An engine that fell behind on this phone is
 *    ranked by its estimated cost like any other (#47): an unproven engine
 *    beats it only when its estimate is no higher. An unproven engine that
 *    costs more would most likely fall behind too.
 *
 * What #50's rule picks with every engine fitting, from the measured
 * errors ([EngineQuality.SCORES]): Parakeet Ultra for English, Spanish,
 * German and French (Whisper turbo is within the margin or worse at about
 * 10x the cost); Parakeet TDT_CTC 0.6B ja for Japanese (#60: the most
 * accurate, turbo within the margin at about 10x the cost); Nemotron 3.5 for
 * Hindi (turbo is twice its error), Arabic and Vietnamese (a tie); Whisper
 * turbo for Turkish and Norwegian (nothing else is close); Qwen3-ASR for
 * Chinese and Korean (Chinese: the most accurate; Korean: 5.7 against
 * turbo's 4.7, inside the margin, at about 0.6x the cost), and for nothing
 * else ([QWEN3_RECOMMENDED], #88).
 */
object EngineRecommender {

    /**
     * Candidates in order, cheapest first apart from Parakeet Ultra, the
     * main engine (#45), which leads. Parakeet TDT_CTC 110M (#25) comes
     * right after it: English only, close to Ultra's English accuracy in a
     * quarter of its memory. Then Parakeet TDT_CTC 0.6B ja for Japanese
     * (#60; about Ultra's cost). Then Canary 180M Flash (#45 fix round, head's
     * decision): Spanish, German or French on a phone too small for Ultra
     * (0.95 GB at its peak against Ultra's 1.18 GB without the arena, #47).
     * Never for English, where 110M is lighter, faster and better
     * ([CANARY]). Then Nemotron 3.5 for Hindi, Arabic and Vietnamese (#37),
     * Qwen3-ASR for Chinese and Korean (#50, [QWEN3_RECOMMENDED]), and turbo
     * last, the costliest. Developer engines and ids not in the
     * catalog are skipped.
     */
    val preference: List<String> = listOf(
        SttEngineCatalog.PARAKEET_ULTRA,
        "parakeet-tdt-110m-en",
        SttEngineCatalog.PARAKEET_JA,
        CANARY,
        SttEngineCatalog.NEMOTRON_35_1120MS,
        SttEngineCatalog.QWEN3,
        "whisper-turbo",
    )

    /**
     * The rules' version, stored with each recommendation
     * ([app.marmalade.stt.data.SpeechPrefs.recommendationRules]). A phone
     * whose stored recommendation was made under other rules gets a new
     * one at startup (`cleanUpRemovedEngines`), pinning an installed engine
     * that is running. Change it whenever the rules or the tables they read
     * change what a phone is recommended. 1: #50's accuracy margin (and
     * #37's Nemotron 3.5 for Hindi, Arabic and Vietnamese only, which had
     * no version). 2: Parakeet TDT_CTC 0.6B ja for Japanese (#60). "+qwen3":
     * Qwen3-ASR for Chinese and Korean (#50; it was a switch until #88,
     * which offers Qwen3 for more languages but recommends it nowhere new,
     * so the version stays).
     */
    const val RULES_VERSION: String = "2+qwen3"

    /**
     * Largest share of the phone's RAM (`totalMem`, a little under the
     * nominal size) an engine's peak may take. Android, the keyboard and the
     * app in front need the rest. With the app's own peaks at a 28 s
     * utterance (#47, [SpeedBudget.REFERENCE]), as reported RAM: Parakeet
     * Ultra needs 4.2 GB with ONNX Runtime's arena (1.71 GB), so a 6 GB phone,
     * and 2.9 GB without it (1.18 GB, 1.34x slower), so a 4 GB phone; a 3 GB
     * phone fits Parakeet 110M, Canary and Nemotron, a 2 GB one 110M and
     * Canary without the arena; Whisper turbo (2.27 GB) needs 5.5 GB.
     */
    const val RAM_FRACTION = 0.4

    /** Canary 180M Flash: a candidate for its other three languages, never English. */
    private const val CANARY = "canary-180m-flash"

    /**
     * The languages Qwen3-ASR is recommended for (#50): Chinese, where it is
     * the most accurate engine we measured, and Korean, where it is within
     * [EngineQuality.MARGIN_POINTS] of Whisper turbo at about 0.6x its cost
     * (`docs/benchmarks/2026-10-nemotron-decision.md`). It is offered for
     * all 30 of its languages since #88 (Max, 2026-10-17: pickable, not
     * newly recommended). Elsewhere it is unmeasured, where it would lead
     * turbo as the first that fits, or measured: Vietnamese (16.3) is inside
     * the margin of turbo's 14.1, so a phone where Nemotron 3.5 fell behind
     * would get it instead of turbo.
     */
    val QWEN3_RECOMMENDED = setOf("zh", "ko")

    /** Why [Recommendation.engine] was chosen. */
    enum class Reason {
        /** The engine the rules pick for the language on any phone, and it fits this one. */
        BEST_FIT,

        /** A better engine for the language needs more memory or speed than the phone has. */
        LIGHTER_FOR_PHONE,

        /** Nothing for the language fits the phone's speed: the cheapest about-as-good one that fits in memory. */
        MAY_BE_SLOW,

        /** Nothing for the language fits in memory: the one needing the least. */
        LOW_MEMORY,
    }

    /**
     * @property languageCovered false when no engine transcribes the phone's
     *   language and an English engine was picked instead.
     */
    data class Recommendation(
        val engine: SttEngineDescriptor,
        val reason: Reason,
        val language: String,
        val languageCovered: Boolean,
    )

    fun fitsMemory(engine: SttEngineDescriptor, totalMemBytes: Long, reference: Map<String, EngineSpeed>): Boolean {
        val peakMb = reference[engine.id]?.peakRssMb ?: return true
        return fitsMemory(peakMb, totalMemBytes)
    }

    /** A peak of [peakMb] fits a phone with [totalMemBytes]: within [RAM_FRACTION] of it. */
    fun fitsMemory(peakMb: Int, totalMemBytes: Long): Boolean = peakMb * MB <= totalMemBytes * RAM_FRACTION

    /**
     * The recommendation for [language] (a BCP-47 tag, e.g. the system
     * locale's) on a phone with [totalMemBytes] of RAM and speed [budget]
     * ([SpeedBudget.budget]). [catalog] looks engines up by id (tests pass
     * Qwen3-ASR as a user engine).
     */
    fun recommend(
        language: String,
        totalMemBytes: Long,
        budget: Double,
        reference: Map<String, EngineSpeed> = SpeedBudget.REFERENCE,
        tooSlow: Set<String> = emptySet(),
        catalog: (String) -> SttEngineDescriptor? = SttEngineCatalog::byId,
    ): Recommendation {
        // Memory and cost as this phone runs each engine: lean where only that fits (#47).
        val phone = SpeedBudget.forPhone(totalMemBytes, reference)
        val lang = EngineLanguages.normalize(language)
        val known = preference.mapNotNull(catalog).filterNot { it.developer }
        fun candidatesFor(language: String) = known.filter {
            EngineLanguages.supports(it, language) && !(it.id == CANARY && language == "en") &&
                !(it.id == SttEngineCatalog.QWEN3 && language !in QWEN3_RECOMMENDED)
        }
        val forLanguage = candidatesFor(lang)
        val covered = forLanguage.isNotEmpty()
        val (spoken, candidates) = if (covered) lang to forLanguage else "en" to candidatesFor("en")

        fun cost(e: SttEngineDescriptor) = phone[e.id]?.relativeCost ?: Double.MAX_VALUE
        fun memory(e: SttEngineDescriptor) = phone[e.id]?.peakRssMb ?: Int.MAX_VALUE

        val inMemory = candidates.filter { fitsMemory(it, totalMemBytes, phone) }
        val fit = nearBest(inMemory.filterNot { SpeedBudget.overBudget(it, budget, phone, tooSlow) }, spoken).firstOrNull()
        val (engine, reason) = when {
            fit != null -> fit to if (fit == nearBest(candidates, spoken).first()) Reason.BEST_FIT else Reason.LIGHTER_FOR_PHONE
            // The cheapest near-best; at the same cost, one that hasn't fallen behind here.
            inMemory.isNotEmpty() ->
                nearBest(inMemory, spoken).minWith(compareBy(::cost, { it.id in tooSlow })) to Reason.MAY_BE_SLOW
            else -> candidates.minBy(::memory) to Reason.LOW_MEMORY
        }
        return Recommendation(engine, reason, lang, covered)
    }

    /**
     * [engines] without those measured in [language] clearly worse than the
     * best measured one ([EngineQuality.nearBest]): the measured near-best
     * first, then the unmeasured ones, each in [engines]' order. So an
     * engine nobody measured in the language is never ruled out by the
     * margin, but a measured near-best one comes before it; it competes on
     * cost only on a phone where nothing fits the speed budget. All of
     * [engines] when none was measured there.
     */
    internal fun nearBest(engines: List<SttEngineDescriptor>, language: String): List<SttEngineDescriptor> {
        val errors = engines.associateWith { EngineQuality.error(it.id, language) }
        val best = errors.values.filterNotNull().minOrNull() ?: return engines
        val (measured, unmeasured) = engines.partition { errors[it] != null }
        return measured.filter { EngineQuality.nearBest(errors.getValue(it)!!, best) } + unmeasured
    }

    private const val MB = 1024L * 1024L
}
