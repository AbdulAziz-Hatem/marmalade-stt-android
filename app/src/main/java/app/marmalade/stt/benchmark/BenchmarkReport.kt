package app.marmalade.stt.benchmark

import app.marmalade.stt.ui.ByteUnits
import app.marmalade.stt.ui.formatBytes
import java.util.Locale

/**
 * The benchmark's text export ("Copy as Markdown"): the numbers and
 * transcripts, never audio. Also the number formats the screen shows, so the
 * two always agree. Pure, unit-tested.
 */
object BenchmarkReport {

    /** Seconds with one decimal: 1234 ms → "1.2 s". */
    fun seconds(ms: Long, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f s", ms / 1_000.0)

    /** Real-time factor with two decimals: "0.12". */
    fun rtf(rtf: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.2f", rtf)

    /** WER as a percentage with one decimal: 0.05 → "5.0 %". */
    fun wer(wer: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f %%", wer * 100.0)

    /** RSS as a byte size ("512 MB"), or "n/a" when it couldn't be sampled. */
    fun rss(bytes: Long?, locale: Locale = Locale.getDefault(), units: ByteUnits = ByteUnits.forLocale(locale)): String =
        bytes?.let { formatBytes(it, locale, units) } ?: "n/a"

    /**
     * A Markdown table of [outcomes], one row per engine in run order, under
     * a header line with the take's length and [deviceModel] (`Build.MODEL`).
     * Every row says whether the take went through the software AGC
     * ([softAgc]), so rows pasted together from different phones stay
     * comparable. "Transcript" is the text after dictation's cleanup
     * ([BenchmarkResult.text], what dictation would have written) and "Raw"
     * the engine's own text ([BenchmarkResult.rawText]). With a non-blank
     * [reference] the reference is quoted and a WER column, scored on the
     * cleaned transcript, is added; without one there is no WER column.
     * Failed engines get a row with dashes, the error in place of the
     * transcript and an empty Raw cell. Numbers use [Locale.ROOT] and sizes
     * English units ([ByteUnits.English]) so the export reads the same
     * everywhere.
     */
    fun markdownTable(
        takeMs: Long,
        deviceModel: String,
        outcomes: List<BenchmarkEvent.Outcome>,
        softAgc: Boolean,
        reference: String = "",
    ): String {
        val gain = if (softAgc) "on" else "off"
        val withWer = normalizeForWer(reference).isNotEmpty()
        val locale = Locale.ROOT
        return buildString {
            append("Marmalade STT benchmark: take ${seconds(takeMs, locale)}, device ${cell(deviceModel)}\n")
            if (withWer) append("\nReference: ${cell(reference)}\n")
            append('\n')
            val headers = buildList {
                addAll(listOf("Engine", "Soft AGC", "Load", "Decode", "RTF", "Peak RSS (sampled)"))
                if (withWer) add("WER")
                add("Transcript")
                add("Raw")
            }
            appendRow(headers)
            appendRow(headers.map { "---" })
            for (outcome in outcomes) {
                val cells = when (outcome) {
                    is BenchmarkEvent.Finished -> {
                        val r = outcome.result
                        buildList {
                            add(r.displayName)
                            add(gain)
                            add(seconds(r.loadMs, locale))
                            add(seconds(r.decodeMs, locale))
                            add(rtf(r.rtf, locale))
                            add(rss(r.peakRssBytes, locale, ByteUnits.English))
                            if (withWer) add(wordErrorRate(reference, r.text)?.let { wer(it, locale) } ?: "n/a")
                            add(r.text)
                            add(r.rawText)
                        }
                    }
                    is BenchmarkEvent.Failed -> buildList {
                        add(outcome.displayName)
                        add(gain)
                        repeat(if (withWer) 5 else 4) { add("-") }
                        add("Failed (${outcome.stage.name.lowercase(Locale.ROOT)}): ${outcome.message}")
                        add("")
                    }
                }
                appendRow(cells.map(::cell))
            }
        }
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        append("| ").append(cells.joinToString(" | ")).append(" |\n")
    }

    /** Text safe inside a table cell: pipes escaped, line breaks flattened to spaces. */
    private fun cell(text: String): String =
        text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace("|", "\\|")
}
