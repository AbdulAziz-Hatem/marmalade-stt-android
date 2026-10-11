package app.marmalade.stt.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Warm rounded shapes for the Marmalade theme (8 / 12 / 16 / 20 / 28 dp,
 * per marmalade-design). Matches marmalade-android and marmalade-tts-android.
 */
val MarmaladeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
