package app.marmalade.stt.ui.screen

import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import java.text.Normalizer
import java.util.Locale

// List editing for the replacement dictionary and snippets screens. Pure
// Kotlin (no Android), so CleanupListsTest covers it on the JVM.
//
// Why duplicates are merged instead of stored: `:textproc` keys every rule by
// its left side (or trigger) in lower case with single spaces, and when two
// entries share a key the FIRST one wins (PhraseMatcher's putIfAbsent). A
// later entry with the same key would never fire, so a list that kept it would
// show the user a rule that silently does nothing. Every edit therefore keeps
// exactly one entry per key: the one just saved.

/** Pure list-editing rules for the cleanup dictionary and snippet editors. */
object CleanupLists {
    private val WHITESPACE = Regex("\\s+")

    /** NFC, trimmed, runs of whitespace (line breaks included) collapsed to one space. */
    private fun tidy(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC).trim().replace(WHITESPACE, " ")

    /**
     * The key `:textproc` matches a dictionary left side or snippet trigger by
     * (PhraseMatcher): NFC, whitespace collapsed, lower case. It must stay the
     * same as textproc's, so the editors treat as duplicates exactly the entries
     * textproc would: "café" typed with a combining accent is the same phrase as
     * "café" typed precomposed.
     */
    fun cleanupKey(phrase: String): String = tidy(phrase).lowercase(Locale.ROOT)

    /** Why a dictionary left side can't be saved; null means it can. */
    enum class LeftSideProblem {
        /** Nothing but whitespace. */
        BLANK,

        /**
         * Contains `->` or starts with `#`. Textproc could match it, but the
         * import/export text format (`Replacement.parseList`) splits at the first
         * `->` and skips `#` lines, so the rule would be lost or mangled on a
         * copy-and-paste round trip.
         */
        BREAKS_EXPORT,
    }

    fun leftSideProblem(from: String): LeftSideProblem? {
        val t = tidy(from)
        return when {
            t.isEmpty() -> LeftSideProblem.BLANK
            "->" in t || t.startsWith("#") -> LeftSideProblem.BREAKS_EXPORT
            else -> null
        }
    }

    /** True when [from] can be saved. The right side may be anything, including empty (deletes the phrase). */
    fun isValidRule(from: String): Boolean = leftSideProblem(from) == null

    /**
     * The rule as it is stored: both sides NFC and trimmed, whitespace collapsed
     * (dictionary rules are one line; textproc matches any spacing anyway).
     */
    fun normalizeRule(rule: Replacement): Replacement = Replacement(tidy(rule.from), tidy(rule.to))

    /** A snippet trigger must have some non-whitespace text; so must the text it inserts. */
    fun isValidSnippet(trigger: String, text: String): Boolean = trigger.isNotBlank() && text.isNotBlank()

    /**
     * The snippet as it is stored: the trigger tidied like a dictionary left side,
     * the text exactly as typed (line breaks and indentation are the point of a
     * snippet).
     */
    fun normalizeSnippet(snippet: Snippet): Snippet = Snippet(tidy(snippet.trigger), snippet.text)

    /**
     * Index of an entry other than [exceptIndex] whose key equals [key], or null.
     * The editor dialogs use it to warn that saving will replace that entry.
     */
    fun <T> indexOfKey(list: List<T>, key: String, exceptIndex: Int?, keyOf: (T) -> String): Int? =
        list.indices.firstOrNull { it != exceptIndex && cleanupKey(keyOf(list[it])) == key }

    /**
     * Saves [rule] into [rules]. [index] is the entry being edited, or null to add.
     *
     * - Adding a rule whose left side already exists updates that entry in place.
     * - Editing entry [index] so its left side equals another entry's keeps the
     *   edited entry at [index] and drops the other one.
     *
     * Either way no two entries share a key afterwards (see the file comment).
     */
    fun upsertReplacement(rules: List<Replacement>, index: Int?, rule: Replacement): List<Replacement> =
        upsert(rules, index, normalizeRule(rule)) { it.from }

    /** [upsertReplacement] for snippets, keyed by trigger. */
    fun upsertSnippet(snippets: List<Snippet>, index: Int?, snippet: Snippet): List<Snippet> =
        upsert(snippets, index, normalizeSnippet(snippet)) { it.trigger }

    fun <T> deleteAt(list: List<T>, index: Int): List<T> {
        require(index in list.indices) { "index $index out of 0 until ${list.size}" }
        return list.filterIndexed { i, _ -> i != index }
    }

    /**
     * Merges pasted rules into [rules]: a rule whose left side already exists
     * replaces that entry in place, new ones are appended in pasted order.
     *
     * If the paste itself repeats a left side, its first occurrence is kept,
     * because that is the one textproc would have used for the pasted list.
     */
    fun mergeImported(rules: List<Replacement>, imported: List<Replacement>): List<Replacement> =
        dedupeKeepFirst(imported).fold(rules) { acc, rule -> upsertReplacement(acc, null, rule) }

    /** What an import would do, shown before the user confirms it. */
    data class ImportPreview(
        /** The rules understood, normalised, with repeated left sides dropped (first kept). */
        val rules: List<Replacement>,
        /** Non-blank, non-comment lines that didn't parse as a rule (no `->`, or an empty left side). */
        val skippedLines: Int,
    )

    fun previewImport(text: String): ImportPreview {
        // A line that uses a typographic arrow instead of "->" (as some fonts,
        // including the app's, draw it) still means `wrong -> right`.
        val lines = text.lines().joinToString("\n") { line ->
            if ("->" !in line && '→' in line) line.replaceFirst("→", "->") else line
        }
        // parseList turns each meaningful line into at most one rule.
        val valid = Replacement.parseList(lines).map { normalizeRule(it) }.filter { isValidRule(it.from) }
        val meaningful = text.lineSequence().map { it.trim() }.count { it.isNotEmpty() && !it.startsWith("#") }
        return ImportPreview(rules = dedupeKeepFirst(valid), skippedLines = meaningful - valid.size)
    }

    private fun dedupeKeepFirst(rules: List<Replacement>): List<Replacement> =
        rules.distinctBy { cleanupKey(it.from) }

    private fun <T> upsert(list: List<T>, index: Int?, item: T, keyOf: (T) -> String): List<T> {
        if (index != null) require(index in list.indices) { "index $index out of 0 until ${list.size}" }
        val key = cleanupKey(keyOf(item))
        val target = index ?: indexOfKey(list, key, exceptIndex = null, keyOf) ?: return list + item
        return list.mapIndexedNotNull { i, existing ->
            when {
                i == target -> item
                cleanupKey(keyOf(existing)) == key -> null
                else -> existing
            }
        }
    }
}
