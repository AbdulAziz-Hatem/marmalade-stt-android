package app.marmalade.stt.ui.a11y

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.install.InstallFailure
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.ui.formatBytes
import app.marmalade.stt.ui.progressPercent
import app.marmalade.stt.ui.screen.EngineAction
import app.marmalade.stt.ui.screen.EngineActionBar
import app.marmalade.stt.ui.screen.EngineCard
import app.marmalade.stt.ui.screen.EngineRow
import app.marmalade.stt.ui.screen.engineActions
import app.marmalade.stt.ui.screen.rememberResumeFocus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Engines tab's card (A1) and row (A3) and the shared action bar, in each
 * install state, with the settings not read yet (prefs = null) (#68).
 * Parakeet Ultra: multilingual, so the card shows the "See all" languages link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = A11Y_QUALIFIERS)
class EngineA11yTest {
    @get:Rule
    val rule = createComposeRule()

    private val engine = checkNotNull(SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA))
    private val name = engine.displayName
    private val downloading = InstallState.Downloading(bytesDone = 280_000_000L, totalBytes = 682_000_000L, currentFile = "encoder.int8.onnx")
    private val failed = InstallState.Failed(reason = "timeout", failure = InstallFailure.NETWORK, bytesOnDisk = 1_000L)
    private val paused = InstallState.Paused(bytesOnDisk = 280_000_000L, totalBytes = 682_000_000L)
    private val states = listOf(InstallState.NotInstalled, downloading, failed, InstallState.Installed)

    private fun showCard(state: InstallState) = rule.setThemedContent {
        EngineCard(engine = engine, state = state, prefs = null, onUse = {}, onShowMore = {}, onAction = {})
    }

    private fun showRow(state: InstallState) = rule.setThemedContent {
        EngineRow(engine = engine, state = state, prefs = null, onSelect = {}, onShowMore = {}, onAction = {})
    }

    // --- EngineCard ---------------------------------------------------------

    @Test
    fun `card passes the checker in every state`() {
        val state = mutableStateOf<InstallState>(InstallState.NotInstalled)
        rule.setThemedContent {
            EngineCard(engine = engine, state = state.value, prefs = null, onUse = {}, onShowMore = {}, onAction = {})
        }
        assertAccessibleIn(state)
    }

    @Test
    fun `card name is a heading, and the card a heading stop`() {
        showCard(InstallState.NotInstalled)
        rule.onNode(hasText(name) and isHeading(), useUnmergedTree = true).assertExists()
        // The heading merges up: TalkBack's heading navigation lands on the whole card.
        rule.onNode(hasText(name, substring = true) and isHeading()).assertExists()
    }

    @Test
    fun `show more is at least 48 dp and names the engine once`() {
        showCard(InstallState.NotInstalled)
        namedButton(named(R.string.engine_show_more))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `card download button names the engine and size once`() {
        showCard(InstallState.NotInstalled)
        namedButton(string(R.string.a11y_engine_download_named, name, formatBytes(engine.sizeBytes)))
    }

    @Test
    fun `card retry and remove download name the engine once`() {
        showCard(failed)
        namedButton(named(R.string.engine_action_retry))
        namedButton(named(R.string.engine_action_remove_download))
    }

    @Test
    fun `card cancel names the engine once and the bar is labelled`() {
        showCard(downloading)
        namedButton(named(R.string.engine_action_cancel))
        assertProgressLabelled()
    }

    @Test
    fun `installed card offers a named remove`() {
        showCard(InstallState.Installed)
        namedButton(named(R.string.engine_action_remove))
    }

    // --- EngineRow ----------------------------------------------------------

    @Test
    fun `row passes the checker in every state`() {
        val state = mutableStateOf<InstallState>(InstallState.NotInstalled)
        rule.setThemedContent {
            EngineRow(engine = engine, state = state.value, prefs = null, onSelect = {}, onShowMore = {}, onAction = {})
        }
        assertAccessibleIn(state)
    }

    @Test
    fun `row retry disc and chevron name the engine`() {
        showRow(failed)
        namedButton(named(R.string.engine_action_retry))
        rule.onNode(hasContentDescription(string(R.string.engine_info, name)) and hasClickAction()).assertExists()
    }

    @Test
    fun `row download ring is labelled`() {
        showRow(downloading)
        assertProgressLabelled()
    }

    // --- EngineActionBar ----------------------------------------------------

    @Test
    fun `action bar with an engine name reads each button once`() {
        rule.setThemedContent {
            EngineActionBar(
                actions = listOf(EngineAction.REMOVE_DOWNLOAD, EngineAction.RESUME),
                onAction = {},
                engineName = name,
            )
        }
        rule.assertAccessible()
        namedButton(named(R.string.engine_action_remove_download))
        namedButton(named(R.string.engine_action_resume))
    }

    @Test
    fun `action bar without a name reads the visible labels`() {
        rule.setThemedContent {
            EngineActionBar(actions = listOf(EngineAction.DOWNLOAD), onAction = {}, downloadSize = "640 MB")
        }
        rule.assertAccessible()
        rule.onNode(hasText(string(R.string.engine_action_download_size, "640 MB")) and hasClickAction())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    // --- Cancel → Resume focus (#68 review) ----------------------------------

    @Test
    fun `card cancel hands focus to resume once the download pauses`() {
        val state = mutableStateOf<InstallState>(downloading)
        rule.setThemedContent {
            EngineCard(
                engine = engine,
                state = state.value,
                prefs = null,
                onUse = {},
                onShowMore = {},
                onAction = { if (it == EngineAction.CANCEL) state.value = paused },
            )
        }
        namedButton(named(R.string.engine_action_cancel)).performKeyboardClick()
        namedButton(named(R.string.engine_action_resume)).assertIsFocused()
    }

    @Test
    fun `action bar cancel hands focus to resume, not to remove download`() {
        val state = mutableStateOf<InstallState>(downloading)
        rule.setThemedContent {
            EngineActionBar(
                actions = engineActions(state.value, onDetail = true),
                onAction = { if (it == EngineAction.CANCEL) state.value = paused },
                engineName = name,
                resumeFocus = rememberResumeFocus(state.value),
            )
        }
        namedButton(named(R.string.engine_action_cancel)).performKeyboardClick()
        namedButton(named(R.string.engine_action_resume)).assertIsFocused()
        namedButton(named(R.string.engine_action_remove_download)).assertIsNotFocused()
    }

    /** Focuses [this] from the keyboard and presses Enter, as a keyboard or Switch Access user would. */
    @OptIn(ExperimentalTestApi::class)
    private fun SemanticsNodeInteraction.performKeyboardClick() {
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        requestFocus()
        performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
    }

    // --- helpers ------------------------------------------------------------

    /** "Retry, Parakeet Ultra". */
    private fun named(label: Int): String = string(R.string.a11y_engine_action_named, string(label), name)

    /** A button TalkBack names [spoken], with its visible label hidden so it isn't read again. */
    private fun namedButton(spoken: String): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription(spoken) and hasClickAction())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))

    /** "Download progress", "41%", with a range for TalkBack's progress announcements. */
    private fun assertProgressLabelled() {
        val pct = progressPercent(downloading.bytesDone, downloading.totalBytes)
        val percent = string(R.string.engine_progress_percent, pct)
        rule.onNode(hasContentDescription(string(R.string.engine_progress_description)), useUnmergedTree = true)
            .assert(hasStateDescription(percent))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        // The visible "41%" is hidden: the bar already says it.
        rule.onAllNodes(hasText(string(R.string.engine_progress_percent, pct), substring = true)).assertCountEquals(0)
    }

    /** Runs the checker with [state] set to each of [states] (one setContent per test). */
    private fun assertAccessibleIn(state: MutableState<InstallState>) {
        val problems = states.flatMap { s ->
            state.value = s
            rule.accessibilityProblems().map { "${s::class.simpleName}: $it" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
