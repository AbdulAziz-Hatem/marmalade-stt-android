package app.marmalade.stt.textproc

import java.util.Locale

/**
 * Phone-number punctuation for digit runs, US style (#17): "5550142" → "555-0142",
 * "5555550142" → "555-555-0142", "15555550142" → "1-555-555-0142", "plus 44…" → "+44…".
 * Runs [Itn]'s output, so "five five five zero one four two" arrives as "5550142".
 *
 * Only in phone context: a word like "phone", "call" or "text", or "my number", in the
 * same sentence close to the digits. "Order number 5550142" stays as it is. Groups the
 * recogniser split ("555 0142", "555, 0142") are joined when their shape is a phone
 * number's. Digits that already have punctuation ("555-0142", "12,300") are left alone,
 * so running it twice changes nothing.
 */
internal object Phones {

    /** Words that mark a phone number on their own. */
    private val CONTEXT = setOf(
        "phone", "phones", "telephone", "cell", "cellphone", "mobile", "landline", "fax",
        "call", "calls", "calling", "called", "dial", "dialed", "dialled",
        "text", "texts", "texting", "texted", "whatsapp", "sms", "voicemail",
    )

    /** "number" is a phone number only after one of these: "my number", "cell number". */
    private val NUMBER_OWNERS = setOf(
        "my", "your", "his", "her", "their", "our", "phone", "cell", "mobile", "contact", "home", "work", "office",
    )

    /** How far a context word may be from the digits, in words. */
    private const val BEFORE = 8
    private const val AFTER = 4

    /** True when [tok] ends a sentence: "call.", "me!", "it?)". */
    private fun endsSentence(tok: String) = tok.trimEnd('"', '\'', '”', '’', ')').let { it.isNotEmpty() && it.last() in ".!?" }

    fun apply(text: String): String {
        if (text.count { it.isDigit() } < 7) return text
        val toks = text.split(' ')
        val out = ArrayList<String>(toks.size)
        var i = 0
        while (i < toks.size) {
            val m = match(toks, i)
            if (m == null) {
                out += toks[i]
                i++
            } else {
                if (m.start < i) out.removeAt(out.lastIndex) // "plus"
                out += m.text
                i = m.end
            }
        }
        return out.joinToString(" ")
    }

    private class Match(val start: Int, val end: Int, val text: String)

    /** The digits of a token that is nothing but digits plus trailing punctuation, and that punctuation. */
    private fun digits(tok: String): Pair<String, String>? {
        val d = tok.takeWhile { it.isDigit() }
        val rest = tok.substring(d.length)
        return if (d.isNotEmpty() && rest.all { it in ",.!?;:" }) d to rest else null
    }

    private fun match(toks: List<String>, i: Int): Match? {
        val plusSign = toks[i].startsWith('+')
        val first = digits(if (plusSign) toks[i].substring(1) else toks[i]) ?: return null
        if (i > 0 && toks[i - 1].isNotEmpty() && toks[i - 1].last().let { it.isDigit() || it == '-' }) return null
        val plusWord = !plusSign && i > 0 && toks[i - 1].lowercase(Locale.ROOT) == "plus"
        val plus = plusSign || plusWord

        // Digit groups separated by a space or ", ": "555 0142", "555, 0142".
        val groups = mutableListOf(first)
        var j = i + 1
        while (j < toks.size && groups.last().second.let { it.isEmpty() || it == "," }) {
            groups += digits(toks[j]) ?: break
            j++
        }
        // The longest prefix whose shape is a phone number's.
        for (n in groups.size downTo 1) {
            val g = groups.subList(0, n)
            val formatted = format(g.map { it.first }, plus) ?: continue
            // A quantity word right after ("5550142 dollars") means it isn't a phone number.
            if (g.last().second.isEmpty() && toks.getOrNull(i + n)?.let(::word) in QUANTITY_AFTER) return null
            val start = if (plusWord) i - 1 else i
            if (!hasContext(toks, start, i + n)) return null
            return Match(start, i + n, formatted + g.last().second)
        }
        return null
    }

    private val QUANTITY_AFTER = setOf("million", "billion", "trillion", "dollars", "euros", "times", "people")

    private fun format(groups: List<String>, plus: Boolean): String? {
        val all = groups.joinToString("")
        val lens = groups.map { it.length }
        if (plus) {
            if (groups.size > 1 && lens.dropLast(1).any { it > 4 }) return null
            return when {
                all.length == 11 && all[0] == '1' -> "+1 " + nanp(all.substring(1))
                all.length in 8..15 -> "+$all"
                else -> null
            }
        }
        return when (lens) {
            listOf(7), listOf(3, 4) -> all.substring(0, 3) + "-" + all.substring(3)
            listOf(10), listOf(3, 3, 4), listOf(3, 7) -> nanp(all)
            listOf(11), listOf(1, 3, 3, 4), listOf(1, 10) -> if (all[0] == '1') "1-" + nanp(all.substring(1)) else null
            else -> null
        }
    }

    /** "5555550142" → "555-555-0142". */
    private fun nanp(ten: String) = "${ten.substring(0, 3)}-${ten.substring(3, 6)}-${ten.substring(6)}"

    /** A context word near tokens [start] until [end], without crossing a sentence end. */
    private fun hasContext(toks: List<String>, start: Int, end: Int): Boolean {
        var k = start - 1
        while (k >= 0 && k >= start - BEFORE) {
            val t = toks[k]
            if (endsSentence(t)) break
            if (isContext(toks, k)) return true
            k--
        }
        if (endsSentence(toks[end - 1])) return false
        k = end
        while (k < toks.size && k < end + AFTER) {
            if (isContext(toks, k)) return true
            if (endsSentence(toks[k])) break
            k++
        }
        return false
    }

    private fun word(tok: String) = tok.trim { !it.isLetter() }.lowercase(Locale.ROOT)

    private fun isContext(toks: List<String>, k: Int): Boolean {
        val w = word(toks[k])
        if (w in CONTEXT) return true
        if (w != "number" && w != "numbers") return false
        return k > 0 && word(toks[k - 1]) in NUMBER_OWNERS && !endsSentence(toks[k - 1])
    }
}
