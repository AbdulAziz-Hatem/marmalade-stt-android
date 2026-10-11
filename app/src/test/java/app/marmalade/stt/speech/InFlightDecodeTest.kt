package app.marmalade.stt.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [InFlightDecode]'s orderings (#49). The session sets its flag, then
 * cancels; the transcriber creates its stream, then [InFlightDecode.begin]s
 * it. Every interleaving must stop a decode that the session cancelled.
 */
class InFlightDecodeTest {
    private val cancelled = mutableListOf<String>()
    private val inFlight = InFlightDecode<String> { cancelled += it }
    private var sessionFlag = false

    private fun sessionCancels() {
        sessionFlag = true
        inFlight.cancel()
    }

    @Test
    fun `a cancel before the stream exists stops it as soon as it begins`() {
        sessionCancels() // finds no stream
        inFlight.begin("stream") { sessionFlag }

        assertEquals(listOf("stream"), cancelled)
    }

    @Test
    fun `a cancel during the decode stops its stream`() {
        inFlight.begin("stream") { sessionFlag }
        sessionCancels()

        assertEquals(listOf("stream"), cancelled)
    }

    @Test
    fun `nothing is cancelled without a cancel, or after the decode ended`() {
        inFlight.begin("first") { sessionFlag }
        inFlight.end()
        sessionCancels() // the stream is released by now: it must not be touched

        assertEquals(emptyList<String>(), cancelled)
    }
}
