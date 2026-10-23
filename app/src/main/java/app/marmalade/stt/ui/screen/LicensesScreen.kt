package app.marmalade.stt.ui.screen

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.data.LicenseCatalog
import app.marmalade.stt.ui.components.DetailTopBar
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.contentDirection
import app.marmalade.ui.theme.depth
import app.marmalade.stt.util.openUrl
import java.io.IOException

// -----------------------------------------------------------------------------
// Open-source licenses
// -----------------------------------------------------------------------------
//   Two detail screens, both reached from Settings → About → "Open-source
//   licenses" (AppRoot hides the bottom nav bar on both):
//
//     LicensesScreen     — licensing posture + components grouped by license,
//                          each row showing its exact copyright holder. A row
//                          opens the component's license text (bundled) or,
//                          for any component we don't ship the text for, the
//                          upstream URL in a browser.
//     LicenseTextScreen  — one component's full license text. For MIT/BSD the
//                          bundled file already embeds the copyright; for the
//                          shared Apache/OFL bodies the component's
//                          attribution is shown above the body.
//
//   Content comes from [LicenseCatalog], which mirrors NOTICE.md.
// -----------------------------------------------------------------------------

/**
 * Settings → About → Open-source licenses. Lists every third-party component
 * the app ships, grouped by license, each with its correct copyright holder —
 * and fronts the app's licensing posture (MIT source and MIT APK).
 *
 * Detail screen; the bottom nav bar is hidden by [app.marmalade.stt.ui.AppRoot]
 * while this is the current destination.
 *
 * @param onViewComponentText navigates to [LicenseTextScreen] for a component
 *        whose license text is bundled. Components without a bundled text open
 *        their license's canonical URL in a browser instead (handled here).
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onViewComponentText: (componentKey: String) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = { DetailTopBar(title = stringResource(R.string.about_licenses), onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            PostureSection(onOpenSource = {
                openUrl(context, LicenseCatalog.POSTURE.SOURCE_URL)
            })

            LicenseCatalog.groupedByLicense().forEach { (license, members) ->
                HorizontalDivider(color = MaterialTheme.depth.line)
                SectionHeader(license.id)
                members.forEach { component ->
                    ComponentRow(
                        component = component,
                        opensBrowser = component.resolvedAsset() == null,
                        onClick = {
                            if (component.resolvedAsset() != null) {
                                onViewComponentText(component.key)
                            } else {
                                openUrl(context, license.url)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostureSection(onOpenSource: () -> Unit) {
    SectionHeader(stringResource(R.string.licenses_licensing))

    Text(
        text = LicenseCatalog.POSTURE.SOURCE,
        style = MaterialTheme.typography.bodyMedium.contentDirection(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Text(
        text = LicenseCatalog.POSTURE.BINARY,
        style = MaterialTheme.typography.bodyMedium.contentDirection(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    ListItem(
        modifier = Modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.a11y_open_in_browser),
            onClick = onOpenSource,
        ),
        headlineContent = { EnglishText(LicenseCatalog.POSTURE.SOURCE_LABEL) },
        supportingContent = { EnglishText(LicenseCatalog.POSTURE.SOURCE_URL) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        colors = licenseRowColors(),
    )
}

/** A component; [opensBrowser] when its license has no bundled text, so TalkBack says where the tap goes. */
@Composable
private fun ComponentRow(
    component: LicenseCatalog.Component,
    opensBrowser: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(
            role = Role.Button,
            onClickLabel = if (opensBrowser) stringResource(R.string.a11y_open_in_browser) else null,
            onClick = onClick,
        ),
        headlineContent = { EnglishText(component.name) },
        supportingContent = {
            Column {
                EnglishText(stringResource(R.string.licenses_component_summary, component.role, component.shipsIn))
                component.copyright.forEach { line ->
                    EnglishText(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                component.note?.let {
                    EnglishText(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Full ink, a step above the dim copyright lines, not the
                // accent (#84, as TTS's #83). The whole row is the tap target.
                Text(
                    text = stringResource(R.string.licenses_view_full),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = licenseRowColors(),
    )
}

/** Rows sit on the page; the chevron takes the quiet glyph ink (#84). */
@Composable
private fun licenseRowColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.depth.page,
    trailingIconColor = MaterialTheme.depth.faint,
)

/**
 * [LicenseCatalog]'s entries are English: in a right-to-left UI they keep
 * their own direction and sit on the left (#69). The rows' chevrons and the
 * translated labels still follow the UI.
 */
@Composable
private fun EnglishText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = style.contentDirection(), color = color, modifier = modifier.fillMaxWidth())
}

/**
 * Full license text for one component. Monospace + selectable so a user (or a
 * license auditor) can read or copy the verbatim text. For MIT/BSD the bundled
 * file already embeds the copyright line; for the shared Apache/OFL bodies
 * the component's attribution is shown above the body so it isn't lost.
 * The body is one Text per paragraph so TalkBack can step through it rather
 * than reading kilobytes as one utterance (#68).
 *
 * Detail screen; the bottom nav bar is hidden while open.
 */
@Composable
fun LicenseTextScreen(
    componentKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val component = remember(componentKey) { LicenseCatalog.componentFor(componentKey) }
    val body = remember(componentKey) {
        val asset = component?.resolvedAsset()
        if (asset == null) {
            context.getString(R.string.license_text_not_bundled)
        } else {
            try {
                context.assets.open("licenses/$asset").bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                Log.e("LicenseTextScreen", "Failed to read licenses/$asset", e)
                context.getString(R.string.license_text_load_failed)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            DetailTopBar(
                title = component?.licenseId ?: stringResource(R.string.license_text_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            component?.let {
                EnglishText(it.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            // Shared Apache/OFL bodies carry no licensor copyright, so show
            // the component's attribution above them. MIT/BSD files already
            // embed it — don't duplicate.
            if (component != null && !component.bodyEmbedsCopyright()) {
                Spacer(Modifier.height(4.dp))
                component.copyright.forEach { line ->
                    EnglishText(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val paragraphs = remember(body) { licenseParagraphs(body) }
            SelectionContainer {
                // English, or the translated "not bundled" / "failed" line: its own direction either way.
                Column {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodySmall.contentDirection(),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * [body] cut at its blank lines, for one Text per paragraph. Each piece but
 * the last ends in a newline, which draws the blank line (a Text ending in a
 * newline has an empty last line), so the screen looks as before. A copy
 * across paragraphs (the selection joins Texts with nothing between them)
 * keeps a line break there but not the blank line. Indentation is kept.
 */
internal fun licenseParagraphs(body: String): List<String> {
    val pieces = mutableListOf<String>()
    var start = 0
    for (gap in Regex("""\n(?:[ \t]*\n)+""").findAll(body)) {
        pieces += body.substring(start, gap.range.first) + "\n"
        start = gap.range.last + 1
    }
    pieces += body.substring(start)
    return pieces.filter { it.isNotBlank() }
}
