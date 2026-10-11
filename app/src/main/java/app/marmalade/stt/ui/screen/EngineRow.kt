package app.marmalade.stt.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marmalade.stt.R
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.EngineQuality
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SpeedTier
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.ui.MarmaladeIcons
import app.marmalade.stt.ui.progressFraction
import app.marmalade.stt.ui.progressPercent
import app.marmalade.stt.ui.theme.accent
import java.util.Locale

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   EnginesScreen → "Other engines" expanded → EngineRow per EngineGroups.other
//     engineCardUi(engine, state, prefs) → radio (installed) and whether a tap picks
//     prefs.speedTier / EngineQuality.rating (ratingLanguage) / EngineLanguages → the marker line
//     languageNote, or shortStatusText + engineFacts → the second line
//
//   Row tap:  installed and pickable → onSelect → vm.select(id)
//             otherwise              → onShowMore → EngineInfoDialog (B1)
//   Disc tap: Download / Resume / Retry / Download again → onAction
//   Chevron:  onShowMore (B1 holds Remove, Check files and the rest)
// -----------------------------------------------------------------------------

/**
 * One engine as a compact row, the design lab's A3 (Max, 2026-09-28), for the
 * engines behind "Other engines": a leading disc (a radio once installed, a
 * download button before, a progress ring while downloading), the name, one
 * line of markers (speed · quality · languages) and one line of facts, and a
 * chevron to the details dialog. Removing happens there.
 */
@Composable
fun EngineRow(
    engine: SttEngineDescriptor,
    state: InstallState,
    prefs: SpeechPrefs?,
    onSelect: () -> Unit,
    onShowMore: () -> Unit,
    onAction: (EngineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = engineCardUi(engine, state, prefs)
    val tier = prefs?.speedTier(engine)
    val selected = ui.selection == EngineSelection.SELECTED
    val facts = engineFacts(engine, prefs?.recommendedEngineId, prefs?.developerEngines == true, inUse = selected, tier = tier)
    val systemLocale = Locale.getDefault().toLanguageTag()
    val note = languageNote(engine, prefs, systemLocale)
    val language = ratingLanguage(prefs, systemLocale)
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val showMoreLabel = stringResource(R.string.engine_info, engine.displayName)
    // An installed row is a radio button (the whole row picks the engine);
    // any other opens the details, like its chevron.
    val interaction = if (ui.selection == EngineSelection.NONE) {
        Modifier.clickable(onClickLabel = showMoreLabel, onClick = onShowMore)
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    }
    Surface(
        shape = shape,
        color = if (selected) colors.surfaceContainer else colors.surface,
        contentColor = colors.onSurface,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.accent.ink)
        } else {
            BorderStroke(1.dp, colors.outlineVariant)
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(interaction),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(start = 8.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLead(state = state, selection = ui.selection, engineName = engine.displayName, onAction = onAction)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = engine.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                )
                Spacer(Modifier.height(4.dp))
                RowMarkers(engine = engine, tier = tier, language = language)
                Spacer(Modifier.height(4.dp))
                if (note != null) {
                    Text(
                        text = languageNoteText(note),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // The note replaces the size and facts, but not a download's
                // progress or problem, which nothing else on the row would say (#68).
                val hasStatus = state != InstallState.Installed && state != InstallState.NotInstalled
                if (note == null || hasStatus) {
                    if (note != null) Spacer(Modifier.height(2.dp))
                    RowFacts(state = state, sizeBytes = engine.sizeBytes, facts = facts)
                }
            }
            IconButton(onClick = onShowMore) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = showMoreLabel,
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The 48 dp leading slot: a radio for an installed engine (shows the state
 * only, the row is the button; dimmed when it can't be picked), a progress
 * ring with the percent while downloading, else a download disc that starts,
 * resumes or retries the download ("Retry <engine>" for TalkBack and Voice
 * Access, which list several rows' discs side by side, #68).
 */
@Composable
private fun RowLead(
    state: InstallState,
    selection: EngineSelection,
    engineName: String,
    onAction: (EngineAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when (state) {
            InstallState.Installed -> RadioButton(
                selected = selection == EngineSelection.SELECTED,
                onClick = null,
                enabled = selection != EngineSelection.NONE,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.accent.ink),
            )
            is InstallState.Downloading -> {
                CircularProgressIndicator(
                    progress = { progressFraction(state.bytesDone, state.totalBytes) },
                    // "Download progress, 45 percent", as the card's bar (#68).
                    modifier = Modifier
                        .size(36.dp)
                        .downloadProgressSemantics(state.bytesDone, state.totalBytes),
                    color = MaterialTheme.accent.ink,
                    strokeWidth = 4.dp,
                    trackColor = colors.outlineVariant,
                    strokeCap = StrokeCap.Round,
                )
                // The ring already tells TalkBack the progress.
                Text(
                    text = stringResource(
                        R.string.engine_progress_percent,
                        progressPercent(state.bytesDone, state.totalBytes),
                    ),
                    // In dp, not scaled sp: the ring doesn't grow with the font, and a
                    // bigger "100%" spilled over its stroke. The row's status line
                    // has the progress in text that does scale (#68).
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = with(LocalDensity.current) { 10.dp.toSp() }),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            else -> {
                val action = engineActions(state, onDetail = true).first { it.primary }
                val label = stringResource(action.labelRes())
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onAction(action) }
                        .spokenAs(namedActionLabel(label, engineName)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MarmaladeIcons.Download,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Speed meter · quality meter and word · "English" or "25 languages". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowMarkers(engine: SttEngineDescriptor, tier: SpeedTier?, language: String) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val centered = Modifier.align(Alignment.CenterVertically)
        if (tier == null) {
            Text(stringResource(R.string.engine_speed_unmeasured), style = small, color = colors.onSurfaceVariant)
        } else {
            val phrase = stringResource(speedMeterRes(tier))
            SpeedMeter(tier = tier, modifier = centered.semantics { contentDescription = phrase })
        }
        MarkerDot(centered)
        val rating = EngineQuality.rating(engine, language)
        if (rating == null) {
            Text(text = qualityText(null, language), style = small, color = colors.onSurfaceVariant)
        } else {
            // Rows have no SPEED / QUALITY labels, so the word ("Great (English)") follows the
            // meter: it tells the two meters apart. One TalkBack stop.
            val spoken = qualityText(rating, language)
            Row(
                modifier = centered.clearAndSetSemantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QualityMeter(stars = rating.stars)
                Text(text = spoken, style = small, fontWeight = FontWeight.SemiBold)
            }
        }
        MarkerDot(centered)
        val languages = when (val label = languagesLabel(EngineLanguages.of(engine))) {
            is LanguagesLabel.One -> languageName(label.code)
            is LanguagesLabel.Many -> pluralStringResource(R.plurals.engine_languages_count, label.count, label.count)
        }
        Text(text = languages, style = small, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun MarkerDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline),
    )
}

/**
 * The row's second line: the size or install status, then its facts,
 * dot-separated. "Recommended for this phone" in the warm ink, "In use" and
 * "May be slow on this phone" in bold (the latter after a red warning sign),
 * a failed or damaged download in the error colour.
 */
@Composable
private fun RowFacts(state: InstallState, sizeBytes: Long, facts: List<EngineFact>) {
    val colors = MaterialTheme.colorScheme
    val status = shortStatusText(state, sizeBytes)
    val problem = state is InstallState.Failed || state is InstallState.Corrupt
    val labels = facts.map { it to factLabel(it) }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = if (problem) colors.error else colors.onSurfaceVariant)) { append(status) }
        labels.forEach { (fact, label) ->
            append(FACT_SEPARATOR)
            when (fact) {
                EngineFact.Recommended ->
                    withStyle(SpanStyle(color = MaterialTheme.accent.ink, fontWeight = FontWeight.SemiBold)) { append(label) }
                EngineFact.InUse ->
                    withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold)) { append(label) }
                EngineFact.MayBeSlow -> {
                    appendInlineContent(WARNING_ICON, alternateText = "⚠")
                    append(" ")
                    withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold)) { append(label) }
                }
                is EngineFact.Streaming, EngineFact.Developer -> append(label)
            }
        }
    }
    val warning = InlineTextContent(Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.Center)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = slowColor())
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        inlineContent = mapOf(WARNING_ICON to warning),
    )
}

private const val WARNING_ICON = "warning"
