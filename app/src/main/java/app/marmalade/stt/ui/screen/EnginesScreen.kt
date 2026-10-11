package app.marmalade.stt.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.R
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.ui.components.MarmaladeOutlinedButton
import app.marmalade.stt.ui.components.TopBarTitle
import app.marmalade.stt.ui.components.rememberAnnouncer

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Bottom-nav "Engines" tab → EnginesScreen(onOpenLicense)
//     │
//     ├── on first show: vm.refresh() → installer.verify(id) per engine
//     │   (cheap disk probe; the installer publishes the result)
//     ├── vm init: SpeedCalibrator.ensureMeasured() (once per phone, #24)
//     │
//     ├── engines  ◄── engineGroups(prefs, installed ids): main (offered on
//     │                this phone, the recommended engine first) and other
//     ├── prefs    ◄── SpeechSettings.prefs: active engine, recommended engine,
//     │                speed budget, language picks, "Other engines" expanded
//     │                (null until read)
//     ├── states   ◄── combine(installer.state(id) for every catalog engine)
//     │
//     ├── engines.main  → EngineCard (A1, EngineCard.kt) each
//     ├── "Other engines (N)" → vm.setOtherEnginesExpanded(!expanded) (remembered)
//     └── engines.other → EngineRow (A3, EngineRow.kt) each, while expanded
//
//   Card / row actions:
//     Use, row tap    → vm.select(id) → SpeechSettings.setSelectedEngine →
//                       SpeechEngineManager rebuilds the engine for the next session
//     Download        → DownloadConfirmDialog → vm.download(id)
//     Resume / Retry /
//     Download again  → vm.download(id)  ─┐
//     Cancel          → vm.cancel(id)     ├─► ModelDownloads (app-wide
//                                         │    scope) → installer.install
//     Remove download → vm.removeDownload(id) → installer.discardDownload
//     Remove          → RemoveConfirmDialog → vm.remove(id) → uninstall,
//                       selection back to the default if it was this one
//     Show more / ›   → EngineInfoDialog (B1, centered modal): details,
//                       language, Check files (vm.checkFiles), Remove, License
//                       → onOpenLicense → AppRoot → license_text/{key}
//
//   Failures and "Check files" results surface as a snackbar via vm.message,
//   hosted by the details dialog while it is open.
//   A download that stops (Downloading → Installed or Paused, or → Failed) is
//   spoken to TalkBack once from here (downloadOutcomes, #68), wherever its
//   card, row or dialog is; their own status lines carry no live region.
// -----------------------------------------------------------------------------

/**
 * Engines tab: a full card per engine offered on this phone, then "Other
 * engines" (over the phone's speed budget, installed leftovers, and with
 * Developer engines on the developer engines) as compact rows behind a
 * button. Picking an engine is its card's "Use" or a tap on its row; "Show
 * more" or a row's chevron opens everything else in a dialog.
 */
@Composable
fun EnginesScreen(
    onOpenLicense: (licenseKey: String) -> Unit,
    viewModel: EnginesViewModel = hiltViewModel(),
) {
    val engines by viewModel.engines.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDownloadId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
    var infoEngineId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // #68: the card, row or dialog shows the end of a download in nodes that
    // weren't there before (the Use button, the size line), which a live region
    // can't report; one announcement from here covers all three.
    val announce = rememberAnnouncer()
    val resources = LocalContext.current.resources
    val previousStates = remember { mutableMapOf<String, InstallState>() }
    LaunchedEffect(states) {
        downloadOutcomes(previousStates, states).forEach { (id, outcome) ->
            val name = SttEngineCatalog.byId(id)?.displayName ?: return@forEach
            announce(resources.getString(outcome.announcement, name))
        }
        previousStates.clear()
        previousStates.putAll(states)
    }

    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    val onAction: (String, EngineAction) -> Unit = { id, action ->
        when (action) {
            EngineAction.DOWNLOAD -> pendingDownloadId = id
            EngineAction.RESUME,
            EngineAction.RETRY,
            EngineAction.DOWNLOAD_AGAIN,
            -> viewModel.download(id)
            EngineAction.CANCEL -> viewModel.cancel(id)
            EngineAction.REMOVE_DOWNLOAD -> viewModel.removeDownload(id)
            EngineAction.CHECK_FILES -> viewModel.checkFiles(id)
            EngineAction.REMOVE -> pendingRemoveId = id
        }
    }
    val otherExpanded = prefs?.otherEnginesExpanded == true

    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { TopBarTitle(stringResource(R.string.engines_title)) },
                windowInsets = WindowInsets(0),
            )
        },
        // While the details dialog is open it hosts the snackbar; this one would be under it.
        snackbarHost = { if (infoEngineId == null) SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .selectableGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = engines.main, key = { it.id }) { engine ->
                EngineCard(
                    engine = engine,
                    state = states[engine.id] ?: InstallState.NotInstalled,
                    prefs = prefs,
                    onUse = { viewModel.select(engine.id) },
                    onShowMore = { infoEngineId = engine.id },
                    onAction = { onAction(engine.id, it) },
                    // Cards sit 12 dp apart, rows 8.
                    modifier = Modifier
                        .animateItem()
                        .padding(bottom = 4.dp),
                )
            }
            if (engines.other.isNotEmpty()) {
                item(key = OTHER_ENGINES_KEY) {
                    OtherEnginesButton(
                        count = engines.other.size,
                        expanded = otherExpanded,
                        onToggle = { viewModel.setOtherEnginesExpanded(!otherExpanded) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (otherExpanded) {
                    items(items = engines.other, key = { it.id }) { engine ->
                        val state = states[engine.id] ?: InstallState.NotInstalled
                        EngineRow(
                            engine = engine,
                            state = state,
                            prefs = prefs,
                            onSelect = {
                                if (engineCardUi(engine, state, prefs).selection == EngineSelection.SELECTABLE) {
                                    viewModel.select(engine.id)
                                }
                            },
                            onShowMore = { infoEngineId = engine.id },
                            onAction = { onAction(engine.id, it) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    infoEngineId?.let(SttEngineCatalog::byId)?.let { engine ->
        EngineInfoDialog(
            engine = engine,
            state = states[engine.id] ?: InstallState.NotInstalled,
            prefs = prefs,
            checking = engine.id in checking,
            snackbarHostState = snackbarHostState,
            onAction = { onAction(engine.id, it) },
            onSetLanguage = { code -> viewModel.setLanguage(engine.id, code) },
            onOpenLicense = onOpenLicense,
            onDismiss = { infoEngineId = null },
        )
    }

    pendingDownloadId?.let(SttEngineCatalog::byId)?.let { engine ->
        DownloadConfirmDialog(
            engine = engine,
            onConfirm = {
                viewModel.download(engine.id)
                pendingDownloadId = null
            },
            onDismiss = { pendingDownloadId = null },
        )
    }
    pendingRemoveId?.let(SttEngineCatalog::byId)?.let { engine ->
        RemoveConfirmDialog(
            engine = engine,
            onConfirm = {
                viewModel.remove(engine.id)
                pendingRemoveId = null
            },
            onDismiss = { pendingRemoveId = null },
        )
    }
}

/**
 * "Other engines (3)" with a chevron that points down while they are hidden
 * and up while they show; the state is remembered ([EnginesViewModel.setOtherEnginesExpanded]).
 */
@Composable
private fun OtherEnginesButton(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val stateLabel = stringResource(if (expanded) R.string.engines_other_expanded else R.string.engines_other_collapsed)
    MarmaladeOutlinedButton(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = stateLabel },
    ) {
        Text(stringResource(R.string.engines_other, count))
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val OTHER_ENGINES_KEY = "other-engines"
