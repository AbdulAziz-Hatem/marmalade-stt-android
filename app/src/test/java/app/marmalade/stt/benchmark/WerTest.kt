package app.marmalade.stt.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WerTest {

    private fun wer(ref: String, hyp: String) = wordErrorRate(ref, hyp)!!

    @Test
    fun `identical text is zero`() {
        assertEquals(0.0, wer("the cat sat on the mat", "the cat sat on the mat"), 1e-9)
    }

    @Test
    fun `one substitution in four words is 25 percent`() {
        assertEquals(0.25, wer("the cat sat down", "the dog sat down"), 1e-9)
    }

    @Test
    fun `an inserted word counts against the reference length`() {
        assertEquals(0.25, wer("the cat sat down", "the big cat sat down"), 1e-9)
    }

    @Test
    fun `a deleted word counts once`() {
        assertEquals(0.25, wer("the cat sat down", "the cat down"), 1e-9)
    }

    @Test
    fun `mixed edits take the cheapest alignment`() {
        // "a b c d e" -> "a x c e f": substitute b, delete d, insert f.
        assertEquals(3.0 / 5, wer("a b c d e", "a x c e f"), 1e-9)
    }

    @Test
    fun `an empty hypothesis is 100 percent and extra words can exceed it`() {
        assertEquals(1.0, wer("hello there", ""), 1e-9)
        assertEquals(3.0, wer("hi", "oh hi there you"), 1e-9)
    }

    @Test
    fun `case, punctuation and spacing do not count`() {
        assertEquals(0.0, wer("Hello, world! How are you?", "hello world how   are\nyou"), 1e-9)
    }

    @Test
    fun `apostrophes inside words are kept, curly or straight`() {
        assertEquals("don't stop it's o'clock", normalizeForWer("Don't stop — it’s 'o'clock'!"))
        assertEquals(0.0, wer("don't", "Don’t."), 1e-9)
        assertEquals(1.0, wer("don't", "dont"), 1e-9)
    }

    @Test
    fun `punctuation between letters splits words`() {
        assertEquals("state of the art", normalizeForWer("state-of-the-art"))
    }

    @Test
    fun `an empty or punctuation-only reference has no WER`() {
        assertNull(wordErrorRate("", "anything"))
        assertNull(wordErrorRate("  ...  ", "anything"))
    }
}
