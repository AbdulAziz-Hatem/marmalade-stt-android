package app.marmalade.stt.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.R
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SpeedTier
import app.marmalade.stt.system.SettingsLink
import app.marmalade.stt.system.SettingsLinks
import app.marmalade.stt.system.SystemSettings
import app.marmalade.stt.system.VoiceInputPath
import app.marmalade.stt.ui.components.JarMascot
import app.marmalade.stt.ui.components.JarMascotState
import app.marmalade.stt.ui.components.MarmaladeOutlinedButton
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.components.rememberAnnouncer
import app.marmalade.stt.ui.contentDirection
import app.marmalade.stt.ui.screen.DownloadConfirmDialog
import app.marmalade.stt.ui.screen.EngineAction
import app.marmalade.stt.ui.screen.downloadOutcomes
import app.marmalade.stt.ui.theme.accent
import app.marmalade.ui.components.MarmaladeCard
import app.marmalade.ui.components.WorkingDots
import app.marmalade.ui.theme.MarmaladeMotion
import app.marmalade.ui.theme.riseIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   AppRoot: Routes.Onboarding (first launch, or Settings → "Setup guide")
//     → OnboardingScreen(onFinished, onClose)
//     │
//     ├── state ◄── OnboardingViewModel.state (OnboardingState, pure step logic
//     │               in OnboardingFlow.kt)
//     │     step, steps (speech service step only where Android has the
//     │     setting), per-step status, advance label / accent
//     │     ON_RESUME → vm.refreshSystemState(): mic permission, enabled
//     │                 keyboards, installed FUTO packages, speech service
//     │
//     ├── Welcome     → what the app is + the PRIVACY.md promise
//     ├── Microphone  → permission launcher → vm.refreshSystemState()
//     │                 denied → rationale; blocked → app settings page
//     ├── Model       → vm.recommendation ◄── RecommendedEngine ◄──
//     │                   SpeedCalibrator.recommend() (CPU probe the first
//     │                   time: "Checking your phone…" meanwhile)
//     │                 → RecommendedEngineCard (C1): the engine, one line of
//     │                   why (recommendationText, RecommendedEngine.kt),
//     │                   Speed / Quality / Languages tiles (Speed ◄──
//     │                   vm.speechPrefs.speedTier), accent Download
//     │                 EngineStatus ◄── vm.modelState ◄── ModelInstaller.state
//     │                 Download → DownloadConfirmDialog → vm.download()
//     │                 → ModelDownloads (app-wide scope: keeps going after
//     │                   the guide closes); Installed while the guide is
//     │                   open → SpeechSettings.setSelectedEngine
//     │                 Required (#74): Next only once it or any other
//     │                 engine is installed. Other engines: the Engines tab.
//     ├── Keyboard    → SystemSettings KEYBOARDS (the keyboard list); enabled state
//     │                 re-read on resume (InputMethodManager)
//     ├── Mic key     → other voice keyboards on → turn them off (keyboard settings)
//     │                 one card per enabled keyboard (Keyboards.toGuide): its
//     │                 KeyboardKind's advice; a "Try it here" text field
//     │                 FUTO's card (when installed):
//     │                 on entering: VoiceInputSwitch mode=check ─┐
//     │                 "Use in FUTO": mode=popup → then check  ──┤ result code
//     │                 → vm.onFutoResult → FutoVoiceInput.resultFromCode
//     │                 ActivityNotFoundException → UNKNOWN → manual steps only
//     ├── Speech      → SystemSettings VOICE_INPUT: the page nearest the Voice
//     │   service       input picker (SettingsLinks); directions per landed page;
//     │                 selected state re-read on resume
//     └── Done        → "Try it" → vm.finish() (done flag on disk) → onFinished
//                       → AppRoot → Dictate, guide dropped from the back stack
//
//   Next / Skip → vm.next(); Back (button or system back) → vm.back(); on the
//   first step system back is left to the NavHost: it leaves the app on first
//   run and returns to Settings when opened from there. The model step has no
//   Skip: its Next is greyed out until an engine is installed (#74). Reaching
//   Done writes OnboardingSettings.done.
// -----------------------------------------------------------------------------

private const val TAG = "OnboardingScreen"

/** How long the jar listens after the mic is allowed before it settles. */
private const val MIC_PERK_MS = 4_000L

/**
 * The setup guide: microphone, speech model, voice keyboard, the keyboards'
 * mic keys, Android's speech recognition service. Every step but the speech
 * model can be skipped (#74).
 *
 * @param onFinished "Try it" on the last step: open Dictate.
 * @param onClose shown as a close button when there is somewhere to go back
 *   to (opened from Settings); null on first launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onClose: (() -> Unit)?,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    if (!ready) {
        // A few milliseconds while the saved step is read: the background
        // only, so an interrupted first run never flashes Welcome first.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val recommendation by viewModel.recommendation.collectAsStateWithLifecycle()
    val speechPrefs by viewModel.speechPrefs.collectAsStateWithLifecycle()
    val futoPackage by viewModel.futoPackage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val highContrast by viewModel.highContrast.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSystemState() }
    ResizeForKeyboard()

    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    // On the first step, back is the NavHost's: out of the app on first
    // launch, back to Settings otherwise.
    BackHandler(enabled = !state.isFirst) { viewModel.back() }

    // --- Microphone -----------------------------------------------------------
    var micDenied by rememberSaveable { mutableStateOf(false) }
    // Denied with "don't ask again" (or twice): the system won't show the
    // dialog any more, so the step sends the user to the app's settings page.
    var micBlocked by rememberSaveable { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshSystemState()
        micDenied = !granted
        micBlocked = !granted && context.findActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
        } ?: false
    }

    // --- FUTO Keyboard --------------------------------------------------------
    val futoCheck = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onFutoResult(FutoMode.CHECK, result.resultCode)
    }
    val futoPopup = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onFutoResult(FutoMode.POPUP, result.resultCode)
        // Confirm with FUTO what it ended up using.
        futoPackage?.let { launchFuto(context, futoCheck, it, FutoMode.CHECK, viewModel::onFutoUnavailable) }
    }
    // Ask FUTO each time its step opens: the user may have changed it meanwhile.
    val onFutoStep = state.step == OnboardingStep.MIC_KEY
    LaunchedEffect(onFutoStep, futoPackage) {
        val pkg = futoPackage
        if (onFutoStep && pkg != null) launchFuto(context, futoCheck, pkg, FutoMode.CHECK, viewModel::onFutoUnavailable)
    }

    var confirmDownload by rememberSaveable { mutableStateOf(false) }
    // "Try it" writes the done flag first; a second tap meanwhile must not navigate twice.
    var finishing by remember { mutableStateOf(false) }

    val stepOf = stringResource(R.string.onboarding_step_of, state.index + 1, state.steps.size)
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                // One line: the bar is a fixed 64 dp, so a wrapped title is
                // clipped. At large font sizes the centred title ("Step …")
                // gets little room beside Close, so it moves out of the bar,
                // above the progress bar (#68).
                title = {
                    if (!largeFont) {
                        Text(
                            text = stepOf,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.onboarding_close),
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        bottomBar = {
            StepNavBar(
                state = state,
                onBack = { viewModel.back() },
                onAdvance = {
                    if (state.step == OnboardingStep.DONE) {
                        if (!finishing) {
                            finishing = true
                            scope.launch {
                                viewModel.finish()
                                onFinished()
                            }
                        }
                    } else {
                        viewModel.next()
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Edge-to-edge: the window doesn't resize for the keyboard
        // (ResizeForKeyboard only stops Android panning it), so the page
        // shrinks itself and its scroll keeps the focused "Try it here" box
        // above it (#68). AppRoot's Scaffold pads the system bars without
        // consuming them, and this bottom bar sits under the keyboard too:
        // both are subtracted from the keyboard's height.
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(WindowInsets.systemBars)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            if (largeFont) {
                Text(
                    text = stepOf,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            // The title says "Step 2 of 6" in words; the bar just shows it,
            // gliding to each new step (#84).
            val progress by animateFloatAsState(
                targetValue = (state.index + 1f) / state.steps.size,
                animationSpec = MarmaladeMotion.slow(),
                label = "stepProgress",
            )
            LinearProgressIndicator(
                progress = { progress },
                // Toast in Marmalade's dark mode, as before the library (Max, #87).
                color = MaterialTheme.accent.progress,
                // The hairline ink: the default track (secondaryContainer) is
                // the page's own cream in light mode.
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clearAndSetSemantics {},
            )
            // Each step rises in (8 dp up and a fade) while the last one fades
            // out, the same either way (#84, TTS's #83).
            val stepEnter = riseIn()
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { (stepEnter togetherWith MarmaladeMotion.fadeExit).using(null) },
                label = "onboarding_step",
                modifier = Modifier.weight(1f),
            ) { step ->
                // This page's own position: while pages swap, the leaving one keeps its number.
                val stepOf = stringResource(R.string.onboarding_step_of, state.steps.indexOf(step) + 1, state.steps.size)
                // The leaving page lies over or under the new one while it
                // fades; a quick tap meant for the new page must not land on
                // the old one's Download or Allow (#84).
                val leaving = transition.targetState == EnterExitState.PostExit
                CompositionLocalProvider(LocalStepOf provides stepOf) {
                    Box(if (leaving) Modifier.fillMaxSize().blockTouches() else Modifier.fillMaxSize()) {
                        when (step) {
                            OnboardingStep.WELCOME -> WelcomeStep(highContrast == true, viewModel::setHighContrast)
                            OnboardingStep.MICROPHONE -> MicrophoneStep(
                                granted = state.micGranted,
                                denied = micDenied,
                                blocked = micBlocked,
                                onAllow = {
                                    if (micBlocked) {
                                        if (SystemSettings.open(context, SettingsLink.APP_DETAILS) == null) viewModel.show(R.string.onboarding_settings_failed)
                                    } else {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            )
                            OnboardingStep.MODEL -> ModelStep(
                                recommendation = recommendation,
                                installState = modelState,
                                speedTier = recommendation?.engine?.let { speechPrefs?.speedTier(it) },
                                onAction = { action ->
                                    when (action) {
                                        EngineAction.DOWNLOAD -> confirmDownload = true
                                        EngineAction.RESUME,
                                        EngineAction.RETRY,
                                        EngineAction.DOWNLOAD_AGAIN,
                                        -> viewModel.download()
                                        EngineAction.CANCEL -> viewModel.cancelDownload()
                                        EngineAction.REMOVE_DOWNLOAD -> viewModel.removeDownload()
                                        // Details-dialog actions; engineActions(onDetail = false) never offers them.
                                        EngineAction.CHECK_FILES, EngineAction.REMOVE -> Unit
                                    }
                                },
                            )
                            OnboardingStep.KEYBOARD -> KeyboardStep(
                                enabled = state.keyboardEnabled,
                                onOpenSettings = {
                                    if (SystemSettings.open(context, SettingsLink.KEYBOARDS) == null) viewModel.show(R.string.onboarding_settings_failed)
                                },
                            )
                            OnboardingStep.MIC_KEY -> MicKeyStep(
                                state = state,
                                onOpenKeyboardSettings = {
                                    if (SystemSettings.open(context, SettingsLink.KEYBOARDS) == null) viewModel.show(R.string.onboarding_settings_failed)
                                },
                                onUseMarmaladeInFuto = {
                                    futoPackage?.let {
                                        launchFuto(context, futoPopup, it, FutoMode.POPUP, viewModel::onFutoUnavailable)
                                    }
                                },
                                onCheckFutoAgain = {
                                    futoPackage?.let {
                                        launchFuto(context, futoCheck, it, FutoMode.CHECK, viewModel::onFutoUnavailable)
                                    }
                                },
                            )
                            OnboardingStep.SPEECH_SERVICE -> SpeechServiceStep(
                                selected = state.speechServiceSelected,
                                onOpenSettings = {
                                    if (SystemSettings.open(context, SettingsLink.VOICE_INPUT) == null) viewModel.show(R.string.onboarding_settings_failed)
                                },
                            )
                            OnboardingStep.DONE -> DoneStep(state = state)
                        }
                    }
                }
            }
        }
    }

    val recommended = recommendation
    if (confirmDownload && recommended != null) {
        DownloadConfirmDialog(
            engine = recommended.engine,
            onConfirm = {
                viewModel.download()
                confirmDownload = false
            },
            onDismiss = { confirmDownload = false },
        )
    }
}

// --- Frame ---------------------------------------------------------------------

/**
 * Keeps Android from panning the window for the keyboard while the guide is
 * shown. MainActivity sets no soft input mode, and Android picks "pan" for
 * it (`dumpsys window`: `sim={adjust=pan}`): a tall keyboard such as FUTO's
 * then slid the whole window up, the top bar under the status bar (#73,
 * #74). "Resize" stops the pan; edge-to-edge, the window itself still
 * doesn't resize, and the page's imePadding does the rest. Leaving the
 * guide sets "pan" back, the mode Android had picked; not a saved earlier
 * value, which a second guide on the back stack would have read as "resize"
 * (#74 review). Once a window has a mode, Android keeps it when handed
 * "unspecified", so it has to be set explicitly (checked on the emulator).
 */
@Suppress("DEPRECATION") // SOFT_INPUT_ADJUST_RESIZE still decides pan or not; insets do the rest
@Composable
private fun ResizeForKeyboard() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) }
    }
}

/** Swallows every touch before the content under it sees one. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** Back on the left; Next / Skip / Get started / Try it on the right. */
@Composable
private fun StepNavBar(state: OnboardingState, onBack: () -> Unit, onAdvance: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back stays in the bar on the first step, hidden, disabled and out of
        // the accessibility tree, rather than leaving and coming back as a new
        // node (#68). The page's pane title says where Back landed.
        MarmaladeTextButton(
            onClick = onBack,
            enabled = !state.isFirst,
            modifier = if (state.isFirst) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
        ) {
            Text(stringResource(R.string.onboarding_back))
        }
        Spacer(Modifier.weight(1f))
        // One button whose look switches between the accent and a quiet text
        // button (#68). Two buttons in an if/else are two nodes: TalkBack
        // focus fell off whenever the accent flipped (Get started → Skip,
        // Skip → Next on coming back from Settings).
        val primary = state.advanceIsPrimary
        Button(
            onClick = onAdvance,
            // Only the model step's Next waits, until an engine is installed (#74).
            enabled = state.canAdvance,
            colors = if (primary) accentButtonColors() else ButtonDefaults.textButtonColors(contentColor = MaterialTheme.accent.text),
            shape = if (primary) ButtonDefaults.shape else ButtonDefaults.textShape,
            elevation = if (primary) ButtonDefaults.buttonElevation() else null,
            contentPadding = if (primary) ButtonDefaults.ContentPadding else ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(stringResource(state.advanceLabel.labelRes()))
        }
    }
}

/**
 * "Step 2 of 6" for the page being composed. [StepPage] puts it in the page's
 * pane title, so TalkBack says where the user is on every page change (#68).
 */
private val LocalStepOf = staticCompositionLocalOf { "" }

/**
 * One step's page: mascot, title, explanation, then the step's own content.
 * Scrolls at large font sizes and in landscape.
 */
@Composable
private fun StepPage(
    mascot: JarMascotState,
    title: String,
    body: String,
    mascotSize: Dp = 96.dp,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val pane = stringResource(R.string.a11y_onboarding_step_pane, LocalStepOf.current, title)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = pane }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Decorative, like everywhere else: the text carries the meaning.
        JarMascot(state = mascot, size = mascotSize)
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        content()
    }
}

/**
 * Where a step stands, in words (a check icon adds to it, never replaces it).
 * Announced politely when it changes, e.g. on coming back from settings.
 */
@Composable
private fun StatusLine(text: String, done: Boolean) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Speaks [message] through TalkBack when it changes to a new non-null text
 * while the page is open; not on first composition, so entering a step
 * already done stays quiet (#68). Progress ticks repeat the same text and
 * aren't spoken again.
 */
@Composable
private fun AnnounceChange(message: String?) {
    val announce = rememberAnnouncer()
    var last by remember { mutableStateOf(message) }
    LaunchedEffect(message) {
        if (message != null && message != last) announce(message)
        last = message
    }
}

/**
 * Speaks the download of engine [name] starting, then stopping: ready, paused
 * or failed (#68). The Download button gives way to a status line that isn't
 * live, so the start would be silent, and the card's status line has no live
 * region so the end isn't said twice. The end follows the Engines tab's rules
 * ([downloadOutcomes]).
 */
@Composable
private fun AnnounceDownload(name: String, installState: InstallState) {
    val announce = rememberAnnouncer()
    val resources = LocalContext.current.resources
    var previous by remember { mutableStateOf(installState) }
    LaunchedEffect(installState) {
        val before = previous
        previous = installState
        val text = when {
            installState is InstallState.Downloading && before !is InstallState.Downloading -> R.string.a11y_download_started
            else -> downloadOutcomes(mapOf(name to before), mapOf(name to installState)).firstOrNull()?.second?.announcement
                ?: return@LaunchedEffect
        }
        announce(resources.getString(text, name))
    }
}

/**
 * The accent button, the one per screen: orange in light mode, the
 * library's dark primaryContainer in dark mode (Orange 800 with light ink
 * in Marmalade, a step quieter than the orange 400 primary).
 */
@Composable
internal fun accentButtonColors(): ButtonColors {
    val colors = MaterialTheme.colorScheme
    return if (colors.surface.luminance() < 0.5f) {
        ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)
    } else {
        ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
    }
}

// --- Steps ---------------------------------------------------------------------

@Composable
private fun WelcomeStep(highContrast: Boolean, onHighContrastChange: (Boolean) -> Unit) {
    StepPage(
        mascot = JarMascotState.IDLE,
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
        mascotSize = 144.dp,
    ) {
        MarmaladeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_privacy_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.onboarding_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // The High contrast preset in one tap, before the orange screens that
        // follow (#68). TalkBack says the switch's own "On" / "Off"; its label
        // already says what it is, so there is no announcement of our own.
        MarmaladeCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = highContrast, role = Role.Switch, onValueChange = onHighContrastChange)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_accessibility),
                    contentDescription = null,
                    tint = MaterialTheme.accent.ink,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_high_contrast),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_high_contrast_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = highContrast, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun MicrophoneStep(granted: Boolean, denied: Boolean, blocked: Boolean, onAllow: () -> Unit) {
    // The jar perks up when the mic is allowed, then settles: a listening jar
    // animates every frame, and the step may stay open for minutes (#63).
    var perked by remember { mutableStateOf(false) }
    // The result of the system dialog lands in nodes that weren't there
    // before (the status line, the denied note), which live regions miss.
    AnnounceChange(
        when {
            granted -> stringResource(R.string.onboarding_mic_granted)
            denied -> stringResource(if (blocked) R.string.onboarding_mic_blocked else R.string.onboarding_mic_denied)
            else -> null
        },
    )
    LaunchedEffect(granted) {
        if (granted) {
            perked = true
            delay(MIC_PERK_MS)
            perked = false
        }
    }
    StepPage(
        mascot = if (perked) JarMascotState.LISTENING else JarMascotState.IDLE,
        title = stringResource(R.string.onboarding_mic_title),
        body = stringResource(R.string.onboarding_mic_body),
    ) {
        if (granted) {
            StatusLine(text = stringResource(R.string.onboarding_mic_granted), done = true)
            return@StepPage
        }
        if (denied) {
            Text(
                text = stringResource(if (blocked) R.string.onboarding_mic_blocked else R.string.onboarding_mic_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onAllow, colors = accentButtonColors()) {
            Text(stringResource(if (blocked) R.string.onboarding_mic_open_settings else R.string.onboarding_mic_allow))
        }
    }
}

/**
 * The engine recommended for this phone: "Checking your phone…" while
 * [recommendation] is null, then its card ([RecommendedEngineCard], the
 * design lab's C1) with one line of why; [speedTier] is its speed here.
 */
@Composable
private fun ModelStep(
    recommendation: EngineRecommender.Recommendation?,
    installState: InstallState,
    speedTier: SpeedTier?,
    onAction: (EngineAction) -> Unit,
) {
    // "Checking your phone…" gives way to a new card: say what it found.
    AnnounceChange(recommendation?.let { stringResource(R.string.a11y_onboarding_recommended, it.engine.displayName) })
    recommendation?.let { AnnounceDownload(name = it.engine.displayName, installState = installState) }
    StepPage(
        mascot = if (installState is InstallState.Downloading) JarMascotState.THINKING else JarMascotState.IDLE,
        title = stringResource(R.string.onboarding_model_title),
        body = stringResource(R.string.onboarding_model_body),
    ) {
        if (recommendation == null) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Working dots, not a spinning ring (#84); "in progress" for
                // TalkBack as the ring was.
                WorkingDots()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_recommend_checking),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            return@StepPage
        }
        RecommendedEngineCard(
            recommendation = recommendation,
            installState = installState,
            speedTier = speedTier,
            onAction = onAction,
        )
        if (installState is InstallState.Downloading) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_model_downloading_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_recommend_other),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun KeyboardStep(enabled: Boolean, onOpenSettings: () -> Unit) {
    StepPage(
        mascot = JarMascotState.IDLE,
        title = stringResource(R.string.onboarding_keyboard_title),
        body = stringResource(R.string.onboarding_keyboard_body),
    ) {
        StatusLine(
            text = stringResource(if (enabled) R.string.onboarding_keyboard_on else R.string.onboarding_keyboard_off),
            done = enabled,
        )
        Spacer(Modifier.height(16.dp))
        if (enabled) {
            // Still reachable, to turn it off again or check.
            MarmaladeTextButton(onClick = onOpenSettings) { Text(stringResource(R.string.onboarding_keyboard_open)) }
        } else {
            Button(onClick = onOpenSettings, colors = accentButtonColors()) {
                Text(stringResource(R.string.onboarding_keyboard_open))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_keyboard_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_keyboard_unlock_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * How the user's keyboards reach Marmalade (#61): other voice keyboards to
 * turn off, one card per enabled keyboard with its kind's advice, FUTO's own
 * card, and a box to try the mic key in (typing in it also lets keyboards
 * without package visibility see Marmalade, until the next restart).
 */
@Composable
private fun MicKeyStep(
    state: OnboardingState,
    onOpenKeyboardSettings: () -> Unit,
    onUseMarmaladeInFuto: () -> Unit,
    onCheckFutoAgain: () -> Unit,
) {
    StepPage(
        mascot = JarMascotState.IDLE,
        title = stringResource(R.string.onboarding_mic_key_title),
        body = stringResource(R.string.onboarding_mic_key_body),
    ) {
        if (!state.keyboardEnabled) {
            StatusLine(text = stringResource(R.string.onboarding_mic_key_needs_voice_keyboard), done = false)
            Spacer(Modifier.height(16.dp))
        }
        if (state.otherVoiceKeyboards.isNotEmpty()) {
            val separator = stringResource(R.string.list_separator)
            val quoted = stringResource(R.string.quoted_name)
            InfoCard(
                title = stringResource(R.string.onboarding_mic_key_other_voice_title),
                body = stringResource(
                    R.string.onboarding_mic_key_other_voice,
                    state.otherVoiceKeyboards.joinToString(separator) { String.format(quoted, it) },
                ),
            ) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenKeyboardSettings, colors = accentButtonColors()) {
                    Text(stringResource(R.string.onboarding_keyboard_open))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        val futoLabel = stringResource(R.string.onboarding_kb_futo)
        // FUTO's card shows whenever FUTO Keyboard is installed, enabled or not.
        val cards = if (state.futoInstalled && state.keyboards.none { it.kind == KeyboardKind.FUTO }) {
            state.keyboards + GuidedKeyboard(futoLabel, KeyboardKind.FUTO, isDefault = false)
        } else {
            state.keyboards
        }
        cards.forEach { keyboard ->
            val title = if (keyboard.isDefault) {
                stringResource(R.string.onboarding_mic_key_current, keyboard.label)
            } else {
                keyboard.label
            }
            if (keyboard.kind == KeyboardKind.FUTO) {
                FutoCard(
                    title = title,
                    status = state.futo,
                    // One accent per screen: turning other voice keyboards off comes first.
                    accent = state.otherVoiceKeyboards.isEmpty(),
                    onUseMarmalade = onUseMarmaladeInFuto,
                    onCheckAgain = onCheckFutoAgain,
                )
            } else {
                InfoCard(title = title, body = stringResource(keyboard.kind.adviceRes()))
            }
            Spacer(Modifier.height(12.dp))
        }
        var tryText by rememberSaveable { mutableStateOf("") }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = tryText,
            onValueChange = { tryText = it },
            label = { Text(stringResource(R.string.onboarding_try_label)) },
            textStyle = LocalTextStyle.current.contentDirection(),
            supportingText = { Text(stringResource(R.string.onboarding_try_hint)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** FUTO Keyboard: what it uses for voice input, the one-tap switch, and the manual route. */
@Composable
private fun FutoCard(
    title: String,
    status: FutoStatus?,
    accent: Boolean,
    onUseMarmalade: () -> Unit,
    onCheckAgain: () -> Unit,
) {
    MarmaladeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardTitle(title)
            Spacer(Modifier.height(8.dp))
            if (status == null) {
                // One stop, like the model step's checking row (#68).
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkingDots()
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_futo_checking),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            } else if (status != FutoStatus.UNKNOWN) {
                // A FUTO that can't be asked gets just the manual route below, no status line (Max, 2026-10-10).
                StatusLine(text = stringResource(status.statusRes()), done = status == FutoStatus.SELECTED)
            }
            if (status == FutoStatus.NOT_SELECTED) {
                Spacer(Modifier.height(12.dp))
                if (accent) {
                    Button(onClick = onUseMarmalade, colors = accentButtonColors()) {
                        Text(stringResource(R.string.onboarding_futo_use))
                    }
                } else {
                    MarmaladeOutlinedButton(onClick = onUseMarmalade) { Text(stringResource(R.string.onboarding_futo_use)) }
                }
                MarmaladeTextButton(onClick = onCheckAgain) { Text(stringResource(R.string.onboarding_futo_check_again)) }
            }
            if (status != FutoStatus.SELECTED) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_futo_manual_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.onboarding_futo_manual_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Always shown: the crash comes after the other voice keyboard is off, when the warning is gone.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_futo_crash_once),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Android's speech recognition service: whether it is ours, and where to pick it. */
@Composable
private fun SpeechServiceStep(selected: Boolean, onOpenSettings: () -> Unit) {
    StepPage(
        mascot = JarMascotState.IDLE,
        title = stringResource(R.string.onboarding_speech_title),
        body = stringResource(R.string.onboarding_speech_body),
    ) {
        StatusLine(
            text = stringResource(if (selected) R.string.onboarding_speech_on else R.string.onboarding_speech_off),
            done = selected,
        )
        Spacer(Modifier.height(16.dp))
        if (selected) {
            MarmaladeTextButton(onClick = onOpenSettings) { Text(stringResource(R.string.onboarding_speech_open)) }
        } else {
            Button(onClick = onOpenSettings, colors = accentButtonColors()) {
                Text(stringResource(R.string.onboarding_speech_open))
            }
        }
        Spacer(Modifier.height(16.dp))
        // The directions follow the page "Open settings" will land on (#79).
        val context = LocalContext.current
        val path = remember {
            SettingsLinks.voiceInputPath(SystemSettings.resolve(context, SettingsLink.VOICE_INPUT)?.page, Build.VERSION.SDK_INT)
        }
        listOfNotNull(
            path.textRes(),
            R.string.onboarding_speech_elsewhere.takeIf { path != VoiceInputPath.SEARCH },
            R.string.onboarding_speech_force_stop,
        ).forEach { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A titled card of advice, with optional content under the text. */
@Composable
private fun InfoCard(title: String, body: String, content: @Composable ColumnScope.() -> Unit = {}) {
    MarmaladeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardTitle(title)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
}

/** All set, plus what was skipped, so nothing left undone is a surprise later. */
@Composable
private fun DoneStep(state: OnboardingState) {
    StepPage(
        mascot = JarMascotState.IDLE,
        title = stringResource(if (state.allDone) R.string.onboarding_done_title else R.string.onboarding_done_title_partial),
        body = stringResource(if (state.allDone) R.string.onboarding_done_body else R.string.onboarding_done_body_partial),
        mascotSize = 144.dp,
    ) {
        MarmaladeCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryRow(
                    label = stringResource(R.string.onboarding_summary_mic),
                    value = stringResource(
                        if (state.micGranted) R.string.onboarding_summary_mic_on else R.string.onboarding_summary_mic_off,
                    ),
                    done = state.micGranted,
                )
                SummaryRow(
                    label = stringResource(R.string.onboarding_summary_model),
                    value = stringResource(if (state.modelReady) R.string.onboarding_summary_model_ready else state.model.summaryRes()),
                    done = state.modelReady,
                )
                SummaryRow(
                    label = stringResource(R.string.onboarding_summary_keyboard),
                    value = stringResource(
                        if (state.keyboardEnabled) R.string.onboarding_summary_keyboard_on else R.string.onboarding_summary_keyboard_off,
                    ),
                    done = state.keyboardEnabled,
                )
                if (state.otherVoiceKeyboards.isNotEmpty()) {
                    val separator = stringResource(R.string.list_separator)
                    val quoted = stringResource(R.string.quoted_name)
                    SummaryRow(
                        label = stringResource(R.string.onboarding_summary_other_voice),
                        value = stringResource(
                            R.string.onboarding_summary_other_voice_on,
                            state.otherVoiceKeyboards.joinToString(separator) { String.format(quoted, it) },
                        ),
                        done = false,
                    )
                }
                // Only a known answer: a FUTO that can't be asked has no row (Max, 2026-10-10).
                if (state.futoInstalled && (state.futo == FutoStatus.SELECTED || state.futo == FutoStatus.NOT_SELECTED)) {
                    SummaryRow(
                        label = stringResource(R.string.onboarding_summary_futo),
                        value = stringResource(state.futo.summaryRes()),
                        done = state.futo == FutoStatus.SELECTED,
                    )
                }
                if (state.speechServiceSupported) {
                    SummaryRow(
                        label = stringResource(R.string.onboarding_summary_speech),
                        value = stringResource(
                            if (state.speechServiceSelected) {
                                R.string.onboarding_summary_speech_on
                            } else {
                                R.string.onboarding_summary_speech_off
                            },
                        ),
                        done = state.speechServiceSelected,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_done_reopen),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, done: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Labels --------------------------------------------------------------------

@StringRes
private fun AdvanceLabel.labelRes(): Int = when (this) {
    AdvanceLabel.GET_STARTED -> R.string.onboarding_get_started
    AdvanceLabel.NEXT -> R.string.onboarding_next
    AdvanceLabel.SKIP -> R.string.onboarding_skip
    AdvanceLabel.TRY_IT -> R.string.onboarding_try_it
}

@StringRes
private fun FutoStatus.statusRes(): Int = when (this) {
    FutoStatus.SELECTED -> R.string.onboarding_futo_selected
    FutoStatus.NOT_SELECTED, FutoStatus.UNKNOWN -> R.string.onboarding_futo_not_selected
}

@StringRes
private fun KeyboardKind.adviceRes(): Int = when (this) {
    KeyboardKind.FIRST_VOICE_KEYBOARD -> R.string.onboarding_kb_first
    KeyboardKind.FIRST_VOICE_KEYBOARD_HIDDEN -> R.string.onboarding_kb_first_hidden
    KeyboardKind.CHOOSER -> R.string.onboarding_kb_chooser
    KeyboardKind.FOSSIFY -> R.string.onboarding_kb_fossify
    KeyboardKind.SWIFTKEY -> R.string.onboarding_kb_swiftkey
    KeyboardKind.OWN_ENGINE -> R.string.onboarding_kb_own_engine
    KeyboardKind.GOOGLE_ONLY -> R.string.onboarding_kb_google_only
    KeyboardKind.NO_MIC_KEY -> R.string.onboarding_kb_no_mic
    KeyboardKind.UNKNOWN -> R.string.onboarding_kb_unknown
    // FUTO gets its own card (FutoCard); this text is never shown.
    KeyboardKind.FUTO -> R.string.onboarding_futo_manual_body
}

@StringRes
private fun VoiceInputPath.textRes(): Int = when (this) {
    VoiceInputPath.ASSIST_9 -> R.string.onboarding_speech_path_assist_9
    VoiceInputPath.ASSIST_10 -> R.string.onboarding_speech_path_assist_10
    VoiceInputPath.ASSIST_11 -> R.string.onboarding_speech_path_assist_11
    VoiceInputPath.ASSIST_12 -> R.string.onboarding_speech_path_assist
    VoiceInputPath.LANGUAGES_AND_INPUT -> R.string.onboarding_speech_path_languages_input
    VoiceInputPath.LANGUAGES -> R.string.onboarding_speech_path_languages
    VoiceInputPath.LANGUAGE_AND_REGION -> R.string.onboarding_speech_path_language_region
    VoiceInputPath.SEARCH -> R.string.onboarding_speech_path_search
}

@StringRes
private fun ModelStatus.summaryRes(): Int = when (this) {
    ModelStatus.INSTALLED -> R.string.onboarding_summary_model_ready
    ModelStatus.DOWNLOADING -> R.string.onboarding_summary_model_downloading
    ModelStatus.NOT_INSTALLED -> R.string.onboarding_summary_model_missing
    ModelStatus.NEEDS_ATTENTION -> R.string.onboarding_summary_model_attention
}

@StringRes
private fun FutoStatus?.summaryRes(): Int =
    if (this == FutoStatus.SELECTED) R.string.onboarding_summary_futo_on else R.string.onboarding_summary_futo_off

// --- System ----------------------------------------------------------------------

/**
 * Start FUTO's switch activity in [mode]. An older FUTO without it throws
 * ActivityNotFoundException; that is reported through [onUnavailable]
 * (status UNKNOWN: only the manual steps apply).
 */
private fun launchFuto(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    futoPackage: String,
    mode: FutoMode,
    onUnavailable: () -> Unit,
) {
    try {
        launcher.launch(FutoVoiceInput.switchIntent(futoPackage, context.packageName, mode))
    } catch (e: ActivityNotFoundException) {
        Log.i(TAG, "$futoPackage has no voice input switch activity", e)
        onUnavailable()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** From this font scale the step count leaves the top bar (see [OnboardingScreen]). */
private const val LARGE_FONT_SCALE = 1.5f
