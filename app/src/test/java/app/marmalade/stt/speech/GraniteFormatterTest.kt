package app.marmalade.stt.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The formatter's pure parts (#33). [FormatLabels] is checked against outputs of
 * `apply` in tools/asr-bench/granite/format_data.py; the real tokenizer and the
 * model are checked by GraniteParityTest.
 */
class GraniteFormatterTest {

    private fun labels(vararg l: Int) = l

    @Test
    fun `labels apply case, marks and contractions like format_data apply`() {
        // format_data.apply(["bjorn","is","birthday","party","starts","at","7","pm","sharp"], ...)
        assertEquals(
            "Bjorn's birthday party starts at 7 PM sharp.",
            FormatLabels.apply(
                "bjorn is birthday party starts at 7 pm sharp".split(" "),
                labels(1, 0, 0, 0, 0, 0, 0, 2, 0), labels(0, 0, 0, 0, 0, 0, 0, 0, 1), labels(0, 1, 0, 0, 0, 0, 0, 0, 0),
            ),
        )
        assertEquals(
            "Thanks, Chidinma! I'll send it tonight.",
            FormatLabels.apply(
                "thanks chidinma i will send it tonight".split(" "),
                labels(1, 1, 1, 0, 0, 0, 0), labels(2, 4, 0, 0, 0, 0, 1), labels(0, 0, 0, 1, 0, 0, 0),
            ),
        )
    }

    @Test
    fun `irregular negatives and a merge with nothing before it`() {
        assertEquals(
            "I won't go, can't you see?",
            FormatLabels.apply(
                "i will not go can not you see".split(" "),
                labels(1, 0, 0, 0, 0, 0, 0, 0), labels(0, 0, 0, 2, 0, 0, 0, 3), labels(0, 0, 1, 0, 0, 1, 0, 0),
            ),
        )
        assertEquals("Won't", FormatLabels.apply(listOf("will", "not"), labels(1, 0), labels(0, 0), labels(0, 1)))
        // A merge label on the first word, or on a word that is no contraction, is ignored.
        assertEquals("Is it", FormatLabels.apply(listOf("is", "it"), labels(1, 0), labels(0, 0), labels(1, 1)))
    }

    @Test
    fun `bpe splits like the GPT-2 regex and merges by rank`() {
        // Vocabulary: stand-in characters ("Ġ" is the space), merges ranked in file order.
        val json = """{"model":{"type":"BPE",
            "vocab":{"Ġ":0,"h":1,"i":2,"Ġh":3,"Ġhi":4,"9":5,"3":6,"0":7,"30":8,"'":9,"s":10,"'s":11},
            "merges":[["Ġ","h"],["Ġh","i"],["3","0"],"' s"]}}"""
        val bpe = GraniteBpe.fromTokenizerJson(Json.parseToJsonElement(json).jsonObject)
        assertEquals(listOf(4), bpe.encode(" hi"))
        // Digits come in groups of three and the space before them stands alone.
        assertEquals(listOf(0, 5, 8), bpe.encode(" 930"))
        assertEquals(listOf(4, 11), bpe.encode(" hi's"))
    }
}
