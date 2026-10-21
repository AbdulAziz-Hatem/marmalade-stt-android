package app.marmalade.stt.install

import java.util.Locale

/*
 * What each engine can transcribe and how well (#27): the language lists the
 * recommendation reads, and the quality markers on the engine cards. Kept
 * out of the catalog entries so new engines (#25) only add rows here.
 */

/** Which languages each engine transcribes, as normalised ISO 639 codes ([EngineLanguages.normalize]). */
object EngineLanguages {

    /** Parakeet TDT 0.6B v3's 25 European languages (model card), which Parakeet Ultra keeps. */
    private val PARAKEET = setOf(
        "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu", "it", "lv", "lt", "mt", "pl",
        "pt", "ro", "sk", "sl", "es", "sv", "ru", "uk",
    )

    /**
     * The languages Nemotron 3.5 is offered for: Hindi, Arabic and Vietnamese
     * (Max, 2026-10-02, #37; `docs/benchmarks/2026-10-nemotron-decision.md`).
     * The model transcribes 28 (model card), but Whisper turbo beats it on
     * Japanese, Chinese, Korean, Turkish and Norwegian, and Parakeet Ultra or
     * Canary cover the European ones. Hindi is a clear win over turbo; Arabic
     * and Vietnamese are a tie, where Nemotron is much cheaper to run. Each is
     * a key of the model's prompt dictionary, so it also takes each as a hint.
     * Hindi comes first: it is the main language [EngineQuality.rating] falls
     * back to.
     */
    val NEMOTRON_35 = setOf("hi", "ar", "vi")

    /**
     * Qwen3-ASR's 30 languages (`SUPPORTED_LANGUAGES` in QwenLM/Qwen3-ASR's
     * `qwen_asr/inference/utils.py`, the model card's list; its 22 Chinese
     * dialects count as zh / yue). A user engine for all of them since #88
     * (Max, 2026-10-17); the recommendation still picks it for Chinese and
     * Korean only ([EngineRecommender.QWEN3_RECOMMENDED]).
     */
    val QWEN3 = setOf(
        "zh", "en", "yue", "ar", "de", "fr", "es", "pt", "id", "it", "ko", "ru", "th", "vi", "ja", "tr", "hi", "ms",
        "nl", "sv", "da", "fi", "pl", "cs", "fil", "fa", "el", "ro", "hu", "mk",
    )

    /** Whisper's multilingual vocabulary (large-v3-turbo), normalised. */
    private val WHISPER_MULTILINGUAL = setOf(
        "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it", "id", "hi",
        "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "nb", "th", "ur", "hr", "bg", "lt", "la",
        "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy",
        "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc", "ka", "be",
        "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "fil",
        "mg", "as", "tt", "haw", "ln", "ha", "ba", "jv", "su", "yue",
    )

    /** Engine id → languages. Engines missing here are treated as English-only only if their catalog entry says "en". */
    val BY_ENGINE: Map<String, Set<String>> = mapOf(
        SttEngineCatalog.PARAKEET_ULTRA to PARAKEET, // post-trained v3: same languages
        "canary-180m-flash" to setOf("en", "es", "de", "fr"),
        SttEngineCatalog.NEMOTRON_35_1120MS to NEMOTRON_35,
        "whisper-turbo" to WHISPER_MULTILINGUAL,
        SttEngineCatalog.QWEN3 to QWEN3,
    )

    /**
     * [language] (a BCP-47 tag or ISO 639 code, e.g. "pt-BR", "iw") as the
     * codes above use it: lower case, the language part only, Android's old
     * codes mapped to the current ones, Norwegian as Bokmål.
     */
    fun normalize(language: String): String {
        val code = language.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        return when (code) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            "tl" -> "fil"
            "no" -> "nb"
            "jw" -> "jv"
            else -> code
        }
    }

    /** The languages [engine] is offered for: this table, else its catalog entry's codes (skipping "mul"). */
    fun of(engine: SttEngineDescriptor): Set<String> =
        BY_ENGINE[engine.id] ?: engine.languages.filter { it != "mul" }.map(::normalize).toSet()

    fun supports(engine: SttEngineDescriptor, language: String): Boolean = normalize(language) in of(engine)

    /**
     * The languages Settings → Recognition language offers: every language a
     * user engine transcribes (developer engines don't count), by code.
     */
    fun userChoices(): List<String> =
        SttEngineCatalog.engines.filterNot { it.developer }.flatMap { of(it) }.distinct().sorted()
}

/** A quality marker: [stars] from 1 (basic) to 5 (best), measured in [language]. */
data class QualityRating(val stars: Int, val language: String)

/**
 * One language's measurements: each engine's error rate in %, on the app's
 * int8 files through the app's pipeline (the host harness, `tools/asr-bench`).
 *
 * @property sets the test sets by their published names, in the order of each engine's scores.
 * @property characters the error is the character error rate (CER: Japanese, Chinese, Korean), not the WER.
 * @property scores engine id → its error on each of [sets].
 */
data class LanguageScores(val sets: List<String>, val characters: Boolean, val scores: Map<String, List<Double>>) {
    init {
        require(scores.values.all { it.size == sets.size }) { "one score per set: $sets" }
    }

    /** [engineId]'s error: the mean over [sets]; null when it wasn't measured. */
    fun error(engineId: String): Double? = scores[engineId]?.average()
}

/**
 * Measured accuracy per language and engine (#27, #50): the quality markers
 * on the engine cards, and what [EngineRecommender] weighs against cost.
 * Sources, all normalised as their docs say:
 *
 * - **English:** `docs/benchmarks/2026-09-host-wer.md` (#14/#15, #25, #26):
 *   WER on LibriSpeech clean, LibriSpeech other, FLEURS-en and AMI.
 *   Canary's uses its loop-cut scores (#18); Nemotron 3.5's and Ultra's the
 *   app's tail padding.
 * - **Spanish, German, French:** the same doc, FLEURS with the language set
 *   (50 clips each, so ±3 points is noise). One FLEURS-fr reference is
 *   truncated, which costs every engine the same few insertions.
 * - **Japanese:** `docs/benchmarks/2026-10-japanese.md` (+ .json, #52):
 *   CER on FLEURS-ja, 200 clips (95 % intervals ±1–3 points). The same
 *   clips as #37, with the pipeline since #43 (turbo 8.1 there).
 * - **Chinese, Korean, Hindi, Arabic, Turkish, Vietnamese,
 *   Norwegian:** `docs/benchmarks/2026-10-nemotron-decision.md` (+ .json,
 *   #37): FLEURS (100 clips), plus AISHELL-1 for Chinese and
 *   Zeroth-Korean for Korean (CER, 95 % intervals ±1–3 points); WER
 *   elsewhere, with the language hint set.
 *
 * Markers are shown only for languages the engine is offered for
 * ([EngineLanguages.of]); the table keeps every measurement.
 */
object EngineQuality {

    private const val TURBO = "whisper-turbo"
    private const val CANARY = "canary-180m-flash"
    private const val P110M = "parakeet-tdt-110m-en"
    private const val GRANITE = "granite-speech-5-470m"
    private const val ULTRA = SttEngineCatalog.PARAKEET_ULTRA
    private const val NEMOTRON = SttEngineCatalog.NEMOTRON_35_1120MS
    private const val QWEN3 = SttEngineCatalog.QWEN3
    private const val PARAKEET_JA = SttEngineCatalog.PARAKEET_JA

    private val FLEURS = listOf("FLEURS")

    private fun fleurs(characters: Boolean = false, vararg scores: Pair<String, Double>) =
        LanguageScores(FLEURS, characters, scores.associate { (id, error) -> id to listOf(error) })

    val SCORES: Map<String, LanguageScores> = mapOf(
        "en" to LanguageScores(
            listOf("LibriSpeech clean", "LibriSpeech other", "FLEURS", "AMI"),
            characters = false,
            mapOf(
                TURBO to listOf(1.7, 5.2, 6.7, 14.4),
                CANARY to listOf(2.3, 4.9, 9.2, 17.0),
                NEMOTRON to listOf(2.8, 7.1, 9.5, 12.9),
                QWEN3 to listOf(2.0, 6.5, 7.6, 10.6),
                P110M to listOf(1.9, 5.2, 8.6, 12.0),
                ULTRA to listOf(1.5, 4.5, 6.9, 9.1),
                GRANITE to listOf(1.2, 2.8, 6.7, 9.3), // it trained on AMI
            ),
        ),
        "es" to fleurs(false, TURBO to 3.1, ULTRA to 3.8, CANARY to 8.0, NEMOTRON to 7.2, QWEN3 to 8.9),
        "de" to fleurs(false, TURBO to 5.8, ULTRA to 6.5, CANARY to 8.4, NEMOTRON to 10.0, QWEN3 to 10.1),
        "fr" to fleurs(false, TURBO to 9.4, ULTRA to 7.9, CANARY to 11.7, NEMOTRON to 13.0, QWEN3 to 16.2),
        "ja" to fleurs(true, PARAKEET_JA to 5.4, TURBO to 7.2, NEMOTRON to 14.5, QWEN3 to 13.4),
        "zh" to LanguageScores(
            listOf("FLEURS", "AISHELL-1"),
            characters = true,
            mapOf(TURBO to listOf(9.6, 9.3), NEMOTRON to listOf(22.2, 24.4), QWEN3 to listOf(9.2, 2.7)),
        ),
        "ko" to LanguageScores(
            listOf("FLEURS", "Zeroth-Korean"),
            characters = true,
            mapOf(TURBO to listOf(4.4, 4.9), NEMOTRON to listOf(8.9, 5.1), QWEN3 to listOf(6.5, 4.9)),
        ),
        "hi" to fleurs(false, TURBO to 31.2, NEMOTRON to 14.4, QWEN3 to 27.3),
        "ar" to fleurs(false, TURBO to 10.3, NEMOTRON to 10.6, QWEN3 to 24.6),
        "tr" to fleurs(false, TURBO to 8.3, NEMOTRON to 13.1, QWEN3 to 29.3),
        "vi" to fleurs(false, TURBO to 14.1, NEMOTRON to 15.0, QWEN3 to 16.3),
        "nb" to fleurs(false, TURBO to 9.2, NEMOTRON to 23.7),
    )

    /**
     * How far behind the best measured engine another may be and still count
     * as about as good (#50): [MARGIN_POINTS] absolute or [MARGIN_RATIO]
     * relative, whichever is looser. Two points is about the noise of a
     * 100-clip FLEURS set; the ratio widens it where errors are high.
     */
    const val MARGIN_POINTS = 2.0
    const val MARGIN_RATIO = 1.2

    /** [engineId]'s error in [language] (normalised), or null when not measured. */
    fun error(engineId: String, language: String): Double? = SCORES[language]?.error(engineId)

    /** An error of [error] is about as good as [best] ([MARGIN_POINTS], [MARGIN_RATIO]). */
    fun nearBest(error: Double, best: Double): Boolean = error <= maxOf(best + MARGIN_POINTS, best * MARGIN_RATIO)

    /** English: the mean of the four sets. */
    fun englishStars(wer: Double): Int = when {
        wer <= 7.0 -> 5
        wer <= 8.0 -> 4
        wer <= 9.5 -> 3
        wer <= 11.0 -> 2
        else -> 1
    }

    /** Other WER languages (FLEURS). */
    fun fleursStars(wer: Double): Int = when {
        wer <= 5.0 -> 5
        wer <= 8.0 -> 4
        wer <= 11.0 -> 3
        wer <= 15.0 -> 2
        else -> 1
    }

    /**
     * Japanese, Chinese, Korean (CER): a point stricter than [fleursStars]
     * below 15, as the best engines reach 3–6 % on these sets.
     */
    fun cerStars(cer: Double): Int = when {
        cer <= 4.0 -> 5
        cer <= 7.0 -> 4
        cer <= 10.0 -> 3
        cer <= 15.0 -> 2
        else -> 1
    }

    /**
     * The fewest stars an engine about as good as the best one measured in
     * the language ([nearBest]) gets, whatever its error (Max, 2026-10-10,
     * #80): in a hard language the best engines' errors are high in absolute
     * terms, but they are still the best there is. Nemotron 3.5's Hindi
     * (14.4, the best) and Vietnamese (15.0, against turbo's 14.1) read
     * Good, not Fair; turbo's Vietnamese too, and Qwen3-ASR's Vietnamese
     * (16.3).
     */
    const val NEAR_BEST_MIN_STARS = 3

    /**
     * [error]'s stars in [language]: its scale ([englishStars], [cerStars],
     * [fleursStars]), raised to [NEAR_BEST_MIN_STARS] when [error] is
     * [nearBest] the lowest error measured there.
     */
    fun stars(language: String, error: Double): Int {
        val scale = when {
            language == "en" -> englishStars(error)
            SCORES[language]?.characters == true -> cerStars(error)
            else -> fleursStars(error)
        }
        val best = SCORES[language]?.let { scores -> scores.scores.keys.mapNotNull(scores::error).minOrNull() }
        return if (best != null && nearBest(error, best)) maxOf(scale, NEAR_BEST_MIN_STARS) else scale
    }

    /**
     * [engine]'s marker for [language], the language the user speaks
     * (`SpeechPrefs.spokenLanguage`, #76), measured (check
     * [QualityRating.language]):
     *
     * 1. in [language] when the engine is offered for it;
     * 2. else in English when it is offered for English;
     * 3. else in its main language: the first of [EngineLanguages.of] that was
     *    measured (Japanese for Parakeet Japanese, Hindi for Nemotron 3.5).
     *    The card says "Tested in Japanese" and,
     *    from [app.marmalade.stt.ui.screen.languageNote], that it doesn't
     *    transcribe the user's language; "Not rated" would hide a measured
     *    engine as if nobody had tested it.
     *
     * Null only for an engine measured in none of its languages.
     */
    fun rating(engine: SttEngineDescriptor, language: String): QualityRating? {
        fun measured(code: String): QualityRating? {
            if (!EngineLanguages.supports(engine, code)) return null
            return error(engine.id, code)?.let { QualityRating(stars(code, it), code) }
        }
        return measured(EngineLanguages.normalize(language)) ?: measured("en")
            ?: EngineLanguages.of(engine).firstNotNullOfOrNull(::measured)
    }
}
