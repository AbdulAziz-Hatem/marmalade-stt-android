package app.marmalade.stt.ime

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * The keyboard panel in the app's own language (#69). Android gives an input
 * method's window the system configuration, without the per-app language set
 * in Android 13's app settings, so with the app in Arabic on an English phone
 * the panel was English and left to right while the app around it was Arabic.
 * Here the panel's context, configuration and layout direction take the app's
 * languages when there are any; otherwise the system's apply, as before.
 */
@Composable
fun InAppLanguage(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val system = LocalConfiguration.current
    val config = remember(system) {
        val appLocales = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else {
            null
        }
        if (appLocales == null || appLocales.isEmpty) null else Configuration(system).apply { setLocales(appLocales) }
    }
    if (config == null) {
        content()
        return
    }
    val localized = remember(config) { context.createConfigurationContext(config) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides config,
        LocalLayoutDirection provides
            if (config.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}
