package app.marmalade.stt.ui.screen

import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import app.marmalade.stt.ui.screen.CleanupLists.LeftSideProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupListsTest {

    private val teh = Replacement("teh", "the")
    private val gh = Replacement("git hub", "GitHub")
    private val um = Replacement("basically", "")

    @Test
    fun `adding a new rule appends it`() {
        val out = CleanupLists.upsertReplacement(listOf(teh), null, gh)
        assertEquals(listOf(teh, gh), out)
    }

    @Test
    fun `adding a rule whose left side exists updates that rule in place`() {
        val rules = listOf(teh, gh, um)
        // Different case and spacing: textproc would treat it as the same phrase.
        val out = CleanupLists.upsertReplacement(rules, null, Replacement("  Git   HUB ", "Github.com"))
        assertEquals(listOf(teh, Replacement("Git HUB", "Github.com"), um), out)
    }

    @Test
    fun `editing a rule replaces it at its index`() {
        val out = CleanupLists.upsertReplacement(listOf(teh, gh, um), 1, Replacement("gitlab", "GitLab"))
        assertEquals(listOf(teh, Replacement("gitlab", "GitLab"), um), out)
    }

    @Test
    fun `editing only the case of a rule's left side keeps it`() {
        val out = CleanupLists.upsertReplacement(listOf(teh, gh), 0, Replacement("TEH", "the"))
        assertEquals(listOf(Replacement("TEH", "the"), gh), out)
    }

    @Test
    fun `an edit that collides with another rule keeps the edited slot and drops the other`() {
        // Row 2 renamed to "teh": the old row 0 would shadow it (first wins), so it goes.
        val out = CleanupLists.upsertReplacement(listOf(teh, gh, um), 2, Replacement("Teh", "The"))
        assertEquals(listOf(gh, Replacement("Teh", "The")), out)
    }

    @Test
    fun `adding cleans up duplicates already in a stored list`() {
        val out = CleanupLists.upsertReplacement(listOf(teh, gh, Replacement("TEH", "tea")), null, Replacement("teh", "THE"))
        assertEquals(listOf(Replacement("teh", "THE"), gh), out)
    }

    @Test
    fun `delete removes only that index`() {
        assertEquals(listOf(teh, um), CleanupLists.deleteAt(listOf(teh, gh, um), 1))
        assertEquals(emptyList<Replacement>(), CleanupLists.deleteAt(listOf(teh), 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `delete out of range fails loudly`() {
        CleanupLists.deleteAt(listOf(teh), 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `edit out of range fails loudly`() {
        CleanupLists.upsertReplacement(listOf(teh), 3, gh)
    }

    @Test
    fun `import replaces overlapping rules in place and appends new ones in order`() {
        val imported = listOf(Replacement("GIT HUB", "Github"), Replacement("kubernetes", "Kubernetes"), Replacement("k8s", "Kubernetes"))
        val out = CleanupLists.mergeImported(listOf(teh, gh, um), imported)
        assertEquals(
            listOf(teh, Replacement("GIT HUB", "Github"), um, Replacement("kubernetes", "Kubernetes"), Replacement("k8s", "Kubernetes")),
            out,
        )
    }

    @Test
    fun `import keeps the first of a repeated left side in the pasted text`() {
        val imported = listOf(Replacement("colour", "color"), Replacement("Colour", "COLOR"))
        assertEquals(listOf(teh, Replacement("colour", "color")), CleanupLists.mergeImported(listOf(teh), imported))
    }

    @Test
    fun `preview counts understood rules and skipped lines, ignoring comments and blanks`() {
        val text = """
            # my fixes
            teh -> the

            no arrow here
             -> empty left
            basically ->
            TEH -> tea
        """.trimIndent()
        val preview = CleanupLists.previewImport(text)
        // "TEH" repeats "teh": the first one wins, as it would in textproc.
        assertEquals(listOf(teh, um), preview.rules)
        assertEquals(2, preview.skippedLines)
    }

    @Test
    fun `a typographic arrow separates only on lines without an ascii arrow`() {
        val text = "teh → the\narrow -> →\n→ nothing on the left"
        assertEquals(
            CleanupLists.ImportPreview(listOf(Replacement("teh", "the"), Replacement("arrow", "→")), 1),
            CleanupLists.previewImport(text),
        )
    }

    @Test
    fun `preview of an export round-trips`() {
        val rules = listOf(teh, gh, um)
        assertEquals(CleanupLists.ImportPreview(rules, 0), CleanupLists.previewImport(Replacement.formatList(rules)))
    }

    @Test
    fun `whitespace-only left sides are rejected and an empty right side is allowed`() {
        assertEquals(LeftSideProblem.BLANK, CleanupLists.leftSideProblem(""))
        assertEquals(LeftSideProblem.BLANK, CleanupLists.leftSideProblem(" \t\n  "))
        assertTrue(CleanupLists.isValidRule("basically"))
        assertEquals(Replacement("basically", ""), CleanupLists.normalizeRule(Replacement(" basically ", "   ")))
    }

    @Test
    fun `left sides that would not survive export are rejected`() {
        assertEquals(LeftSideProblem.BREAKS_EXPORT, CleanupLists.leftSideProblem("a -> b"))
        assertEquals(LeftSideProblem.BREAKS_EXPORT, CleanupLists.leftSideProblem("  #hashtag"))
        assertNull(CleanupLists.leftSideProblem("c#"))
        assertNull(CleanupLists.leftSideProblem("a-b"))
    }

    @Test
    fun `stored rules are trimmed with whitespace collapsed`() {
        assertEquals(
            Replacement("git hub", "Git Hub"),
            CleanupLists.normalizeRule(Replacement("  git \n  hub ", " Git\tHub ")),
        )
    }

    @Test
    fun `keys fold case and unicode normal forms`() {
        assertEquals(CleanupLists.cleanupKey("café"), CleanupLists.cleanupKey("CAFÉ"))
        // "e" + combining acute (NFD) is the same phrase as precomposed "é".
        assertEquals(CleanupLists.cleanupKey("café"), CleanupLists.cleanupKey("café"))
        assertEquals("café", CleanupLists.normalizeRule(Replacement("café", "")).from)
    }

    @Test
    fun `unicode rules merge by key and keep their text`() {
        val rules = listOf(Replacement("café", "Café"), Replacement("東京", "Tokyo"), Replacement("🎉 party", "party"))
        val out = CleanupLists.upsertReplacement(rules, null, Replacement("CAFÉ", "coffee"))
        assertEquals(listOf(Replacement("CAFÉ", "coffee"), rules[1], rules[2]), out)
        val out2 = CleanupLists.upsertReplacement(out, null, Replacement("東京", "Tōkyō"))
        assertEquals(Replacement("東京", "Tōkyō"), out2[1])
        val out3 = CleanupLists.upsertReplacement(out2, null, Replacement("🎉  PARTY", "fun"))
        assertEquals(3, out3.size)
        assertEquals(Replacement("🎉 PARTY", "fun"), out3[2])
    }

    @Test
    fun `snippet text is saved exactly, line breaks and indentation included`() {
        val text = "Jane Doe\n  12 Example Street\r\n\nSpringfield  \n"
        val out = CleanupLists.upsertSnippet(emptyList(), null, Snippet("  my address ", text))
        assertEquals(listOf(Snippet("my address", text)), out)
    }

    @Test
    fun `snippets with the same trigger merge like dictionary rules`() {
        val email = Snippet("my email", "jane@example.com")
        val sig = Snippet("my signature", "Best,\nJane")
        val added = CleanupLists.upsertSnippet(listOf(email, sig), null, Snippet("My  Email", "j@example.org"))
        assertEquals(listOf(Snippet("My Email", "j@example.org"), sig), added)
        val edited = CleanupLists.upsertSnippet(listOf(email, sig), 1, Snippet("MY EMAIL", "x"))
        assertEquals(listOf(Snippet("MY EMAIL", "x")), edited)
    }

    @Test
    fun `snippets need a trigger and some text`() {
        assertTrue(CleanupLists.isValidSnippet("sig", "Best,\nJane"))
        assertFalse(CleanupLists.isValidSnippet("  ", "text"))
        assertFalse(CleanupLists.isValidSnippet("sig", " \n "))
    }

    @Test
    fun `indexOfKey finds a collision but not the entry being edited`() {
        val rules = listOf(teh, gh)
        assertEquals(1, CleanupLists.indexOfKey(rules, CleanupLists.cleanupKey("Git Hub"), null) { it.from })
        assertNull(CleanupLists.indexOfKey(rules, CleanupLists.cleanupKey("git hub"), 1) { it.from })
        assertNull(CleanupLists.indexOfKey(rules, CleanupLists.cleanupKey("gitlab"), null) { it.from })
    }
}
