package app.marmalade.stt.ime

import app.marmalade.stt.speech.EndOfDictation
import app.marmalade.stt.speech.SttErrorKind
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.ui.screen.DictationNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceImeTest {

    private fun VoiceImeState.feed(vararg events: SttEvent): VoiceImeState =
        events.fold(this) { state, event -> VoiceIme.reduce(state, event) }

    private fun listening(): VoiceImeState =
        VoiceIme.started(VoiceImeState(), modelLoaded = true).feed(SttEvent.Ready)

    @Test
    fun `a session loads, hears speech, shows the partial and drops it on the final`() {
        var s = VoiceIme.started(VoiceImeState(), modelLoaded = false)
        assertEquals(VoiceImePhase.LOADING, s.phase)
        s = s.feed(SttEvent.Ready)
        assertEquals(VoiceImePhase.LISTENING, s.phase)
        s = s.feed(SttEvent.SpeechStarted, SttEvent.Partial("hello wor"))
        assertEquals(VoiceImePhase.HEARING, s.phase)
        assertEquals("hello wor", s.partial)

        s = s.feed(SttEvent.Final("hello world.", nearSilent = false))
        assertEquals("", s.partial)
        assertEquals(VoiceImePhase.LISTENING, s.phase)
    }

    @Test
    fun `the mic opening ends Loading model, and Ready after it changes nothing (#78)`() {
        var s = VoiceIme.started(VoiceImeState(), modelLoaded = false)
        s = s.feed(SttEvent.Listening)
        assertEquals(VoiceImePhase.LISTENING, s.phase)
        s = s.feed(SttEvent.Level(-20f))
        assertTrue("the meter moves while the model loads", s.level > 0f)
        assertEquals(VoiceImePhase.LISTENING, s.feed(SttEvent.Ready).phase)

        // Pause tapped while the model loads: Ready doesn't undo "Finishing".
        s = VoiceIme.stopping(s, exitAfter = false).feed(SttEvent.Ready)
        assertEquals(VoiceImePhase.FINISHING, s.phase)
    }

    @Test
    fun `an empty partial clears the shown one`() {
        // VoiceInputService cleans partials first; a loop or noise caption can leave "".
        val s = listening().feed(SttEvent.SpeechStarted, SttEvent.Partial("hello"), SttEvent.Partial(""))
        assertEquals("", s.partial)
        assertEquals(VoiceImePhase.HEARING, s.phase)
    }

    @Test
    fun `a loaded model skips the loading phase`() {
        assertEquals(VoiceImePhase.LISTENING, VoiceIme.started(VoiceImeState(), modelLoaded = true).phase)
    }

    @Test
    fun `the level maps dBFS onto the meter`() {
        assertEquals(0.5f, listening().feed(SttEvent.Level(-30f)).level, 1e-6f)
    }

    @Test
    fun `Done while listening finishes, then switches back`() {
        var s = VoiceIme.stopping(listening().feed(SttEvent.SpeechStarted), exitAfter = true)
        assertEquals(VoiceImePhase.FINISHING, s.phase)
        assertTrue(s.exitWhenDone)
        // A late SpeechStarted doesn't undo finishing.
        s = s.feed(SttEvent.SpeechStarted, SttEvent.Final("hi", false))
        assertEquals(VoiceImePhase.FINISHING, s.phase)

        val (ended, switchBack) = VoiceIme.ended(s)
        assertTrue(switchBack)
        assertEquals(VoiceImePhase.IDLE, ended.phase)
        assertFalse(ended.exitWhenDone)
    }

    @Test
    fun `Done after pause still switches back`() {
        var s = VoiceIme.stopping(listening(), exitAfter = false)
        s = VoiceIme.stopping(s, exitAfter = true)
        assertTrue(VoiceIme.ended(s).second)
    }

    @Test
    fun `pause stays on the panel`() {
        val s = VoiceIme.stopping(listening(), exitAfter = false)
        assertEquals(VoiceImePhase.FINISHING, s.phase)
        val (ended, switchBack) = VoiceIme.ended(s.feed(SttEvent.NoSpeech))
        assertFalse(switchBack)
        assertEquals(VoiceImePhase.IDLE, ended.phase)
        assertEquals(DictationNotice.NO_SPEECH, ended.notice)
    }

    @Test
    fun `stopping while idle changes nothing`() {
        val idle = VoiceImeState()
        assertEquals(idle, VoiceIme.stopping(idle, exitAfter = true))
    }

    @Test
    fun `an error shows a notice and never switches back, even after Done`() {
        var s = VoiceIme.stopping(listening().feed(SttEvent.Partial("hel")), exitAfter = true)
        s = s.feed(SttEvent.Error(SttErrorKind.MIC, "mic gone"))
        assertEquals(VoiceImePhase.IDLE, s.phase)
        assertEquals("", s.partial)
        assertEquals(DictationNotice.MIC_ERROR, s.notice)

        val (ended, switchBack) = VoiceIme.ended(s)
        assertFalse(switchBack)
        assertEquals(DictationNotice.MIC_ERROR, ended.notice)
    }

    @Test
    fun `error kinds map to notices`() {
        assertEquals(DictationNotice.MODEL_ERROR, listening().feed(SttEvent.Error(SttErrorKind.MODEL, "")).notice)
        assertEquals(
            DictationNotice.RECOGNITION_ERROR,
            listening().feed(SttEvent.Error(SttErrorKind.RECOGNITION, "")).notice,
        )
        assertEquals(DictationNotice.BUSY, listening().feed(SttEvent.Error(SttErrorKind.BUSY, "")).notice)
    }

    @Test
    fun `starting again clears the last notice`() {
        val failed = listening().feed(SttEvent.Error(SttErrorKind.BUSY, "busy"))
        assertNull(VoiceIme.started(failed, modelLoaded = true).notice)
    }

    @Test
    fun `cancel drops the partial and the pending switch back`() {
        val s = VoiceIme.cancelled(
            VoiceIme.stopping(listening().feed(SttEvent.Partial("hello"), SttEvent.Level(-10f)), exitAfter = true),
        )
        assertEquals(VoiceImePhase.IDLE, s.phase)
        assertEquals("", s.partial)
        assertEquals(0f, s.level)
        assertFalse(VoiceIme.ended(s).second)
    }

    @Test
    fun `a blocker is set and cleared`() {
        val blocked = VoiceIme.blocked(VoiceImeState(), VoiceImeBlocker.NO_MIC_PERMISSION)
        assertEquals(VoiceImeBlocker.NO_MIC_PERMISSION, blocked.blocker)
        assertNull(VoiceIme.blocked(blocked, null).blocker)
    }

    // ---- End of speech and the lock (#43) --------------------------------------

    private val autoModes = listOf(EndOfDictation.SMART_TURN, EndOfDictation.PAUSE, EndOfDictation.END_WORD)

    private fun listening(end: EndOfDictation, locked: Boolean = false): VoiceImeState =
        VoiceIme.configured(VoiceIme.started(VoiceImeState(), modelLoaded = true, locked = locked), end).feed(SttEvent.Ready)

    private val final = SttEvent.Final("send the report.", nearSilent = false)

    @Test
    fun `auto modes - the first committed Final acts like Done, and the session switches back when it ends`() {
        for (end in autoModes) {
            val s = listening(end).feed(SttEvent.SpeechStarted, final)
            assertTrue(end.name, s.autoStop)
            assertTrue(end.name, VoiceIme.actsAsDone(s, final, committed = true))
            assertTrue(end.name, VoiceIme.idlePauses(s))
            // The service's Done path: stopping with exitAfter, then the flow ends.
            val done = VoiceIme.stopping(s, exitAfter = true)
            assertEquals(VoiceImePhase.FINISHING, done.phase)
            assertTrue(VoiceIme.ended(done).second)
        }
    }

    @Test
    fun `auto modes - a Final the cleanup dropped doesn't end listening, an end word does even when empty`() {
        val s = listening(EndOfDictation.PAUSE).feed(SttEvent.SpeechStarted, final)
        assertFalse(VoiceIme.actsAsDone(s, final, committed = false))
        val emptyEndWord = SttEvent.Final("", nearSilent = true, endWord = true)
        assertTrue(VoiceIme.actsAsDone(listening(EndOfDictation.END_WORD), emptyEndWord, committed = false))
        assertFalse(VoiceIme.actsAsDone(s, SttEvent.NoSpeech, committed = false))
    }

    @Test
    fun `auto modes - the Final a mic tap flushes lands, and the panel stays`() {
        val tapped = VoiceIme.stopping(listening(EndOfDictation.SMART_TURN).feed(SttEvent.SpeechStarted), exitAfter = false)
        val s = tapped.feed(final)
        assertFalse(VoiceIme.actsAsDone(s, final, committed = true))
        assertFalse(VoiceIme.ended(s).second)
    }

    @Test
    fun `Tap to stop - no Final acts like Done, the idle pause is off and a long press doesn't lock`() {
        val s = listening(EndOfDictation.TAP_STOP).feed(SttEvent.SpeechStarted, final.copy(endWord = true))
        assertFalse(s.autoStop)
        assertFalse(VoiceIme.actsAsDone(s, final, committed = true))
        assertFalse(VoiceIme.idlePauses(s))
        assertFalse(s.canLock) // the panel makes a long press a tap
        assertEquals(s, VoiceIme.locking(s))
        // Started by a long press: Tap to stop drops the lock.
        assertFalse(listening(EndOfDictation.TAP_STOP, locked = true).locked)
        // The Final itself is still handled: the partial goes, listening goes on.
        assertEquals(VoiceImePhase.LISTENING, s.phase)
        assertEquals("", s.partial)
    }

    @Test
    fun `lock - a long press in an auto mode locks, Finals commit but never act as Done, no idle pause`() {
        for (end in autoModes) {
            val running = listening(end).feed(SttEvent.SpeechStarted)
            assertTrue(running.canLock)
            val locked = VoiceIme.locking(running)
            assertTrue(end.name, locked.locked)
            assertFalse(locked.canLock)
            val after = locked.feed(final)
            assertEquals(VoiceImePhase.LISTENING, after.phase)
            assertTrue(after.locked)
            assertFalse(end.name, VoiceIme.actsAsDone(after, final, committed = true))
            assertFalse(end.name, VoiceIme.actsAsDone(after, final.copy(endWord = true), committed = true))
            assertFalse(end.name, VoiceIme.idlePauses(after))
        }
    }

    @Test
    fun `lock - a tap unlocks and stops, the Final lands and the panel stays`() {
        val locked = VoiceIme.locking(listening(EndOfDictation.SMART_TURN).feed(SttEvent.SpeechStarted))
        val tapped = VoiceIme.stopping(locked, exitAfter = false)
        assertFalse(tapped.locked)
        assertEquals(VoiceImePhase.FINISHING, tapped.phase)
        val s = tapped.feed(final)
        assertFalse(VoiceIme.actsAsDone(s, final, committed = true))
        val (idle, switchBack) = VoiceIme.ended(s)
        assertFalse(switchBack)
        assertFalse(idle.locked)
        assertEquals(VoiceImePhase.IDLE, idle.phase)
    }

    @Test
    fun `lock - a long press while idle starts listening locked, and Done, cancel and errors drop the lock`() {
        val idle = VoiceImeState()
        assertTrue(idle.canLock)
        assertFalse(VoiceImeState(blocker = VoiceImeBlocker.NO_MODEL).canLock)
        // Locking needs a session: the service starts one instead.
        assertEquals(idle, VoiceIme.locking(idle))
        val s = listening(EndOfDictation.PAUSE, locked = true)
        assertTrue(s.locked)
        // Done still switches back.
        val done = VoiceIme.stopping(s, exitAfter = true)
        assertFalse(done.locked)
        assertTrue(VoiceIme.ended(done).second)
        assertFalse(VoiceIme.cancelled(s).locked)
        assertFalse(s.feed(SttEvent.Error(SttErrorKind.MIC, "gone")).locked)
        // Nothing to lock while finishing.
        assertFalse(VoiceIme.stopping(listening(EndOfDictation.PAUSE), exitAfter = false).canLock)
    }
}
