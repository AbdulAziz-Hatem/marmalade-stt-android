package app.marmalade.stt.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.collectLatest

/** The mic button's fill, its icon, and the level ring behind it. */
@Immutable
data class MicColors(val container: Color, val content: Color, val ring: Color)

/**
 * Colours for the three mic buttons (Dictate screen, voice keyboard, speech
 * sheet), kept in one place so they stay alike: the accent button's
 * `primary` / `onPrimary`. That is orange with white in light mode (the
 * brand; dark mode is the AA-contrast theme, #68) and toast with toast ink
 * in dark mode, which keeps a big orange disc (the "Halloween"
 * anti-pattern) off the stone page. The rich brown disc it replaced was
 * 1.2:1 against the page (#68, Max's pick P2-B). Disabled (finishing, or
 * blocked) uses the neutral M3 pair. The ring is decorative (the status line
 * and the button's state say the same in words).
 */
@Composable
fun micColors(enabled: Boolean): MicColors {
    val colors = MaterialTheme.colorScheme
    return if (enabled) {
        MicColors(colors.primary, colors.onPrimary, colors.primary.copy(alpha = 0.22f))
    } else {
        MicColors(colors.onSurface.copy(alpha = 0.12f), colors.onSurface.copy(alpha = 0.38f), colors.primary.copy(alpha = 0.22f))
    }
}

/**
 * The mic level 0..1 for the ring, eased with a spring. With "Remove
 * animations" on (the animator duration scale is 0, read live as
 * [JarMascot] does) it stays at 0, so the ring doesn't pulse ten times a
 * second (#68). Read `.value` only in the draw phase.
 */
@Composable
fun rememberMicMeter(level: () -> Float): Animatable<Float, AnimationVector1D> {
    val meter = remember { Animatable(0f) }
    LaunchedEffect(meter) {
        val motion = coroutineContext[MotionDurationScale]
        snapshotFlow(level).collectLatest {
            if (motion?.scaleFactor == 0f) {
                meter.snapTo(0f)
            } else {
                meter.animateTo(it, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    return meter
}
