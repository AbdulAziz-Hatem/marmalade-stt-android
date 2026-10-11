package app.marmalade.stt.ui.onboarding

import app.marmalade.stt.install.CorruptProblem
import app.marmalade.stt.install.InstallFailure
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.ui.onboarding.OnboardingStep.DONE
import app.marmalade.stt.ui.onboarding.OnboardingStep.KEYBOARD
import app.marmalade.stt.ui.onboarding.OnboardingStep.MICROPHONE
import app.marmalade.stt.ui.onboarding.OnboardingStep.MIC_KEY
import app.marmalade.stt.ui.onboarding.OnboardingStep.MODEL
import app.marmalade.stt.ui.onboarding.OnboardingStep.SPEECH_SERVICE
import app.marmalade.stt.ui.onboarding.OnboardingStep.WELCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    @Test
    fun `mic key step always, speech service step only where Android has the setting`() {
        assertEquals(listOf(WELCOME, MICROPHONE, MODEL, KEYBOARD, MIC_KEY, DONE), OnboardingState.stepsFor(false))
        assertEquals(
            listOf(WELCOME, MICROPHONE, MODEL, KEYBOARD, MIC_KEY, SPEECH_SERVICE, DONE),
            OnboardingState.stepsFor(true),
        )
    }

    @Test
    fun `next walks every step in order and stops at Done`() {
        for (speech in listOf(false, true)) {
            var state = OnboardingState(speechServiceSupported = speech, model = ModelStatus.INSTALLED)
            val visited = mutableListOf(state.step)
            repeat(10) {
                state = state.next()
                if (state.step != visited.last()) visited += state.step
            }
            assertEquals(OnboardingState.stepsFor(speech), visited)
            assertEquals(DONE, state.step)
        }
    }

    @Test
    fun `next skips the speech service step where Android has no such setting`() {
        assertEquals(DONE, OnboardingState(step = MIC_KEY, speechServiceSupported = false).next().step)
        assertEquals(SPEECH_SERVICE, OnboardingState(step = MIC_KEY, speechServiceSupported = true).next().step)
    }

    @Test
    fun `back goes one step back, and is null on the first step`() {
        assertNull(OnboardingState(step = WELCOME).back())
        assertEquals(WELCOME, OnboardingState(step = MICROPHONE).back()?.step)
        assertEquals(MIC_KEY, OnboardingState(step = DONE, speechServiceSupported = false).back()?.step)
        assertEquals(SPEECH_SERVICE, OnboardingState(step = DONE, speechServiceSupported = true).back()?.step)
    }

    @Test
    fun `back and next keep the per-step status`() {
        val state = OnboardingState(step = MODEL, micGranted = true, model = ModelStatus.DOWNLOADING)
        val round = state.back()!!.next()
        assertEquals(state, round)
    }

    @Test
    fun `next stops at the model step until an engine is installed`() {
        for (model in listOf(ModelStatus.NOT_INSTALLED, ModelStatus.DOWNLOADING, ModelStatus.NEEDS_ATTENTION)) {
            val state = OnboardingState(step = MODEL, model = model)
            assertFalse(state.canAdvance)
            assertSame(state, state.next())
            // Back and leaving the guide still work.
            assertEquals(MICROPHONE, state.back()?.step)
        }
        assertEquals(KEYBOARD, OnboardingState(step = MODEL, model = ModelStatus.INSTALLED).next().step)
        assertEquals(KEYBOARD, OnboardingState(step = MODEL, anyEngineInstalled = true).next().step)
    }

    @Test
    fun `every other step can be skipped`() {
        for (step in OnboardingState.stepsFor(true) - MODEL) {
            assertTrue(step.name, OnboardingState(step = step, speechServiceSupported = true).canAdvance)
        }
    }

    @Test
    fun `index and first step follow the visible steps`() {
        assertTrue(OnboardingState(step = WELCOME).isFirst)
        assertFalse(OnboardingState(step = MICROPHONE).isFirst)
        assertEquals(5, OnboardingState(step = DONE, speechServiceSupported = false).index)
        assertEquals(6, OnboardingState(step = DONE, speechServiceSupported = true).index)
    }

    @Test
    fun `removing FUTO keeps the step and forgets its status`() {
        val onMicKey = OnboardingState(step = MIC_KEY, futoInstalled = true, futo = FutoStatus.SELECTED)
        val after = onMicKey.withFutoInstalled(false)
        assertEquals(MIC_KEY, after.step)
        assertFalse(after.futoInstalled)
        assertNull(after.futo)
    }

    @Test
    fun `installing FUTO keeps the step list`() {
        val onKeyboard = OnboardingState(step = KEYBOARD, futoInstalled = false)
        val installed = onKeyboard.withFutoInstalled(true)
        assertTrue(installed.futoInstalled)
        assertEquals(onKeyboard.steps, installed.steps)
        assertSame(onKeyboard, onKeyboard.withFutoInstalled(false))
    }

    @Test
    fun `welcome and done always lead with their own labels`() {
        assertEquals(AdvanceLabel.GET_STARTED, OnboardingState(step = WELCOME).advanceLabel)
        assertEquals(AdvanceLabel.TRY_IT, OnboardingState(step = DONE).advanceLabel)
        assertTrue(OnboardingState(step = WELCOME).advanceIsPrimary)
        assertTrue(OnboardingState(step = DONE).advanceIsPrimary)
    }

    @Test
    fun `microphone step - Skip until granted, then Next is the accent`() {
        val denied = OnboardingState(step = MICROPHONE, micGranted = false)
        assertEquals(AdvanceLabel.SKIP, denied.advanceLabel)
        assertFalse(denied.advanceIsPrimary)

        val granted = denied.copy(micGranted = true)
        assertEquals(AdvanceLabel.NEXT, granted.advanceLabel)
        assertTrue(granted.advanceIsPrimary)
    }

    @Test
    fun `model step - no Skip, and Next only once the engine is installed`() {
        fun on(model: ModelStatus) = OnboardingState(step = MODEL, model = model)
        for (model in ModelStatus.entries) assertEquals(AdvanceLabel.NEXT, on(model).advanceLabel)
        for (model in listOf(ModelStatus.NOT_INSTALLED, ModelStatus.DOWNLOADING, ModelStatus.NEEDS_ATTENTION)) {
            assertFalse(on(model).stepDone)
            assertFalse(on(model).advanceIsPrimary)
        }
        assertTrue(on(ModelStatus.INSTALLED).canAdvance)
        assertTrue(on(ModelStatus.INSTALLED).advanceIsPrimary)
    }

    @Test
    fun `model step - another engine already installed is enough, but Download stays the accent`() {
        fun on(model: ModelStatus) = OnboardingState(step = MODEL, model = model, anyEngineInstalled = true)
        for (model in listOf(ModelStatus.NOT_INSTALLED, ModelStatus.NEEDS_ATTENTION)) {
            assertTrue(on(model).stepDone)
            assertEquals(AdvanceLabel.NEXT, on(model).advanceLabel)
            // The recommended engine's Download / Resume is the one accent.
            assertFalse(on(model).advanceIsPrimary)
        }
        assertTrue(on(ModelStatus.DOWNLOADING).advanceIsPrimary)
        assertTrue(on(ModelStatus.INSTALLED).advanceIsPrimary)
    }

    @Test
    fun `the speech model is ready with the recommended engine or any other installed`() {
        assertTrue(OnboardingState(model = ModelStatus.INSTALLED).modelReady)
        assertTrue(OnboardingState(model = ModelStatus.NOT_INSTALLED, anyEngineInstalled = true).modelReady)
        assertTrue(OnboardingState(model = ModelStatus.DOWNLOADING, anyEngineInstalled = true).modelReady)
        assertFalse(OnboardingState(model = ModelStatus.DOWNLOADING).modelReady)
        assertFalse(OnboardingState(model = ModelStatus.NEEDS_ATTENTION).modelReady)
    }

    @Test
    fun `keyboard step follows the enabled state`() {
        assertEquals(AdvanceLabel.SKIP, OnboardingState(step = KEYBOARD, keyboardEnabled = false).advanceLabel)
        assertEquals(AdvanceLabel.NEXT, OnboardingState(step = KEYBOARD, keyboardEnabled = true).advanceLabel)
    }

    @Test
    fun `mic key step with FUTO - Next once selected, or when FUTO can't be asked`() {
        fun on(futo: FutoStatus?) = OnboardingState(step = MIC_KEY, futoInstalled = true, futo = futo)
        assertEquals(AdvanceLabel.SKIP, on(null).advanceLabel) // still checking
        assertEquals(AdvanceLabel.SKIP, on(FutoStatus.NOT_SELECTED).advanceLabel)
        assertEquals(AdvanceLabel.NEXT, on(FutoStatus.SELECTED).advanceLabel)
        // Only the manual steps are left, and we can't see whether they were done.
        assertEquals(AdvanceLabel.NEXT, on(FutoStatus.UNKNOWN).advanceLabel)
    }

    @Test
    fun `mic key step - Skip while another voice keyboard is on`() {
        val plain = OnboardingState(step = MIC_KEY)
        assertEquals(AdvanceLabel.NEXT, plain.advanceLabel)
        assertTrue(plain.advanceIsPrimary)
        val shadowed = plain.copy(otherVoiceKeyboards = listOf("Google voice typing"))
        assertEquals(AdvanceLabel.SKIP, shadowed.advanceLabel)
        assertFalse(shadowed.advanceIsPrimary)
        assertEquals(
            AdvanceLabel.SKIP,
            shadowed.copy(futoInstalled = true, futo = FutoStatus.SELECTED).advanceLabel,
        )
    }

    @Test
    fun `speech service step follows the setting`() {
        val off = OnboardingState(step = SPEECH_SERVICE, speechServiceSupported = true)
        assertEquals(AdvanceLabel.SKIP, off.advanceLabel)
        assertEquals(AdvanceLabel.NEXT, off.copy(speechServiceSelected = true).advanceLabel)
    }

    @Test
    fun `all done needs every step finished, FUTO and the speech service only when present`() {
        val everything = OnboardingState(
            micGranted = true,
            model = ModelStatus.INSTALLED,
            keyboardEnabled = true,
        )
        assertTrue(everything.allDone)
        assertFalse(everything.copy(micGranted = false).allDone)
        assertFalse(everything.copy(model = ModelStatus.DOWNLOADING).allDone)
        assertTrue(everything.copy(model = ModelStatus.NOT_INSTALLED, anyEngineInstalled = true).allDone)
        assertFalse(everything.copy(keyboardEnabled = false).allDone)

        val withFuto = everything.copy(futoInstalled = true)
        assertFalse(withFuto.allDone)
        assertFalse(withFuto.copy(futo = FutoStatus.UNKNOWN).allDone)
        assertTrue(withFuto.copy(futo = FutoStatus.SELECTED).allDone)

        assertFalse(everything.copy(otherVoiceKeyboards = listOf("Google voice typing")).allDone)

        val withSpeech = everything.copy(speechServiceSupported = true)
        assertFalse(withSpeech.allDone)
        assertTrue(withSpeech.copy(speechServiceSelected = true).allDone)
        // Selected but no picker (can't happen) doesn't matter either way.
        assertTrue(everything.copy(speechServiceSelected = true).allDone)
    }

    @Test
    fun `install states reduce to the guide's model status`() {
        assertEquals(ModelStatus.NOT_INSTALLED, modelStatusOf(InstallState.NotInstalled))
        assertEquals(ModelStatus.DOWNLOADING, modelStatusOf(InstallState.Downloading(1L, 10L, "tokens.txt")))
        assertEquals(ModelStatus.INSTALLED, modelStatusOf(InstallState.Installed))
        assertEquals(ModelStatus.NEEDS_ATTENTION, modelStatusOf(InstallState.Paused(1L, 10L)))
        assertEquals(
            ModelStatus.NEEDS_ATTENTION,
            modelStatusOf(InstallState.Failed("net", InstallFailure.NETWORK, bytesOnDisk = 1L)),
        )
        assertEquals(ModelStatus.NEEDS_ATTENTION, modelStatusOf(InstallState.Corrupt(CorruptProblem.MISSING, "x")))
    }

    @Test
    fun `a restart resumes on the saved step`() {
        assertEquals(MODEL, OnboardingState().resumedAt(MODEL).step)
        assertEquals(
            SPEECH_SERVICE,
            OnboardingState(speechServiceSupported = true, anyEngineInstalled = true).resumedAt(SPEECH_SERVICE).step,
        )
    }

    @Test
    fun `a saved speech service step on a phone without the setting resumes on Done`() {
        val ready = OnboardingState(speechServiceSupported = false, anyEngineInstalled = true)
        assertEquals(DONE, ready.resumedAt(SPEECH_SERVICE).step)
    }

    @Test
    fun `a saved step past the model step resumes on it while no engine is installed`() {
        for (step in listOf(KEYBOARD, MIC_KEY, SPEECH_SERVICE, DONE)) {
            assertEquals(step.name, MODEL, OnboardingState(speechServiceSupported = true).resumedAt(step).step)
        }
        assertEquals(MICROPHONE, OnboardingState().resumedAt(MICROPHONE).step)
        assertEquals(KEYBOARD, OnboardingState(model = ModelStatus.INSTALLED).resumedAt(KEYBOARD).step)
    }

    @Test
    fun `saved step names parse, unknown ones are ignored`() {
        assertEquals(KEYBOARD, OnboardingState.stepNamed("KEYBOARD"))
        assertNull(OnboardingState.stepNamed(null))
        assertNull(OnboardingState.stepNamed("RENAMED_STEP"))
    }

    @Test
    fun `a FUTO step saved before #61 resumes on the mic key step`() {
        val step = OnboardingState.stepNamed("FUTO")
        assertEquals(MIC_KEY, step)
        assertEquals(MIC_KEY, OnboardingState(anyEngineInstalled = true).resumedAt(step!!).step)
    }
}
