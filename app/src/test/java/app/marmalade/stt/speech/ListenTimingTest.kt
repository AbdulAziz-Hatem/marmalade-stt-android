package app.marmalade.stt.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenTimingTest {
    @Test
    fun `records each stage once, relative to the request, in order`() {
        var now = 950L
        val timing = ListenTiming("keyboard x", requestedAt = 900L, nowMs = { now })
        timing.on(SttEvent.Listening)
        now = 1_000L
        timing.on(SttEvent.Ready)
        now = 1_050L
        timing.on(SttEvent.Level(-40f))
        timing.on(SttEvent.Partial(""))
        now = 1_400L
        timing.on(SttEvent.Level(-30f))
        timing.on(SttEvent.SpeechStarted)
        now = 2_000L
        timing.on(SttEvent.Partial("hello"))
        timing.on(SttEvent.Final("hello", nearSilent = false))
        timing.on(SttEvent.NoSpeech)
        assertEquals("mic=50 ready=100 level=150 speech=500 partial=1100 final=1100", timing.summary())
    }
}
