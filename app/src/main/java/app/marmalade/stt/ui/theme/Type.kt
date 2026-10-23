package app.marmalade.stt.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.marmalade.stt.R

// =============================================================================
// The type scale and Manrope come from the shared design library
// (app.marmalade.ui.theme.MarmaladeTypography, #87). This app keeps its own
// wordmark family, as it always drew it (#84 P: skipped).
// =============================================================================

/** Wordmark "marmalade" — Momo Trust Display, the brand display face (matches
 *  the marmalade-agent web UI); Fredoka is the fallback, reached only if the
 *  Momo resource is ever missing. Both sit in the Normal (400) slot — Momo
 *  ships a single Regular master, so the 600 requested at the call site is
 *  synthesized, exactly as the browser faux-bolds it on the web. Momo is first
 *  in the list, so it always wins over the bundled Fredoka. Momo is the
 *  library's copy of the font. Always rendered lowercase, tracking 0. */
val Wordmark = FontFamily(
    Font(app.marmalade.ui.R.font.momo_trust_display, weight = FontWeight.Normal),
    Font(R.font.fredoka_variable, weight = FontWeight.Normal),
)
