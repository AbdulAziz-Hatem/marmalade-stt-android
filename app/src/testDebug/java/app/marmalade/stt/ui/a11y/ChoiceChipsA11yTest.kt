package app.marmalade.stt.ui.a11y

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.marmalade.stt.R
import app.marmalade.stt.ui.screen.AppearanceSection
import app.marmalade.stt.ui.theme.Appearance
import app.marmalade.stt.ui.theme.ThemeMode
import app.marmalade.stt.ui.theme.ThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MarmaladeChoiceChip (ui/MarmaladeChips.kt) in its real rows, Settings →
 * Appearance (#68): each chip a radio button with its selected state, each
 * row a single-choice group under a heading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = A11Y_QUALIFIERS)
class ChoiceChipsA11yTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `chips are radio buttons in selectable groups`() {
        rule.setThemedContent {
            AppearanceSection(
                appearance = Appearance(preset = ThemePreset.MARMALADE, mode = ThemeMode.DARK),
                onModeChange = {},
                onPresetChange = {},
            )
        }
        rule.assertAccessible()

        val radios = rule.onAllNodes(hasRole(Role.RadioButton))
        // Mode (3) + every preset (the Wallpaper one too: SDK 34 has Material You).
        radios.assertCountEquals(ThemeMode.entries.size + ThemePreset.entries.size)
        // Every chip sits in a selectableGroup, and exactly one per group is selected.
        rule.onAllNodes(hasRole(Role.RadioButton) and !hasAnyAncestor(inSelectableGroup)).assertCountEquals(0)
        rule.onAllNodes(hasRole(Role.RadioButton) and isSelected).assertCountEquals(2)

        rule.onNodeWithText(string(R.string.settings_mode_dark)).assert(hasRole(Role.RadioButton)).assertIsSelected()
        rule.onNodeWithText(string(R.string.settings_mode_light)).assertIsNotSelected()
        // Not Material's own Role.Checkbox, which FilterChip sets.
        rule.onAllNodes(hasRole(Role.Checkbox)).assertCountEquals(0)

        rule.onNodeWithText(string(R.string.settings_appearance)).assert(isHeading())
        rule.onNodeWithText(string(R.string.settings_mode)).assert(isHeading())
        rule.onNodeWithText(string(R.string.settings_color)).assert(isHeading())
    }

    private val inSelectableGroup = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
    private val isSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
}
