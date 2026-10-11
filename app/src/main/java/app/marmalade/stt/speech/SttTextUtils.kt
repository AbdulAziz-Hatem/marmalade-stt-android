package app.marmalade.stt.speech

/** Whisper's eager end-of-chunk punctuation: `.?!,;:` runs at the very end. */
private val TRAILING_PUNCT = Regex("[.?!,;:]+$")

/**
 * Strip trailing punctuation from a partial result.
 *
 * Whisper punctuates every chunk it decodes as if it were a finished
 * sentence, so a partial of half a sentence ends in "." and the full stop
 * flickers on and off as the partial grows. Partials are display-only, so
 * [StreamingSession] strips it there. Finals keep Whisper's punctuation: that
 * is the real end of the utterance and `:textproc` relies on it.
 *
 * Only the end is touched: "Dr. Smith said" is unchanged.
 */
fun stripTrailingPunctuation(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.replace(TRAILING_PUNCT, "").trimEnd()
}
