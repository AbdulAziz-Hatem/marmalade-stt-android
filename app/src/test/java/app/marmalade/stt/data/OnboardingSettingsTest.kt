package app.marmalade.stt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real DataStore over a temp file, reopened as after a process restart (#11). */
class OnboardingSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var job: Job? = null
    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    /** Only one DataStore may be open per file; the old one lets go once its scope has finished. */
    private suspend fun reopen(): OnboardingSettings {
        job?.cancelAndJoin()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob().also { job = it })
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return OnboardingSettings(store)
    }

    @After
    fun closeStore() = runBlocking { job?.cancelAndJoin() ?: Unit }

    @Test
    fun `a first launch has no step to resume`() = runBlocking {
        val settings = reopen()
        assertFalse(settings.done.first())
        assertNull(settings.resumeStep.first())
    }

    @Test
    fun `an unfinished guide's step survives a restart`() = runBlocking {
        reopen().setStep("MODEL")
        assertEquals("MODEL", reopen().resumeStep.first())
    }

    @Test
    fun `a finished guide resumes nowhere`() = runBlocking {
        val settings = reopen()
        settings.setStep("DONE")
        settings.setDone()
        val restarted = reopen()
        assertTrue(restarted.done.first())
        assertNull(restarted.resumeStep.first())
    }
}
