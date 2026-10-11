package app.marmalade.stt.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialStabilizerTest {

    /** The texts shown for [partials], fed in order to one stabilizer. */
    private fun shown(vararg partials: String): List<String> {
        val s = PartialStabilizer()
        return partials.map(s::update)
    }

    @Test
    fun `the first partial is shown as it is`() {
        assertEquals(listOf("hello there"), shown("hello there"))
    }

    @Test
    fun `words two partials agree on stay when a later partial changes them`() {
        val out = shown("the quick brown", "the quick brown fox", "a quick brown fox jumps")
        // The second partial agreed on "the quick brown", so the third's "a" doesn't replace "the".
        assertEquals("the quick brown fox jumps", out.last())
    }

    @Test
    fun `a partial's last word is never made stable`() {
        val out = shown("I went to the sto", "I went to the sto", "I went to the store today")
        assertEquals("I went to the store today", out.last())
    }

    @Test
    fun `a repeat at the end is cut before agreement, so it never becomes stable`() {
        val out = shown("this is a test test test", "this is a test test test test", "this is a test of the system")
        assertEquals(listOf("this is a test", "this is a test", "this is a test of the system"), out)
    }

    @Test
    fun `stable words never change or vanish`() {
        // Whisper-style partials of one utterance: growing, revising the end, one re-decode that
        // drops a word, one that rewords the start.
        val partials = listOf(
            "So", "So I", "So I think we", "So I think we should", "So I think we should go",
            "So I think we should go to the", "Also I think we should go to the park", "So I think we go to the park after",
            "So I think we should go to the park after lunch",
        )
        val s = PartialStabilizer()
        var stableSoFar = emptyList<String>()
        for (p in partials) {
            val words = s.update(p).split(' ')
            assertEquals("stable words kept in <${words.joinToString(" ")}>", stableSoFar, words.take(stableSoFar.size))
            assertTrue(s.stableWords >= stableSoFar.size)
            stableSoFar = words.take(s.stableWords)
        }
        assertEquals("So I think we should go to the park after", stableSoFar.joinToString(" "))
        assertEquals("So I think we should go to the park after lunch", s.update(partials.last()))
    }

    @Test
    fun `a stable word survives a re-decode that loses it`() {
        val out = shown("please call Anna", "please call Anna now", "please call now and")
        assertEquals("please call Anna now and", out.last())
    }

    @Test
    fun `a stable word the model rewords is not shown twice`() {
        val out = shown("please call Anna", "please call Anna now", "please call Ana now and")
        assertEquals("please call Anna now and", out.last())
    }

    @Test
    fun `case and punctuation don't stop agreement`() {
        val out = shown("hello, world how", "Hello world, how are", "HELLO WORLD how are you")
        assertEquals("Hello world, how are you", out.last())
    }

    @Test
    fun `an empty partial shows the stable words`() {
        val out = shown("one two three", "one two three four", "")
        assertEquals("one two three", out.last())
    }

    @Test
    fun `reset starts over`() {
        val s = PartialStabilizer()
        s.update("one two three")
        s.update("one two three four")
        s.reset()
        assertEquals("five six", s.update("five six"))
    }

    @Test
    fun `alignment picks where the stable words end`() {
        assertEquals(0, alignedEnd(emptyList(), listOf("a", "b")))
        assertEquals(2, alignedEnd(listOf("a", "b"), listOf("a", "b", "c")))
        assertEquals(3, alignedEnd(listOf("a", "b"), listOf("a", "x", "b", "c")))
        assertEquals(2, alignedEnd(listOf("a", "b", "c"), listOf("a", "c", "d")))
        assertEquals(1, alignedEnd(listOf("a", "b"), listOf("a")))
        assertTrue(alignedEnd(listOf("a"), emptyList()) == 0)
    }
}
