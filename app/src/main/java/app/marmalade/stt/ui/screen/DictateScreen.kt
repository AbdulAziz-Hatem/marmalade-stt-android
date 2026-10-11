package app.marmalade.stt.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.R
import app.marmalade.stt.system.SettingsLink
import app.marmalade.stt.system.SystemSettings
import app.marmalade.stt.ui.MarmaladeIcons
import app.marmalade.stt.ui.components.JarMascot
import app.marmalade.stt.ui.components.JarMascotState
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.components.micColors
import app.marmalade.stt.ui.components.rememberMicMeter
import app.marmalade.stt.ui.contentDirection
import app.marmalade.stt.ui.theme.LocalWordmarkColor
import app.marmalade.stt.ui.theme.Wordmark
import app.marmalade.stt.ui.theme.accent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Bottom-nav "Dictate" tab → DictateScreen(onOpenEngines)
//     │
//     ├── modelReady ◄── installer.state(SpeechEngineManager.activeEngine) == Installed
//     │     (live; follows the engine picked on the Engines tab)
//     │     false → NoModelCard → onOpenEngines → AppRoot navigateToTab(Engines)
//     │     false → true, or a switch to another installed engine, while open
//     │       → vm warms the model up in the background
//     │
//     ├── screen ON_START → vm.onVisible(): SpeechEngineManager.hold(preload)
//     │     loads the model now and keeps it between taps (#78); ON_STOP (not
//     │     a rotation) → vm.onBackground() closes the hold
//     │
//     ├── mic tap ──► RECORD_AUDIO granted? ──no──► permission launcher
//     │     │                                        denied → MicPermissionCard
//     │     │                                        (blocked → app settings page)
//     │     yes
//     │     ▼
//     │   vm.start() → viewModelScope job: SpeechEngineManager.listen()
//     │     SttEvents → Dictation.reduce(state, event)
//     │       Level          → level (0..1) → LevelRing (draw phase only)
//     │       Ready / SpeechStarted → phase → StatusLine (polite live region)
//     │       Partial        → partial (muted italic after the transcript)
//     │       CatchingUp     → catchingUp → StatusLine "Catching up…"
//     │       Final          → TextPipeline.process(text, before = transcript,
//     │                          nearSilent) → transcript = edit.applyTo(transcript)
//     │       NoSpeech/Error/TooSlow → notice → snackbar
//     │     flow ends → phase IDLE
//     │   mic tap again → vm.stop() → manager.stop(): graceful flush (FINISHING)
//     │   screen ON_STOP while listening (not a rotation) → vm.onBackground()
//     │     → same graceful stop
//     │
//     └── Copy → ClipboardManager (+ "Copied" snackbar below Android 13)
//         Clear → vm.clear()
//
//   The transcript lives only in DictateViewModel's memory; nothing is written
//   to disk (ARCHITECTURE.md D10).
// -----------------------------------------------------------------------------

/** The mic button; 96 dp until Max's v1 review (#67) asked for it a little smaller. */
private val MIC_SIZE = 80.dp

/** Extra ring radius at full input level, beyond the mic button's edge. */
private val RING_REACH = 24.dp

/** The loading/finishing spinner's reach around the button. */
private val SPINNER_HALO = 10.dp

/** The transcript's least height when the controls below it need to scroll. */
private val TRANSCRIPT_MIN_HEIGHT = 96.dp

/** Dictate tab: the home tab, branded with the wordmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictateScreen(
    onOpenEngines: () -> Unit,
    viewModel: DictateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val levelState = viewModel.level.collectAsStateWithLifecycle()
    val modelReady by viewModel.modelReady.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Mic permission -------------------------------------------------------
    var micGranted by remember { mutableStateOf(context.hasMicPermission()) }
    var micDenied by rememberSaveable { mutableStateOf(false) }
    // Denied with "don't ask again" (or twice): the system won't show the
    // dialog any more, so the card sends the user to the app's settings page.
    var micBlocked by rememberSaveable { mutableStateOf(false) }
    var startWhenGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted) {
            micDenied = false
            micBlocked = false
            if (startWhenGranted) viewModel.start()
        } else {
            micDenied = true
            micBlocked = context.findActivity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false
        }
        startWhenGranted = false
    }
    // The user may have granted (or revoked) it in system settings meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        micGranted = context.hasMicPermission()
        if (micGranted) {
            micDenied = false
            micBlocked = false
        }
    }
    // On show: the engine loads now and stays loaded between taps (#78).
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onVisible() }
    // Leaving the screen (another tab, or the app in the background) never
    // keeps the mic open: finish gracefully, the Final still lands, and the
    // model may go. A rotation stops the activity too, but the ViewModel, its
    // session and its hold survive it, so that one keeps listening.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (context.findActivity()?.isChangingConfigurations != true) viewModel.onBackground()
    }

    // --- One-off messages -----------------------------------------------------
    val noticeText = state.notice?.let { stringResource(it.messageRes()) }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            viewModel.noticeShown()
        }
    }
    val copiedText = stringResource(R.string.dictate_copied)
    val settingsFailedText = stringResource(R.string.dictate_mic_settings_failed)
    val screenTitle = stringResource(R.string.nav_dictate)

    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                // Brand wordmark per marmalade-design: always lowercase,
                // Momo Trust Display 600, orange in light / cream in dark
                // (LocalWordmarkColor carries the mode-aware swap).
                title = {
                    Text(
                        text = stringResource(R.string.wordmark),
                        fontFamily = Wordmark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        color = LocalWordmarkColor.current,
                        // The brand wordmark stands in for the screen title:
                        // TalkBack hears "Dictate" as a heading, not the brand
                        // name, so heading navigation finds the screen (#68).
                        modifier = Modifier.clearAndSetSemantics {
                            heading()
                            contentDescription = screenTitle
                        },
                    )
                },
                windowInsets = WindowInsets(0),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            val controlsMaxHeight = (maxHeight - TRANSCRIPT_MIN_HEIGHT).coerceAtLeast(0.dp)
            Column(modifier = Modifier.fillMaxSize()) {
                TranscriptPanel(
                    transcript = state.transcript,
                    partial = state.partial,
                    phase = state.phase,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                // Everything below the transcript. It fits in portrait at normal
                // font sizes; in landscape, split screen or at large font scales
                // it scrolls instead of pushing the mic (or a card's button) off
                // the bottom, and the transcript keeps TRANSCRIPT_MIN_HEIGHT (#68).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = controlsMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TranscriptActions(
                        canCopy = state.transcript.isNotEmpty(),
                        canClear = state.transcript.isNotEmpty() || state.partial.isNotEmpty(),
                        onCopy = {
                            clipboard.setText(AnnotatedString(state.transcript))
                            // Android 13+ confirms every copy itself; a snackbar on
                            // top would say it twice.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                scope.launch { snackbarHostState.showSnackbar(copiedText) }
                            }
                        },
                        onClear = viewModel::clear,
                    )
                    Spacer(Modifier.height(8.dp))

                    if (!modelReady) {
                        NoModelCard(onOpenEngines = onOpenEngines)
                    } else {
                        if (micDenied && !micGranted) {
                            MicPermissionCard(
                                blocked = micBlocked,
                                onAllow = {
                                    if (micBlocked) {
                                        if (SystemSettings.open(context, SettingsLink.APP_DETAILS) == null) {
                                            scope.launch { snackbarHostState.showSnackbar(settingsFailedText) }
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        StatusLine(phase = state.phase, catchingUp = state.catchingUp)
                        Spacer(Modifier.height(8.dp))
                        MicButton(
                            phase = state.phase,
                            level = { levelState.value },
                            onClick = {
                                when {
                                    state.phase.isActive -> viewModel.stop()
                                    micGranted -> viewModel.start()
                                    else -> {
                                        startWhenGranted = true
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The transcript, then the partial in muted italics. Selectable, and it
 * follows the end of the text as it grows. Empty: the mascot, reacting to
 * the session.
 */
@Composable
private fun TranscriptPanel(
    transcript: String,
    partial: String,
    phase: DictationPhase,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = modifier) {
        if (transcript.isEmpty() && partial.isEmpty()) {
            EmptyTranscript(phase = phase)
            return@OutlinedCard
        }
        val scroll = rememberScrollState()
        // Content grew (a partial or a Final arrived): show its end. Scrolling
        // up to reread is left alone until the next text arrives.
        LaunchedEffect(scroll) {
            snapshotFlow { scroll.maxValue }
                .filter { it != Int.MAX_VALUE } // not measured yet
                .collectLatest { scroll.animateScrollTo(it) }
        }
        val partialColor = MaterialTheme.colorScheme.onSurfaceVariant
        val text = remember(transcript, partial, partialColor) {
            buildAnnotatedString {
                append(transcript)
                if (partial.isNotEmpty()) {
                    append(Dictation.partialSeparator(transcript))
                    withStyle(SpanStyle(color = partialColor, fontStyle = FontStyle.Italic)) { append(partial) }
                }
            }
        }
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.contentDirection(),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun EmptyTranscript(phase: DictationPhase) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // heightIn(min = viewport) after verticalScroll: centred when it
        // fits, scrollable in landscape or at large font scales.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarMascot(state = phase.mascotState(), size = 120.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dictate_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TranscriptActions(
    canCopy: Boolean,
    canClear: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        // Ink in every preset: Max kept these two out of the orange text (#68).
        val ink = MaterialTheme.accent.ink
        MarmaladeTextButton(onClick = onCopy, enabled = canCopy, contentColor = ink) {
            Icon(imageVector = MarmaladeIcons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dictate_copy))
        }
        MarmaladeTextButton(onClick = onClear, enabled = canClear, contentColor = ink) {
            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dictate_clear))
        }
    }
}

/**
 * What the session is doing, announced politely by TalkBack. Empty while
 * idle (#67: the mic says "Start dictation"), but still one line tall, so
 * the mic doesn't move when "Loading model…" appears.
 */
@Composable
private fun StatusLine(phase: DictationPhase, catchingUp: Boolean) {
    val res = if (catchingUp && phase.isActive) R.string.dictate_status_catching_up else phase.statusRes()
    Text(
        text = res?.let { stringResource(it) }.orEmpty(),
        minLines = 1,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The start/stop toggle, with a ring behind it that grows with the input
 * level while listening. The level is read only in the draw phase, so its
 * ten updates a second redraw the ring without recomposing anything.
 *
 * Colours: [micColors], shared with the voice keyboard and the speech sheet.
 * Disabled (finishing) uses the neutral M3 treatment.
 */
@Composable
private fun MicButton(
    phase: DictationPhase,
    level: () -> Float,
    onClick: () -> Unit,
) {
    val enabled = phase != DictationPhase.FINISHING
    val active = phase.isActive
    val micColors = micColors(enabled)
    val meter = rememberMicMeter(level)

    val description = stringResource(if (active) R.string.dictate_stop else R.string.dictate_start)
    val stateText = stringResource(phase.buttonStateRes())

    Box(
        modifier = Modifier
            .size(MIC_SIZE + RING_REACH * 2)
            // Decorative: the status line and the button's state say the same in words.
            .drawBehind {
                if (active) {
                    val radius = MIC_SIZE.toPx() / 2 + RING_REACH.toPx() * meter.value
                    drawCircle(color = micColors.ring, radius = radius)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (phase == DictationPhase.LOADING || phase == DictationPhase.FINISHING) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(MIC_SIZE + SPINNER_HALO)
                    .clearAndSetSemantics {},
                strokeWidth = 3.dp,
            )
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = micColors.container,
            contentColor = micColors.content,
            modifier = Modifier
                .size(MIC_SIZE)
                .semantics {
                    role = Role.Button
                    contentDescription = description
                    stateDescription = stateText
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (active) MarmaladeIcons.Stop else MarmaladeIcons.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

/** Shown instead of the mic until a speech model is installed. */
@Composable
private fun NoModelCard(onOpenEngines: () -> Unit) {
    OutlinedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dictate_no_model_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dictate_no_model_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // The screen's one accent CTA while there's no mic to press.
            // primaryContainer, like the Engines screen's main action: the
            // theme's dark primary is orange, which marmalade-design keeps
            // off dark surfaces.
            Button(
                onClick = onOpenEngines,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(stringResource(R.string.dictate_no_model_action))
            }
        }
    }
}

/** Why the app needs the mic, after the user said no. */
@Composable
private fun MicPermissionCard(blocked: Boolean, onAllow: () -> Unit) {
    OutlinedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dictate_mic_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (blocked) R.string.dictate_mic_body_blocked else R.string.dictate_mic_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAllow,
                modifier = Modifier.align(Alignment.End),
                // Toast default button: the mic below stays the accent.
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(stringResource(if (blocked) R.string.dictate_mic_open_settings else R.string.dictate_mic_allow))
            }
        }
    }
}

private fun DictationPhase.mascotState(): JarMascotState = when (this) {
    DictationPhase.IDLE -> JarMascotState.IDLE
    DictationPhase.LOADING, DictationPhase.FINISHING -> JarMascotState.THINKING
    DictationPhase.LISTENING, DictationPhase.HEARING -> JarMascotState.LISTENING
}

/** The status line's text; none while idle. */
@StringRes
private fun DictationPhase.statusRes(): Int? = when (this) {
    DictationPhase.IDLE -> null
    DictationPhase.LOADING -> R.string.dictate_status_loading
    DictationPhase.LISTENING -> R.string.dictate_status_listening
    DictationPhase.HEARING -> R.string.dictate_status_hearing
    DictationPhase.FINISHING -> R.string.dictate_status_finishing
}

@StringRes
private fun DictationPhase.buttonStateRes(): Int = when (this) {
    DictationPhase.IDLE -> R.string.dictate_state_idle
    DictationPhase.LOADING -> R.string.dictate_state_loading
    DictationPhase.LISTENING, DictationPhase.HEARING -> R.string.dictate_state_listening
    DictationPhase.FINISHING -> R.string.dictate_state_finishing
}

@StringRes
private fun DictationNotice.messageRes(): Int = when (this) {
    DictationNotice.NO_SPEECH -> R.string.dictate_no_speech
    DictationNotice.MIC_ERROR -> R.string.dictate_error_mic
    DictationNotice.MODEL_ERROR -> R.string.dictate_error_model
    DictationNotice.RECOGNITION_ERROR -> R.string.dictate_error_recognition
    DictationNotice.BUSY -> R.string.dictate_error_busy
    DictationNotice.TOO_SLOW -> R.string.dictate_too_slow
}

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
