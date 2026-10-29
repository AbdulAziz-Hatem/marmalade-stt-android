package app.marmalade.stt.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.install.CorruptProblem
import app.marmalade.stt.install.InstallFailure
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.ui.byteProgress
import app.marmalade.stt.ui.components.MarmaladeOutlinedButton
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.formatBytes
import app.marmalade.stt.ui.progressFraction
import app.marmalade.stt.ui.progressPercent
import app.marmalade.stt.ui.rememberUiLocale
import app.marmalade.stt.ui.theme.accent
import app.marmalade.stt.ui.theme.isDark
import app.marmalade.ui.components.WorkingDots
import app.marmalade.ui.theme.MarmaladeMotion
import java.util.Locale

// Pieces shared by the Engines cards and rows, the engine details dialog and
// the setup guide, so one install state reads and acts the same everywhere.

/**
 * The status line for [state], plus a determinate progress bar while
 * downloading. Failure and damage read in the error colour. Not a live
 * region: the screens announce a download stopping themselves
 * ([downloadOutcomes]: EnginesScreen and the setup guide's model step, #68).
 */
@Composable
fun EngineStatus(state: InstallState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (state is InstallState.Downloading) {
            EngineProgressBar(done = state.bytesDone, total = state.totalBytes)
            Spacer(Modifier.height(4.dp))
        }
        val isProblem = state is InstallState.Failed || state is InstallState.Corrupt
        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun statusText(state: InstallState): String = when (state) {
    InstallState.NotInstalled -> stringResource(R.string.engine_status_not_installed)
    is InstallState.Downloading ->
        stringResource(R.string.engine_status_downloading, progressText(state.bytesDone, state.totalBytes))
    is InstallState.Paused ->
        stringResource(R.string.engine_status_paused, progressText(state.bytesOnDisk, state.totalBytes))
    is InstallState.Failed -> failureText(state)
    is InstallState.Corrupt -> stringResource(R.string.engine_status_corrupt, corruptText(state))
    InstallState.Installed -> stringResource(R.string.engine_status_ready)
}

/**
 * [state] in a few words, for a card's download line and a row's second line:
 * the size ([sizeBytes]) when installed or not downloaded, the progress
 * ("Downloading · 123 of 299 MB"), or "Download failed" / "Files damaged"
 * (the details dialog says why, with [EngineStatus]).
 */
@Composable
fun shortStatusText(state: InstallState, sizeBytes: Long): String = when (state) {
    InstallState.NotInstalled, InstallState.Installed -> formatBytes(sizeBytes)
    is InstallState.Downloading, is InstallState.Paused -> statusText(state)
    is InstallState.Failed -> stringResource(R.string.engine_status_failed_short)
    is InstallState.Corrupt -> stringResource(R.string.engine_status_corrupt_short)
}

/** "123 of 299 MB". */
@Composable
private fun progressText(done: Long, total: Long): String {
    val progress = byteProgress(done, total)
    return stringResource(R.string.engine_progress_bytes, progress.done, progress.total)
}

/**
 * Our wording for a failure. The installer's own reason is English for the
 * log, so an unknown failure gets the generic sentence (#51).
 */
@Composable
private fun failureText(state: InstallState.Failed): String = when (state.failure) {
    InstallFailure.NOT_AVAILABLE -> stringResource(R.string.engine_failure_not_available)
    InstallFailure.NO_SPACE -> stringResource(R.string.engine_failure_no_space)
    InstallFailure.NETWORK -> stringResource(R.string.engine_failure_network)
    InstallFailure.CHECKSUM -> stringResource(R.string.engine_failure_checksum)
    null -> stringResource(R.string.engine_failure_generic)
}

/** "encoder.int8.onnx is missing." */
@Composable
private fun corruptText(state: InstallState.Corrupt): String = when (state.problem) {
    CorruptProblem.OUTDATED -> stringResource(R.string.engine_corrupt_outdated)
    CorruptProblem.MISSING -> stringResource(R.string.engine_corrupt_missing, state.file)
    CorruptProblem.WRONG_SIZE -> stringResource(R.string.engine_corrupt_wrong_size, state.file)
    CorruptProblem.DAMAGED -> stringResource(R.string.engine_corrupt_damaged, state.file)
}

/**
 * A download's determinate progress bar, with its percent for TalkBack. The
 * accent ink (toast ink in Marmalade) on toast in light mode; in dark the
 * progress colour (toast in Marmalade, Max, #87) on the translucent hairline
 * ink (a toast track under a toast bar made 10% look nearly full on the
 * Pixel; surfaceVariant, the track until #84, is now the dark card's own
 * colour). The bar glides between progress updates (#84).
 */
@Composable
fun EngineProgressBar(done: Long, total: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fraction by animateFloatAsState(
        targetValue = progressFraction(done, total),
        animationSpec = MarmaladeMotion.slow(),
        label = "downloadProgress",
    )
    LinearProgressIndicator(
        progress = { fraction },
        color = if (colors.isDark) MaterialTheme.accent.progress else MaterialTheme.accent.ink,
        trackColor = if (colors.isDark) colors.outlineVariant else colors.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .downloadProgressSemantics(done, total),
    )
}

/** "Download progress, 41%" for TalkBack: the card's bar and the row's ring (#68). */
@Composable
fun Modifier.downloadProgressSemantics(done: Long, total: Long): Modifier {
    val progressLabel = stringResource(R.string.engine_progress_description)
    // "41%", which TalkBack reads in the UI's language ("41 percent"). A "%1$d percent"
    // string couldn't agree with the number in Slavic languages or Arabic (#69).
    val progressState = stringResource(R.string.engine_progress_percent, progressPercent(done, total))
    return semantics {
        contentDescription = progressLabel
        stateDescription = progressState
    }
}

/**
 * [actions] as one row of buttons, wrapping at large font sizes: the
 * secondary ones outlined at the start (Remove, Cancel, Remove download), then
 * [end] and the primary ones (Toast, [TonalEngineButton]) at the end. With
 * [downloadSize], "Download" says how much: "Download · 640 MB". Not for
 * "Check files", which the details dialog puts by the size. With
 * [engineName] each button tells TalkBack and Voice Access whose it is
 * ("Cancel Parakeet 110M"), as the card's Use does: a list can show several
 * cards with the same buttons (#68). With [resumeFocus], Cancel hands focus
 * to Resume ([rememberResumeFocus]). Each button keeps its own node, so focus
 * on Cancel never turns into focus on "Remove download" in its place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineActionBar(
    actions: List<EngineAction>,
    onAction: (EngineAction) -> Unit,
    modifier: Modifier = Modifier,
    downloadSize: String? = null,
    engineName: String? = null,
    resumeFocus: ResumeFocus? = null,
    end: @Composable () -> Unit = {},
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.filterNot { it.primary }.forEach { action ->
            key(action) {
                val label = stringResource(action.labelRes())
                val spoken = namedActionLabel(label, engineName)
                MarmaladeOutlinedButton(
                    onClick = {
                        if (action == EngineAction.CANCEL) resumeFocus?.cancelled()
                        onAction(action)
                    },
                    modifier = Modifier.spokenAs(spoken),
                ) { ButtonLabel(label, spoken) }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.align(Alignment.CenterVertically)) { end() }
        actions.filter { it.primary }.forEach { action ->
            key(action) {
                val sized = action == EngineAction.DOWNLOAD && downloadSize != null
                val label = if (sized) {
                    stringResource(R.string.engine_action_download_size, downloadSize)
                } else {
                    stringResource(action.labelRes())
                }
                val spoken = if (sized && engineName != null) {
                    // "Download Parakeet 110M, 640 MB", not "Download · 640 MB Parakeet 110M".
                    stringResource(R.string.a11y_engine_download_named, engineName, downloadSize)
                } else {
                    namedActionLabel(label, engineName)
                }
                TonalEngineButton(
                    label = label,
                    onClick = { onAction(action) },
                    spokenLabel = spoken,
                    modifier = if (action == EngineAction.RESUME && resumeFocus != null) {
                        Modifier.focusRequester(resumeFocus.requester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * Hands focus from Cancel to Resume: Cancel's button goes away as the
 * download pauses, and keyboard or Switch Access focus would go with it
 * (#68 review). Cancel calls [cancelled]; [requester] goes on Resume.
 */
@Stable
class ResumeFocus {
    val requester = FocusRequester()
    internal var pending = false

    fun cancelled() {
        pending = true
    }
}

/** A [ResumeFocus] for an engine in [state]: focus moves once Cancel's pause lands. */
@Composable
fun rememberResumeFocus(state: InstallState): ResumeFocus {
    val focus = remember { ResumeFocus() }
    LaunchedEffect(state) {
        when {
            !focus.pending || state is InstallState.Downloading -> Unit
            // Every screen shows Resume for a paused download.
            state is InstallState.Paused -> {
                focus.pending = false
                focus.requester.requestFocus()
            }
            else -> focus.pending = false
        }
    }
    return focus
}

/** [label] and [engineName] for TalkBack and Voice Access ("Retry, Parakeet 110M"); null without a name. */
@Composable
fun namedActionLabel(label: String, engineName: String?): String? =
    engineName?.let { stringResource(R.string.a11y_engine_action_named, label, it) }

/** A control TalkBack names [spoken] instead of its visible label; as is when null. */
fun Modifier.spokenAs(spoken: String?): Modifier =
    if (spoken == null) this else semantics { contentDescription = spoken }

/**
 * A button's visible [label]. With [spoken] it's hidden from TalkBack, so the
 * button ([spokenAs]) reads "Remove, Parakeet 110M", not that and then
 * "Remove" again: a merged button reads its description and its text (#68).
 */
@Composable
fun ButtonLabel(label: String, spoken: String?) {
    Text(label, modifier = if (spoken == null) Modifier else Modifier.clearAndSetSemantics {})
}

/**
 * The tonal button (marmalade-design's toast default in light mode; the
 * orange accent is kept for one CTA per screen): Download, Resume, Use. `primaryContainer` is toast in
 * light mode and Orange 800 in dark (the library's, in Marmalade).
 */
@Composable
fun TonalEngineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, spokenLabel: String? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.spokenAs(spokenLabel),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { ButtonLabel(label, spokenLabel) }
}

/**
 * "Check files", or working dots and "Checking files…" while [checking] (it
 * can't be pressed again). The dots replace a spinning ring (#84) and keep
 * its "in progress" for TalkBack.
 */
@Composable
fun CheckFilesButton(checking: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MarmaladeOutlinedButton(onClick = onClick, enabled = !checking, modifier = modifier) {
        if (checking) {
            WorkingDots(dotSize = 4.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.engine_action_checking))
        } else {
            Text(stringResource(R.string.engine_action_check_files))
        }
    }
}

@Composable
fun ImportArchiveButton(importing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MarmaladeOutlinedButton(onClick = onClick, enabled = !importing, modifier = modifier) {
        Text(stringResource(R.string.engine_action_import))
    }
}

@Composable
fun ExportBackupButton(exporting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MarmaladeOutlinedButton(onClick = onClick, enabled = !exporting, modifier = modifier) {
        Text(stringResource(R.string.engine_action_export))
    }
}

/** A text button in the warm ink (Cancel, Close), not Material's orange `primary`. */
@Composable
fun EngineTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, spokenLabel: String? = null) {
    MarmaladeTextButton(onClick = onClick, modifier = modifier.spokenAs(spokenLabel), contentColor = MaterialTheme.accent.ink) {
        ButtonLabel(label, spokenLabel)
    }
}

@StringRes
fun EngineAction.labelRes(): Int = when (this) {
    EngineAction.DOWNLOAD -> R.string.engine_action_download
    EngineAction.RESUME -> R.string.engine_action_resume
    EngineAction.RETRY -> R.string.engine_action_retry
    EngineAction.DOWNLOAD_AGAIN -> R.string.engine_action_download_again
    EngineAction.CANCEL -> R.string.engine_action_cancel
    EngineAction.REMOVE_DOWNLOAD -> R.string.engine_action_remove_download
    EngineAction.CHECK_FILES -> R.string.engine_action_check_files
    EngineAction.REMOVE -> R.string.engine_action_remove
    EngineAction.IMPORT -> R.string.engine_action_import
    EngineAction.EXPORT -> R.string.engine_action_export
    EngineAction.COPY_LINKS -> R.string.engine_copy_download_links
}

/**
 * Confirms a first download: how big it is, and that the internet is used
 * this once (ARCHITECTURE.md D10). The confirm button is the screen's one
 * accent CTA.
 */
@Composable
fun DownloadConfirmDialog(
    engine: SttEngineDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.engine_download_confirm_title, engine.displayName),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = { Text(stringResource(R.string.engine_download_confirm_body, formatBytes(engine.sizeBytes))) },
        confirmButton = {
            TonalEngineButton(label = stringResource(R.string.engine_action_download), onClick = onConfirm)
        },
        dismissButton = {
            EngineTextButton(label = stringResource(R.string.engine_dialog_cancel), onClick = onDismiss)
        },
    )
}

/** Confirms deleting an installed model. */
@Composable
fun RemoveConfirmDialog(
    engine: SttEngineDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.engine_remove_confirm_title, engine.displayName),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = { Text(stringResource(R.string.engine_remove_confirm_body, formatBytes(engine.sizeBytes))) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.engine_action_remove)) }
        },
        dismissButton = {
            EngineTextButton(label = stringResource(R.string.engine_dialog_cancel), onClick = onDismiss)
        },
    )
}

/**
 * One BCP-47 code as its name in [locale], capitalised for use on its own (a
 * list, a setting's value): "de" → "German", "Alemán"; the code itself if
 * there is no name. Inside a sentence use [languageNameInText]. [locale] is
 * the UI's ([rememberUiLocale]), not the phone's.
 */
fun languageName(code: String, locale: Locale): String =
    languageNameInText(code, locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** [languageName] in the UI's language. */
@Composable
fun languageName(code: String): String = languageName(code, rememberUiLocale())

/**
 * One BCP-47 code as its name in [locale] as written mid-sentence: "German",
 * "Deutsch", but "alemán", "allemand" (#51: Spanish, French, Italian and
 * Portuguese write language names in lower case).
 */
fun languageNameInText(code: String, locale: Locale): String =
    Locale.forLanguageTag(code).getDisplayLanguage(locale).ifEmpty { code }

/** [languageNameInText] in the UI's language. */
@Composable
fun languageNameInText(code: String): String = languageNameInText(code, rememberUiLocale())
