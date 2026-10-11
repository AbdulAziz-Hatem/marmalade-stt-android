package app.marmalade.stt.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.marmalade.stt.BuildConfig
import app.marmalade.stt.R
import app.marmalade.stt.ui.MarmaladeIcons
import app.marmalade.stt.ui.components.DetailTopBar
import app.marmalade.stt.ui.components.JarMascot
import app.marmalade.stt.ui.components.JarMascotState
import app.marmalade.stt.ui.components.SettingsRow

/**
 * Settings → About. Identity (mascot, name, version) on top, then the
 * privacy policy and open-source licenses. The bug report and sponsor links
 * are in Settings → Support (#44).
 *
 * Detail screen; the bottom nav bar is hidden by
 * [app.marmalade.stt.ui.AppRoot] while this is the current destination.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToLicenses: () -> Unit,
) {
    Scaffold(
        // Nested-Scaffold inset handoff — see DetailTopBar.
        contentWindowInsets = WindowInsets(0),
        topBar = { DetailTopBar(title = stringResource(R.string.about_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                JarMascot(state = JarMascotState.IDLE, size = 96.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            HorizontalDivider()

            SettingsRow(
                label = stringResource(R.string.about_privacy),
                supporting = stringResource(R.string.about_privacy_desc),
                leading = { Icon(imageVector = MarmaladeIcons.Privacy, contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onNavigateToPrivacy),
            )
            SettingsRow(
                label = stringResource(R.string.about_licenses),
                supporting = stringResource(R.string.about_licenses_desc),
                leading = { Icon(imageVector = MarmaladeIcons.LicenseDoc, contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onNavigateToLicenses),
            )
        }
    }
}
