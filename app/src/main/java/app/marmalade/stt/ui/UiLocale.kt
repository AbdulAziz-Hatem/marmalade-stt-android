package app.marmalade.stt.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.marmalade.stt.R
import java.util.Locale

/**
 * The locale the UI's strings resolved to (#51): each `values-<locale>`
 * holds its own tag in `ui_locale`, English "en". On a phone language the
 * app doesn't ship (Thai, Greek, Persian) the UI falls back
 * to English, and so must the language names, their case and their sorting;
 * [Locale.getDefault] would still be the phone's ("γερμανικά" in an English
 * sentence). The language the user *speaks* still comes from the phone (or
 * the per-app language): that is the recognition language, not this.
 */
fun uiLocale(resources: Resources): Locale = Locale.forLanguageTag(resources.getString(R.string.ui_locale))

/** [uiLocale] in a composable; follows a configuration change. */
@Composable
fun rememberUiLocale(): Locale {
    val tag = stringResource(R.string.ui_locale)
    return remember(tag) { Locale.forLanguageTag(tag) }
}
