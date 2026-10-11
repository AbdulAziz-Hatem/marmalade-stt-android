package app.marmalade.stt.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

// Right-to-left UIs (Arabic, #69). Compose lays every Text out in the UI's
// direction unless its style says otherwise, so in Arabic an English
// transcript "Hello." shows as ".Hello" and "1 Main St." as "Main St. 1".
// Text that isn't the app's own translated words (what the user said or
// typed, the English privacy policy and licences, another app's prompt) uses
// [contentDirection]: its own first strong letter sets its direction, and
// TextAlign.Start then follows that direction.

/** This style, laid out in the direction of its text's own script instead of the UI's. */
fun TextStyle.contentDirection(): TextStyle = copy(textDirection = TextDirection.Content)

/**
 * [text] as a Unicode bidi isolate (FSI … PDI): user text inside a translated
 * sentence keeps its own direction, and can't reorder the words around it.
 */
fun isolate(text: String): String = "\u2068$text\u2069"
