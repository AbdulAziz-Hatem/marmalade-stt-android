package app.marmalade.stt.textproc

import kotlin.test.Test
import kotlin.test.assertEquals

class ReplacementFormatTest {
    @Test fun parsesRulesSkippingCommentsAndJunk() {
        val text = """
            # my fixes
            kubernetes -> Kubernetes
              git hub   ->   GitHub
            no arrow here
             -> empty left side
            basically ->
            a -> b -> c
        """.trimIndent()
        assertEquals(
            listOf(
                Replacement("kubernetes", "Kubernetes"),
                Replacement("git hub", "GitHub"),
                Replacement("basically", ""),
                Replacement("a", "b -> c"),
            ),
            Replacement.parseList(text),
        )
    }

    @Test fun formatRoundTrips() {
        val rules = listOf(Replacement("teh", "the"), Replacement("git hub", "GitHub"))
        assertEquals("teh -> the\ngit hub -> GitHub", Replacement.formatList(rules))
        assertEquals(rules, Replacement.parseList(Replacement.formatList(rules)))
    }
}
