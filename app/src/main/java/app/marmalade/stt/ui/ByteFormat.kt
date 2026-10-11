package app.marmalade.stt.ui

import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import java.util.Locale
import kotlin.math.roundToLong

// Byte counts for people: "299 MB", "1.5 MB", and download progress as
// "123 of 299 MB". Decimal units (1 MB = 1,000,000 bytes), the same ones
// Android's own storage screens use, so our numbers match what the user sees
// in system settings. The units are the locale's ("299 Mo", "299 МБ",
// "٢٩٩ م.ب"), read from ICU like Android's Formatter does. Everything but
// [ByteUnits.forLocale] is pure Kotlin, unit-tested on the JVM with explicit
// [ByteUnits].

private enum class ByteUnit(val bytes: Long) {
    B(1L),
    KB(1_000L),
    MB(1_000_000L),
    GB(1_000_000_000L),
}

/**
 * What a locale writes after the number for each unit, with the space before
 * it: " MB" in English, " Mo" (a narrow no-break space) in French, "MB" with
 * no space in Korean, " م.ب" in Arabic.
 */
data class ByteUnits(val b: String, val kb: String, val mb: String, val gb: String) {

    companion object {
        /** Android's English units; also the benchmark's Markdown export, which reads the same everywhere. */
        val English = ByteUnits(" B", " kB", " MB", " GB")

        @Volatile private var cached: Pair<Locale, ByteUnits>? = null

        /**
         * [locale]'s short units from ICU, the way Android's
         * `Formatter.formatFileSize` gets them: format a sample measure and
         * take what follows the number. Android only (the JVM tests pass
         * explicit units).
         */
        fun forLocale(locale: Locale): ByteUnits {
            cached?.let { (l, units) -> if (l == locale) return units }
            val uLocale = ULocale.forLocale(locale)
            val measures = MeasureFormat.getInstance(uLocale, MeasureFormat.FormatWidth.SHORT)
            val number = NumberFormat.getInstance(uLocale).format(SAMPLE)
            fun after(unit: MeasureUnit): String {
                val text = measures.format(Measure(SAMPLE, unit))
                // No locale we ship puts the unit first; if one did, it goes after anyway.
                return if (text.startsWith(number)) text.substring(number.length) else " " + text.replace(number, "").trim()
            }
            val units = ByteUnits(
                b = after(MeasureUnit.BYTE),
                kb = after(MeasureUnit.KILOBYTE),
                mb = after(MeasureUnit.MEGABYTE),
                gb = after(MeasureUnit.GIGABYTE),
            )
            cached = locale to units
            return units
        }

        private const val SAMPLE = 5
    }
}

private fun ByteUnits.after(unit: ByteUnit): String = when (unit) {
    ByteUnit.B -> b
    ByteUnit.KB -> kb
    ByteUnit.MB -> mb
    ByteUnit.GB -> gb
}

/**
 * The progress of a download, split so a string resource can place the words
 * around it: [done] is a bare number ("123"), [total] carries the unit
 * ("299 MB"). Both use [total]'s unit and precision, so they line up.
 */
data class ByteProgress(val done: String, val total: String)

/** [bytes] as a short size: "850 B", "12 kB", "1.5 MB", "299 MB", "1.2 GB". Negative counts read as 0. */
fun formatBytes(
    bytes: Long,
    locale: Locale = Locale.getDefault(),
    units: ByteUnits = ByteUnits.forLocale(locale),
): String {
    val b = bytes.coerceAtLeast(0L)
    val unit = unitFor(b)
    return number(b, unit, decimalsFor(b, unit), locale) + units.after(unit)
}

/** [done] of [total] bytes, e.g. "123" and "299 MB". [done] is clamped to 0..[total]. */
fun byteProgress(
    done: Long,
    total: Long,
    locale: Locale = Locale.getDefault(),
    units: ByteUnits = ByteUnits.forLocale(locale),
): ByteProgress {
    val t = total.coerceAtLeast(0L)
    val d = done.coerceIn(0L, t)
    val unit = unitFor(t)
    val decimals = decimalsFor(t, unit)
    return ByteProgress(
        done = number(d, unit, decimals, locale),
        total = number(t, unit, decimals, locale) + units.after(unit),
    )
}

/** Whole percent of [done] in [total], 0..100. A zero or negative total is 0 %. */
fun progressPercent(done: Long, total: Long): Int =
    if (total <= 0L) 0 else (done.coerceIn(0L, total) * 100L / total).toInt()

/** [done] / [total] as a 0..1 fraction for a progress bar. A zero or negative total is 0. */
fun progressFraction(done: Long, total: Long): Float =
    if (total <= 0L) 0f else (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()

/**
 * The largest unit in which [bytes] is at least 1, stepping up when rounding
 * would print "1000" (999,950,000 bytes is "1.0 GB", not "1000 MB").
 */
private fun unitFor(bytes: Long): ByteUnit {
    val units = ByteUnit.entries
    for (i in units.lastIndex downTo 1) {
        if (bytes >= units[i].bytes) return units[i]
        // Would the next unit down round up to 1000 of itself?
        val lower = units[i - 1]
        if (rounded(bytes, lower, decimalsFor(bytes, lower)) >= 1000.0) return units[i]
    }
    return ByteUnit.B
}

/** One decimal under 10 of a unit ("1.5 MB"), whole numbers otherwise ("299 MB"). Bytes are always whole. */
private fun decimalsFor(bytes: Long, unit: ByteUnit): Int =
    if (unit == ByteUnit.B || bytes.toDouble() / unit.bytes >= 9.95) 0 else 1

private fun rounded(bytes: Long, unit: ByteUnit, decimals: Int): Double {
    val scale = if (decimals == 0) 1.0 else 10.0
    return (bytes.toDouble() / unit.bytes * scale).roundToLong() / scale
}

private fun number(bytes: Long, unit: ByteUnit, decimals: Int, locale: Locale): String =
    String.format(locale, "%.${decimals}f", rounded(bytes, unit, decimals))
