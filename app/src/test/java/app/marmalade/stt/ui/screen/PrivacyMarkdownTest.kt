package app.marmalade.stt.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The privacy screen renders PRIVACY.md with a deliberately tiny Markdown
 * parser. If the policy is edited to use a construct the parser doesn't know
 * (a numbered list, a ### heading, …) the screen shows raw Markdown syntax to
 * users — nothing fails at build time. These tests pin the parser's subset and
 * check the real policy stays inside it.
 */
class PrivacyMarkdownTest {

    @Test
    fun `real PRIVACY md parses into known blocks with no leftover markup`() {
        // Unit tests run with app/ as the working directory.
        val blocks = parseMarkdown(File("../PRIVACY.md").readText())

        val first = blocks.first()
        assertTrue("Policy must open with a # title", first is MdBlock.Heading && first.level == 1)
        for (block in blocks) {
            val text = when (block) {
                is MdBlock.Heading -> block.text
                is MdBlock.Paragraph -> block.text
                is MdBlock.Bullet -> block.text
                is MdBlock.Quote -> block.text
            }
            // A block-level marker surviving into the text means the parser
            // didn't recognise that construct.
            for (marker in listOf("#", "* ", "1. ", ">", "|", "```")) {
                assertFalse("Unsupported Markdown in PRIVACY.md: \"$text\"", text.startsWith(marker))
            }
        }
    }

    @Test
    fun `wrapped bullets and paragraphs are joined into one block`() {
        val blocks = parseMarkdown(
            """
            - first line
              continues here
            - second

            A paragraph
            that wraps.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MdBlock.Bullet("first line continues here"),
                MdBlock.Bullet("second"),
                MdBlock.Paragraph("A paragraph that wraps."),
            ),
            blocks,
        )
    }

    @Test
    fun `headings, quotes and tables map to their blocks`() {
        val blocks = parseMarkdown(
            """
            # Title
            ## Section
            > quoted
            > more
            | Permission | Why |
            |---|---|
            | `INTERNET` | downloads |
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"),
                MdBlock.Heading(2, "Section"),
                MdBlock.Quote("quoted more"),
                MdBlock.Paragraph("**Permission** — Why"),
                MdBlock.Paragraph("**`INTERNET`** — downloads"),
            ),
            blocks,
        )
    }
}
