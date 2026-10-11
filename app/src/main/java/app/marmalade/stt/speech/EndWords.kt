package app.marmalade.stt.speech

import java.util.Locale
import java.util.regex.Pattern

/**
 * The end words of [EndOfDictation.END_WORD] mode (#43): a word or phrase
 * ("over", "send it") the user says to finish dictating.
 *
 * **The word must stand alone** (Max, #43), so "he fell over" or "the man
 * walked over the bridge" never end dictation. It counts only as its own
 * sentence or utterance ([match]). Matching runs on each VAD segment's own
 * text, never on partials: "over" at the end of a partial may be "over there"
 * still being spoken.
 */
object EndWords {
    /** The default end word. */
    val DEFAULT: List<String> = listOf("over")

    /**
     * Normalise user input: each entry trimmed and lower-cased, blanks
     * dropped, duplicates removed (first one kept). May return an empty list;
     * the setting never stores one.
     */
    fun normalize(words: List<String>): List<String> =
        words.map { it.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()

    /** The Settings field's text ("over, send it") as a list. */
    fun parse(commaSeparated: String): List<String> = normalize(commaSeparated.split(','))

    /** A list for the Settings field. */
    fun format(words: List<String>): String = words.joinToString(", ")

    /**
     * Whether [segmentText] (one VAD segment's text, as the engine wrote it)
     * ends with one of [words] standing alone. It does when:
     *
     * - (a) the text is just the word or phrase plus punctuation ("Over.",
     *   "over", "OVER!"): a separate utterance after a pause. This is the path
     *   for engines without punctuation (Granite before its formatter,
     *   Nemotron), or
     * - (b) the word directly follows sentence-final punctuation (. ! ?) from
     *   the engine and only punctuation follows it ("…the bridge. Over.").
     *
     * Case-insensitive, whole words only ("hangover" never matches); a
     * phrase's words may be separated by spaces or commas.
     *
     * @return the text before the word, trimmed, with its punctuation ("the
     *   bridge." for "the bridge. Over."; "" when the word stood alone), or
     *   null when there is no match.
     */
    fun match(segmentText: String, words: List<String>): String? {
        // Only punctuation (and spaces) may follow the word.
        val core = segmentText.trim().trimEnd { !it.isLetterOrDigit() }
        if (core.isEmpty()) return null
        for (phrase in words) {
            val m = patternFor(phrase)?.matcher(core) ?: continue
            if (m.matches()) return m.group(1)?.trim().orEmpty()
        }
        return null
    }

    /**
     * `^(?:[^letters/digits]*|(.*SENTENCE_END CLOSERS*)\s*)PHRASE$`: the
     * phrase alone (after any leading punctuation), or after text that ends
     * a sentence. Group 1 is that text.
     */
    private fun patternFor(phrase: String): Pattern? {
        val parts = phrase.split(WHITESPACE)
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val body = parts.joinToString("""[\s,]+""") { Pattern.quote(it) }
        return Pattern.compile(
            """^(?:[^\p{L}\p{N}]*|(.*[$SENTENCE_END][$CLOSERS]*)\s*)$body$""",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.DOTALL,
        )
    }

    /** Sentence-final punctuation: . ! ? and the ellipsis, plus their CJK full-width forms. */
    private const val SENTENCE_END = """.!?…。！？"""

    /** Quotes and brackets that may close a sentence before the end word. */
    private const val CLOSERS = """"'”’»)\]"""

    private val WHITESPACE = Regex("""\s+""")
}
