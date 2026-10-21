package app.marmalade.stt.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.marmalade.stt.R
import app.marmalade.stt.ui.onboarding.OnboardingScreen
import app.marmalade.stt.ui.screen.AboutScreen
import app.marmalade.stt.ui.screen.BenchmarkScreen
import app.marmalade.stt.ui.screen.DictateScreen
import app.marmalade.stt.ui.screen.DictionaryScreen
import app.marmalade.stt.ui.screen.EnginesScreen
import app.marmalade.stt.ui.screen.LicenseTextScreen
import app.marmalade.stt.ui.screen.LicensesScreen
import app.marmalade.stt.ui.screen.PrivacyPolicyScreen
import app.marmalade.stt.ui.screen.SettingsScreen
import app.marmalade.stt.ui.screen.SnippetsScreen

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   MainActivity → MarmaladeSttTheme → AppRoot(startDestination)
//                    │   MainActivity reads OnboardingSettings.done once:
//                    │   false → Routes.Onboarding, true → Routes.Dictate
//                    │   (started as the SettingsActivity alias: Settings tab on top, #90)
//                    │
//                    └── Scaffold { bottomBar = NavigationBar(NAV_TABS) }
//                          NavHost(startDestination)
//                            ├── Routes.Onboarding → OnboardingScreen (full screen)
//                            │     first launch, or Settings → "Setup guide"
//                            │     "Try it" → Dictate, guide dropped from the stack
//                            ├── Routes.Dictate    → DictateScreen   (tab)
//                            │     listens via SpeechEngineManager; no model →
//                            │     onOpenEngines → navigateToTab(Routes.Engines)
//                            ├── Routes.Engines    → EnginesScreen   (tab)
//                            │     reads SttEngineCatalog + ModelInstaller state;
//                            │     engine details are a sheet on the tab itself
//                            │     └── license_text/{key} → LicenseTextScreen
//                            ├── Routes.Settings   → SettingsScreen  (tab)
//                            │     ├── onOpenSetup → Routes.Onboarding (above)
//                            │     ├── Routes.CleanupDictionary → DictionaryScreen (detail)
//                            │     ├── Routes.CleanupSnippets   → SnippetsScreen   (detail)
//                            │     │     both edit TextProcSettings via CleanupListsViewModel
//                            │     ├── Routes.Benchmark → BenchmarkScreen (detail; developer
//                            │     │     engines on): one recorded take through every
//                            │     │     installed engine, in memory only
//                            │     └── Routes.About       → AboutScreen        (detail)
//                            │           ├── Routes.Privacy    → PrivacyPolicyScreen
//                            │           │     reads assets/PRIVACY.md (copied from
//                            │           │     the repo root by copyPrivacyPolicy)
//                            │           └── Routes.Licenses   → LicensesScreen
//                            │                 reads LicenseCatalog
//                            │                 └── license_text/{key} → LicenseTextScreen
//                            │                       reads assets/licenses/<file>
//
//   No state lives here: Dictate, Engines, Settings and the cleanup editors keep
//   theirs in Hilt ViewModels; the rest render static content (strings, BuildConfig,
//   LicenseCatalog, bundled assets). Outbound links (bug report, sponsors, source code) leave
//   the app via util.openUrl.
//
//   Bottom-nav tabs use popUpTo(Routes.Dictate) + saveState/restoreState
//   so tab switching never grows the back stack — matches marmalade-android
//   and marmalade-tts-android. Detail screens are reached with navigate(),
//   dismissed with popBackStack(), and hide the bottom bar.
// -----------------------------------------------------------------------------

/** Route identifiers for the top-level nav graph. */
object Routes {
    const val Dictate = "dictate"
    const val Engines = "engines"
    const val Settings = "settings"

    /**
     * The setup guide. The start destination on first launch, and reachable
     * from Settings afterwards. Full screen: no bottom bar.
     */
    const val Onboarding = "onboarding"

    /** Settings → About. Leaf detail screen; the bottom nav bar is hidden. */
    const val About = "about"

    /** About → Open-source licenses. Detail screen. */
    const val Licenses = "licenses"

    /** About → Privacy policy. Detail screen. */
    const val Privacy = "privacy"

    /** Settings → Text cleanup → Replacement dictionary. Detail screen. */
    const val CleanupDictionary = "cleanup_dictionary"

    /** Settings → Text cleanup → Snippets. Detail screen. */
    const val CleanupSnippets = "cleanup_snippets"

    /** Settings → Developer → A/B benchmark (only while developer engines are on). Detail screen. */
    const val Benchmark = "benchmark"

    /** Full license text for one component. Use [licenseText] to build the route. */
    const val LicenseText = "license_text"

    /** Build the license-text route for a component [key]. */
    fun licenseText(key: String): String = "$LicenseText/${Uri.encode(key)}"
}

/** Tabs that show in the bottom NavigationBar. Order = display order. */
private data class NavTab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

// Same vector selected or not — the NavigationBar pill already carries the
// selection (the pattern marmalade-tts-android settled on).
private val NAV_TABS = listOf(
    NavTab(Routes.Dictate, R.string.nav_dictate, MarmaladeIcons.Mic),
    NavTab(Routes.Engines, R.string.nav_engines, Icons.Filled.Build),
    NavTab(Routes.Settings, R.string.nav_settings, Icons.Filled.Settings),
)

private val TAB_ROUTES = NAV_TABS.map { it.route }.toSet()

/**
 * Top-level navigation root: a Scaffold with a bottom NavigationBar and a
 * NavHost. The bar shows only on tab destinations; every other route is a
 * detail screen with its own back arrow.
 *
 * @param startDestination [Routes.Onboarding] until the setup guide has been
 *   finished, [Routes.Dictate] after. Must not change for the life of the
 *   NavHost: a new start destination resets the back stack.
 * @param initialTab a tab to open on top of Dictate when the app starts
 *   (Settings, from the system settings' gear, #90): once, not again after
 *   a recreation. Ignored while the setup guide is the start.
 */
@Composable
fun AppRoot(startDestination: String, initialTab: String? = null) {
    val navController = rememberNavController()
    val backStackEntry = navController.currentBackStackEntryAsState().value
    // Null before the NavHost's first composition: go by the start
    // destination then, or the bar would slide in (or out) on every cold start.
    val currentRoute = backStackEntry?.destination?.route ?: startDestination
    val showBottomBar = currentRoute in TAB_ROUTES

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                NavigationBar {
                    NAV_TABS.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any {
                            it.route == tab.route
                        } == true
                        val label = stringResource(tab.label)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab.route) },
                            // The label below already names the tab; a
                            // contentDescription would make TalkBack read it twice.
                            icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                            label = { NavLabel(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
        ) {
            composable(Routes.Dictate) {
                DictateScreen(onOpenEngines = { navController.navigateToTab(Routes.Engines) })
            }
            composable(Routes.Engines) {
                EnginesScreen(onOpenLicense = { key -> navController.navigate(Routes.licenseText(key)) })
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onNavigateToAbout = { navController.navigate(Routes.About) },
                    onNavigateToDictionary = { navController.navigate(Routes.CleanupDictionary) },
                    onNavigateToSnippets = { navController.navigate(Routes.CleanupSnippets) },
                    // Single top: a double tap would stack two guides (#74 review).
                    onOpenSetup = { navController.navigate(Routes.Onboarding) { launchSingleTop = true } },
                    onOpenBenchmark = { navController.navigate(Routes.Benchmark) },
                )
            }
            composable(Routes.Onboarding) {
                // Something below it (Settings) → a close button; first launch → none.
                val canClose = navController.previousBackStackEntry != null
                OnboardingScreen(
                    onFinished = { navController.finishOnboarding() },
                    onClose = if (canClose) ({ navController.popBackStack() }) else null,
                )
            }
            composable(Routes.CleanupDictionary) {
                DictionaryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CleanupSnippets) {
                SnippetsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Benchmark) {
                BenchmarkScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.About) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPrivacy = { navController.navigate(Routes.Privacy) },
                    onNavigateToLicenses = { navController.navigate(Routes.Licenses) },
                )
            }
            composable(Routes.Licenses) {
                LicensesScreen(
                    onBack = { navController.popBackStack() },
                    onViewComponentText = { key -> navController.navigate(Routes.licenseText(key)) },
                )
            }
            composable(Routes.Privacy) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "${Routes.LicenseText}/{key}",
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                val key = entry.arguments?.getString("key") ?: return@composable
                LicenseTextScreen(componentKey = key, onBack = { navController.popBackStack() })
            }
        }
        // After the NavHost, which sets the graph in this same composition.
        if (initialTab != null && startDestination == Routes.Dictate) {
            var initialTabShown by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!initialTabShown) {
                    initialTabShown = true
                    navController.navigateToTab(initialTab)
                }
            }
        }
    }
}

/**
 * Bottom-nav-style navigation: drop everything above the Dictate tab,
 * single-instance the target, restore prior state if any. Matches the
 * marmalade-android `MarmaladeNavHost` pattern, except that it pops to
 * Dictate by route rather than to the graph's start destination: after a
 * first launch the start destination is the (finished and popped) setup
 * guide, and Dictate is the bottom of the stack.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.Dictate) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Leave the setup guide for Dictate, with the guide off the back stack.
 * Opened from Settings, Dictate is the bottom of the stack: pop down to it.
 * On first launch the guide is the whole stack: replace it with Dictate.
 */
private fun NavHostController.finishOnboarding() {
    if (!popBackStack(Routes.Dictate, inclusive = false)) {
        navigate(Routes.Dictate) {
            popUpTo(Routes.Onboarding) { inclusive = true }
        }
    }
}

/** The most a tab label grows with the system font size. */
private const val NAV_LABEL_MAX_SCALE = 1.5f

/**
 * A bottom-bar tab label on one line. A third of the width is too narrow for
 * "Settings" at 200 % font on the largest display size, where it broke
 * mid-word ("Setting / s", #68), so the label grows only up to
 * [NAV_LABEL_MAX_SCALE] and ellipsizes past that (a long translation).
 * The icon beside it and TalkBack (the full text) still name the tab.
 */
@Composable
private fun NavLabel(text: String) {
    val fontScale = LocalDensity.current.fontScale
    val style = MaterialTheme.typography.labelMedium.let {
        if (fontScale > NAV_LABEL_MAX_SCALE) it.copy(fontSize = it.fontSize * (NAV_LABEL_MAX_SCALE / fontScale)) else it
    }
    Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
