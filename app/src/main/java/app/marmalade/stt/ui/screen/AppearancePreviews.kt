package app.marmalade.stt.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.ui.theme.Appearance
import app.marmalade.stt.ui.theme.MarmaladeSttTheme
import app.marmalade.stt.ui.theme.ThemeMode
import app.marmalade.stt.ui.theme.ThemePreset

// Settings → Appearance (#35) and the engine card in use under each curated
// preset, light and dark: the card carries the accent pill, outline, links
// and quality dots (MaterialTheme.accent).

private val PreviewPrefs = SpeechPrefs(
    selectedEngineId = SttEngineCatalog.PARAKEET_ULTRA,
    recommendedEngineId = SttEngineCatalog.PARAKEET_ULTRA,
)

@Composable
private fun PresetSample(appearance: Appearance) {
    MarmaladeSttTheme(appearance) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                AppearanceSection(appearance = appearance, onModeChange = {}, onPresetChange = {})
                EngineCard(
                    engine = SttEngineCatalog.default,
                    state = InstallState.Installed,
                    prefs = PreviewPrefs,
                    onUse = {},
                    onShowMore = {},
                    onAction = {},
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Preview(name = "Presets, light", widthDp = 412, heightDp = 2400)
@Composable
private fun PresetsLightPreview() {
    Column {
        (ThemePreset.entries - ThemePreset.SYSTEM).forEach { PresetSample(Appearance(it, ThemeMode.LIGHT)) }
    }
}

@Preview(name = "Presets, dark", widthDp = 412, heightDp = 2400)
@Composable
private fun PresetsDarkPreview() {
    Column {
        (ThemePreset.entries - ThemePreset.SYSTEM).forEach { PresetSample(Appearance(it, ThemeMode.DARK)) }
    }
}
