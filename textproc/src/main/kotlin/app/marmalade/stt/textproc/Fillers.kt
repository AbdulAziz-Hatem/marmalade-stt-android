package app.marmalade.stt.textproc

/**
 * Removes filler words together with the commas Whisper puts around them:
 * "So, um, I think" → "So I think", "Yes, um I agree" → "Yes, I agree",
 * "Um, it works" → "It works" (a capitalised filler that started a sentence
 * hands its capital to the next word).
 */
internal class Fillers(words: List<String>) {
    private val regex: Regex? = words.map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.length }
        // Lower case or capital first letter only: "ER" (emergency room) is not a filler.
        .flatMap { listOf(it, it.replaceFirstChar { c -> c.titlecase() }) }
        .takeIf { it.isNotEmpty() }
        ?.let { forms ->
            val body = forms.joinToString("|") { Regex.escape(it) }
            Regex("(,\\s*)?${Words.FILLER_START}($body)${Words.FILLER_END}(,|\\.\\.\\.|…)?")
        }

    fun remove(text: String): String {
        val r = regex ?: return text
        val out = r.replace(text) { m ->
            val leadingComma = m.groups[1] != null
            val trailing = m.groups[3] != null
            val filler = m.groupValues[2]
            val before = text.substring(0, m.range.first).trimEnd()
            val sentenceStart = before.isEmpty() || before.last() in ".!?"
            val cap = if (!leadingComma && sentenceStart && filler[0].isUpperCase()) CAP else ""
            when {
                leadingComma && trailing -> " "
                leadingComma -> ", "
                else -> " "
            } + cap
        }
        if (CAP !in out) return out
        return CAP_TARGET.replace(out) { Words.capitalizeFirst(it.groupValues[1]) }.replace(CAP, "")
    }

    private companion object {
        const val CAP = "\u0001"
        val CAP_TARGET = Regex("\u0001+([^\\p{L}\\p{N}\\uFDD0]*.)")
    }
}
