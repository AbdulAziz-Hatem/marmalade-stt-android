package app.marmalade.stt.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.ui.theme.MarmaladeSttTheme

// Previews of the Engines tab's pieces in light and dark, the design lab's
// scenario (docs/design/engine-cards-lab.html): Parakeet Ultra in use and
// recommended, Nemotron 3.5 downloading, Parakeet 110M installed, Whisper
// turbo not downloaded and too slow for the phone. The phone is unmeasured,
// so tiers come from the reference budget.

private val PreviewPrefs = SpeechPrefs(
    selectedEngineId = SttEngineCatalog.PARAKEET_ULTRA,
    recommendedEngineId = SttEngineCatalog.PARAKEET_ULTRA,
    developerEngines = true,
)

private fun engine(id: String): SttEngineDescriptor = checkNotNull(SttEngineCatalog.byId(id))

private val Downloading = InstallState.Downloading(bytesDone = 280_000_000L, totalBytes = 682_000_000L, currentFile = "encoder.int8.onnx")

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MarmaladeSttTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

@Preview(name = "A1 cards, light", widthDp = 412, heightDp = 1500)
@Preview(name = "A1 cards, dark", widthDp = 412, heightDp = 1500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EngineCardsPreview() {
    PreviewSurface {
        listOf(
            engine(SttEngineCatalog.PARAKEET_ULTRA) to InstallState.Installed,
            engine("parakeet-tdt-110m-en") to InstallState.Installed,
            engine(SttEngineCatalog.NEMOTRON_35_1120MS) to Downloading,
            engine("whisper-turbo") to InstallState.NotInstalled,
        ).forEach { (engine, state) ->
            EngineCard(engine = engine, state = state, prefs = PreviewPrefs, onUse = {}, onShowMore = {}, onAction = {})
        }
    }
}

@Preview(name = "A3 rows, light", widthDp = 412)
@Preview(name = "A3 rows, dark", widthDp = 412, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EngineRowsPreview() {
    PreviewSurface {
        listOf(
            engine(SttEngineCatalog.PARAKEET_ULTRA) to InstallState.Installed,
            engine("parakeet-tdt-110m-en") to InstallState.Installed,
            engine(SttEngineCatalog.NEMOTRON_35_1120MS) to Downloading,
            engine("canary-180m-flash") to InstallState.NotInstalled,
            engine("whisper-turbo") to InstallState.Failed(reason = "", bytesOnDisk = 1L),
        ).forEach { (engine, state) ->
            EngineRow(engine = engine, state = state, prefs = PreviewPrefs, onSelect = {}, onShowMore = {}, onAction = {})
        }
    }
}

@Preview(name = "B1 in use, light", widthDp = 412, heightDp = 1100)
@Composable
private fun EngineDetailsInUsePreview() {
    DetailsPreview(engine(SttEngineCatalog.PARAKEET_ULTRA), InstallState.Installed)
}

@Preview(name = "B1 not downloaded, dark", widthDp = 412, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EngineDetailsNotDownloadedPreview() {
    DetailsPreview(engine(SttEngineCatalog.NEMOTRON_35_1120MS), InstallState.NotInstalled)
}

@Composable
private fun DetailsPreview(engine: SttEngineDescriptor, state: InstallState) {
    PreviewSurface {
        EngineDetails(
            engine = engine,
            state = state,
            prefs = PreviewPrefs,
            checking = false,
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
            onChooseLanguage = {},
            onOpenLicense = {},
            onDismiss = {},
        )
    }
}
