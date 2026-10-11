package app.marmalade.stt.textproc

/**
 * Settings for [TextPipeline]. Plain data so the app can persist it however it
 * likes (DataStore, JSON, …); nothing here depends on Android.
 *
 * The rule reference, including exact matching behaviour, is `textproc/README.md`.
 *
 * @property phantomFilter when to drop Whisper's silence hallucinations ("Thank you.", "[BLANK_AUDIO]").
 * @property removeFillers remove non-lexical fillers listed in [fillerWords].
 * @property fillerWords words removed when [removeFillers] is on. Matched whole-word, in lower case or
 *   with a capital first letter only, so an all-caps "ER" survives.
 * @property spokenCommands honour "new line", "new paragraph" and "scratch that".
 * @property spokenPunctuation turn spoken "period", "comma", "question mark", … into punctuation.
 *   Off by default because these are also ordinary words ("the trial period").
 * @property numbersAsDigits write spoken numbers as digits ("twenty five percent" → "25%"); see [Itn].
 * @property capitalizeSentences capitalise the first letter of a sentence, a line, or the transcript.
 *   Never lowercases anything. Not for an utterance with a kana or kanji in it: Chinese and
 *   Japanese have no capitals, so a Latin word there keeps its own case.
 * @property replacements `wrong -> right` fixes, applied whole-word and case-insensitively. A word
 *   edge depends on the script ([Words.Edge]): in Chinese and Japanese text an entry matches
 *   inside a sentence, and a Latin entry next to kana or kanji is a whole word.
 * @property snippets trigger phrases that expand to saved text, inserted verbatim.
 * @property language the language being dictated: an ISO 639 code or BCP-47 tag ("en", "de",
 *   "pt-BR"). The English-only rules ([removeFillers], [spokenCommands], [spokenPunctuation],
 *   [numbersAsDigits]) run only for English; for any other language, or "" (unknown), they
 *   are skipped, because they damage other languages (the filler "er" is German "he").
 *   The Japanese rules ("ja": 。 at the end, no space between Japanese characters) run only on
 *   utterances that contain a kana or kanji, so English heard on a Japanese phone stays English.
 * @property lowerCaseLatin the engine writes Latin letters only in lower case (Parakeet ja,
 *   `SttEngineDescriptor.lowerCaseLatin`): short runs outside the dictionary's replacements are
 *   taken as acronyms ("fbi" → "FBI").
 * @property recognizer the recogniser family whose text this cleans: phantom phrases are each
 *   recogniser's own ([Phantoms.isPhantom], #57), and Qwen3's leaked special tokens are removed.
 *   [Recognizer.ANY] (the default, for callers that don't know) checks every list.
 */
data class TextProcConfig(
    val phantomFilter: PhantomFilter = PhantomFilter.NEAR_SILENT,
    val removeFillers: Boolean = true,
    val fillerWords: List<String> = DEFAULT_FILLER_WORDS,
    val spokenCommands: Boolean = true,
    val spokenPunctuation: Boolean = false,
    val numbersAsDigits: Boolean = true,
    val capitalizeSentences: Boolean = true,
    val replacements: List<Replacement> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val language: String = "en",
    val lowerCaseLatin: Boolean = false,
    val recognizer: Recognizer = Recognizer.ANY,
) {
    /** [language]'s primary subtag in lower case: "pt-BR" → "pt". */
    val languageCode: String get() = language.substringBefore('-').substringBefore('_').lowercase()

    /** True when [language] is English, so the English-only rules run. */
    val isEnglish: Boolean get() = languageCode == "en"

    companion object {
        /** English non-lexical fillers. "mm" is deliberately absent (millimetres), as is "uh-huh" (means yes). */
        val DEFAULT_FILLER_WORDS: List<String> = listOf(
            "um", "umm", "ummm", "uhm", "uh", "uhh", "uhhh", "er", "erm", "ah", "ahh", "hm", "hmm", "hmmm",
        )
    }
}

/** The recogniser family that wrote the text: each has its own phantom phrases ([Phantoms], #57). */
enum class Recognizer {
    /** Unknown: every family's phrases count. */
    ANY,
    WHISPER,
    QWEN3,
    CANARY,

    /** A family with no phantom phrases in #57 (Parakeet, Nemotron, Granite). */
    OTHER,
}

/** When the hallucination filter drops a whole utterance. */
enum class PhantomFilter {
    /** Never drop anything. */
    OFF,

    /**
     * Drop phantom phrases only when the caller says the audio was quiet: distinctive ones
     * when it was near-silent, ordinary ones ("Thank you") only when it was silent (`nearSilent`
     * and `silent` in [TextPipeline.process]). A user who really says "thank you" keeps it.
     */
    NEAR_SILENT,

    /** Drop phantom phrases whatever the audio was. For callers that can't measure speech energy. */
    ALWAYS,
}

/**
 * One replacement rule: every whole-word, case-insensitive occurrence of [from]
 * becomes [to]. Whitespace inside [from] matches any whitespace. An empty [to]
 * deletes the phrase.
 *
 * Capitalisation: if [to] contains an upper-case letter it is used verbatim.
 * Otherwise it follows the match: "Foo" gives a capitalised [to], "FOO" an
 * all-caps one, anything else [to] as written.
 */
data class Replacement(val from: String, val to: String) {
    companion object {
        /**
         * Parses the text format: one `wrong -> right` rule per line. Blank lines and
         * lines starting with `#` are skipped, as are lines without `->` or with an
         * empty left side. The line is split at the first `->`.
         */
        fun parseList(text: String): List<Replacement> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "->" in it }
            .map { Replacement(it.substringBefore("->").trim(), it.substringAfter("->").trim()) }
            .filter { it.from.isNotEmpty() }
            .toList()

        /** Inverse of [parseList]. */
        fun formatList(rules: List<Replacement>): String =
            rules.joinToString("\n") { "${it.from} -> ${it.to}" }
    }
}

/**
 * A text expansion: saying [trigger] (whole-word, case-insensitive) inserts
 * [text] exactly as written, line breaks included. No other rule touches the
 * inserted text. If the trigger is the whole utterance, Whisper's punctuation
 * around it is dropped too.
 */
data class Snippet(val trigger: String, val text: String)
