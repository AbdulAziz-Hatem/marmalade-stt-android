package app.marmalade.stt.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * Speaks [message] through TalkBack (or any screen reader) once, for an
 * async change that has no on-screen node to carry a live region: a download
 * finishing while its card shows other controls, a page swap (#68).
 * Prefer a live region on a node that stays put; use this when the text
 * appears in a node that wasn't there before, which live regions miss.
 *
 * `View.announceForAccessibility` is deprecated from API 36 in favour of
 * live regions and pane titles, but still delivered; keeping the one call
 * here makes it easy to replace.
 */
@Composable
fun rememberAnnouncer(): (String) -> Unit {
    val view = LocalView.current
    return { message -> @Suppress("DEPRECATION") view.announceForAccessibility(message) }
}
