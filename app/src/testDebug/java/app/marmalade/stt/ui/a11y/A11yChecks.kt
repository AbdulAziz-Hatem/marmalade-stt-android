package app.marmalade.stt.ui.a11y

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.marmalade.stt.ui.theme.MarmaladeSttTheme

// Automated Compose accessibility checks (#68), run under Robolectric. They
// catch the mechanical mistakes; a TalkBack pass on the phone still decides
// whether a screen reads well.
//
// The checker walks both semantics trees:
// - the unmerged tree, to find every control and heading, including one
//   whose action got merged into an ancestor;
// - the merged tree, which is what TalkBack reads: a clickable, toggleable or
//   selectable node merges its descendants' text, so its spoken label is the
//   merged node's text and content descriptions.
// Subtrees under clearAndSetSemantics are skipped: TalkBack never sees them.

/** Material's minimum touch target. */
private val MinTouchTarget = 48.dp

/** Fails with a readable list when [accessibilityProblems] finds any. */
fun ComposeTestRule.assertAccessible() {
    val problems = accessibilityProblems()
    if (problems.isNotEmpty()) {
        throw AssertionError("${problems.size} accessibility problem(s):\n" + problems.joinToString("\n") { "  - $it" })
    }
}

/**
 * What's wrong with the current content, one line per problem:
 * - a control (click, toggle, select, slider) with nothing to say: no text
 *   and no content description in its merged semantics;
 * - a control whose touch target ([targetSize]) is under 48 × 48 dp;
 * - a control that reads its label twice: a content description set on the
 *   control itself (its name, e.g. "Retry, Parakeet Ultra") plus visible text
 *   merged in ("Retry"), which TalkBack reads after it. Descriptions that come
 *   from children, like a meter's inside a row, don't count: they add to the
 *   text rather than replace it;
 * - a heading with no text for TalkBack's heading navigation to read.
 */
fun ComposeTestRule.accessibilityProblems(): List<String> {
    waitForIdle()
    val merged = onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { it.visible() }.associateBy { it.id }
    val unmerged = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.visible() }
    // Half a pixel of slack for dp → px rounding.
    val minTouchPx = with(density) { MinTouchTarget.toPx() } - 0.5f
    val problems = mutableListOf<String>()
    for (node in unmerged) {
        val own = node.config
        // A node merged into an ancestor has no merged-tree node of its own.
        val spoken = merged[node.id]?.config ?: own
        val texts = spoken.texts()
        val descriptions = spoken.descriptions()
        val what = describe(node.id, descriptions, texts)
        if (own.isControl()) {
            if (texts.isEmpty() && descriptions.isEmpty()) problems += "control without a label: $what"
            if (own.descriptions().isNotEmpty() && texts.isNotEmpty()) {
                problems += "reads its label twice: $what"
            }
            val (w, h) = node.targetSize()
            if (w < minTouchPx || h < minTouchPx) {
                problems += "touch target %.0f × %.0f dp, under 48 × 48: %s".format(w / density.density, h / density.density, what)
            }
        }
        if (own.contains(SemanticsProperties.Heading) && texts.isEmpty()) problems += "heading without text: $what"
    }
    return problems
}

/**
 * A control's touch target in px, per side: the larger of its semantics
 * bounds and its whole layout node's, which include
 * minimumInteractiveComponentSize (M3 pads a 40 dp Button or IconButton to 48
 * outside the click modifier). Not `touchBoundsInRoot`: Compose stretches that
 * to 48 dp for every clickable, however small, so it can't fail.
 */
private fun SemanticsNode.targetSize(): Pair<Float, Float> {
    val own = boundsInRoot
    // LayoutInfo's size is the node's with all its modifiers; its coordinates are the innermost.
    return maxOf(own.width, layoutInfo.width.toFloat()) to maxOf(own.height, layoutInfo.height.toFloat())
}

/** The node and its descendants that TalkBack can reach. */
private fun SemanticsNode.visible(): List<SemanticsNode> =
    listOf(this) + if (config.isClearingSemantics) emptyList() else children.flatMap { it.visible() }

private fun SemanticsConfiguration.isControl(): Boolean =
    contains(SemanticsActions.OnClick) || contains(SemanticsActions.SetProgress)

private fun SemanticsConfiguration.texts(): List<String> =
    getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }.filter { it.isNotBlank() }

private fun SemanticsConfiguration.descriptions(): List<String> =
    getOrNull(SemanticsProperties.ContentDescription).orEmpty().filter { it.isNotBlank() }

private fun describe(id: Int, descriptions: List<String>, texts: List<String>): String =
    "node #$id (description=$descriptions, text=$texts)"

fun hasRole(role: Role): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

/** [content] in the app theme (light) on a surface, as the screens draw it. */
fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) {
    setContent {
        MarmaladeSttTheme(darkTheme = false) {
            Surface(color = MaterialTheme.colorScheme.surface) { Column { content() } }
        }
    }
}

/** The app's string [id], formatted with [args]. */
fun string(@StringRes id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
