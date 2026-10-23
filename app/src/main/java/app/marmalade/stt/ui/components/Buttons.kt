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
 * An outlined button in [MaterialTheme.accent]'s ink: toast ink in light
 * mode, the scheme's primary in dark (orange 400 in Marmalade), the
 * preset's own ink in the others. Material's own takes `primary`, which is
 * orange 500 in light mode, and that orange is kept for each screen's one
 * accent action. Not `onPrimaryContainer`: that is text for a container, and
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
 * [MaterialTheme.accent]'s `text` colour: the scheme's primary in
 * Marmalade (orange 500 in light mode, the brand, Max 2026-10-06; orange
 * 400 in dark, AA), an AA ink in High contrast and the other presets'
 * light mode (#68, #87). Pass the `ink` where
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
