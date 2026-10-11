package app.marmalade.stt.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.marmalade.stt.ui.theme.accent

/**
 * An outlined button in the brand's colours (marmalade-design): toast ink
 * text in light mode, toast in dark ([MaterialTheme.accent]'s ink, the
 * preset's own in the others). Material's own takes `primary`, which is
 * orange in light mode, and orange is kept for each screen's one accent
 * action. Not `onPrimaryContainer`: that is text for a container, and
 * Material You schemes don't promise it reads on the page.
 */
@Composable
fun MarmaladeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.accent.ink),
        content = content,
    )
}

/**
 * A text button for dialog actions, Skip/Back and other quiet actions, in
 * [MaterialTheme.accent]'s `text` colour: orange 500 in Marmalade's light
 * mode (the brand, Max 2026-10-06), ink everywhere else, so the High
 * contrast preset and dark mode stay at WCAG AA (#68). Pass the `ink` where
 * a button keeps the ink in every preset (Copy / Clear, the engine screens).
 * Use this, never a bare `TextButton` (`ButtonContrastSourceTest` checks).
 */
@Composable
fun MarmaladeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.accent.text,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        content = content,
    )
}
