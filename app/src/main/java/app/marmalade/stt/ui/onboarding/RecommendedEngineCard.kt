package app.marmalade.stt.ui.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SpeedTier
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.ui.formatBytes
import app.marmalade.stt.ui.rememberUiLocale
import app.marmalade.stt.ui.screen.AccentPill
import app.marmalade.stt.ui.screen.EngineAction
import app.marmalade.stt.ui.screen.EngineActionBar
import app.marmalade.stt.ui.screen.EngineStatus
import app.marmalade.stt.ui.screen.SpecTiles
import app.marmalade.stt.ui.screen.engineActions
import app.marmalade.stt.ui.screen.rememberResumeFocus
import app.marmalade.stt.ui.theme.MarmaladeSttTheme
import app.marmalade.stt.ui.components.MarmaladeOutlinedButton
import app.marmalade.ui.components.MarmaladeCard

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   OnboardingScreen → ModelStep → RecommendedEngineCard(recommendation, state, tier)
//     recommendation ◄── vm.recommendation (EngineRecommender via RecommendedEngine)
//     state          ◄── vm.modelState (the recommended engine's install state)
//     speedTier      ◄── vm.speechPrefs.speedTier(engine)
//     QUALITY tile   ◄── rated in recommendation.language: the language the
//                        recommendation was made for (SpeechPrefs.spokenLanguage:
//                        Recognition language, or on Auto the system's, #76)
//     why            ◄── recommendationText(rec) + autoDetectedLanguages(engine)
//
//   Not downloaded → the accent "Download · 670 MB" → onAction(DOWNLOAD)
//     → DownloadConfirmDialog (OnboardingScreen) → vm.download()
//   Otherwise      → EngineStatus + the install lifecycle's buttons
//                    (engineActions(state, onDetail = false)) → onAction
// -----------------------------------------------------------------------------

/**
 * The setup guide's recommended engine, the design lab's C1 (Max,
 * 2026-09-28): one card with the "Recommended for this phone" pill, the
 * engine, one line of why, SPEED / QUALITY / LANGUAGES as tiles and a
 * full-width Download in the accent, the screen's one main action. Once a
 * download starts the button gives way to its progress and buttons.
 */
@Composable
internal fun RecommendedEngineCard(
    recommendation: EngineRecommender.Recommendation,
    installState: InstallState,
    speedTier: SpeedTier?,
    onAction: (EngineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = recommendation.engine
    val colors = MaterialTheme.colorScheme
    val resumeFocus = rememberResumeFocus(installState)
    // The library's card (#84), as TTS's setup cards: the card step, a
    // hairline edge and the soft shadow, in both modes.
    MarmaladeCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Pill and name are one heading stop, "Recommended for this
            // phone, Parakeet …": the card's title, reachable by heading (#68).
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                AccentPill(stringResource(R.string.engine_badge_recommended))
                Spacer(Modifier.height(10.dp))
                Text(text = engine.displayName, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = recommendationWhy(recommendation),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            SpecTiles(engine = engine, speedTier = speedTier, language = recommendation.language, tileColor = colors.surface)
            Spacer(Modifier.height(14.dp))
            if (installState == InstallState.NotInstalled) {
                Button(
                    onClick = { onAction(EngineAction.DOWNLOAD) },
                    colors = accentButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.engine_action_download_size, formatBytes(engine.sizeBytes)))
                }
                Spacer(Modifier.height(8.dp))
                MarmaladeOutlinedButton(
                    onClick = { onAction(EngineAction.IMPORT) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.engine_action_import))
                }
            } else {
                EngineStatus(state = installState)
                val actions = engineActions(installState, onDetail = false)
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    EngineActionBar(actions = actions, onAction = onAction, resumeFocus = resumeFocus)
                }
                if (installState is InstallState.Failed || installState is InstallState.Paused) {
                    Spacer(Modifier.height(8.dp))
                    MarmaladeOutlinedButton(
                        onClick = { onAction(EngineAction.IMPORT) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.engine_action_import))
                    }
                }
            }
        }
    }
}

/** The reason ([recommendationText]), then "25 languages, detected automatically." where that applies. */
@Composable
private fun recommendationWhy(recommendation: EngineRecommender.Recommendation): String {
    val why = recommendationText(recommendation, rememberUiLocale())
    val reason = stringResource(why.res, *why.args.toTypedArray())
    val languages = autoDetectedLanguages(recommendation.engine)
        ?.let { pluralStringResource(R.plurals.onboarding_recommend_languages, it, it) }
    return listOfNotNull(reason, languages).joinToString(" ")
}

// --- Previews (C1, light and dark) ---------------------------------------------

@Preview(name = "C1 light", widthDp = 380)
@Preview(name = "C1 dark", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendedEngineCardPreview() {
    PreviewCard(InstallState.NotInstalled)
}

@Preview(name = "C1 downloading, light", widthDp = 380)
@Preview(name = "C1 downloading, dark", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendedEngineCardDownloadingPreview() {
    PreviewCard(InstallState.Downloading(bytesDone = 280_000_000L, totalBytes = 670_000_000L, currentFile = "encoder.int8.onnx"))
}

@Composable
private fun PreviewCard(state: InstallState) {
    val engine = checkNotNull(SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA))
    MarmaladeSttTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            RecommendedEngineCard(
                recommendation = EngineRecommender.Recommendation(
                    engine = engine,
                    reason = EngineRecommender.Reason.BEST_FIT,
                    language = "en",
                    languageCovered = true,
                ),
                installState = state,
                speedTier = SpeedTier.FASTEST,
                onAction = {},
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
