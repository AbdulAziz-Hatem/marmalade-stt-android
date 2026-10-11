package app.marmalade.stt.textproc

import java.text.Normalizer
import java.util.Locale

/** Regex building blocks and text helpers shared by the rules. */
internal object Words {
    /** Characters that continue a word: letters, combining marks, digits. */
    private const val W = "\\p{L}\\p{M}\\p{N}"

    /**
     * Word start: not preceded by a word character, nor by a word character plus
     * apostrophe (so "t" never matches inside "don't").
     */
    const val START = "(?<![$W]|[$W]['’])"

    /**
     * Stricter boundaries for fillers: hyphens and apostrophes also block, so
     * "uh-oh" and "um's" are left alone.
     */
    const val FILLER_START = "(?<![$W'’-])"
    const val FILLER_END = "(?![$W'’-])"

    /** Command phrases don't match before an apostrophe at all: "scratch that's" is speech. */
    const val COMMAND_END = "(?![$W'’])"

    /**
     * Snippet text is held out of the other rules as a placeholder: the
     * noncharacter U+FDD0 (reserved by Unicode for internal use, so it never
     * appears in real text and is stripped if it does) followed by a
     * private-use character encoding the snippet index.
     */
    const val SENTINEL = '\uFDD0'
    const val PLACEHOLDER_LIMIT = 0x1900 // U+E000..U+F8FF
    val PLACEHOLDER = Regex("\\uFDD0[\\uE000-\\uF8FF]")

    fun placeholder(index: Int): String = "$SENTINEL${(0xE000 + index).toChar()}"
    fun placeholderIndex(p: String): Int = p[1].code - 0xE000
    fun isPlaceholder(c: Char) = c == SENTINEL

    /** A phrase as a regex body: each word quoted, any whitespace between words. */
    fun phrasePattern(phrase: String): String =
        phrase.trim().split(WHITESPACE).joinToString("\\s+") { Regex.escape(it) }

    /** Lookup key for a matched phrase: lower case, single spaces. */
    fun key(phrase: String): String = phrase.trim().split(WHITESPACE).joinToString(" ").lowercase(Locale.ROOT)

    /** Upper-cases the first letter, unless a digit or a snippet placeholder comes first. */
    fun capitalizeFirst(s: String): String {
        val i = s.indexOfFirst { it.isLetterOrDigit() || isPlaceholder(it) }
        if (i < 0 || !s[i].isLowerCase()) return s
        return s.substring(0, i) + s[i].titlecase(Locale.ROOT) + s.substring(i + 1)
    }

    val WHITESPACE = Regex("\\s+")

    /**
     * Regex class body for Chinese and Japanese characters, which are written without
     * spaces between words: Han, CJK symbols and punctuation (U+3000–303F: 。、「」),
     * hiragana, katakana (with the long-vowel mark ー and the small Ainu kana), and the
     * full-width and half-width forms (？！（）, half-width katakana). Hangul is left out,
     * half-width Hangul too (U+FFA0–FFDF), because Korean separates words with spaces.
     */
    const val CJK = "\\p{IsHan}\\u3000-\\u30FF\\u31F0-\\u31FF\\uFF00-\\uFF9F\\uFFE0-\\uFFEF"
    private val CJK_CHAR = Regex("[$CJK]")

    fun isCjk(codePoint: Int): Boolean = CJK_CHAR.matches(String(Character.toChars(codePoint)))
    fun hasCjk(text: String): Boolean = CJK_CHAR.containsMatchIn(text)

    /** Katakana with its long-vowel marks (ー, ｰ) and half-width voicing marks; not the middle dot ・. */
    private const val KATAKANA_CHARS = "\\p{IsKatakana}\\u30FC\\uFF70\\uFF9E\\uFF9F"

    /** Full-width digits and Latin letters (０-９, Ａ-Ｚ, ａ-ｚ): words of their own, not CJK, for triggers. */
    private const val FULL_WIDTH_ALNUM = "\\uFF10-\\uFF19\\uFF21-\\uFF3A\\uFF41-\\uFF5A"

    /**
     * Word characters of the scripts that separate words with spaces: all but CJK and
     * Hangul, with the full-width digits and Latin letters put back, so "gh" is not a whole
     * word in 「gh２」 and "ｇｈ" not in "ｇｈｏｓｔ".
     */
    private const val SPACED_W = "[$W&&[^$CJK\\p{IsHangul}]]$FULL_WIDTH_ALNUM"

    /**
     * Korean particles and endings that join the end of a noun (#62): a Hangul trigger
     * matches before any run of them, so 「메일」 matches in 「메일을」 and 「메일에서는」, but
     * not in 「메일함」. Keep `textproc/README.md` (Languages) in step with this list.
     */
    val KOREAN_PARTICLES = listOf(
        "이", "가", "을", "를", "은", "는", "의", "에", "에서", "에게", "께", "한테", "께서",
        "으로", "로", "와", "과", "랑", "이랑", "하고", "도", "만", "까지", "부터", "처럼",
        "보다", "이나", "나", "요", "이다", "이에요", "예요", "입니다", "이야", "야",
    )

    /** A Hangul word end: no more Hangul, after any run of [KOREAN_PARTICLES]. */
    private val HANGUL_END = KOREAN_PARTICLES.sortedByDescending { it.length }
        .joinToString("|", "(?=(?:", ")*(?!\\p{IsHangul}))")

    /**
     * The word boundary a dictionary or snippet trigger needs, chosen by the script of
     * its first character ([before]) and its last ([after]) (#62). A change of script
     * always counts as a boundary, so "gh" matches in 「ghのページ」 and "gh에서".
     */
    enum class Edge(val before: String, val after: String) {
        /**
         * Latin, Cyrillic, digits and the rest: whole words, but CJK and Hangul don't continue
         * a word. An apostrophe plus a letter continues it ("t" never matches in "don't"),
         * except a possessive "'s" ("github's" matches "github").
         */
        SPACED("(?<![$SPACED_W]|[$SPACED_W]['’])", "(?![$SPACED_W])(?!['’](?![sS](?![$SPACED_W]))[$SPACED_W])"),

        /** A run of katakana is one word (a loanword), so never inside one: 「メール」 not in 「メールアドレス」. */
        KATAKANA("(?<![$KATAKANA_CHARS])", "(?![$KATAKANA_CHARS])"),

        /** Korean words start after a space, but particles join their end: 「메일」 in 「메일을」, not 「메일함」. */
        HANGUL("(?<!\\p{IsHangul})", HANGUL_END),

        /** Han, hiragana and CJK marks: written without spaces and with no telling words apart, so anywhere. */
        CJK("", ""),
        ;

        companion object {
            private val KATAKANA_CHAR = Regex("[$KATAKANA_CHARS]")
            private val FULL_WIDTH_ALNUM_CHAR = Regex("[$FULL_WIDTH_ALNUM]")
            private val HANGUL_CHAR = Regex("\\p{IsHangul}")

            fun of(codePoint: Int): Edge {
                val c = String(Character.toChars(codePoint))
                return when {
                    FULL_WIDTH_ALNUM_CHAR.matches(c) -> SPACED
                    KATAKANA_CHAR.matches(c) -> KATAKANA
                    HANGUL_CHAR.matches(c) -> HANGUL
                    CJK_CHAR.matches(c) -> CJK
                    else -> SPACED
                }
            }
        }
    }
}

/**
 * Finds whole-word ([Words.Edge]), case-insensitive occurrences of any of [phrases] in one
 * left-to-right pass. At a given position the longest phrase wins, and matched
 * text is never re-scanned, so rules don't cascade. For duplicate phrases the
 * first one wins; [onMatch] receives its index in [phrases].
 *
 * Phrases are normalised to NFC, like the text they're matched against
 * ([TextPipeline.normalize]), so an accent typed as a combining mark and one
 * typed precomposed are the same phrase, whoever built the config.
 */
internal class PhraseMatcher(phrases: List<String>) {
    private val byKey = LinkedHashMap<String, Int>()
    private val regex: Regex?

    init {
        phrases.forEachIndexed { i, p ->
            if (p.isNotBlank()) byKey.putIfAbsent(Words.key(Normalizer.normalize(p, Normalizer.Form.NFC)), i)
        }
        regex = if (byKey.isEmpty()) {
            null
        } else {
            // Phrases that can match at the same position start with the same script, so one
            // lookbehind per script group; the longest still comes first within a group.
            val groups = byKey.keys.sortedByDescending { it.length }.groupBy { Words.Edge.of(it.codePointAt(0)) }
            val body = groups.entries.joinToString("|") { (edge, keys) ->
                edge.before + "(?:" + keys.joinToString("|") {
                    Words.phrasePattern(it) + Words.Edge.of(it.codePointBefore(it.length)).after
                } + ")"
            }
            // (?iu): case-insensitive with Unicode case folding (Java's (?i) alone is ASCII-only).
            Regex("(?iu)$body")
        }
    }

    /** Replaces every match with [onMatch]'s text; the text between matches goes through [between]. */
    fun replace(
        text: String,
        between: (String) -> String = { it },
        onMatch: (index: Int, matched: String) -> String,
    ): String {
        val r = regex ?: return between(text)
        val out = StringBuilder()
        var last = 0
        for (m in r.findAll(text)) {
            out.append(between(text.substring(last, m.range.first)))
            out.append(onMatch(byKey[Words.key(m.value)] ?: slowLookup(m.value), m.value))
            last = m.range.last + 1
        }
        return out.append(between(text.substring(last))).toString()
    }

    // Case folding in the regex and lowercase() can disagree on rare characters.
    private fun slowLookup(matched: String): Int = byKey.entries.first { (k, _) ->
        Regex("(?iu)" + Words.phrasePattern(k)).matches(matched)
    }.value
}
