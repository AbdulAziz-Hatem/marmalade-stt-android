package app.marmalade.stt.recognizer

import app.marmalade.stt.speech.SttEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadyForSpeechTest {

    private fun due(vararg events: SttEvent): List<SttEvent> {
        val ready = ReadyForSpeech()
        return events.filter { ready.isDue(it) }
    }

    @Test
    fun `readyForSpeech goes out once, when the mic opens, not again at Ready (#78)`() {
        assertEquals(
            listOf(SttEvent.Listening),
            due(SttEvent.Listening, SttEvent.Level(-40f), SttEvent.Ready, SttEvent.SpeechStarted, SttEvent.Ready),
        )
    }

    @Test
    fun `an engine that sends only Ready still gets it, once`() {
        assertEquals(listOf(SttEvent.Ready), due(SttEvent.Level(-40f), SttEvent.Ready, SttEvent.Ready))
    }
}
