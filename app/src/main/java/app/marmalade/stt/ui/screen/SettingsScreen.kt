package app.marmalade.stt.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.BuildConfig
import app.marmalade.stt.R
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.speech.OrtTuning
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.components.SettingsRow
import app.marmalade.stt.ui.components.SettingsSwitchRow
import app.marmalade.stt.ui.components.TopBarTitle
import app.marmalade.stt.ui.rememberUiLocale
import app.marmalade.ui.theme.depth
import java.util.Locale

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Bottom-nav "Settings" tab → SettingsScreen(onNavigateToAbout, …)
//     │
//     ├── Appearance section (#35, AppearanceSection.kt; first since #44) ◄── vm.appearance
//     │     (AppearanceSettings) — Mode / Color chips → vm.setThemeMode /
//     │     vm.setThemePreset → DataStore → MainActivity, the voice keyboard
//     │     and the speech sheet restyle live
//     │
//     ├── Speech section ◄── SettingsViewModel.speech ◄── SpeechSettings.prefs (DataStore)
//     │     "Recognition language" (with a line on what it does, #42) dialog
//     │       (Auto + EngineLanguages.userChoices)
//     │       → vm.setRecognitionLanguage → SpeechSettings → the language hint
//     │         (SpeechPrefs.languageFor) and SpeedCalibrator.recommendAgain
//     │         → the recommended engine on the Engines tab
//     │     "Keep engine ready" switch (keepModelLoaded, off by default since
//     │       #42): saved on toggle
//     │       → SpeechSettings.set*() → DataStore; the next listen() reads it
//     │     save failure → snackbar
//     │
//     ├── End of speech section (EndOfSpeechSection.kt, #43) ◄── the same prefs
//     │     Voice keyboard / Other apps rows → choice dialog → vm.setKeyboardEnd
//     │       / vm.setAppsEnd
//     │     pause length slider (dragged value kept locally, saved on release),
//     │       Smart Turn patience (dialog → vm.setPatience; only while a surface
//     │       uses Smart Turn, #42), end words (saved on focus loss or Done;
//     │       only while a surface uses an end word), then the hold-the-mic tip
//     │
//     ├── Text cleanup section (CleanupSection.kt)
//     │     ◄── SettingsViewModel.cleanup ◄── TextProcSettings.config (DataStore)
//     │     switches (English-only ones greyed out for other languages, #42;
//     │       Punctuation words under Voice commands): saved on change
//     │       → TextProcSettings.set*() → DataStore → config emits → every
//     │         listening surface rebuilds its TextPipeline
//     │     save failure → snackbar
//     │     "Replacement dictionary" → onNavigateToDictionary → Routes.CleanupDictionary
//     │     "Snippets"               → onNavigateToSnippets   → Routes.CleanupSnippets
//     │
//     ├── Support section (SupportSection.kt, #44): Support development →
//     │     GitHub Sponsors, More Marmalade → Max's GitHub, Report a bug →
//     │     BugReportUrl; each opens the browser (openUrl)
//     │
//     ├── About section
//     │     "Setup guide" → onOpenSetup        → AppRoot → Routes.Onboarding
//     │     About row     → onNavigateToAbout  → AppRoot → Routes.About
//     │
//     └── Developer section, last (#42) ◄── SettingsViewModel.speech
//           (developerEngines, deviceSpeedFactor) + vm.measuring
//           "Developer engines" switch → vm.setDeveloperEngines → DataStore →
//             Engines tab lists them; SpeechEngineManager falls back to the
//             default engine while they are hidden
//           Only while it is on:
//           "Re-measure phone speed" (disabled while measuring)
//             → vm.remeasureSpeed → SpeedCalibrator.remeasure (own scope) →
//             SpeechSettings.setDeviceProbe → budget on the Engines tab;
//             result → vm.speedMessage → snackbar
//           "Runtime tuning" → vm.setOrtTuning → SpeechSettings →
//             SpeechEngineManager rebuilds the engine; the A/B benchmark
//             loads its engines with it (#31)
//           "A/B benchmark" → onOpenBenchmark → AppRoot
// -----------------------------------------------------------------------------

/**
 * Settings tab: appearance, speech settings, end of speech, text cleanup,
 * support, About, then the developer options (last since #42, out of
 * everyone's way). Later issues add their sections before Support,
 * separated by HorizontalDividers. Tab destination — no back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToSnippets: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenBenchmark: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val speech by viewModel.speech.collectAsStateWithLifecycle()
    val cleanup by viewModel.cleanup.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val measuring by viewModel.measuring.collectAsStateWithLifecycle()
    val speedMessage by viewModel.speedMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val saveFailedText = stringResource(R.string.settings_save_failed)
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(saveFailedText)
            viewModel.saveFailedShown()
        }
    }
    val speedMessageText = speedMessage?.resolve()
    LaunchedEffect(speedMessageText) {
        if (speedMessageText != null) {
            snackbarHostState.showSnackbar(speedMessageText)
            viewModel.speedMessageShown()
        }
    }

    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { TopBarTitle(stringResource(R.string.settings_title)) },
                windowInsets = WindowInsets(0),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Section breaks are the quiet row hairline (#84).
        val line = MaterialTheme.depth.line
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            appearance?.let {
                AppearanceSection(
                    appearance = it,
                    onModeChange = viewModel::setThemeMode,
                    onPresetChange = viewModel::setThemePreset,
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = line)
            }

            speech?.let { prefs ->
                SpeechSection(
                    prefs = prefs,
                    onRecognitionLanguageChange = viewModel::setRecognitionLanguage,
                    onKeepModelLoadedChange = viewModel::setKeepModelLoaded,
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = line)
                EndOfSpeechSection(
                    prefs = prefs,
                    onKeyboardEndChange = viewModel::setKeyboardEnd,
                    onAppsEndChange = viewModel::setAppsEnd,
                    onMinSilenceChange = viewModel::setMinSilenceSeconds,
                    onPatienceChange = viewModel::setPatience,
                    onEndWordsChange = viewModel::setEndWords,
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = line)
            }

            cleanup?.let { config ->
                CleanupSection(
                    config = config,
                    onRemoveFillersChange = viewModel::setRemoveFillers,
                    onSpokenCommandsChange = viewModel::setSpokenCommands,
                    onSpokenPunctuationChange = viewModel::setSpokenPunctuation,
                    onNumbersAsDigitsChange = viewModel::setNumbersAsDigits,
                    onCapitalizeSentencesChange = viewModel::setCapitalizeSentences,
                    onOpenDictionary = onNavigateToDictionary,
                    onOpenSnippets = onNavigateToSnippets,
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = line)
            }

            SupportSection()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = line)

            SectionHeader(stringResource(R.string.settings_about))
            SettingsRow(
                label = stringResource(R.string.onboarding_settings_row),
                supporting = stringResource(R.string.onboarding_settings_row_desc),
                leading = { Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenSetup),
            )
            SettingsRow(
                label = stringResource(R.string.settings_about_row),
                supporting = stringResource(R.string.settings_about_row_desc, BuildConfig.VERSION_NAME),
                leading = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onNavigateToAbout),
            )

            speech?.let { prefs ->
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = line)
                DeveloperSection(
                    prefs = prefs,
                    onDeveloperEnginesChange = viewModel::setDeveloperEngines,
                    onOrtTuningChange = viewModel::setOrtTuning,
                    speedSummary = phoneSpeedSummary(measuring, prefs.deviceSpeedFactor),
                    measuring = measuring,
                    onRemeasureSpeed = viewModel::remeasureSpeed,
                    onOpenBenchmark = onOpenBenchmark,
                )
            }
        }
    }
}

@Composable
private fun SpeechSection(
    prefs: SpeechPrefs,
    onRecognitionLanguageChange: (String) -> Unit,
    onKeepModelLoadedChange: (Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_speech))

    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        label = stringResource(R.string.settings_recognition_language),
        supporting = recognitionLanguageLabel(prefs.recognitionLanguage) + "\n" +
            stringResource(R.string.settings_recognition_language_desc),
        modifier = Modifier.clickable(role = Role.Button) { choosingLanguage = true },
    )
    if (choosingLanguage) {
        LanguageDialog(
            title = stringResource(R.string.settings_recognition_language),
            options = listOf(SpeechPrefs.AUTO) + rememberUiLocale().let { locale ->
                remember(locale) { sortedLanguageCodes(EngineLanguages.userChoices(), locale) }
            },
            selected = prefs.recognitionLanguage,
            label = { recognitionLanguageLabel(it) },
            onSelect = { code ->
                onRecognitionLanguageChange(code)
                choosingLanguage = false
            },
            onDismiss = { choosingLanguage = false },
        )
    }

    SettingsSwitchRow(
        label = stringResource(R.string.settings_speech_keep_loaded),
        supporting = stringResource(R.string.settings_speech_keep_loaded_desc),
        checked = prefs.keepModelLoaded,
        onCheckedChange = onKeepModelLoadedChange,
    )
}

/** "Auto (German)" for [SpeechPrefs.AUTO], naming the system language; else the language's name. */
@Composable
private fun recognitionLanguageLabel(code: String): String =
    if (code == SpeechPrefs.AUTO) {
        stringResource(R.string.settings_recognition_language_auto, languageNameInText(Locale.getDefault().language))
    } else {
        languageName(code)
    }

/**
 * Opt-in to the developer engines (#13), and while they are shown:
 * re-measuring the phone's speed for the speed budget (#24; the Engines tab
 * measures on its own, so it's hidden otherwise, #42), ONNX Runtime tuning
 * (#31) and the entry to the A/B benchmark that compares them.
 */
@Composable
private fun DeveloperSection(
    prefs: SpeechPrefs,
    onDeveloperEnginesChange: (Boolean) -> Unit,
    onOrtTuningChange: (String?) -> Unit,
    speedSummary: TextRes,
    measuring: Boolean,
    onRemeasureSpeed: () -> Unit,
    onOpenBenchmark: () -> Unit,
) {
    val developerEngines = prefs.developerEngines
    SectionHeader(stringResource(R.string.settings_developer))
    SettingsSwitchRow(
        label = stringResource(R.string.settings_developer_engines),
        supporting = stringResource(R.string.settings_developer_engines_desc),
        checked = developerEngines,
        onCheckedChange = onDeveloperEnginesChange,
    )
    if (developerEngines) {
        SettingsRow(
            label = stringResource(R.string.settings_developer_speed),
            supporting = speedSummary.resolve(),
            modifier = Modifier.clickable(enabled = !measuring, role = Role.Button, onClick = onRemeasureSpeed),
            showChevron = false,
        )
        OrtTuningRow(prefs, onOrtTuningChange)
        SettingsRow(
            label = stringResource(R.string.settings_developer_benchmark),
            supporting = stringResource(R.string.settings_developer_benchmark_desc),
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenBenchmark),
        )
    }
}

/** Settings → Developer → Runtime tuning (#31): one of [OrtTuning.PRESETS], [OrtTuning.DEFAULT] stored as null. */
@Composable
private fun OrtTuningRow(prefs: SpeechPrefs, onChange: (String?) -> Unit) {
    @Composable
    fun label(id: String): String = OrtTuning.parse(id)?.let { ortTuningLabel(it) } ?: id

    var choosing by rememberSaveable { mutableStateOf(false) }
    val selected = (prefs.ortTuningId?.let { OrtTuning.parse(it) } ?: OrtTuning.DEFAULT).id
    SettingsRow(
        label = stringResource(R.string.settings_developer_ort_tuning),
        supporting = label(selected) + "\n" + stringResource(R.string.settings_developer_ort_tuning_desc),
        modifier = Modifier.clickable(role = Role.Button) { choosing = true },
    )
    if (choosing) {
        LanguageDialog(
            title = stringResource(R.string.settings_developer_ort_tuning),
            options = OrtTuning.PRESETS.map { it.id },
            selected = selected,
            label = { label(it) },
            onSelect = { id ->
                onChange(id.takeIf { it != OrtTuning.DEFAULT.id })
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

/** "CPU, no spinning, pinned to fast cores"; [OrtTuning.DEFAULT] reads "Default (CPU)". */
@Composable
private fun ortTuningLabel(tuning: OrtTuning): String {
    val parts = buildList {
        add(stringResource(R.string.settings_developer_ort_tuning_cpu))
        if (!tuning.spinning) add(stringResource(R.string.settings_developer_ort_tuning_no_spinning))
        if (tuning.pin) add(stringResource(R.string.settings_developer_ort_tuning_pinned))
    }.joinToString(stringResource(R.string.list_separator))
    return if (tuning == OrtTuning.DEFAULT) stringResource(R.string.settings_developer_ort_tuning_default, parts) else parts
}
