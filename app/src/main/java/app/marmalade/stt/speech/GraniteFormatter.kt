package app.marmalade.stt.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.LongBuffer
import java.util.regex.Pattern

/**
 * Granite's byte-level BPE encoder (#33), as Hugging Face `tokenizers` runs
 * its `tokenizer.json`: the GPT-2 split regex, bytes to stand-in characters,
 * then the lowest-ranked merge first until none applies. The formatter reads
 * Granite's text through it; `GraniteFormatterTest` holds it to the Python
 * tokenizer on golden words.
 */
internal class GraniteBpe(private val ids: Map<String, Int>, private val ranks: Map<Pair<String, String>, Int>) {

    /** Token ids of [text]. */
    fun encode(text: String): List<Int> {
        val out = ArrayList<Int>()
        val m = SPLIT.matcher(text)
        while (m.find()) {
            val chunk = m.group().toByteArray(Charsets.UTF_8).joinToString("") { CHAR_OF_BYTE[it.toInt() and 0xff].toString() }
            for (piece in merge(chunk)) out += requireNotNull(ids[piece]) { "no id for BPE piece \"$piece\"" }
        }
        return out
    }

    private fun merge(chunk: String): List<String> {
        val parts = chunk.map { it.toString() }.toMutableList()
        while (parts.size > 1) {
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.size - 1) {
                val r = ranks[parts[i] to parts[i + 1]] ?: continue
                if (r < bestRank) {
                    bestRank = r
                    best = i
                }
            }
            if (best < 0) break
            parts[best] = parts[best] + parts.removeAt(best + 1)
        }
        return parts
    }

    companion object {
        /**
         * The pre-tokenizer's `Split` regex from Granite's tokenizer.json. No
         * UNICODE_CHARACTER_CLASS flag: Android's ICU regex rejects it (and its
         * `\s` is Unicode anyway). The formatter only splits words it got by
         * splitting on spaces, so the JVM's ASCII `\s` in tests changes nothing.
         */
        private val SPLIT: Pattern = Pattern.compile(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+",
        )

        /** GPT-2's bytes_to_unicode: printable Latin-1 bytes stand for themselves, the rest for 256, 257, …. */
        private val CHAR_OF_BYTE: CharArray = CharArray(256).also { table ->
            fun printable(b: Int) = b in '!'.code..'~'.code || b in '¡'.code..'¬'.code || b in '®'.code..'ÿ'.code
            var next = 256
            for (b in 0 until 256) table[b] = if (printable(b)) b.toChar() else (next++).toChar()
        }

        fun fromTokenizerJson(tokenizer: JsonObject): GraniteBpe {
            val model = tokenizer.getValue("model").jsonObject
            val ids = model.getValue("vocab").jsonObject.mapValues { it.value.jsonPrimitive.int }
            val ranks = HashMap<Pair<String, String>, Int>()
            model.getValue("merges").jsonArray.forEachIndexed { rank, m ->
                // Newer files store a merge as ["a", "b"], older ones as "a b".
                val pair = if (m is JsonArray) m[0].jsonPrimitive.content to m[1].jsonPrimitive.content
                else m.jsonPrimitive.content.split(' ', limit = 2).let { it[0] to it[1] }
                ranks[pair] = rank
            }
            return GraniteBpe(ids, ranks)
        }
    }
}

/**
 * Word labels back to text: the Kotlin twin of `apply` in
 * tools/asr-bench/granite/format_data.py, which defines them.
 */
internal object FormatLabels {
    const val CASE_CAP = 1
    const val CASE_UPPER = 2
    val PUNCT = listOf("", ".", ",", "?", "!", ":")

    /** The word the normaliser wrote for a contraction -> the suffix it came from. */
    private val SUFFIX = mapOf(
        "not" to "n't", "are" to "'re", "is" to "'s", "has" to "'s", "would" to "'d", "had" to "'d",
        "will" to "'ll", "have" to "'ve", "am" to "'m", "us" to "'s",
    )
    private val IRREGULAR = mapOf(("will" to "not") to "won't", ("can" to "not") to "can't")

    fun apply(words: List<String>, case: IntArray, punct: IntArray, merge: IntArray): String {
        val out = ArrayList<String>(words.size)
        for (i in words.indices) {
            val w = words[i]
            val suffix = SUFFIX[w]
            if (merge[i] == 1 && out.isNotEmpty() && suffix != null) {
                val prev = out.last()
                val fixed = IRREGULAR[prev.lowercase() to w]
                out[out.size - 1] = when {
                    fixed == null -> prev + suffix
                    prev.firstOrNull()?.isUpperCase() == true -> fixed.replaceFirstChar { it.uppercaseChar() }
                    else -> fixed
                }
            } else {
                out += when (case[i]) {
                    CASE_UPPER -> w.uppercase()
                    CASE_CAP -> w.replaceFirstChar { it.uppercaseChar() }
                    else -> w
                }
            }
            if (punct[i] != 0) out[out.size - 1] = out.last() + PUNCT[punct[i]]
        }
        return out.joinToString(" ")
    }
}

/**
 * Restores case, punctuation and apostrophes in Granite's lowercase text (#33):
 * a tiny Transformer trained from scratch (tools/asr-bench/granite/format_model.py)
 * labels each word, and [FormatLabels] applies the labels. Words are never
 * added, dropped or respelled, apart from joining an expanded contraction
 * back onto the word before it ("bjorn is birthday" -> "Bjorn's birthday").
 *
 * The graph takes Granite BPE ids `[1, T]` (each word encoded as " word")
 * and returns `[1, T, 11]` logits: case 0..2, punctuation 3..8, merge 9..10,
 * read at each word's first token.
 */
internal class GraniteFormatter(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val bpe: GraniteBpe,
) : AutoCloseable {

    fun format(text: String): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return text
        val out = ArrayList<String>()
        var from = 0
        while (from < words.size) {
            val ids = ArrayList<Int>()
            val starts = ArrayList<Int>()
            var to = from
            while (to < words.size) {
                val w = bpe.encode(" " + words[to])
                if (ids.isNotEmpty() && ids.size + w.size > MAX_TOKENS) break
                starts += ids.size
                ids += w
                to++
            }
            out += label(words.subList(from, to), ids.take(MAX_TOKENS), starts)
            from = to
        }
        return out.joinToString(" ")
    }

    private fun label(words: List<String>, ids: List<Int>, starts: List<Int>): String {
        val input = LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() })
        OnnxTensor.createTensor(env, input, longArrayOf(1, ids.size.toLong())).use { tensor ->
            session.run(mapOf(INPUT to tensor)).use { result ->
                val logits = (result.get(0) as OnnxTensor).floatBuffer
                fun argmax(token: Int, from: Int, until: Int): Int {
                    var best = from
                    for (c in from + 1 until until) if (logits.get(token * WIDTH + c) > logits.get(token * WIDTH + best)) best = c
                    return best - from
                }
                val n = words.size
                val case = IntArray(n) { argmax(starts[it], 0, 3) }
                val punct = IntArray(n) { argmax(starts[it], 3, 9) }
                val merge = IntArray(n) { argmax(starts[it], 9, 11) }
                return FormatLabels.apply(words, case, punct, merge)
            }
        }
    }

    override fun close() = session.close()

    companion object {
        private const val INPUT = "ids"
        private const val WIDTH = 11

        /** The model's learned positions stop at 256; longer text is labelled in pieces. */
        private const val MAX_TOKENS = 256

        fun load(env: OrtEnvironment, model: ByteArray, bpe: GraniteBpe, threads: Int): GraniteFormatter {
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            return GraniteFormatter(env, options.use { env.createSession(model, it) }, bpe)
        }
    }
}
