package app.marmalade.stt.textproc

import java.util.Locale

/**
 * English inverse text normalisation: spoken numbers become digits, by fixed
 * rules (ARCHITECTURE.md D7). Nemotron, Canary and Qwen3 write "twenty five
 * percent" where a dictation user wants "25%". The rules and their limits are
 * in `textproc/README.md` ("Numbers").
 *
 * Small numbers (zero to nine, first to ninth) stay words unless something
 * around them makes them a quantity: a unit, a currency, a time, a month, a
 * keyword like "page". So "one of them" and "three boxes" are left alone.
 * Digits already in the text are left alone too, apart from three fix-ups
 * ([fixDigits]); running the rules twice changes nothing.
 *
 * Input is one line with single spaces ([TextPipeline] tidies it first).
 */
internal object Itn {

    fun apply(text: String): String {
        val toks = fixDigits(splitHyphens(text)).split(' ').filter { it.isNotEmpty() }.map(::Tok)
        val out = ArrayList<String>(toks.size)
        var i = 0
        while (i < toks.size) {
            val c = convert(toks, i)
            if (c == null) {
                out += toks[i].raw
                i++
            } else {
                repeat(i - c.start) { out.removeAt(out.lastIndex) }
                out += c.text
                i = c.end
            }
        }
        val joined = out.joinToString(" ")
        return if (joined.any { it.isDigit() }) Phones.apply(DATE_YEAR.replace(joined, "$1, $2")) else joined
    }

    /** A converted span: tokens [start] until [end] (exclusive) become [text]. */
    private class Span(val start: Int, val end: Int, val text: String)

    /** A whitespace-separated token split into leading punctuation, word and trailing punctuation. */
    private class Tok(val raw: String) {
        val pre: String
        val core: String
        val post: String
        val lower: String

        init {
            val a = raw.indexOfFirst { it.isLetterOrDigit() }
            val b = raw.indexOfLast { it.isLetterOrDigit() }
            pre = if (a < 0) raw else raw.substring(0, a)
            core = if (a < 0) "" else raw.substring(a, b + 1)
            post = if (a < 0) "" else raw.substring(b + 1)
            lower = core.lowercase(Locale.ROOT).replace('’', '\'')
        }
    }

    private enum class Kind { UNIT, TEEN, TENS, HUNDRED, SCALE }

    private class Entry(val kind: Kind, val value: Long, val ordinal: Boolean)

    /** A parsed cardinal or ordinal: tokens [start] until [end], [words] number words (not counting "and"). */
    private class Num(
        val value: Long,
        val end: Int,
        val words: Int,
        val ordinal: Boolean,
        val magnitude: Boolean, // contains "hundred" or a scale word
        val last: Kind,
        val lastWord: String,
    ) {
        val singleDigit get() = words == 1 && last == Kind.UNIT && !ordinal
        val twoDigit get() = value in 10..99 && !magnitude && !ordinal
        val hourLike get() = value in 1..12 && !magnitude && !ordinal
    }

    private val SCALE_VALUES: Map<String, Long> = mapOf(
        "thousand" to 1_000L, "million" to 1_000_000L, "billion" to 1_000_000_000L, "trillion" to 1_000_000_000_000L,
    )

    private val LEXICON: Map<String, Entry> = buildMap {
        fun add(words: String, kind: Kind, first: Long, step: Long, ordinal: Boolean) =
            words.split(' ').forEachIndexed { i, w -> put(w, Entry(kind, first + step * i, ordinal)) }
        add("zero one two three four five six seven eight nine", Kind.UNIT, 0, 1, false)
        add("ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen", Kind.TEEN, 10, 1, false)
        add("twenty thirty forty fifty sixty seventy eighty ninety", Kind.TENS, 20, 10, false)
        add("first second third fourth fifth sixth seventh eighth ninth", Kind.UNIT, 1, 1, true)
        add(
            "tenth eleventh twelfth thirteenth fourteenth fifteenth sixteenth seventeenth eighteenth nineteenth",
            Kind.TEEN, 10, 1, true,
        )
        add("twentieth thirtieth fortieth fiftieth sixtieth seventieth eightieth ninetieth", Kind.TENS, 20, 10, true)
        put("hundred", Entry(Kind.HUNDRED, 100, false))
        put("hundredth", Entry(Kind.HUNDRED, 100, true))
        for ((w, v) in SCALE_VALUES) {
            put(w, Entry(Kind.SCALE, v, false))
            put(w + "th", Entry(Kind.SCALE, v, true))
        }
    }

    /** Scale words kept as words after a round number: "1 million", "2.5 billion". */
    private val KEPT_SCALES = setOf("million", "billion", "trillion")

    private val MONTHS = setOf(
        "january", "february", "march", "april", "may", "june", "july", "august",
        "september", "october", "november", "december",
    )

    /** Words before a number that make even a small one a label: "page 3", "gate 4". */
    private val KEYWORDS = setOf(
        "page", "pages", "chapter", "section", "step", "room", "gate", "platform", "version", "level", "floor",
        "flight", "bus", "route", "channel", "episode", "season", "volume", "exit", "apartment", "suite", "unit",
        "figure", "verse", "row", "seat", "track", "android", "ios",
    )

    /** Words after a number that make it a quantity. Time spans (minutes, weeks) are not here: "three weeks" stays. */
    private val UNITS = setOf(
        "degree", "degrees", "percent",
        "millimeter", "millimeters", "millimetre", "millimetres", "mm", "centimeter", "centimeters", "centimetre",
        "centimetres", "cm", "meter", "meters", "metre", "metres", "kilometer", "kilometers", "kilometre", "kilometres",
        "km", "inch", "inches", "feet", "yard", "yards", "mile", "miles", "mph",
        "gram", "grams", "kilogram", "kilograms", "kilo", "kilos", "kg", "milligram", "milligrams", "mg",
        "ounce", "ounces", "oz", "pound", "pounds", "lbs",
        "liter", "liters", "litre", "litres", "milliliter", "milliliters", "millilitre", "millilitres", "ml",
        "gallon", "gallons", "cup", "cups", "teaspoon", "teaspoons", "tablespoon", "tablespoons",
        "kilobytes", "megabytes", "gigabytes", "terabytes", "kb", "mb", "gb", "tb",
        "volts", "watts", "amps", "hertz", "khz", "mhz", "ghz", "kilowatts", "kw", "kwh",
        "cents", "cent",
    )

    /** Words before a number that make a following pair a time: "at nine thirty" → "at 9:30". */
    private val TIME_BEFORE = setOf("at", "by", "until", "till", "around", "from")
    private val AM_PM = Regex("[ap]\\.?m")
    private val DAYPARTS = setOf("morning", "afternoon", "evening")
    private val CENTS = setOf("cents", "cent")
    private val MINUS = setOf("minus", "negative")

    /** Single letters that are words, not labels: "a four", "I two". */
    private val NOT_LETTERS = setOf("a", "i", "o")
    private val CURRENCIES = mapOf("dollars" to "$", "dollar" to "$", "euros" to "€", "euro" to "€")

    // ---- digit fix-ups ---------------------------------------------------------------------------

    /** "9.30 am" → "9:30 am" (Whisper's British-style time). */
    private val DOT_TIME = Regex("(?<![\\d.,])(1[0-2]|0?[1-9])\\.([0-5]\\d)(?=\\s?[AaPp]\\.?[Mm](?!\\p{L}))")

    /** "came to$5" → "came to $5" (Parakeet glues the sign to the word before). */
    private val GLUED_CURRENCY = Regex("(?<=\\p{L})(?=[$€]\\d)")

    /** "$1800" → "$1,800". */
    private val UNGROUPED_MONEY = Regex("(?<=[$€])(\\d{4,})(?![\\d,])")

    private fun fixDigits(s: String): String {
        if (s.none { it.isDigit() }) return s
        var t = DOT_TIME.replace(s, "$1:$2")
        t = GLUED_CURRENCY.replace(t, " ")
        return UNGROUPED_MONEY.replace(t) { group(it.value.toLong()) }
    }

    /** "March 14 2027" → "March 14, 2027". */
    private val DATE_YEAR = Regex(
        "(?i)(\\b(?:january|february|march|april|may|june|july|august|september|october|november|december) " +
            "\\d{1,2}(?:st|nd|rd|th)?) (\\d{4})(?![\\d,.:])",
    )

    /** "twenty-five" → "twenty five", but not "twenty-five-year-old". */
    private val HYPHENATED = Regex(
        "(?i)(?<![\\p{L}-])(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)-" +
            "(one|two|three|four|five|six|seven|eight|nine|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)" +
            "(?![\\p{L}-])",
    )

    private fun splitHyphens(s: String) = if ('-' in s) HYPHENATED.replace(s, "$1 $2") else s

    // ---- parsing ---------------------------------------------------------------------------------

    /** True when [j] continues the token before it: no punctuation in between. */
    private fun joined(toks: List<Tok>, j: Int) = j > 0 && j < toks.size && toks[j - 1].post.isEmpty() && toks[j].pre.isEmpty()

    private fun entry(t: Tok): Entry? = if (t.core.length == t.lower.length) LEXICON[t.lower] else null

    /**
     * The longest well-formed number starting at [i]: "two hundred and forty nine", "twenty first".
     * "five five" or "twenty thirty" stop after the first word; the callers read those as digit
     * runs, years or times. An ordinal word ends the number.
     */
    private fun parse(toks: List<Tok>, i: Int, limit: Int = toks.size): Num? {
        var total = 0L
        var current = 0L
        var last: Kind? = null
        var lastScale = Long.MAX_VALUE
        var j = i
        var end = i
        var words = 0
        var ordinal = false
        var magnitude = false
        while (j < limit && (j == i || joined(toks, j))) {
            if (toks[j].lower == "and" && j > i && (last == Kind.HUNDRED || last == Kind.SCALE)) {
                val next = toks.getOrNull(j + 1)?.takeIf { joined(toks, j + 1) }?.let(::entry)
                if (next == null || next.kind == Kind.HUNDRED || next.kind == Kind.SCALE) break
                j++
                continue
            }
            val e = entry(toks[j]) ?: break
            val ok = when (e.kind) {
                Kind.UNIT -> last == null || (e.value > 0 && last != Kind.UNIT && last != Kind.TEEN)
                Kind.TEEN, Kind.TENS -> last == null || last == Kind.HUNDRED || last == Kind.SCALE
                Kind.HUNDRED -> last != null && last != Kind.HUNDRED && last != Kind.SCALE && current in 1..99
                Kind.SCALE -> last != null && last != Kind.SCALE && current > 0 && e.value < lastScale
            }
            if (!ok || (last == Kind.UNIT && current == 0L)) break // nothing follows a bare "zero"
            when (e.kind) {
                Kind.HUNDRED -> current *= 100
                Kind.SCALE -> {
                    total += current * e.value
                    current = 0
                    lastScale = e.value
                }
                else -> current += e.value
            }
            if (e.kind == Kind.HUNDRED || e.kind == Kind.SCALE) magnitude = true
            last = e.kind
            words++
            j++
            end = j
            if (e.ordinal) {
                ordinal = true
                break
            }
        }
        if (words == 0 || last == null) return null
        return Num(total + current, end, words, ordinal, magnitude, last, toks[end - 1].lower)
    }

    /** One digit word, or "oh" inside a run. */
    private fun digitAt(toks: List<Tok>, j: Int, first: Boolean): Char? {
        if (j >= toks.size || (!first && !joined(toks, j))) return null
        if (!first && (toks[j].lower == "oh" || toks[j].lower == "o")) return '0'
        val e = entry(toks[j]) ?: return null
        return if (e.kind == Kind.UNIT && !e.ordinal) ('0' + e.value.toInt()) else null
    }

    /** The second half of "nine thirty", "twenty twenty six", "nineteen oh five": 10–99 or "oh" + digit. */
    private fun pairAt(toks: List<Tok>, j: Int): Pair<Long, Int>? {
        if (!joined(toks, j)) return null
        if (toks[j].lower == "oh" || toks[j].lower == "o") {
            val d = digitAt(toks, j + 1, first = false) ?: return null
            return (d - '0').toLong() to j + 2
        }
        val b = parse(toks, j) ?: return null
        return if (b.twoDigit) b.value to b.end else null
    }

    /** "point five", "point one four", "point thirteen" after a number; repeated for "1.13.8". */
    private fun fractionAt(toks: List<Tok>, j: Int): Pair<String, Int>? {
        if (!joined(toks, j) || toks[j].lower != "point") return null
        val digits = StringBuilder()
        var k = j + 1
        while (true) {
            val d = digitAt(toks, k, first = false)?.takeIf { toks[k].lower != "o" } ?: break
            digits.append(d)
            k++
        }
        if (digits.isNotEmpty()) return digits.toString() to k
        if (!joined(toks, j + 1)) return null
        val b = parse(toks, j + 1)?.takeIf { it.twoDigit } ?: return null
        return b.value.toString() to b.end
    }

    private fun isMonth(t: Tok): Boolean =
        t.lower in MONTHS && (t.lower != "may" && t.lower != "march" || t.core[0].isUpperCase())

    private fun word(toks: List<Tok>, j: Int): String? = if (joined(toks, j)) toks[j].lower else null

    private fun isNumberish(t: Tok) = t.core.isNotEmpty() && (t.core[0].isDigit() || entry(t) != null)

    // ---- conversion ------------------------------------------------------------------------------

    private fun convert(toks: List<Tok>, i: Int): Span? {
        val first = entry(toks[i]) ?: return null
        if (first.kind == Kind.HUNDRED || first.kind == Kind.SCALE) return null // "a hundred" stays

        // Prefixes: "minus four", "gate B twenty two".
        val prev = toks.getOrNull(i - 1)?.takeIf { joined(toks, i) }
        val minus = prev != null && prev.lower in MINUS &&
            toks.getOrNull(i - 2)?.takeIf { joined(toks, i - 1) }?.let(::isNumberish) != true
        val letter = prev != null && prev.core.length == 1 && prev.core[0].isLetter() && prev.lower !in NOT_LETTERS
        val keyword = prev != null && prev.lower in KEYWORDS
        val month = prev != null && isMonth(prev)
        val start = if (minus || letter) i - 1 else i
        val pre = toks[start].pre + when {
            minus -> "-"
            letter -> prev!!.core
            else -> ""
        }

        val a = parse(toks, i) ?: return null
        var end = a.end
        var core: String
        var value: Long? = a.value // null once the core isn't a plain integer
        var digitsOnly = false // a digit run or a year: never grouped with commas
        var context = minus || letter || keyword || month

        // After a month the first number is the day, unless the pair is the whole year:
        // "May twenty twenty six" → May 2026, "March fourteen twenty twenty seven" → March 14, 2027.
        val pair = if (a.ordinal) null else pairAt(toks, a.end)?.takeIf { (_, after) ->
            !month || (a.twoDigit && toks.getOrNull(after)?.takeIf { joined(toks, after) }?.let(::entry) == null)
        }
        val timeAfter = { j: Int ->
            val w = word(toks, j)
            w != null && (AM_PM.matches(w) || w == "o'clock" ||
                (w == "in" && word(toks, j + 1) == "the" && word(toks, j + 2) in DAYPARTS) ||
                (w == "at" && word(toks, j + 1) == "night"))
        }
        val currencyAfter = { j: Int -> word(toks, j) in CURRENCIES }
        var run = 0
        val runDigits = StringBuilder()
        while (true) runDigits.append(digitAt(toks, i + run, first = run == 0) ?: break).also { run++ }

        if (pair != null && !month && a.hourLike && pair.first <= 59 &&
            (timeAfter(pair.second) || prev?.lower in TIME_BEFORE || (a.value >= 10 && !currencyAfter(pair.second)))
        ) {
            // "nine thirty am", "at six forty five", "ten fifteen" → 9:30, 6:45, 10:15
            core = "${a.value}:${pair.first.toString().padStart(2, '0')}"
            value = null
            end = pair.second
            context = true
        } else if (run >= 3 && !month) {
            // "four eight two one three" → 48213, "four oh four" → 404
            core = runDigits.toString()
            value = null
            end = i + run
            context = true
        } else if (pair != null && (a.twoDigit || (a.singleDigit && pair.first >= 10))) {
            // "twenty twenty six" → 2026, "nineteen oh five" → 1905, "seven forty two" → 742
            value = a.value * 100 + pair.first
            core = value.toString()
            digitsOnly = true
            end = pair.second
            context = true
        } else {
            core = a.value.toString()
            if (!a.ordinal) {
                // "twelve point five" → 12.5, "one point thirteen point eight" → 1.13.8
                var f = fractionAt(toks, end)
                while (f != null) {
                    core += "." + f.first
                    value = null
                    context = true
                    end = f.second
                    f = fractionAt(toks, end)
                }
            }
        }

        // "one million" → "1 million", "two point five billion" → "2.5 billion"
        val scaleWord = when {
            value == a.value && a.last == Kind.SCALE && a.lastWord in KEPT_SCALES && !a.ordinal -> a.lastWord
            value == null && core.contains('.') && !core.contains(':') && word(toks, end) in KEPT_SCALES -> word(toks, end)
            else -> null
        }
        if (scaleWord != null) {
            val scale = SCALE_VALUES.getValue(scaleWord)
            if (value != null && value % scale == 0L && value / scale < 1000) {
                core = "${value / scale} ${toks[end - 1].core}"
                value = null
            } else if (value == null) {
                core = "$core ${toks[end].core}"
                end++
            }
        }

        if (a.ordinal) {
            val ofMonth = word(toks, end) == "of" &&
                toks.getOrNull(end + 1)?.takeIf { joined(toks, end + 1) }?.let(::isMonth) == true
            val date = month || ofMonth
            // "twenty second" is as often a duration as a date: only after a month.
            val big = a.value >= 10 && !(a.words > 1 && a.lastWord == "second")
            if (!date && !big) {
                // "a twenty second delay": the number is "twenty"
                val c = if (a.words > 1) parse(toks, i, limit = a.end - 1) else null
                if (c == null || c.singleDigit) return null
                return Span(start, c.end, pre + (if (c.value >= 10_000) group(c.value) else c.value) + toks[c.end - 1].post)
            }
            return Span(start, end, pre + a.value + ordinalSuffix(a.value) + toks[end - 1].post)
        }

        var prefix = ""
        var suffix = ""
        val w = word(toks, end)
        when {
            w == "percent" -> {
                suffix = "%"
                end++
                context = true
            }
            w == "per" && word(toks, end + 1) == "cent" -> {
                suffix = "%"
                end += 2
                context = true
            }
            w != null && w in CURRENCIES -> {
                prefix = CURRENCIES.getValue(w)
                end++
                context = true
                // "forty nine dollars and ninety nine cents" → $49.99
                if (!core.contains('.') && !core.contains(' ')) {
                    val c = if (word(toks, end) == "and") end + 1 else end
                    val cents = if (joined(toks, c)) parse(toks, c) else null
                    if (cents != null && !cents.ordinal && !cents.magnitude && cents.value in 1..99 &&
                        word(toks, cents.end) in CENTS
                    ) {
                        suffix = "." + cents.value.toString().padStart(2, '0')
                        end = cents.end + 1
                    }
                }
            }
            w != null && (w in UNITS || timeAfter(end)) -> context = true
        }

        if (a.singleDigit && !context) return null
        val money = prefix.isNotEmpty()
        val v = value
        if (v != null && ((money && v >= 1000) || (!digitsOnly && v >= 10_000))) core = group(v)
        return Span(start, end, pre + prefix + core + suffix + toks[end - 1].post)
    }

    private fun group(v: Long) = String.format(Locale.US, "%,d", v)

    private fun ordinalSuffix(v: Long): String = when {
        v % 100 in 11..13 -> "th"
        v % 10 == 1L -> "st"
        v % 10 == 2L -> "nd"
        v % 10 == 3L -> "rd"
        else -> "th"
    }
}
