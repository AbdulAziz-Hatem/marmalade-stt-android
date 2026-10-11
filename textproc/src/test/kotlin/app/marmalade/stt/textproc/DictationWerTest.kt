package app.marmalade.stt.textproc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Re-scores the #14 benchmark's raw dictation outputs (`dictation-hyps.tsv`) through
 * the pipeline, with and without number conversion. Raw WER as in
 * `tools/asr-bench/asrbench/score.py`: words split on whitespace, case and
 * punctuation count, corpus-level. Prints the table that
 * `docs/benchmarks/2026-09-host-wer.md` quotes (`./gradlew :textproc:test --info`).
 */
class DictationWerTest {
    private class Row(val engine: String, val ref: String, val hyp: String)

    private val rows: List<Row> = javaClass.getResource("/dictation-hyps.tsv")!!.readText()
        .lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .drop(1)
        .map { it.split('\t').let { c -> Row(c[0], c[2], c[3]) } }

    /** Word-level edit distance: substitutions + deletions + insertions. */
    private fun errors(ref: List<String>, hyp: List<String>): Int {
        var prev = IntArray(hyp.size + 1) { it }
        for (i in 1..ref.size) {
            val cur = IntArray(hyp.size + 1)
            cur[0] = i
            for (j in 1..hyp.size) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[hyp.size]
    }

    private fun words(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun wer(rows: List<Row>, clean: (String) -> String): Double {
        val errs = rows.sumOf { errors(words(it.ref), words(clean(it.hyp))) }
        return 100.0 * errs / rows.sumOf { words(it.ref).size }
    }

    private val withoutItn = TextPipeline(TextProcConfig(numbersAsDigits = false))
    private val withItn = TextPipeline(TextProcConfig())

    @Test fun dictationTable() {
        val engines = rows.map { it.engine }.distinct()
        assertEquals(11, engines.size)
        val table = StringBuilder("| Engine | Raw (#14) | Cleanup, digits off | Cleanup, digits on |\n|---|---|---|---|\n")
        for (e in engines) {
            val r = rows.filter { it.engine == e }
            assertEquals(40, r.size, e)
            val raw = wer(r) { it }
            val off = wer(r) { withoutItn.process(it).insert }
            val on = wer(r) { withItn.process(it).insert }
            // The harness reproduces #14's published raw numbers exactly.
            assertEquals(PUBLISHED_RAW.getValue(e), raw, 0.01, e)
            // Number conversion never makes an engine's dictation worse.
            assertTrue(on <= off, "$e: digits on $on > off $off")
            table.append("| $e | %.1f | %.1f | %.1f |\n".format(raw, off, on))
        }
        println(table)
    }

    private companion object {
        /** `dictation-raw.wer` per engine in `docs/benchmarks/2026-09-host-wer.json`. */
        val PUBLISHED_RAW = mapOf(
            "distil-small-en" to 38.65,
            "whisper-tiny-en" to 27.91,
            "whisper-base-en" to 24.23,
            "whisper-small-en" to 17.48,
            "whisper-turbo" to 23.01,
            "parakeet-tdt-0-6b-v3" to 38.96,
            "canary-180m-flash" to 57.06,
            "nemotron-3-5-streaming-320ms" to 67.79,
            "nemotron-3-5-streaming-560ms" to 67.48,
            "nemotron-en-streaming-560ms" to 72.39,
            "qwen3-asr-0-6b" to 42.33,
        )
    }
}
