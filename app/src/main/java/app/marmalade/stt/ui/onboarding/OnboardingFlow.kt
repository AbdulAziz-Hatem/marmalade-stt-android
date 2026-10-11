package app.marmalade.stt.ui.onboarding

import app.marmalade.stt.install.InstallState

// Pure step logic for the setup guide: which steps exist, where Next / Skip /
// Back go, which button is the step's accent, and the one step that can't be
// skipped (the speech model, #74). No Android types, so every
// transition is unit-tested (OnboardingFlowTest); OnboardingViewModel only
// feeds in what it observes and stores the result.

/** The setup guide's steps, in order. */
enum class OnboardingStep {
    /** What the app is, and the privacy promise. */
    WELCOME,

    /** RECORD_AUDIO. */
    MICROPHONE,

    /**
     * Download the engine recommended for this phone (#27). Required (#74):
     * the steps after it try dictation, which needs an engine.
     */
    MODEL,

    /** Turn on the Marmalade voice keyboard in Android's keyboard settings. */
    KEYBOARD,

    /**
     * The keyboards' mic keys (#61): advice per enabled keyboard, other
     * voice keyboards to turn off, FUTO Keyboard's own setting, and a box
     * to try it in.
     */
    MIC_KEY,

    /** Pick Marmalade STT as Android's speech recognition service. Only where Android has that setting. */
    SPEECH_SERVICE,

    /** All set: "Try it" opens Dictate. */
    DONE,
}

/**
 * The recommended engine's install state, reduced to what the guide needs.
 * [NEEDS_ATTENTION] covers paused, failed and damaged downloads: the card's
 * action row offers the way forward for each.
 */
enum class ModelStatus { NOT_INSTALLED, DOWNLOADING, INSTALLED, NEEDS_ATTENTION }

/** [InstallState] → [ModelStatus]. */
fun modelStatusOf(state: InstallState): ModelStatus = when (state) {
    InstallState.NotInstalled -> ModelStatus.NOT_INSTALLED
    is InstallState.Downloading -> ModelStatus.DOWNLOADING
    InstallState.Installed -> ModelStatus.INSTALLED
    is InstallState.Paused, is InstallState.Failed, is InstallState.Corrupt -> ModelStatus.NEEDS_ATTENTION
}

/** The label of the forward button at the bottom of a step. */
enum class AdvanceLabel {
    /** Welcome. */
    GET_STARTED,

    /** The step's task is done (or can't be checked), so moving on is the natural next action. */
    NEXT,

    /** The step's task isn't done; the step's own button is the main action. Never on [OnboardingStep.MODEL]. */
    SKIP,

    /** Done: finish and open Dictate. */
    TRY_IT,
}

/**
 * Where the guide stands.
 *
 * @property futoInstalled whether a FUTO Keyboard package is installed; adds
 *   FUTO's card to the [OnboardingStep.MIC_KEY] step.
 * @property futo FUTO's answer about its voice engine; null until the
 *   `check` round trip has come back.
 * @property otherVoiceKeyboards labels from [Keyboards.otherVoiceKeyboards].
 * @property keyboards the enabled keyboards to advise on ([Keyboards.toGuide]).
 * @property speechServiceSupported whether Android has a speech recognition
 *   service setting; adds the [OnboardingStep.SPEECH_SERVICE] step.
 * @property speechServiceSelected whether that setting names Marmalade STT.
 * @property model the recommended engine's status; [ModelStatus.NOT_INSTALLED]
 *   while the phone is still being checked.
 * @property anyEngineInstalled whether some engine, the recommended one or
 *   another, is installed: someone re-running the guide with an engine
 *   already has what the step sets up.
 */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val futoInstalled: Boolean = false,
    val micGranted: Boolean = false,
    val model: ModelStatus = ModelStatus.NOT_INSTALLED,
    val anyEngineInstalled: Boolean = false,
    val keyboardEnabled: Boolean = false,
    val futo: FutoStatus? = null,
    val otherVoiceKeyboards: List<String> = emptyList(),
    val keyboards: List<GuidedKeyboard> = emptyList(),
    val speechServiceSupported: Boolean = false,
    val speechServiceSelected: Boolean = false,
) {
    val steps: List<OnboardingStep> get() = stepsFor(speechServiceSupported)

    /** 0-based position of [step] in [steps]. */
    val index: Int get() = steps.indexOf(step).coerceAtLeast(0)

    val isFirst: Boolean get() = index == 0

    /** An engine is installed, so dictation can work. The Done step's "Speech model: Ready". */
    val modelReady: Boolean get() = model == ModelStatus.INSTALLED || anyEngineInstalled

    /** The recommended engine is installed or on its way. */
    private val recommendedStarted: Boolean
        get() = model == ModelStatus.INSTALLED || model == ModelStatus.DOWNLOADING

    /**
     * Whether the step's task is finished, or far enough along that moving
     * on is the natural thing: when FUTO can't tell us its setting
     * ([FutoStatus.UNKNOWN]) the manual instructions are all there is to do.
     * The model step is done once an engine is installed, not while one is
     * downloading (#74). The mic-key step is done once no other voice
     * keyboard is on (they would win the mic keys) and FUTO, if installed,
     * is settled.
     */
    val stepDone: Boolean
        get() = when (step) {
            OnboardingStep.WELCOME, OnboardingStep.DONE -> true
            OnboardingStep.MICROPHONE -> micGranted
            OnboardingStep.MODEL -> modelReady
            OnboardingStep.KEYBOARD -> keyboardEnabled
            OnboardingStep.MIC_KEY ->
                otherVoiceKeyboards.isEmpty() && (!futoInstalled || futo == FutoStatus.SELECTED || futo == FutoStatus.UNKNOWN)
            OnboardingStep.SPEECH_SERVICE -> speechServiceSelected
        }

    /**
     * Whether everything the guide sets up is finished: the Done step says
     * "all set" only then, and lists what's still open otherwise. A running
     * download or an unanswerable FUTO don't count as finished here.
     */
    val allDone: Boolean
        get() = micGranted &&
            modelReady &&
            keyboardEnabled &&
            otherVoiceKeyboards.isEmpty() &&
            (!futoInstalled || futo == FutoStatus.SELECTED) &&
            (!speechServiceSupported || speechServiceSelected)

    /**
     * Whether the forward button is the step's accent button. When it isn't,
     * the step's own action (Allow, Download, Open settings…) is, and the
     * forward button is a quiet "Skip" (on the model step, a greyed-out
     * "Next") — one accent per screen. With another engine installed the
     * model step is done, but its Download is still offered, so the forward
     * button is a quiet "Next".
     */
    val advanceIsPrimary: Boolean
        get() = stepDone && !(step == OnboardingStep.MODEL && !recommendedStarted)

    /** The model step's Next is "Next", greyed out until an engine is installed ([canAdvance]). */
    val advanceLabel: AdvanceLabel
        get() = when {
            step == OnboardingStep.WELCOME -> AdvanceLabel.GET_STARTED
            step == OnboardingStep.DONE -> AdvanceLabel.TRY_IT
            stepDone || step == OnboardingStep.MODEL -> AdvanceLabel.NEXT
            else -> AdvanceLabel.SKIP
        }

    /**
     * Whether the forward button works. Only the model step holds the user
     * back, until an engine is installed (#74, Max: the next steps try
     * dictation, which can't work without one). Back and leaving the guide
     * always work.
     */
    val canAdvance: Boolean
        get() = step != OnboardingStep.MODEL || modelReady

    /** Next and Skip both move one step on; nothing while [canAdvance] is false. Stays put on [OnboardingStep.DONE]. */
    fun next(): OnboardingState =
        if (canAdvance) copy(step = steps.getOrElse(index + 1) { OnboardingStep.DONE }) else this

    /**
     * One step back, or null on the first step: the caller leaves the guide
     * (the app on first run, back to Settings when re-opened from there).
     */
    fun back(): OnboardingState? = if (isFirst) null else copy(step = steps[index - 1])

    /** FUTO Keyboard was installed or removed. Its old answer goes with it. */
    fun withFutoInstalled(installed: Boolean): OnboardingState = when {
        installed == futoInstalled -> this
        else -> copy(futoInstalled = installed, futo = null)
    }

    /**
     * Back on [step] after a restart. A step that isn't shown on this phone
     * lands on [OnboardingStep.DONE]. A step past the model step lands on
     * the model step while no engine is installed: one saved by a build
     * that let the user skip it, or an engine removed since (#74).
     */
    fun resumedAt(step: OnboardingStep): OnboardingState {
        val target = if (step in steps) step else OnboardingStep.DONE
        val pastModel = steps.indexOf(target) > steps.indexOf(OnboardingStep.MODEL)
        return copy(step = if (pastModel && !modelReady) OnboardingStep.MODEL else target)
    }

    companion object {
        /**
         * The step called [name] (an [OnboardingStep] name), or null for none
         * or an unknown one. `FUTO`, saved by builds before #61, is now part
         * of [OnboardingStep.MIC_KEY].
         */
        fun stepNamed(name: String?): OnboardingStep? =
            if (name == LEGACY_FUTO_STEP) OnboardingStep.MIC_KEY else OnboardingStep.entries.firstOrNull { it.name == name }

        private const val LEGACY_FUTO_STEP = "FUTO"

        /** The steps shown, in order. The speech service step only where Android has that setting. */
        fun stepsFor(speechServiceSupported: Boolean): List<OnboardingStep> =
            OnboardingStep.entries.filter { it != OnboardingStep.SPEECH_SERVICE || speechServiceSupported }
    }
}
