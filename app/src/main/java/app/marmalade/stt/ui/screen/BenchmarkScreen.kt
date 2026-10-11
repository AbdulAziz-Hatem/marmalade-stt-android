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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.R
import app.marmalade.stt.system.SettingsLink
import app.marmalade.stt.system.SystemSettings
import app.marmalade.stt.benchmark.BenchmarkEvent
import app.marmalade.stt.benchmark.BenchmarkReport
import app.marmalade.stt.benchmark.TakeRecorder
import app.marmalade.stt.benchmark.wordErrorRate
import app.marmalade.stt.ui.MarmaladeIcons
import app.marmalade.stt.ui.components.DetailTopBar
import app.marmalade.stt.ui.components.MarmaladeOutlinedButton
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.components.rememberAnnouncer
import app.marmalade.stt.ui.contentDirection
import app.marmalade.stt.ui.isolate
import java.util.Locale
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Settings (developer section) → Routes → BenchmarkScreen(onBack)
//     BenchmarkViewModel.state ─► phase / takeSeconds / engines / playing / notice
//
//   Record tap ── RECORD_AUDIO granted? ──no──► permission launcher
//     │             denied → MicPermissionCard (blocked → app settings)
//     └── yes ──► vm.record()  ··· Recording(level, elapsed) ··· Stop → vm.stopRecording()
//   Play       ──► vm.togglePlayback()   (AudioTrack, take from memory)
//   Reference  ──  rememberSaveable text; WER per card = wordErrorRate(reference, text),
//                  text being the cleaned transcript (rawText is shown under it)
//   Run        ──► vm.run() ─► Running(current, results) ─► Done(results)
//   Copy       ──► vm.markdown(reference) ─► ClipboardManager (text only)
//   ON_RESUME  ──► vm.refreshEngines()   (engines installed meanwhile)
//   ON_STOP (not a rotation) ──► vm.onBackground()  (mic and speaker off)
//   Back       ──► onBack; leaving clears the ViewModel, which cancels the run
//                  after the engine in flight is released.
// -----------------------------------------------------------------------------


/**
 * Developer A/B benchmark (#13): record one take, run it through every
 * installed engine one at a time, compare transcripts, timings, memory and
 * WER. Detail screen; the bottom bar is hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkScreen(
    onBack: () -> Unit,
    viewModel: BenchmarkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var reference by rememberSaveable { mutableStateOf("") }

    // --- Mic permission (as on the Dictate screen) ----------------------------
    var micGranted by remember { mutableStateOf(context.hasMicPermission()) }
    var micDenied by rememberSaveable { mutableStateOf(false) }
    var micBlocked by rememberSaveable { mutableStateOf(false) }
    var recordWhenGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted) {
            micDenied = false
            micBlocked = false
            if (recordWhenGranted) viewModel.record()
        } else {
            micDenied = true
            micBlocked = context.findActivity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false
        }
        recordWhenGranted = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        micGranted = context.hasMicPermission()
        if (micGranted) {
            micDenied = false
            micBlocked = false
        }
        viewModel.refreshEngines()
    }
    // A rotation stops the activity too, but the ViewModel survives it: keep recording then.
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
    val copiedText = stringResource(R.string.benchmark_copied)
    val settingsFailedText = stringResource(R.string.dictate_mic_settings_failed)

    val phase = state.phase
    val recording = phase is BenchmarkPhase.Recording
    val running = phase is BenchmarkPhase.Running
    val takeSeconds = state.takeSeconds
    val hasTake = takeSeconds != null

    // #68: the end of a run, for TalkBack (the results land below, out of view).
    val announce = rememberAnnouncer()
    val finishedText = stringResource(R.string.a11y_benchmark_finished)
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (wasRunning && phase is BenchmarkPhase.Done) announce(finishedText)
        wasRunning = running
    }

    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = { DetailTopBar(title = stringResource(R.string.benchmark_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.benchmark_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // --- Take ---------------------------------------------------------
            SectionHeader(stringResource(R.string.benchmark_section_take))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    Spacer(Modifier.height(12.dp))
                }
                // Wraps at large font sizes rather than squeezing the second button (#68).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            when {
                                recording -> viewModel.stopRecording()
                                micGranted -> viewModel.record()
                                else -> {
                                    recordWhenGranted = true
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !running,
                    ) {
                        Icon(
                            imageVector = if (recording) MarmaladeIcons.Stop else MarmaladeIcons.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(if (recording) R.string.benchmark_stop else R.string.benchmark_record))
                    }
                    MarmaladeOutlinedButton(
                        onClick = viewModel::togglePlayback,
                        enabled = hasTake && !recording && !running,
                    ) {
                        Icon(
                            imageVector = if (state.playing) MarmaladeIcons.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(if (state.playing) R.string.benchmark_stop_playback else R.string.benchmark_play))
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    phase is BenchmarkPhase.Recording -> RecordingMeter(phase)
                    takeSeconds != null -> Text(
                        text = stringResource(
                            if (state.softAgc) R.string.benchmark_take_length_soft_agc else R.string.benchmark_take_length,
                            BenchmarkReport.seconds((takeSeconds * 1_000).toLong()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Text(
                        text = pluralStringResource(
                            R.plurals.benchmark_no_take,
                            TakeRecorder.MAX_TAKE_SECONDS,
                            TakeRecorder.MAX_TAKE_SECONDS,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Reference ----------------------------------------------------
            SectionHeader(stringResource(R.string.benchmark_section_reference))
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it },
                label = { Text(stringResource(R.string.benchmark_reference_label)) },
                textStyle = LocalTextStyle.current.contentDirection(),
                supportingText = { Text(stringResource(R.string.benchmark_reference_hint)) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            // --- Engines + run --------------------------------------------------
            SectionHeader(stringResource(R.string.benchmark_section_engines))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (state.engines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.benchmark_no_engines),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = state.engines.joinToString(stringResource(R.string.list_separator)) { it.displayName },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::run,
                    enabled = hasTake && state.engines.isNotEmpty() && !recording && !running,
                ) {
                    Text(
                        stringResource(
                            if (phase is BenchmarkPhase.Done) R.string.benchmark_run_again else R.string.benchmark_run,
                        ),
                    )
                }
                if (phase is BenchmarkPhase.Running) {
                    val runningText = stringResource(
                        R.string.benchmark_running,
                        phase.current.displayName,
                        phase.results.size + 1,
                        state.engines.size,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Live: TalkBack hears each next engine (#68).
                    Text(
                        text = runningText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = runningText },
                    )
                }
            }

            // --- Results --------------------------------------------------------
            if (state.results.isNotEmpty()) {
                SectionHeader(stringResource(R.string.benchmark_section_results))
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (outcome in state.results) {
                        ResultCard(outcome = outcome, reference = reference)
                    }
                    if (!running) {
                        MarmaladeOutlinedButton(
                            onClick = {
                                val md = viewModel.markdown(reference) ?: return@MarmaladeOutlinedButton
                                clipboard.setText(AnnotatedString(md))
                                // Android 13+ confirms every copy itself.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    scope.launch { snackbarHostState.showSnackbar(copiedText) }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MarmaladeIcons.Copy,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.benchmark_copy_markdown))
                        }
                    }
                }
            }
        }
    }
}

/** "12.3 s / 30 s" and the input level while recording. */
@Composable
private fun RecordingMeter(phase: BenchmarkPhase.Recording) {
    Text(
        text = stringResource(
            R.string.benchmark_elapsed,
            String.format(Locale.getDefault(), "%.1f", phase.elapsedSeconds),
            TakeRecorder.MAX_TAKE_SECONDS,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))
    val levelLabel = stringResource(R.string.benchmark_level)
    LinearProgressIndicator(
        progress = { phase.level },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = levelLabel },
    )
}

/** One engine: name, cleaned transcript and raw text (or error), and the metrics line. */
@Composable
private fun ResultCard(outcome: BenchmarkEvent.Outcome, reference: String) {
    OutlinedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = outcome.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            when (outcome) {
                is BenchmarkEvent.Finished -> {
                    val r = outcome.result
                    SelectionContainer {
                        Column {
                            // As dictation would write it; the engine's own text below.
                            Text(
                                text = r.text.ifEmpty { stringResource(R.string.benchmark_empty_transcript) },
                                style = MaterialTheme.typography.bodyLarge.contentDirection(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (r.rawText.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.benchmark_raw, isolate(r.rawText)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val wer = remember(reference, r.text) { wordErrorRate(reference, r.text) }
                    val parts = buildList {
                        add(stringResource(R.string.benchmark_metric_load, BenchmarkReport.seconds(r.loadMs)))
                        add(stringResource(R.string.benchmark_metric_decode, BenchmarkReport.seconds(r.decodeMs)))
                        add(stringResource(R.string.benchmark_metric_rtf, BenchmarkReport.rtf(r.rtf)))
                        add(stringResource(R.string.benchmark_metric_rss, BenchmarkReport.rss(r.peakRssBytes)))
                        if (wer != null) add(stringResource(R.string.benchmark_metric_wer, BenchmarkReport.wer(wer)))
                    }
                    Text(
                        text = parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is BenchmarkEvent.Failed -> Text(
                    text = stringResource(outcome.stage.messageRes(), outcome.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Why the benchmark needs the mic, after the user said no. */
@Composable
private fun MicPermissionCard(blocked: Boolean, onAllow: () -> Unit) {
    OutlinedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.benchmark_mic_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (blocked) R.string.dictate_mic_body_blocked else R.string.benchmark_mic_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAllow, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(if (blocked) R.string.dictate_mic_open_settings else R.string.dictate_mic_allow))
            }
        }
    }
}

@StringRes
private fun BenchmarkEvent.Stage.messageRes(): Int = when (this) {
    BenchmarkEvent.Stage.LOAD -> R.string.benchmark_failed_load
    BenchmarkEvent.Stage.TRANSCRIBE -> R.string.benchmark_failed_transcribe
    BenchmarkEvent.Stage.RELEASE -> R.string.benchmark_failed_release
}

@StringRes
private fun BenchmarkNotice.messageRes(): Int = when (this) {
    BenchmarkNotice.MIC_ERROR -> R.string.benchmark_error_mic
    BenchmarkNotice.EMPTY_TAKE -> R.string.benchmark_error_empty
    BenchmarkNotice.PLAYBACK_ERROR -> R.string.benchmark_error_playback
    BenchmarkNotice.NO_ENGINES -> R.string.benchmark_error_no_engines
    BenchmarkNotice.RUN_ERROR -> R.string.benchmark_error_run
}

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
