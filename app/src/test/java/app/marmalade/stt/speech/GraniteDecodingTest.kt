package app.marmalade.stt.speech

import java.nio.FloatBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GraniteDecodingTest {

    /** GPT-2's bytes_to_unicode, forwards: how tokenizer.json spells [text]'s bytes. */
    private fun spell(text: String): String {
        val printable = ('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)
        var next = 256
        val map = IntArray(256)
        for (b in 0 until 256) map[b] = if (b in printable) b else next++
        return text.toByteArray().joinToString("") { map[it.toInt() and 0xff].toChar().toString() }
    }

    private fun logits(vararg best: Int, vocab: Int = 5): FloatBuffer {
        val out = FloatArray(best.size * vocab)
        best.forEachIndexed { t, id -> out[t * vocab + id] = 1f }
        return FloatBuffer.wrap(out)
    }

    @Test
    fun `greedy CTC collapses repeats and drops blanks`() {
        // a a _ a b b _ _ c  ->  a a b c (a blank separates the two a's)
        assertArrayEquals(intArrayOf(1, 1, 2, 3), graniteCtcGreedy(logits(1, 1, 0, 1, 2, 2, 0, 0, 3), 9, 5))
        assertArrayEquals(intArrayOf(), graniteCtcGreedy(logits(0, 0, 0), 3, 5))
    }

    @Test
    fun `ties go to the lowest id, as numpy argmax`() {
        assertArrayEquals(intArrayOf(), graniteCtcGreedy(FloatBuffer.wrap(FloatArray(10)), 2, 5))
    }

    @Test
    fun `byte-level pieces decode to UTF-8 and the result is trimmed`() {
        val vocab = GraniteVocab(arrayOf("<|blank|>", spell(" pay"), spell(" €"), spell("35"), spell(" björn")))
        assertEquals("pay €35 björn", vocab.decode(intArrayOf(1, 2, 3, 0, 4)))
    }

    @Test
    fun `a multi-byte character split across pieces still decodes`() {
        val euro = spell("€")
        val vocab = GraniteVocab(arrayOf("<|blank|>", euro.substring(0, 1), euro.substring(1)))
        assertEquals("€", vocab.decode(intArrayOf(1, 2)))
    }

    @Test
    fun `tokenizer json vocab is read by id`() {
        val json = """{"version":"1.0","added_tokens":[{"id":0,"content":"<|blank|>"}],
            "model":{"type":"BPE","vocab":{"<|blank|>":0,"${spell(" b")}":2,"${spell(" a")}":1},"merges":[]}}"""
        val vocab = GraniteVocab.fromTokenizerJson(json)
        assertEquals(3, vocab.size)
        assertEquals("a b", vocab.decode(intArrayOf(1, 2)))
    }
}
