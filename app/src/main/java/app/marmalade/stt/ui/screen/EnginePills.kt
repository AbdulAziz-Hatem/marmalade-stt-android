package app.marmalade.stt.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.ui.theme.accent

// The pills of the Engines card, the details dialog and the setup guide's
// recommended engine (the design lab's .pill): small non-interactive labels.
// "Recommended for this phone" and "In use" are Toast, the one warm fill on
// the card; "May be slow on this phone" is outlined with the meter's red
// warning sign; the rest are neutral.

/** [facts] as pills, wrapping at large font sizes. [long] uses the dialog's fuller wording ("Developer engine"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnginePills(facts: List<EngineFact>, modifier: Modifier = Modifier, long: Boolean = false) {
    if (facts.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        facts.forEach { fact ->
            val text = factLabel(fact, long)
            when (fact) {
                EngineFact.Recommended, EngineFact.InUse -> AccentPill(text)
                EngineFact.MayBeSlow -> SlowPill(text)
                is EngineFact.Streaming, EngineFact.Developer -> Pill(
                    text = text,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What [fact] says: "Streaming · 1120 ms steps", "Developer" ([long]: "Developer engine"). */
@Composable
fun factLabel(fact: EngineFact, long: Boolean = false): String = when (fact) {
    EngineFact.Recommended -> stringResource(R.string.engine_badge_recommended)
    EngineFact.InUse -> stringResource(R.string.engine_badge_in_use)
    is EngineFact.Streaming -> fact.stepMs?.let { stringResource(R.string.engine_streaming_steps, it) }
        ?: stringResource(R.string.engine_badge_streaming)
    EngineFact.MayBeSlow -> stringResource(R.string.engine_badge_may_be_slow_long)
    EngineFact.Developer ->
        stringResource(if (long) R.string.engine_badge_developer_long else R.string.engine_badge_developer)
}

/** "Recommended for this phone": Toast with its ink in both modes (the preset's pill colour, [MaterialTheme.accent]). */
@Composable
fun AccentPill(text: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.accent
    Pill(text = text, color = accent.pill, contentColor = accent.pillInk, bold = true, modifier = modifier)
}

/**
 * The A1 card's "✓ In use", where the Use button would be: a larger Toast
 * pill, button height so the action row doesn't jump when it turns into one.
 */
@Composable
fun InUsePill(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.accent
    Surface(shape = RoundedCornerShape(20.dp), color = accent.pill, contentColor = accent.pillInk, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.engine_badge_in_use),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** "May be slow on this phone": outlined, with a red warning sign (red alone doesn't say it; the words do). */
@Composable
private fun SlowPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = slowColor(), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Surface(shape = MaterialTheme.shapes.small, color = color, contentColor = contentColor, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
