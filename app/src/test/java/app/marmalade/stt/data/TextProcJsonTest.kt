package app.marmalade.stt.data

import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import org.junit.Assert.assertEquals
import org.junit.Test

class TextProcJsonTest {

    private fun roundTripRules(rules: List<Replacement>) =
        assertEquals(rules, TextProcJson.decodeReplacements(TextProcJson.encodeReplacements(rules)))

    private fun roundTripSnippets(snippets: List<Snippet>) =
        assertEquals(snippets, TextProcJson.decodeSnippets(TextProcJson.encodeSnippets(snippets)))

    @Test
    fun `empty lists round-trip`() {
        roundTripRules(emptyList())
        roundTripSnippets(emptyList())
    }

    @Test
    fun `order, duplicates and empty right sides survive`() {
        roundTripRules(
            listOf(
                Replacement("teh", "the"),
                Replacement("new york", "New York City"),
                Replacement("teh", "tea"), // a shadowed duplicate keeps its place
                Replacement("basically", ""),
            ),
        )
    }

    @Test
    fun `unicode and characters JSON must escape round-trip`() {
        roundTripRules(
            listOf(
                Replacement("café", "Café ☕"),
                Replacement("CAFÉ", "café"), // decomposed é stays decomposed
                Replacement("東京", "Tōkyō"),
                Replacement("smile", "😀👍🏽"),
                Replacement("quote \"x\"", "back\\slash /slash"),
                Replacement("a -> b", "arrows -> stay"),
                Replacement("  padded  ", "\ttab\u0000nul ls"),
            ),
        )
    }

    @Test
    fun `snippet text keeps line breaks and surrounding whitespace exactly`() {
        roundTripSnippets(
            listOf(
                Snippet("my address", "1 Example Road\nSpringfield\r\n\nUK  "),
                Snippet("sig", "— Max 🍊"),
                Snippet("json", "{\"a\": [1, 2]}"),
                Snippet("empty", ""),
            ),
        )
    }

    @Test
    fun `stored format is a JSON array of objects`() {
        assertEquals(
            """[{"from":"teh","to":"the"}]""",
            TextProcJson.encodeReplacements(listOf(Replacement("teh", "the"))),
        )
        assertEquals(
            """[{"trigger":"sig","text":"a\nb"}]""",
            TextProcJson.encodeSnippets(listOf(Snippet("sig", "a\nb"))),
        )
    }

    @Test
    fun `missing or damaged values decode to an empty list`() {
        for (bad in listOf(null, "", "not json", "{", "{\"from\":\"a\",\"to\":\"b\"}", "42", "\"str\"", "null")) {
            assertEquals(bad, emptyList<Replacement>(), TextProcJson.decodeReplacements(bad))
            assertEquals(bad, emptyList<Snippet>(), TextProcJson.decodeSnippets(bad))
        }
    }

    @Test
    fun `malformed entries are skipped, good ones kept`() {
        val json = """[{"from":"a","to":"b"}, {"from":"c"}, 7, {"from":1,"to":"x"}, {"from":"d","to":null}, {"from":"e","to":"f","extra":true}]"""
        assertEquals(listOf(Replacement("a", "b"), Replacement("e", "f")), TextProcJson.decodeReplacements(json))
    }
}
