package app.marmalade.stt.ui.a11y

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import app.marmalade.stt.R
import app.marmalade.stt.ui.components.DetailTopBar
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.components.SettingsRow
import app.marmalade.stt.ui.components.SettingsSliderRow
import app.marmalade.stt.ui.components.SettingsSwitchRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared settings rows (ui/components/SettingsRows.kt) as TalkBack meets them (#68). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = A11Y_QUALIFIERS)
class SettingsRowsA11yTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `switch row is one switch stop with its label and reason`() {
        rule.setThemedContent {
            var checked by remember { mutableStateOf(true) }
            SettingsSwitchRow(label = "Auto-punctuate", supporting = "Adds commas", checked = checked, onCheckedChange = { checked = it })
        }
        rule.assertAccessible()
        rule.onAllNodes(isToggleable()).assertCountEquals(1)
        val row = rule.onNode(isToggleable())
        row.assert(hasRole(Role.Switch)).assertIsOn().assertIsEnabled().assertTextEquals("Auto-punctuate", "Adds commas")
        row.performClick()
        row.assertIsOff()
    }

    @Test
    fun `disabled switch row stays one stop and can't be toggled`() {
        rule.setThemedContent {
            SettingsSwitchRow(label = "Keep screen on", supporting = "Needs Developer engines", checked = false, onCheckedChange = {}, enabled = false)
        }
        rule.assertAccessible()
        rule.onAllNodes(isToggleable()).assertCountEquals(1)
        rule.onNode(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assertIsNotEnabled()
            .assertTextEquals("Keep screen on", "Needs Developer engines")
    }

    @Test
    fun `clickable settings row merges label and supporting text`() {
        rule.setThemedContent { SettingsRow(label = "Licences", supporting = "Open-source notices", modifier = Modifier.clickable {}) }
        rule.assertAccessible()
        rule.onNodeWithText("Licences").assertHasClickAction().assertTextEquals("Licences", "Open-source notices")
    }

    @Test
    fun `slider names itself and says its value, the line above it is hidden`() {
        rule.setThemedContent {
            SettingsSliderRow(
                label = "Pause before stop",
                supporting = "How long a silence ends a turn",
                valueText = "0.9 s",
                valueDescription = "0.9 seconds",
                value = 0.9f,
                onValueChange = {},
                onValueChangeFinished = {},
                valueRange = 0.3f..2f,
                steps = 16,
            )
        }
        rule.assertAccessible()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .assert(hasContentDescription("Pause before stop"))
            .assert(hasStateDescription("0.9 seconds"))
        // The header row (label + "0.9 s") is cleared, so neither is read on its own.
        rule.onAllNodes(hasText("0.9 s")).assertCountEquals(0)
        rule.onAllNodes(hasText("Pause before stop")).assertCountEquals(0)
        rule.onNodeWithText("How long a silence ends a turn").assertExists()
    }

    @Test
    fun `section header is a heading`() {
        rule.setThemedContent { SectionHeader("Recognition") }
        rule.assertAccessible()
        rule.onNodeWithText("Recognition").assert(isHeading())
    }

    @Test
    fun `detail top bar title is a heading and back is labelled`() {
        rule.setThemedContent { DetailTopBar(title = "Privacy", onBack = {}) }
        rule.assertAccessible()
        rule.onNodeWithText("Privacy").assert(isHeading())
        rule.onNodeWithContentDescription(string(R.string.settings_back)).assertHasClickAction()
    }
}
