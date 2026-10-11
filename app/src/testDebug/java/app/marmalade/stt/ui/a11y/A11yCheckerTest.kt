package app.marmalade.stt.ui.a11y

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The checker itself: each rule fires on a known-bad control, and good ones pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = A11Y_QUALIFIERS)
class A11yCheckerTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `good controls pass`() {
        rule.setThemedContent {
            Text("Section", modifier = Modifier.semantics { heading() })
            Button(onClick = {}) { Text("Save") }
            IconButton(onClick = {}) { Icon(Icons.Filled.Check, contentDescription = "Done") }
        }
        rule.assertAccessible()
    }

    @Test
    fun `flags an unlabelled control`() {
        rule.setThemedContent { IconButton(onClick = {}) { Icon(Icons.Filled.Check, contentDescription = null) } }
        assertOneProblem("control without a label")
    }

    @Test
    fun `flags a small touch target`() {
        rule.setThemedContent { Box(Modifier.size(24.dp).clickable {}.semantics { contentDescription = "Tiny" }) }
        assertOneProblem("touch target 24 × 24 dp")
    }

    @Test
    fun `flags a label read twice`() {
        rule.setThemedContent {
            Button(onClick = {}, modifier = Modifier.semantics { contentDescription = "Retry, Parakeet Ultra" }) {
                Text("Retry")
            }
        }
        assertOneProblem("reads its label twice")
    }

    @Test
    fun `flags a heading without text`() {
        rule.setThemedContent { Box(Modifier.size(48.dp).semantics { heading() }) }
        assertOneProblem("heading without text")
    }

    private fun assertOneProblem(prefix: String) {
        val problems = rule.accessibilityProblems()
        assertEquals(problems.toString(), 1, problems.size)
        assertTrue(problems.single(), problems.single().startsWith(prefix))
    }
}

/** A Pixel 8a-sized portrait screen (#68: the device the TalkBack pass ran on). */
const val A11Y_QUALIFIERS = "w411dp-h914dp-port-xxhdpi"
