package app.marmalade.stt.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.marmalade.stt.ui.theme.Appearance
import app.marmalade.stt.ui.theme.ThemeMode
import app.marmalade.stt.ui.theme.ThemePreset
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Settings → Appearance (#35): the colour preset and light/dark mode, stored
 * by name under marmalade-tts-android's keys. The app, the voice keyboard
 * and the speech sheet collect [appearance] and restyle as it changes.
 */
@Singleton
class AppearanceSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * The stored appearance; Marmalade following the system's dark theme
     * until set. An unreadable settings file reads as the default rather
     * than failing the app's first frame.
     */
    val appearance: Flow<Appearance> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read the settings; default appearance", e)
            emit(emptyPreferences())
        }
        .map { Appearance(preset = ThemePreset.fromString(it[PRESET]), mode = ThemeMode.fromString(it[MODE])) }
        .distinctUntilChanged()

    /**
     * Settings → Appearance's pick. It also forgets the preset the setup
     * guide's switch would go back to, so that can't go stale.
     */
    suspend fun setPreset(preset: ThemePreset) {
        dataStore.edit {
            it[PRESET] = preset.name
            it.remove(PRESET_BEFORE_HIGH_CONTRAST)
        }
    }

    /**
     * The setup guide's High contrast switch (#68). On remembers the preset in
     * use and switches to [ThemePreset.HIGH_CONTRAST]; off goes back to the
     * remembered one (Marmalade if none, e.g. High contrast was picked in
     * Settings). It is kept on disk, so leaving the guide and coming back
     * still finds it. One edit reads and writes, so quick taps can't
     * interleave.
     */
    suspend fun setHighContrast(on: Boolean) {
        dataStore.edit {
            val current = ThemePreset.fromString(it[PRESET])
            if (on && current != ThemePreset.HIGH_CONTRAST) {
                it[PRESET_BEFORE_HIGH_CONTRAST] = current.name
                it[PRESET] = ThemePreset.HIGH_CONTRAST.name
            } else if (!on && current == ThemePreset.HIGH_CONTRAST) {
                it[PRESET] = ThemePreset.fromString(it[PRESET_BEFORE_HIGH_CONTRAST]).name
                it.remove(PRESET_BEFORE_HIGH_CONTRAST)
            }
        }
    }

    suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    private companion object {
        const val TAG = "AppearanceSettings"
        val PRESET = stringPreferencesKey("theme_preset")
        val MODE = stringPreferencesKey("theme_mode")
        val PRESET_BEFORE_HIGH_CONTRAST = stringPreferencesKey("theme_preset_before_high_contrast")
    }
}
