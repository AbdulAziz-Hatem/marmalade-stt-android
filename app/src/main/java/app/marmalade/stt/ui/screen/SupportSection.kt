package app.marmalade.stt.ui.screen

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.marmalade.stt.BuildConfig
import app.marmalade.stt.R
import app.marmalade.stt.ui.MarmaladeIcons
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.components.SettingsRow
import app.marmalade.stt.util.BugReportUrl
import app.marmalade.stt.util.openUrl

/** GitHub Sponsors page, same as marmalade-tts-android's Support row. */
internal const val SPONSORS_URL = "https://github.com/sponsors/maxwhipw"

/** Max's GitHub profile: the other Marmalade apps. */
internal const val MORE_MARMALADE_URL = "https://github.com/maxwhipw"

/**
 * Settings → Support (#44), marmalade-tts-android's: donate, the other
 * Marmalade apps, and a bug report. Each row opens its page in the browser,
 * and TalkBack says so ("double-tap to open in browser", #68).
 */
@Composable
fun SupportSection() {
    val context = LocalContext.current
    val openInBrowser = stringResource(R.string.a11y_open_in_browser)
    SectionHeader(stringResource(R.string.settings_support))
    // Both flavors, as in marmalade-tts-android (Max, 2026-09-07).
    // Donations are voluntary and not tax-deductible: Marmalade is not a
    // registered charity. The row's copy and the README say so.
    SettingsRow(
        label = stringResource(R.string.settings_support_dev),
        supporting = stringResource(R.string.settings_support_dev_desc),
        leading = {
            // A vector heart (not an emoji), in the quiet glyph ink like the
            // other row icons: a primary tint put orange on the row (#84).
            Icon(imageVector = Icons.Filled.Favorite, contentDescription = null)
        },
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = openInBrowser) {
            openUrl(context, SPONSORS_URL)
        },
    )
    SettingsRow(
        label = stringResource(R.string.settings_more_marmalade),
        supporting = stringResource(R.string.settings_more_marmalade_desc),
        leading = {
            // Decorative: TalkBack would read "tangerine" before the label (#68).
            // Opaque ink: the row's translucent glyph ink would fade the emoji (#84).
            Text(
                text = "🍊",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = openInBrowser) {
            openUrl(context, MORE_MARMALADE_URL)
        },
    )
    SettingsRow(
        label = stringResource(R.string.settings_report_bug),
        supporting = stringResource(R.string.settings_report_bug_desc),
        leading = { Icon(imageVector = MarmaladeIcons.Bug, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = openInBrowser) {
            openUrl(
                context,
                BugReportUrl.build(
                    versionName = BuildConfig.VERSION_NAME,
                    flavor = BuildConfig.FLAVOR,
                    androidVersion = Build.VERSION.RELEASE,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                ),
            )
        },
    )
}
