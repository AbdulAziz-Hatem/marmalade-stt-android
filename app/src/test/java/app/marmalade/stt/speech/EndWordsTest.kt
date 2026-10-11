package app.marmalade.stt.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [EndWords.match]: the end word counts only standing alone (#43, Max's
 * requirement): a segment that is just the word, or the word right after
 * sentence-final punctuation with nothing but punctuation after it. Each row
 * is one VAD segment's text; null means "no match".
 */
class EndWordsTest {

    private val over = listOf("over")
    private val sendIt = listOf("send it")

    private fun assertMatch(text: String, words: List<String>, expected: String?) =
        assertEquals("\"$text\"", expected, EndWords.match(text, words))

    @Test
    fun `the word inside or ending a sentence never matches`() {
        assertMatch("he fell over", over, null)
        assertMatch("he fell over.", over, null)
        assertMatch("He fell over!", over, null)
        assertMatch("the man walked over the bridge", over, null)
        assertMatch("the man walked over the bridge.", over, null)
        assertMatch("Game over", over, null)
    }

    @Test
    fun `the word as its own sentence after the engine's full stop matches, keeping the sentence before`() {
        assertMatch("the man walked over the bridge. Over.", over, "the man walked over the bridge.")
        assertMatch("the man walked over the bridge. over", over, "the man walked over the bridge.")
        assertMatch("Is that it? Over!", over, "Is that it?")
        assertMatch("Stop! Over.", over, "Stop!")
    }

    @Test
    fun `only the last over of Game over counts`() {
        assertMatch("Game over. Over.", over, "Game over.")
    }

    @Test
    fun `a separate segment that is just the word matches with nothing before it`() {
        // "…the bridge" (one segment), a pause, then this segment: the path
        // engines without punctuation (Granite, Nemotron) rely on.
        assertMatch("Over.", over, "")
        assertMatch("over", over, "")
        assertMatch(" Over", over, "")
    }

    @Test
    fun `case and trailing punctuation don't matter`() {
        assertMatch("OVER!", over, "")
        assertMatch("over...", over, "")
        assertMatch("Over?!", over, "")
        assertMatch("Done. OVER…", over, "Done.")
        assertMatch("\"Over.\"", over, "")
    }

    @Test
    fun `whole words only`() {
        assertMatch("hangover", over, null)
        assertMatch("Hangover.", over, null)
        assertMatch("I have a hangover. Hangover.", over, null)
        assertMatch("Overture.", over, null)
    }

    @Test
    fun `a phrase works the same way`() {
        assertMatch("Please send it.", sendIt, null)
        assertMatch("Done. Send it.", sendIt, "Done.")
        assertMatch("send it", sendIt, "")
        assertMatch("Send, it!", sendIt, "")
        assertMatch("Please send it. Send it.", sendIt, "Please send it.")
    }

    @Test
    fun `any of several words matches, and a closing quote may end the sentence`() {
        val words = listOf("send it", "over")
        assertMatch("He said \"stop.\" Over.", words, "He said \"stop.\"")
        assertMatch("Done. Send it.", words, "Done.")
        assertMatch("nothing to see", words, null)
    }

    @Test
    fun `blank text or words never match`() {
        assertMatch("", over, null)
        assertMatch("...", over, null)
        assertMatch("Over.", listOf(" "), null)
    }

    @Test
    fun `input is trimmed, lower-cased, blanks and repeats dropped`() {
        assertEquals(listOf("over", "send it"), EndWords.parse(" Over, send   IT ,, over "))
        assertEquals(emptyList<String>(), EndWords.parse(" , "))
        assertEquals("over, send it", EndWords.format(listOf("over", "send it")))
    }
}
