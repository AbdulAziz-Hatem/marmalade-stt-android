package app.marmalade.stt.ui.screen

import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.PhantomFilter
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Dictate screen's reducer against real `:textproc` (the default
 * config the screen uses), so these also pin the textproc integration:
 * `before` is the transcript, and the edit is applied to it.
 */
class DictationTest {

    private val dictation = Dictation()

    private fun DictationState.feed(vararg events: SttEvent): DictationState =
        events.fold(this) { state, event -> dictation.reduce(state, event) }

    private fun listening(transcript: String = ""): DictationState =
        dictation.started(DictationState(transcript = transcript), modelLoaded = true).feed(SttEvent.Ready)

    @Test
    fun `a partial shows beside the transcript and a final replaces it`() {
        var s = listening().feed(SttEvent.SpeechStarted, SttEvent.Partial("hello wor"), SttEvent.Partial("hello world"))
        assertEquals(DictationPhase.HEARING, s.phase)
        assertEquals("", s.transcript)
        assertEquals("hello world", s.partial)

        s = s.feed(SttEvent.Final("hello world.", nearSilent = false))
        assertEquals("Hello world.", s.transcript) // textproc capitalises the start
        assertEquals("", s.partial)
        assertEquals(DictationPhase.LISTENING, s.phase)

        s = s.feed(SttEvent.SpeechStarted, SttEvent.Partial("how are you"), SttEvent.Final("How are you?", false))
        assertEquals("Hello world. How are you?", s.transcript)
    }

    @Test
    fun `catching up shows while behind, too slow leaves a notice and the session running`() {
        var s = listening().feed(SttEvent.SpeechStarted, SttEvent.CatchingUp(true))
        assertEquals(true, s.catchingUp)
        s = s.feed(SttEvent.TooSlow)
        assertEquals(DictationNotice.TOO_SLOW, s.notice)
        assertEquals(DictationPhase.HEARING, s.phase)
        assertEquals(false, s.feed(SttEvent.CatchingUp(false)).catchingUp)
        assertEquals(false, dictation.ended(s).catchingUp)
    }

    @Test
    fun `a silent Thank you is dropped, a soft or spoken one is kept`() {
        val s = listening("Hello.")
            .feed(SttEvent.Partial("Thank you"), SttEvent.Final("Thank you.", nearSilent = true, silent = true))
        assertEquals("Hello.", s.transcript)
        assertEquals("", s.partial)

        // Near-silent is not enough for an ordinary phrase a user may say quietly (#57).
        assertEquals("Hello. Thank you.", s.feed(SttEvent.Final("Thank you.", nearSilent = true)).transcript)
        assertEquals("Hello. Thank you.", s.feed(SttEvent.Final("Thank you.", nearSilent = false)).transcript)
    }

    @Test
    fun `spoken commands edit the transcript`() {
        var s = listening("First line.").feed(SttEvent.Final("New line.", false))
        assertEquals("First line.\n", s.transcript)
        s = s.feed(SttEvent.Final("second line", false))
        assertEquals("First line.\nSecond line", s.transcript)

        // "Scratch that" reaches back into earlier utterances.
        val scratched = listening("One. Two.").feed(SttEvent.Final("Scratch that.", false))
        assertEquals("One.", scratched.transcript)
    }

    @Test
    fun `a partial that carries finished utterances is shown until the one final`() {
        var s = listening("Earlier text.").feed(
            SttEvent.SpeechStarted,
            SttEvent.Partial("Hello there"),
            // As Tap stop's do: the finished utterance stays in the partial, with its punctuation.
            SttEvent.Partial("Hello there. How are"),
        )
        assertEquals("Earlier text.", s.transcript)
        assertEquals("Hello there. How are", s.partial)

        s = dictation.stopping(s)
        assertEquals(DictationPhase.FINISHING, s.phase)
        s = s.feed(SttEvent.Final("Hello there. How are you?", false))
        assertEquals("Earlier text. Hello there. How are you?", s.transcript)
        assertEquals("", s.partial)
        assertEquals(DictationPhase.FINISHING, s.phase) // until the flow ends

        assertEquals(DictationPhase.IDLE, dictation.ended(s).phase)
    }

    @Test
    fun `phases follow the session`() {
        var s = dictation.started(DictationState(), modelLoaded = false)
        assertEquals(DictationPhase.LOADING, s.phase)
        s = s.feed(SttEvent.Ready)
        assertEquals(DictationPhase.LISTENING, s.phase)

        // #78: the mic opens before the model has loaded; Ready after it changes nothing.
        s = dictation.started(DictationState(), modelLoaded = false).feed(SttEvent.Listening)
        assertEquals(DictationPhase.LISTENING, s.phase)
        s = s.feed(SttEvent.Level(-20f))
        assertEquals("the meter moves while the model loads", 2f / 3f, s.level, 0.001f)
        assertEquals(DictationPhase.LISTENING, s.feed(SttEvent.Ready).phase)

        // Stop pressed while loading: Ready and speech onset don't undo "Finishing".
        s = dictation.stopping(dictation.started(DictationState(), modelLoaded = false))
        s = s.feed(SttEvent.Ready, SttEvent.SpeechStarted)
        assertEquals(DictationPhase.FINISHING, s.phase)

        // Stopping while idle changes nothing.
        assertEquals(DictationState(), dictation.stopping(DictationState()))
    }

    @Test
    fun `a looping or noise-only partial is cleaned before it is shown`() {
        var s = listening("Hi.").feed(SttEvent.SpeechStarted, SttEvent.Partial("so it was " + "E ".repeat(12)))
        assertEquals("so it was", s.partial)
        // Nothing left: the stale partial goes too.
        s = s.feed(SttEvent.Partial("(buzzing)"))
        assertEquals("", s.partial)

        val off = Dictation(TextPipeline(TextProcConfig(phantomFilter = PhantomFilter.OFF)))
        assertEquals("E E E E E E E", off.reduce(DictationState(), SttEvent.Partial("E E E E E E E ")).partial)
    }

    @Test
    fun `a session that ends without a final drops the partial but keeps the transcript`() {
        val s = listening("Kept.").feed(SttEvent.Partial("never finished"), SttEvent.Level(-20f))
        val ended = dictation.ended(s)
        assertEquals("Kept.", ended.transcript)
        assertEquals("", ended.partial)
        assertEquals(0f, ended.level, 0f)
        assertEquals(DictationPhase.IDLE, ended.phase)
    }

    @Test
    fun `no speech raises a notice and keeps the transcript`() {
        val s = listening("Kept.").feed(SttEvent.NoSpeech)
        assertEquals(DictationNotice.NO_SPEECH, s.notice)
        assertEquals("Kept.", s.transcript)
        assertNull(dictation.noticeShown(s).notice)
    }

    @Test
    fun `errors map to a notice per kind and return to idle`() {
        val expected = mapOf(
            SttErrorKind.MIC to DictationNotice.MIC_ERROR,
            SttErrorKind.MODEL to DictationNotice.MODEL_ERROR,
            SttErrorKind.RECOGNITION to DictationNotice.RECOGNITION_ERROR,
            SttErrorKind.BUSY to DictationNotice.BUSY,
        )
        for ((kind, notice) in expected) {
            val s = listening("Kept.").feed(SttEvent.Partial("half"), SttEvent.Error(kind, "boom"))
            assertEquals(notice, s.notice)
            assertEquals(DictationPhase.IDLE, s.phase)
            assertEquals("", s.partial)
            assertEquals("Kept.", s.transcript)
        }
    }

    @Test
    fun `starting a session clears the last notice and partial`() {
        val s = dictation.started(DictationState(partial = "stale", notice = DictationNotice.NO_SPEECH), true)
        assertNull(s.notice)
        assertEquals("", s.partial)
    }

    @Test
    fun `clear empties the transcript and the partial`() {
        val s = dictation.cleared(listening("Some text.").feed(SttEvent.Partial("more")))
        assertEquals("", s.transcript)
        assertEquals("", s.partial)
        assertEquals(DictationPhase.LISTENING, s.phase)
    }

    @Test
    fun `level is clamped to the meter's -60 to 0 dBFS scale`() {
        fun level(db: Float) = listening().feed(SttEvent.Level(db)).level
        assertEquals(0f, level(-100f), 0f) // NearSilence.FLOOR_DB
        assertEquals(0f, level(-60f), 0f)
        assertEquals(0.5f, level(-30f), 1e-6f)
        assertEquals(1f, level(0f), 0f)
        assertEquals(1f, level(6f), 0f)
        assertEquals(0f, level(Float.NaN), 0f)
        assertEquals(0f, level(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun `the partial is separated from the transcript only when needed`() {
        assertEquals("", Dictation.partialSeparator(""))
        assertEquals(" ", Dictation.partialSeparator("Hello."))
        assertEquals("", Dictation.partialSeparator("Line one.\n"))
    }

    @Test
    fun `Dictate never stops on its own - each pause's Final adds text and listening goes on`() {
        // The Dictate screen always runs Pause (#43, SpeechPrefs.endFor(DICTATE)); only the mic tap stops it.
        var s = listening().feed(
            SttEvent.SpeechStarted,
            SttEvent.Partial("the man walked over the bridge"),
            SttEvent.Final("the man walked over the bridge.", nearSilent = false),
        )
        assertEquals("The man walked over the bridge.", s.transcript)
        assertEquals(DictationPhase.LISTENING, s.phase)
        s = s.feed(SttEvent.SpeechStarted, SttEvent.Final("then he sat down.", nearSilent = false))
        assertEquals("The man walked over the bridge. Then he sat down.", s.transcript)
        assertEquals(DictationPhase.LISTENING, s.phase)
        // Even an end word would only finalise the text before it.
        s = s.feed(SttEvent.SpeechStarted, SttEvent.Final("", nearSilent = true, endWord = true))
        assertEquals(DictationPhase.LISTENING, s.phase)
    }
}
