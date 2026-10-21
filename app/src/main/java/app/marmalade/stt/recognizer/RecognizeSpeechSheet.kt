package app.marmalade.stt.recognizer

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marmalade.stt.R
import app.marmalade.stt.ui.MarmaladeIcons
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.components.micColors
import app.marmalade.stt.ui.components.rememberAnnouncer
import app.marmalade.stt.ui.components.rememberMicMeter
import app.marmalade.stt.ui.contentDirection
import app.marmalade.stt.ui.screen.languageNameInText
import app.marmalade.stt.ui.theme.LocalWordmarkColor
import app.marmalade.stt.ui.theme.Wordmark

private val MIC_SIZE = 64.dp
private val RING_REACH = 20.dp

/**
 * The speech-input sheet: a card at the bottom of the screen over a scrim.
 * Tapping the scrim closes it, like any dialog.
 *
 * @param prompt the caller's `EXTRA_PROMPT`, shown instead of "Speak now".
 * @param level mic level 0..1, read only in the draw phase.
 */
@Composable
fun RecognizeSpeechSheet(
    state: SheetState,
    prompt: String?,
    level: () -> Float,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onLock: () -> Unit,
    onRetry: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            // The scrim closes the sheet; TalkBack users have the Cancel button.
            // Not focusable either: a keyboard user would Tab onto an invisible
            // stop whose Enter cancels the request (#68).
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            )
            .clearAndSetSemantics {},
    )
    // Drawn over the scrim. Only the card handles touches (a Surface swallows
    // them); taps elsewhere fall through to the scrim. Below the status bar,
    // for when the card fills the window.
    val paneTitle = prompt ?: stringResource(R.string.recognizer_speak_now)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            // A pane title tells TalkBack what this dialog-like card is (#68).
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.paneTitle = paneTitle },
        ) {
            // Scrolls when it doesn't fit (landscape, 200 % font, a long
            // prompt), so Done/Cancel can't end up off the screen (#68).
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.wordmark),
                    fontFamily = Wordmark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = LocalWordmarkColor.current,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.height(12.dp))
                if (state.phase == SheetPhase.PROBLEM) {
                    ProblemBody(state, onCancel, onRetry, onOpenApp, onOpenSettings)
                } else {
                    ListeningBody(state, prompt, level, onCancel, onDone, onLock)
                }
            }
        }
    }
}

@Composable
private fun ListeningBody(
    state: SheetState,
    prompt: String?,
    level: () -> Float,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onLock: () -> Unit,
) {
    Text(
        text = prompt ?: stringResource(R.string.recognizer_speak_now),
        // The prompt is the calling app's text, in whatever language it chose.
        style = if (prompt != null) MaterialTheme.typography.titleMedium.contentDirection() else MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(4.dp))
    if (state.locked) {
        LockedStatus()
    } else {
        Text(
            text = stringResource(state.phase.statusRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(12.dp))
    MicButton(state, level, onTap = onDone, onLongPress = onLock)
    Spacer(Modifier.height(12.dp))
    // Up to three lines, then it scrolls: reverse scrolling keeps the newest
    // words in view, where an ellipsis would freeze it at the start (#68).
    val partialStyle = MaterialTheme.typography.bodyLarge
    val threeLines = with(LocalDensity.current) { partialStyle.lineHeight.toDp() } * 3
    Text(
        text = state.partial,
        style = partialStyle.contentDirection(),
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = threeLines)
            .verticalScroll(rememberScrollState(), reverseScrolling = true),
    )
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MarmaladeTextButton(onClick = onCancel) { Text(stringResource(R.string.recognizer_cancel)) }
        AccentButton(onClick = onDone, enabled = state.phase != SheetPhase.FINISHING, textRes = R.string.recognizer_done)
    }
}

@Composable
private fun ProblemBody(
    state: SheetState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val problem = state.problem
    // The message replaces the listening body, so it is a new node, which a
    // live region wouldn't announce: it is spoken once when it appears (#68).
    val message = when (problem) {
        // The caller's language, named in the UI's language (#90).
        SheetProblem.LANGUAGE_UNAVAILABLE, SheetProblem.LANGUAGE_NOT_SUPPORTED ->
            stringResource(problem.messageRes(), languageNameInText(state.language.orEmpty()))
        else -> stringResource(problem.messageRes())
    }
    val announce = rememberAnnouncer()
    LaunchedEffect(message) { announce(message) }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MarmaladeTextButton(onClick = onCancel) { Text(stringResource(R.string.recognizer_cancel)) }
        when {
            problem == SheetProblem.NO_MODEL || problem == SheetProblem.LANGUAGE_UNAVAILABLE ->
                AccentButton(onClick = onOpenApp, textRes = R.string.recognizer_open_app)
            problem == SheetProblem.MIC_BLOCKED ->
                AccentButton(onClick = onOpenSettings, textRes = R.string.recognizer_open_settings)
            problem?.canRetry == true ->
                AccentButton(onClick = onRetry, textRes = R.string.recognizer_try_again)
        }
    }
}

/**
 * The sheet's main action. primaryContainer, like the Dictate screen's CTA:
 * the theme's dark primary is orange, which marmalade-design keeps off dark
 * surfaces, and the tonal default (secondaryContainer) vanishes on the cream
 * light surface.
 */
@Composable
private fun AccentButton(onClick: () -> Unit, @StringRes textRes: Int, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(stringResource(textRes))
    }
}

/**
 * The mic with a ring that grows with the input level. A tap does what Done
 * does; pressing and holding locks listening (#43, [SheetState.canLock]), so
 * the request doesn't end at the first pause. Colours: [micColors], shared
 * with the Dictate screen's mic and the voice keyboard's; disabled (the
 * neutral pair) while finishing.
 */
@OptIn(ExperimentalFoundationApi::class) // combinedClickable (press-and-hold)
@Composable
private fun MicButton(state: SheetState, level: () -> Float, onTap: () -> Unit, onLongPress: () -> Unit) {
    val phase = state.phase
    val enabled = phase != SheetPhase.FINISHING
    val busy = phase == SheetPhase.LOADING || phase == SheetPhase.FINISHING
    val micColors = micColors(enabled)
    val meter = rememberMicMeter(level)

    val description = stringResource(R.string.recognizer_mic_stop)
    // A state per phase, as the Dictate mic: "Stop listening" alone is
    // misleading while the model is still loading (#68).
    val stateText = stringResource(if (state.locked) R.string.lock_state else phase.buttonStateRes())
    Box(
        modifier = Modifier
            .size(MIC_SIZE + RING_REACH * 2)
            .drawBehind {
                drawCircle(color = micColors.ring, radius = MIC_SIZE.toPx() / 2 + RING_REACH.toPx() * meter.value)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(MIC_SIZE + 10.dp)
                    .clearAndSetSemantics {},
                strokeWidth = 3.dp,
            )
        }
        Box(
            modifier = Modifier
                .size(MIC_SIZE)
                .clip(CircleShape)
                .background(micColors.container)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onLongClickLabel = if (state.canLock) stringResource(R.string.lock_action) else null,
                    onLongClick = if (state.canLock) onLongPress else null,
                    onClick = onTap,
                )
                .semantics {
                    contentDescription = description
                    stateDescription = stateText
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.locked) Icons.Filled.Lock else MarmaladeIcons.Mic,
                contentDescription = null,
                tint = micColors.content,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@StringRes
private fun SheetPhase.statusRes(): Int = when (this) {
    SheetPhase.LOADING -> R.string.dictate_status_loading
    SheetPhase.LISTENING -> R.string.dictate_status_listening
    SheetPhase.HEARING -> R.string.dictate_status_hearing
    SheetPhase.FINISHING, SheetPhase.PROBLEM -> R.string.dictate_status_finishing
}

/** The mic's TalkBack state, in the Dictate mic's words. */
@StringRes
private fun SheetPhase.buttonStateRes(): Int = when (this) {
    SheetPhase.LOADING -> R.string.dictate_state_loading
    SheetPhase.LISTENING, SheetPhase.HEARING -> R.string.dictate_state_listening
    SheetPhase.FINISHING -> R.string.dictate_state_finishing
    SheetPhase.PROBLEM -> R.string.dictate_state_idle // no mic is shown then
}

@StringRes
private fun SheetProblem?.messageRes(): Int = when (this) {
    SheetProblem.NO_SPEECH, null -> R.string.dictate_no_speech
    SheetProblem.NO_MODEL -> R.string.recognizer_no_model
    SheetProblem.MIC_BLOCKED -> R.string.recognizer_mic_blocked
    SheetProblem.MIC_ERROR -> R.string.dictate_error_mic
    SheetProblem.RECOGNITION_ERROR -> R.string.dictate_error_recognition
    SheetProblem.BUSY -> R.string.dictate_error_busy
    SheetProblem.LANGUAGE_UNAVAILABLE -> R.string.recognizer_language_unavailable
    SheetProblem.LANGUAGE_NOT_SUPPORTED -> R.string.recognizer_language_not_supported
}

/** "Locked: tap to stop" with a lock icon; TalkBack announces it (a polite live region). */
@Composable
private fun LockedStatus() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.lock_status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
