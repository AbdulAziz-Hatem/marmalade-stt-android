package app.marmalade.stt.ime

import app.marmalade.stt.speech.ListenTimeouts.IME_IDLE_TIMEOUT_MS
import app.marmalade.stt.speech.SttEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdleTimeoutTest {

    private var idles = 0

    private fun TestScope.timeout() = IdleTimeout(backgroundScope) { idles++ }

    /** Advance virtual time by [ms] and run what is due. */
    private fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `fires once, 30 s after the mic goes live with no speech`() = runTest {
        val t = timeout()
        t.onEvent(SttEvent.Ready)
        after(IME_IDLE_TIMEOUT_MS - 1)
        assertEquals(0, idles)
        after(1)
        assertEquals(1, idles)
        after(IME_IDLE_TIMEOUT_MS * 3)
        assertEquals(1, idles)
    }

    @Test
    fun `not armed before the mic is live`() = runTest {
        timeout()
        after(IME_IDLE_TIMEOUT_MS * 2)
        assertEquals(0, idles)
    }

    @Test
    fun `a slow model load isn't silence - the countdown starts at Ready, not Listening (#78)`() = runTest {
        val t = timeout()
        t.onEvent(SttEvent.Listening)
        t.onEvent(SttEvent.Level(-50f))
        after(IME_IDLE_TIMEOUT_MS * 2) // the model loads
        assertEquals(0, idles)
        t.onEvent(SttEvent.Ready)
        after(IME_IDLE_TIMEOUT_MS - 1)
        assertEquals(0, idles)
        after(1)
        assertEquals(1, idles)
    }

    @Test
    fun `speech and partials restart the countdown`() = runTest {
        val t = timeout()
        t.onEvent(SttEvent.Ready)
        after(20_000)
        t.onEvent(SttEvent.SpeechStarted)
        after(20_000)
        t.onEvent(SttEvent.Partial("thinking out loud"))
        after(IME_IDLE_TIMEOUT_MS - 1)
        assertEquals(0, idles)
        after(1)
        assertEquals(1, idles)
    }

    @Test
    fun `levels and finals are not speech`() = runTest {
        val t = timeout()
        t.onEvent(SttEvent.Ready)
        after(15_000)
        t.onEvent(SttEvent.Level(-20f))
        t.onEvent(SttEvent.Final("done.", nearSilent = false))
        after(15_000)
        assertEquals(1, idles)
    }

    @Test
    fun `cancel stops it`() = runTest {
        val t = timeout()
        t.onEvent(SttEvent.Ready)
        after(10_000)
        t.cancel()
        after(IME_IDLE_TIMEOUT_MS * 2)
        assertEquals(0, idles)
    }
}
