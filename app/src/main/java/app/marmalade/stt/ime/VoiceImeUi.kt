package app.marmalade.stt.ime

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.ui.MarmaladeIcons
import app.marmalade.stt.ui.components.micColors
import app.marmalade.stt.ui.components.rememberAnnouncer
import app.marmalade.stt.ui.components.rememberMicMeter
import app.marmalade.stt.ui.contentDirection
import app.marmalade.stt.ui.screen.DictationNotice
import app.marmalade.stt.ui.theme.Appearance
import app.marmalade.stt.ui.theme.MarmaladeSttTheme
import app.marmalade.stt.ui.theme.accent
import app.marmalade.ui.theme.MarmaladeMotion
import app.marmalade.ui.theme.depth
import app.marmalade.ui.theme.riseIn

private val MIC_SIZE = 72.dp

/** Extra ring radius at full input level, beyond the mic button's edge. */
private val RING_REACH = 20.dp

/** Room for three lines of partial at 100 % font (or a blocker message and its button). */
private val BODY_HEIGHT = 88.dp

/** The partial area's vertical padding, inside [BODY_HEIGHT]. */
private val BODY_PADDING = 8.dp

/**
 * The voice keyboard panel: status, the live partial, and Cancel · mic · Done.
 * About as tall as a keyboard. [level] is read only in the draw phase.
 */
@Composable
fun VoiceImePanel(
    state: VoiceImeState,
    appearance: Appearance,
    level: () -> Float,
    onCancel: () -> Unit,
    onMic: () -> Unit,
    onMicLongPress: () -> Unit,
    onDone: () -> Unit,
    onAllowMic: () -> Unit,
    onOpenApp: () -> Unit,
) {
    MarmaladeSttTheme(appearance) {
        // A hairline along the top sets the panel off the app above it (#84).
        // No bar shadow: the IME window ends at the panel's top edge, and its
        // frame clips anything drawn above it.
        val line = MaterialTheme.depth.line
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    drawLine(line, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                },
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val blocker = state.blocker
                if (blocker != null) {
                    BlockerMessage(blocker = blocker, onAllowMic = onAllowMic, onOpenApp = onOpenApp)
                } else {
                    StatusLine(state)
                    PartialText(state)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ime_cancel))
                    }
                    MicButton(state = state, level = level, onClick = onMic, onLongPress = onMicLongPress)
                    // primaryContainer like the app's other accent actions: the
                    // tonal default (secondaryContainer) vanishes on the cream
                    // light surface, and dark primary is orange.
                    FilledTonalIconButton(
                        onClick = onDone,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.ime_done))
                    }
                }
            }
        }
    }
}

/**
 * What the session is doing, or what went wrong last time; "Locked: tap to
 * stop" with a lock icon while locked (#43). Announced politely by TalkBack.
 *
 * A new status rises in over the old one (#84). The live region and the
 * words TalkBack reads sit on the outer Row, which stays put; the animated
 * lines inside are hidden from it, so the line on its way out is never read.
 */
@Composable
private fun StatusLine(state: VoiceImeState) {
    val notice = state.notice
    val locked = state.locked && state.phase.isActive
    val text = when {
        locked -> R.string.lock_status
        !state.phase.isActive && notice != null -> notice.messageRes()
        else -> state.phase.statusRes()
    }
    val spoken = stringResource(text)
    val enter = riseIn(MarmaladeMotion.BASE_MS)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                this.text = AnnotatedString(spoken)
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = ImeStatus(text, locked),
            contentAlignment = Alignment.Center,
            // No size animation: the panel's height drives the app's
            // insets, so it changes once, not every frame.
            transitionSpec = { (enter togetherWith MarmaladeMotion.fadeExit) using null },
            label = "imeStatus",
        ) { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status.locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = stringResource(status.textRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // No maxLines: error notices need three or four lines at large
                    // font scales, and the panel has room to grow (#68).
                )
            }
        }
    }
}

/** One status line's look: its words and whether the lock shows. */
private data class ImeStatus(@StringRes val textRes: Int, val locked: Boolean)

/**
 * The live partial in muted italics, or "Speak now" while the mic waits.
 * Long partials scroll: reverse scrolling keeps the newest words in view.
 * The box holds three lines at any font scale (#68): [BODY_HEIGHT] at 100 %,
 * taller when the font is bigger. Its height doesn't follow the text, so the
 * mic row doesn't jump as a partial comes and goes.
 *
 * The hint and the partial fade in and out rather than pop (#84); a partial
 * growing word by word just updates in place.
 */
@Composable
private fun PartialText(state: VoiceImeState) {
    val hint = state.partial.isEmpty()
    val style = MaterialTheme.typography.bodyLarge
    // lineHeight.toDp() per line, not (3 * lineHeight).toDp(): non-linear
    // font scaling grows a 72 sp size less than three 24 sp lines.
    val threeLines = with(LocalDensity.current) { style.lineHeight.toDp() } * 3
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(BODY_HEIGHT, threeLines + BODY_PADDING * 2))
            .padding(vertical = BODY_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        val shown = when {
            !hint -> PartialShown(PartialKind.PARTIAL, state.partial)
            state.phase == VoiceImePhase.LISTENING -> PartialShown(PartialKind.HINT, "")
            else -> PartialShown(PartialKind.NONE, "")
        }
        AnimatedContent(
            targetState = shown,
            // Keyed on the kind: a new partial is the same content, recomposed
            // in place. The partial on its way out keeps its last words.
            contentKey = { it.kind },
            contentAlignment = Alignment.Center,
            transitionSpec = { (MarmaladeMotion.fadeEnter togetherWith MarmaladeMotion.fadeExit) using null },
            label = "imePartial",
        ) { content ->
            if (content.kind == PartialKind.NONE) return@AnimatedContent
            val isHint = content.kind == PartialKind.HINT
            Text(
                text = if (isHint) stringResource(R.string.ime_speak_now) else content.text,
                style = if (isHint) style else style.contentDirection(),
                fontStyle = if (isHint) FontStyle.Normal else FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
            )
        }
    }
}

/**
 * Why the keyboard can't listen, with the one action that fixes it. The
 * message replaces the status line, so it is a new node, which a live region
 * wouldn't announce: it is spoken once when it appears instead (#68).
 */
@Composable
private fun BlockerMessage(blocker: VoiceImeBlocker, onAllowMic: () -> Unit, onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BODY_HEIGHT + 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        val (message, action, onClick) = when (blocker) {
            VoiceImeBlocker.NO_MIC_PERMISSION -> Triple(R.string.ime_mic_needed, R.string.ime_mic_allow, onAllowMic)
            VoiceImeBlocker.NO_MODEL -> Triple(R.string.ime_no_model, R.string.ime_open_app, onOpenApp)
        }
        val messageText = stringResource(message)
        val announce = rememberAnnouncer()
        LaunchedEffect(messageText) { announce(messageText) }
        Text(
            text = messageText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // primaryContainer: the theme's dark primary is orange, which
        // marmalade-design keeps off dark surfaces.
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(stringResource(action))
        }
    }
}

/**
 * Pause/resume, with a ring behind it that grows with the input level. The
 * level is read only in the draw phase, so its ten updates a second redraw
 * the ring without recomposing anything. Colours: [micColors], shared with
 * the Dictate screen's mic and the speech sheet's. Pressing and
 * holding locks listening (#43) while [VoiceImeState.canLock]; otherwise a
 * long press is a tap. Locked, it shows a lock.
 */
@OptIn(ExperimentalFoundationApi::class) // combinedClickable (press-and-hold)
@Composable
private fun MicButton(state: VoiceImeState, level: () -> Float, onClick: () -> Unit, onLongPress: () -> Unit) {
    val phase = state.phase
    val clickable = state.blocker == null && phase != VoiceImePhase.FINISHING
    val active = phase.isActive
    val locked = state.locked && active
    val micColors = micColors(clickable)
    val meter = rememberMicMeter(level)

    val description = stringResource(if (active) R.string.ime_pause else R.string.ime_resume)
    // A state per phase, as the Dictate mic: "Pause listening" alone is
    // misleading while the model is still loading (#68).
    val stateText = stringResource(if (locked) R.string.lock_state else phase.buttonStateRes())
    val canLock = clickable && state.canLock

    Box(
        modifier = Modifier
            .size(MIC_SIZE + RING_REACH * 2)
            .drawBehind {
                if (active) {
                    val radius = MIC_SIZE.toPx() / 2 + RING_REACH.toPx() * meter.value
                    drawCircle(color = micColors.ring, radius = radius)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (phase == VoiceImePhase.LOADING || phase == VoiceImePhase.FINISHING) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(MIC_SIZE + 10.dp)
                    .clearAndSetSemantics {},
                color = MaterialTheme.accent.progress,
                strokeWidth = 3.dp,
            )
        }
        Box(
            modifier = Modifier
                .size(MIC_SIZE)
                .clip(CircleShape)
                .background(micColors.container)
                .combinedClickable(
                    enabled = clickable,
                    role = Role.Button,
                    onLongClickLabel = if (canLock) stringResource(R.string.lock_action) else null,
                    onLongClick = if (canLock) onLongPress else null,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = description
                    stateDescription = stateText
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    locked -> Icons.Filled.Lock
                    active -> MarmaladeIcons.Stop
                    else -> MarmaladeIcons.Mic
                },
                contentDescription = null,
                tint = micColors.content,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private enum class PartialKind { NONE, HINT, PARTIAL }

private data class PartialShown(val kind: PartialKind, val text: String)

@StringRes
private fun VoiceImePhase.statusRes(): Int = when (this) {
    VoiceImePhase.IDLE -> R.string.ime_status_paused
    VoiceImePhase.LOADING -> R.string.ime_status_loading
    VoiceImePhase.LISTENING -> R.string.ime_status_listening
    VoiceImePhase.HEARING -> R.string.ime_status_hearing
    VoiceImePhase.FINISHING -> R.string.ime_status_finishing
}

/** The mic's TalkBack state, in the Dictate mic's words. */
@StringRes
private fun VoiceImePhase.buttonStateRes(): Int = when (this) {
    VoiceImePhase.IDLE -> R.string.dictate_state_idle
    VoiceImePhase.LOADING -> R.string.dictate_state_loading
    VoiceImePhase.LISTENING, VoiceImePhase.HEARING -> R.string.dictate_state_listening
    VoiceImePhase.FINISHING -> R.string.dictate_state_finishing
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
