package app.marmalade.stt.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [SpeechSettings] over a real DataStore in a temp file. */
class SpeechSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "s.preferences_pb") })
    }
    private val settings by lazy { SpeechSettings(store) }

    @After
    fun close() = scope.cancel()

    @Test
    fun `keep engine ready is off by default (42)`() = runBlocking {
        assertFalse(SpeechPrefs().keepModelLoaded)
        assertFalse(settings.current().keepModelLoaded)
    }

    @Test
    fun `keep engine ready that someone turned on stays on`() = runBlocking {
        store.edit { it[booleanPreferencesKey("speech_keep_model_loaded")] = true }
        assertTrue(settings.current().keepModelLoaded)
    }

    @Test
    fun `keep engine ready saves both ways`() = runBlocking {
        settings.setKeepModelLoaded(true)
        assertTrue(settings.current().keepModelLoaded)
        settings.setKeepModelLoaded(false)
        assertFalse(settings.current().keepModelLoaded)
    }
}
