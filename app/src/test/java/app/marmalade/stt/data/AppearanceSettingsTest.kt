package app.marmalade.stt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.marmalade.stt.ui.theme.Appearance
import app.marmalade.stt.ui.theme.ThemeMode
import app.marmalade.stt.ui.theme.ThemePreset
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Settings → Appearance (#35) in the real DataStore over a temp file, reopened as after a process restart. */
class AppearanceSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var job: Job? = null
    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }
    private lateinit var store: DataStore<Preferences>

    /** Only one DataStore may be open per file; the old one lets go once its scope has finished. */
    private suspend fun reopen(): AppearanceSettings {
        job?.cancelAndJoin()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob().also { job = it })
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return AppearanceSettings(store)
    }

    @After
    fun closeStore() = runBlocking { job?.cancelAndJoin() ?: Unit }

    @Test
    fun `nothing stored is Marmalade following the system`() = runBlocking {
        assertEquals(Appearance(ThemePreset.MARMALADE, ThemeMode.SYSTEM), reopen().appearance.first())
    }

    @Test
    fun `the preset and the mode survive a restart, independently`() = runBlocking {
        val settings = reopen()
        settings.setPreset(ThemePreset.FOREST)
        assertEquals(Appearance(ThemePreset.FOREST, ThemeMode.SYSTEM), settings.appearance.first())
        settings.setMode(ThemeMode.DARK)
        assertEquals(Appearance(ThemePreset.FOREST, ThemeMode.DARK), reopen().appearance.first())

        reopen().setPreset(ThemePreset.SYSTEM)
        assertEquals(Appearance(ThemePreset.SYSTEM, ThemeMode.DARK), reopen().appearance.first())
    }

    @Test
    fun `an unknown stored name reads as the default`() = runBlocking {
        reopen()
        store.edit {
            it[stringPreferencesKey("theme_preset")] = "SOLARIZED"
            it[stringPreferencesKey("theme_mode")] = "sepia"
        }
        assertEquals(Appearance(), reopen().appearance.first())
    }

    @Test
    fun `names are read whatever their case, as marmalade-tts-android writes them`() = runBlocking {
        reopen()
        store.edit {
            it[stringPreferencesKey("theme_preset")] = "midnight"
            it[stringPreferencesKey("theme_mode")] = "light"
        }
        assertEquals(Appearance(ThemePreset.MIDNIGHT, ThemeMode.LIGHT), reopen().appearance.first())
    }

    // --- The setup guide's High contrast switch (#68) -------------------------

    @Test
    fun `the switch goes back to the preset before it, even after leaving the guide`() = runBlocking {
        reopen().setPreset(ThemePreset.MIDNIGHT)
        reopen().setHighContrast(true)
        assertEquals(ThemePreset.HIGH_CONTRAST, reopen().appearance.first().preset)
        reopen().setHighContrast(false)
        assertEquals(ThemePreset.MIDNIGHT, reopen().appearance.first().preset)
    }

    @Test
    fun `a pick in Settings forgets the preset the switch remembered`() = runBlocking {
        val settings = reopen()
        settings.setPreset(ThemePreset.MIDNIGHT)
        settings.setHighContrast(true)
        settings.setPreset(ThemePreset.FOREST)
        settings.setPreset(ThemePreset.HIGH_CONTRAST)
        settings.setHighContrast(false)
        // Not the stale Midnight: nothing is remembered, so the default.
        assertEquals(ThemePreset.MARMALADE, settings.appearance.first().preset)
    }

    @Test
    fun `the switch leaves a preset it didn't set alone`() = runBlocking {
        val settings = reopen()
        settings.setPreset(ThemePreset.BERRY)
        settings.setHighContrast(false)
        assertEquals(ThemePreset.BERRY, settings.appearance.first().preset)
        // Already High contrast from Settings: on changes nothing, off goes to the default.
        settings.setPreset(ThemePreset.HIGH_CONTRAST)
        settings.setHighContrast(true)
        assertEquals(ThemePreset.HIGH_CONTRAST, settings.appearance.first().preset)
        settings.setHighContrast(false)
        assertEquals(ThemePreset.MARMALADE, settings.appearance.first().preset)
    }

    @Test
    fun `quick taps land in order, ending where the last one left it`() = runBlocking {
        val settings = reopen()
        settings.setPreset(ThemePreset.MIDNIGHT)
        // Launched together, as fast taps are; each read-and-write is one edit.
        coroutineScope { repeat(21) { i -> launch { settings.setHighContrast(i % 2 == 0) } } }
        assertEquals(ThemePreset.HIGH_CONTRAST, settings.appearance.first().preset)
        coroutineScope { repeat(2) { i -> launch { settings.setHighContrast(i % 2 == 0) } } }
        assertEquals(ThemePreset.MIDNIGHT, settings.appearance.first().preset)
    }
}
