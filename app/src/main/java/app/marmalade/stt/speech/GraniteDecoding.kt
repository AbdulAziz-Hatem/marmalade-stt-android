package app.marmalade.stt.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer

/**
 * Greedy CTC over Granite's logits (#32), as `asrbench.granite.ctc_greedy`:
 * argmax per frame, repeats collapsed, blanks (id 0) dropped.
 *
 * @param logits row-major `[frames, vocab]`, read from its current position.
 */
internal fun graniteCtcGreedy(logits: FloatBuffer, frames: Int, vocab: Int): IntArray {
    val ids = ArrayList<Int>()
    var previous = -1
    for (t in 0 until frames) {
        val base = t * vocab
        var best = 0
        var bestValue = logits.get(base)
        for (v in 1 until vocab) {
            val x = logits.get(base + v)
            if (x > bestValue) {
                bestValue = x
                best = v
            }
        }
        if (best != previous && best != GraniteVocab.BLANK) ids += best
        previous = best
    }
    return ids.toIntArray()
}

/**
 * Granite's byte-level BPE vocabulary (GPT-2 style), read from the model's
 * `tokenizer.json`: each piece spells bytes with printable stand-in characters.
 * Decoding maps them back to bytes and the bytes to UTF-8.
 */
internal class GraniteVocab(private val pieces: Array<String>) {

    val size: Int get() = pieces.size

    fun decode(ids: IntArray): String {
        val bytes = ByteArrayOutputStream()
        for (id in ids) {
            if (id == BLANK) continue
            for (ch in pieces[id]) bytes.write(BYTE_OF_CHAR[ch.code])
        }
        // Like Python's errors="replace": a malformed sequence becomes U+FFFD.
        return bytes.toString(Charsets.UTF_8.name()).trim()
    }

    companion object {
        const val BLANK = 0

        /** The `model.vocab` map of a Hugging Face `tokenizer.json` (piece → id, ids dense from 0). */
        fun fromTokenizerJson(json: String): GraniteVocab = fromTokenizerJson(Json.parseToJsonElement(json).jsonObject)

        fun fromTokenizerJson(tokenizer: JsonObject): GraniteVocab {
            val vocab = tokenizer.getValue("model").jsonObject.getValue("vocab").jsonObject
            val pieces = arrayOfNulls<String>(vocab.size)
            for ((piece, id) in vocab) pieces[id.jsonPrimitive.int] = piece
            return GraniteVocab(Array(pieces.size) { i -> requireNotNull(pieces[i]) { "vocab id $i is missing" } })
        }

        /**
         * The inverse of GPT-2's `bytes_to_unicode`, indexed by stand-in char
         * code (all below 324): printable Latin-1 bytes stand for themselves,
         * the other 68 bytes for 256, 257, … in byte order.
         */
        private val BYTE_OF_CHAR: IntArray = IntArray(256 + 68).also { table ->
            fun printable(b: Int) = b in '!'.code..'~'.code || b in '¡'.code..'¬'.code || b in '®'.code..'ÿ'.code
            var next = 256
            for (b in 0 until 256) {
                if (printable(b)) table[b] = b else table[next++] = b
            }
        }
    }
}
