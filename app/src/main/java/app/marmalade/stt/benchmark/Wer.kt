package app.marmalade.stt.benchmark

import java.util.Locale

// Word error rate for the A/B benchmark (#13): how far an engine's transcript
// is from the reference text the user typed. Pure Kotlin, unit-tested.

/**
 * [text] as the words WER compares: lowercased, punctuation stripped, and
 * single spaces between words. Punctuation becomes a word break ("state-of-
 * the-art" is four words), except an apostrophe inside a word, which stays
 * ("don't", "o'clock"); the typographic apostrophe ’ counts as '. Leading
 * and trailing apostrophes ('quoted') are dropped.
 */
fun normalizeForWer(text: String): String = werWords(text).joinToString(" ")

/**
 * Word-level Levenshtein distance between the normalized [reference] and
 * [hypothesis], divided by the reference's word count: 0.0 is a perfect
 * match; above 1.0 is possible when the hypothesis adds many words. Null when
 * the reference has no words, where WER is undefined.
 */
fun wordErrorRate(reference: String, hypothesis: String): Double? {
    val ref = werWords(reference)
    if (ref.isEmpty()) return null
    val hyp = werWords(hypothesis)
    return editDistance(ref, hyp).toDouble() / ref.size
}

private fun werWords(text: String): List<String> {
    val cleaned = buildString(text.length) {
        for (c in text.lowercase(Locale.ROOT)) {
            append(
                when {
                    c.isLetterOrDigit() -> c
                    c == '\'' || c == '’' -> '\''
                    else -> ' '
                },
            )
        }
    }
    return cleaned.split(' ')
        .map { it.trim('\'') }
        .filter { it.isNotEmpty() }
}

/** Substitutions + insertions + deletions turning [a] into [b], two rows of the DP table at a time. */
private fun editDistance(a: List<String>, b: List<String>): Int {
    var prev = IntArray(b.size + 1) { it }
    var curr = IntArray(b.size + 1)
    for (i in 1..a.size) {
        curr[0] = i
        for (j in 1..b.size) {
            val substitution = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(substitution, prev[j] + 1, curr[j - 1] + 1)
        }
        val t = prev
        prev = curr
        curr = t
    }
    return prev[b.size]
}
