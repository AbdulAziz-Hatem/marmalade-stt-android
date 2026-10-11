package app.marmalade.stt

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.marmalade.stt.data.AppearanceSettings
import app.marmalade.stt.data.OnboardingSettings
import app.marmalade.stt.ui.AppRoot
import app.marmalade.stt.ui.Routes
import app.marmalade.stt.ui.theme.MarmaladeSttTheme
import app.marmalade.stt.ui.theme.ThemeMode
import app.marmalade.stt.ui.theme.ThemePreset
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Single Activity host for the Compose UI. `@AndroidEntryPoint` plumbs Hilt
 * through to every `hiltViewModel()` call in the screen composables.
 *
 * The theme is Settings → Appearance's ([AppearanceSettings], #35): a colour
 * preset, and light, dark or the system's setting. It restyles live as it
 * changes; the system bars' icons follow the resolved light/dark.
 *
 * The NavHost's start destination (setup guide or Dictate) is decided here,
 * once, from [OnboardingSettings.done]. Until that DataStore read returns (a
 * few milliseconds, the appearance read with it) only the theme background is drawn, so a first launch
 * never flashes Dictate before the guide. The choice is saved
 * with the instance state: the restored back stack was built on that same
 * start destination, and finishing the guide later must not swap it (a new
 * start destination resets the NavHost's back stack).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboardingSettings: OnboardingSettings

    @Inject lateinit var appearanceSettings: AppearanceSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by appearanceSettings.appearance.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = (appearance?.mode ?: ThemeMode.SYSTEM).isDark(isSystemInDarkTheme())
            // Key system-bar icon contrast off the resolved theme: the default
            // follows the system's dark setting, which Appearance can override.
            LaunchedEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                if (startDestination == null) {
                    startDestination = if (onboardingSettings.done.first()) Routes.Dictate else Routes.Onboarding
                }
            }
            MarmaladeSttTheme(darkTheme = darkTheme, themePreset = appearance?.preset ?: ThemePreset.MARMALADE) {
                val start = startDestination
                if (start != null && appearance != null) {
                    AppRoot(startDestination = start)
                } else {
                    // The theme's background, the same colour as the XML
                    // window background, so the wait looks like the app opening.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }
        }
    }
}
