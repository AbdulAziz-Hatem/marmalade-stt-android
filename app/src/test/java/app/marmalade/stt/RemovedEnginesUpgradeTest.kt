package app.marmalade.stt

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.EngineRelease
import app.marmalade.stt.install.FakeFetcher
import app.marmalade.stt.install.ModelFilesDir
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.install.SpeedBudget
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.speech.PhoneHardware
import app.marmalade.stt.speech.SpeedCalibrator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

/**
 * An upgrade from before #45 (or #37, for Nemotron 3.5's 560 ms export) on an engine that left the app, through
 * [cleanUpRemovedEngines], the Application's startup step: the real catalog,
 * a real DataStore, a real `models/` directory and the real
 * [SpeedCalibrator.recommendAgain] (pinning the running engine included),
 * with only the phone's RAM faked. The recognition language is set, so the
 * host's locale doesn't matter.
 */
class RemovedEnginesUpgradeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "s.preferences_pb") })
    }
    private val settings by lazy { SpeechSettings(store) }
    private val filesDir by lazy { tmp.newFolder("files") }
    private val models get() = File(filesDir, "models")
    private val installer by lazy { ModelInstaller(ModelFilesDir { filesDir }, FakeFetcher(), EngineRelease {}) }

    @After
    fun close() = scope.cancel()

    /** A model directory that passes the install probe, so `installedModel` sees it. */
    private fun install(id: String) {
        val engine = checkNotNull(SttEngineCatalog.byId(id))
        val dir = File(models, id).apply { mkdirs() }
        for (f in engine.files) java.io.RandomAccessFile(File(dir, f.name), "rw").use { it.setLength(f.sizeBytes) }
        File(dir, ".install.json").writeText(
            engine.files.joinToString(",\n", "{\n", "\n}\n") { "  \"${it.name}\": \"${it.sha256}\"" },
        )
        assertTrue("$id looks installed", installer.installedModel(id) != null)
    }

    /** A leftover of an engine that left the app. */
    private fun leftover(name: String) = File(models, name).apply {
        mkdirs()
        File(this, "encoder.int8.onnx").writeText("weights")
    }

    /** [rules]: the recommendation's rules version; null as stored before #50. */
    private suspend fun store(
        selected: String?,
        recommended: String?,
        language: String = "en",
        rules: String? = EngineRecommender.RULES_VERSION,
    ) {
        store.edit { p ->
            selected?.let { p[stringPreferencesKey("speech_engine")] = it }
            recommended?.let { p[stringPreferencesKey("recommended_engine")] = it }
            rules?.let { p[stringPreferencesKey("recommendation_rules")] = it }
        }
        settings.setRecognitionLanguage(language)
        settings.setDeviceProbe(SpeedBudget.PROBE_REFERENCE_MS, 4)
    }

    /** A 3 GB phone as it reports itself (#47: 4 GB phones fit Parakeet Ultra). */
    private val threeGb = 2.7

    private fun upgrade(totalMemGb: Double) = runBlocking {
        val hardware = object : PhoneHardware {
            override fun totalMemBytes() = (totalMemGb * (1L shl 30)).toLong()
            override fun probeMs() = error("an upgrade never measures")
        }
        cleanUpRemovedEngines(installer, settings, SpeedCalibrator(hardware, settings, installer))?.join()
    }

    @Test
    fun `distil selected and installed - files deleted, the recommendation runs`() = runBlocking {
        val distil = leftover("distil-small-en")
        install("parakeet-tdt-110m-en")
        store(selected = "distil-small-en", recommended = "parakeet-tdt-0-6b-v3")

        upgrade(totalMemGb = 8.0)

        assertFalse(distil.exists())
        assertTrue("a catalog engine's files stay", installer.installedModel("parakeet-tdt-110m-en") != null)
        val prefs = settings.current()
        assertNull(prefs.selectedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_ULTRA, prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_ULTRA, prefs.activeEngine.id)
    }

    @Test
    fun `v3 selected with no recommendation stored on a 3 GB phone - recommends again, ends on 110M`() = runBlocking {
        val v3 = leftover("parakeet-tdt-0-6b-v3")
        val v3Partial = leftover("parakeet-tdt-0-6b-v3.partial")
        store(selected = "parakeet-tdt-0-6b-v3", recommended = null)

        upgrade(totalMemGb = threeGb)

        assertFalse(v3.exists())
        assertFalse(v3Partial.exists())
        val prefs = settings.current()
        assertNull(prefs.selectedEngineId)
        // Not the default (Ultra, not downloaded and too big for 3 GB): the phone's recommendation.
        assertEquals("parakeet-tdt-110m-en", prefs.recommendedEngineId)
        assertEquals("parakeet-tdt-110m-en", prefs.activeEngine.id)
    }

    @Test
    fun `a removed selection recommends again even under a kept recommendation`() = runBlocking {
        store(selected = "whisper-tiny-en", recommended = SttEngineCatalog.PARAKEET_ULTRA, language = "de")

        upgrade(totalMemGb = threeGb)

        val prefs = settings.current()
        assertNull(prefs.selectedEngineId)
        // A 3 GB German phone: Ultra doesn't fit, Canary does (#45 fix round).
        assertEquals("canary-180m-flash", prefs.activeEngine.id)
    }

    @Test
    fun `an installed running engine is pinned rather than switched to an undownloaded recommendation`() = runBlocking {
        // distil was selected and v3 recommended; Ultra (the default) was downloaded too.
        install(SttEngineCatalog.PARAKEET_ULTRA)
        store(selected = "distil-small-en", recommended = "parakeet-tdt-0-6b-v3")

        upgrade(totalMemGb = threeGb)

        val prefs = settings.current()
        // The new recommendation is 110M, which isn't downloaded: keepRunningEngine pins Ultra.
        assertEquals("parakeet-tdt-110m-en", prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_ULTRA, prefs.selectedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_ULTRA, prefs.activeEngine.id)
    }

    @Test
    fun `kept choices are left alone and nothing is recommended`() = runBlocking {
        store(selected = "canary-180m-flash", recommended = SttEngineCatalog.PARAKEET_ULTRA)

        assertFalse(settings.forgetUnknownEngines())
        upgrade(totalMemGb = 4.0)

        val prefs = settings.current()
        assertEquals("canary-180m-flash", prefs.selectedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_ULTRA, prefs.recommendedEngineId)
    }

    @Test
    fun `removed engines leave the too-slow evidence and the per-engine languages`() = runBlocking {
        store.edit { p ->
            p[stringSetPreferencesKey("too_slow_engines")] = setOf("nemotron-en-streaming-560ms", "whisper-turbo")
            p[stringPreferencesKey("engine_language_whisper-small-en")] = "en"
            p[stringPreferencesKey("engine_language_canary-180m-flash")] = "de"
        }

        assertFalse("no choice was removed", settings.forgetUnknownEngines())

        val prefs = settings.current()
        assertEquals(setOf("whisper-turbo"), prefs.tooSlowEngineIds)
        assertEquals(mapOf("canary-180m-flash" to "de"), prefs.engineLanguages)
    }

    /** Settings → Developer's Nemotron 3.5 chunk size before #37; nothing reads it now. */
    private val nemotronChunkKey = intPreferencesKey("nemotron_chunk_ms")

    @Test
    fun `Nemotron 3_5 at 560 ms on a Japanese phone - files and ids gone, Parakeet-ja recommended (#37, #60)`() = runBlocking {
        val gone = "nemotron-3-5-streaming-560ms"
        val files = leftover(gone)
        val partial = leftover("$gone.partial")
        store(selected = gone, recommended = gone, language = "ja")
        store.edit { p ->
            p[stringSetPreferencesKey("too_slow_engines")] = setOf(gone, "whisper-turbo")
            p[stringPreferencesKey("engine_language_$gone")] = "ja"
            p[nemotronChunkKey] = 560
        }

        upgrade(totalMemGb = 8.0)

        assertFalse(files.exists())
        assertFalse(partial.exists())
        val prefs = settings.current()
        assertNull(prefs.selectedEngineId)
        // Nemotron 3.5 has no Japanese since #37; Parakeet-ja is the Japanese engine since #60.
        assertEquals(SttEngineCatalog.PARAKEET_JA, prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_JA, prefs.activeEngine.id)
        assertEquals(setOf("whisper-turbo"), prefs.tooSlowEngineIds)
        assertEquals(emptyMap<String, String>(), prefs.engineLanguages)
        assertNull(store.data.first()[nemotronChunkKey])
    }

    @Test
    fun `Nemotron 3_5 at 560 ms on a Hindi phone - the 1120 ms export is recommended`() = runBlocking {
        val gone = "nemotron-3-5-streaming-560ms"
        val files = leftover(gone)
        install(SttEngineCatalog.NEMOTRON_35_1120MS)
        store(selected = gone, recommended = gone, language = "hi")

        upgrade(totalMemGb = 8.0)

        assertFalse(files.exists())
        assertTrue("the 1120 ms export's files stay", installer.installedModel(SttEngineCatalog.NEMOTRON_35_1120MS) != null)
        val prefs = settings.current()
        assertNull(prefs.selectedEngineId)
        assertEquals(SttEngineCatalog.NEMOTRON_35_1120MS, prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.NEMOTRON_35_1120MS, prefs.activeEngine.id)
    }

    @Test
    fun `the Nemotron chunk size setting is dropped even when no choice was removed`() = runBlocking {
        store(selected = SttEngineCatalog.NEMOTRON_35_1120MS, recommended = SttEngineCatalog.NEMOTRON_35_1120MS, language = "hi")
        store.edit { it[nemotronChunkKey] = 1120 }

        assertFalse("no choice was removed", settings.forgetUnknownEngines())

        assertNull(store.data.first()[nemotronChunkKey])
        val prefs = settings.current()
        assertEquals(SttEngineCatalog.NEMOTRON_35_1120MS, prefs.selectedEngineId)
        assertEquals(SttEngineCatalog.NEMOTRON_35_1120MS, prefs.recommendedEngineId)
    }

    @Test
    fun `a Japanese phone recommended Nemotron 3_5 under older rules gets Parakeet-ja, Nemotron pinned while installed (#50)`() =
        runBlocking {
            install(SttEngineCatalog.NEMOTRON_35_1120MS)
            store(selected = null, recommended = SttEngineCatalog.NEMOTRON_35_1120MS, language = "ja", rules = null)
            assertTrue(settings.current().recommendationOutdated)

            upgrade(totalMemGb = 8.0)

            val prefs = settings.current()
            assertEquals(SttEngineCatalog.PARAKEET_JA, prefs.recommendedEngineId)
            assertEquals(EngineRecommender.RULES_VERSION, prefs.recommendationRules)
            assertFalse(prefs.recommendationOutdated)
            // Nemotron was running and is downloaded; Parakeet-ja isn't: Nemotron stays until the user switches.
            assertEquals(SttEngineCatalog.NEMOTRON_35_1120MS, prefs.selectedEngineId)
            assertEquals(SttEngineCatalog.NEMOTRON_35_1120MS, prefs.activeEngine.id)
        }

    @Test
    fun `a Japanese phone running turbo under rules 1 gets Parakeet-ja recommended, turbo kept while installed (#60)`() =
        runBlocking {
            install("whisper-turbo")
            store(selected = null, recommended = "whisper-turbo", language = "ja", rules = "1+qwen3")
            assertTrue(settings.current().recommendationOutdated)

            upgrade(totalMemGb = 8.0)

            val prefs = settings.current()
            assertEquals(SttEngineCatalog.PARAKEET_JA, prefs.recommendedEngineId)
            assertFalse(prefs.recommendationOutdated)
            // Turbo is downloaded and was running: it stays until the user switches to Parakeet-ja.
            assertEquals("whisper-turbo", prefs.selectedEngineId)
            assertEquals("whisper-turbo", prefs.activeEngine.id)
        }

    @Test
    fun `a Japanese phone recommended turbo under rules 1, turbo not downloaded, moves to Parakeet-ja (#60)`() = runBlocking {
        store(selected = null, recommended = "whisper-turbo", language = "ja", rules = "1")

        upgrade(totalMemGb = threeGb)

        // A 3 GB phone too: Parakeet-ja fits it without ORT's arena.
        val prefs = settings.current()
        assertNull(prefs.selectedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_JA, prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.PARAKEET_JA, prefs.activeEngine.id)
    }

    @Test
    fun `a Thai phone recommended Qwen3 under older rules gets turbo, nothing pinned when Qwen3 isn't installed`() =
        runBlocking {
            store(selected = null, recommended = SttEngineCatalog.QWEN3, language = "th", rules = null)

            upgrade(totalMemGb = 12.0)

            val prefs = settings.current()
            assertNull(prefs.selectedEngineId)
            assertEquals("whisper-turbo", prefs.recommendedEngineId)
            assertEquals("whisper-turbo", prefs.activeEngine.id)
        }

    @Test
    fun `a Thai phone running an installed Qwen3 it was recommended under older rules keeps running it`() = runBlocking {
        // Developer engines off, nothing selected: Qwen3 runs as the recommendation (#27, before #37).
        install(SttEngineCatalog.QWEN3)
        store(selected = null, recommended = SttEngineCatalog.QWEN3, language = "th", rules = null)
        assertEquals(SttEngineCatalog.QWEN3, settings.current().activeEngine.id)

        upgrade(totalMemGb = 12.0)

        val prefs = settings.current()
        assertEquals("whisper-turbo", prefs.recommendedEngineId)
        // Pinned: still running, still listed (a user engine since #50).
        assertEquals(SttEngineCatalog.QWEN3, prefs.selectedEngineId)
        assertEquals(SttEngineCatalog.QWEN3, prefs.activeEngine.id)
        assertTrue(prefs.visibleEngines.any { it.id == SttEngineCatalog.QWEN3 })
    }

    @Test
    fun `a Qwen3 the user picked while it was recommended keeps running when the recommendation moves`() = runBlocking {
        install(SttEngineCatalog.QWEN3)
        store(selected = null, recommended = SttEngineCatalog.QWEN3, language = "th", rules = null)
        settings.setSelectedEngine(SttEngineCatalog.QWEN3) // the user's pick, Developer engines off

        upgrade(totalMemGb = 12.0)

        val prefs = settings.current()
        assertEquals("whisper-turbo", prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.QWEN3, prefs.activeEngine.id)
        assertTrue(prefs.visibleEngines.any { it.id == SttEngineCatalog.QWEN3 })
    }

    @Test
    fun `a Qwen3 picked by a version before #50 keeps running at the re-recommendation`() = runBlocking {
        install(SttEngineCatalog.QWEN3)
        store(selected = SttEngineCatalog.QWEN3, recommended = SttEngineCatalog.QWEN3, language = "th", rules = null)

        upgrade(totalMemGb = 12.0)

        val prefs = settings.current()
        assertEquals("whisper-turbo", prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.QWEN3, prefs.activeEngine.id)
    }

    @Test
    fun `a Qwen3 kept by #50's mark keeps running, and the retired mark is dropped`() = runBlocking {
        install(SttEngineCatalog.QWEN3)
        store(selected = SttEngineCatalog.QWEN3, recommended = SttEngineCatalog.QWEN3, language = "th", rules = null)
        store.edit { it[booleanPreferencesKey("speech_engine_kept")] = true }

        upgrade(totalMemGb = 12.0)

        val prefs = settings.current()
        assertEquals("whisper-turbo", prefs.recommendedEngineId)
        assertEquals(SttEngineCatalog.QWEN3, prefs.activeEngine.id)
        assertNull(store.data.first()[booleanPreferencesKey("speech_engine_kept")])
    }

    @Test
    fun `a Qwen3 that isn't downloaded isn't pinned`() = runBlocking {
        store(selected = SttEngineCatalog.QWEN3, recommended = SttEngineCatalog.QWEN3, language = "th", rules = null)

        upgrade(totalMemGb = 12.0)

        // Nothing to keep running: the selection stays as the user left it, a user engine's choice.
        val prefs = settings.current()
        assertEquals(SttEngineCatalog.QWEN3, prefs.activeEngine.id)
    }

    @Test
    fun `a recommendation under the current rules, or none at all, isn't redone`() = runBlocking {
        // Stored now: Nemotron on a Japanese phone would not be the pick, so a rerun would show.
        store(selected = null, recommended = SttEngineCatalog.NEMOTRON_35_1120MS, language = "ja")
        assertNull(cleanUpRemovedEngines(installer, settings, SpeedCalibrator(noHardware, settings, installer)))
        assertEquals(SttEngineCatalog.NEMOTRON_35_1120MS, settings.current().recommendedEngineId)

        // A fresh install: nothing recommended yet, no version. The setup guide recommends.
        store.edit { it.clear() }
        assertFalse(settings.current().recommendationOutdated)
        assertNull(cleanUpRemovedEngines(installer, settings, SpeedCalibrator(noHardware, settings, installer)))
        assertNull(settings.current().recommendedEngineId)
    }

    @Test
    fun `a recommendation from other rules is outdated, whatever they were`() = runBlocking {
        store(selected = null, recommended = SttEngineCatalog.PARAKEET_ULTRA, rules = "0")
        assertTrue(settings.current().recommendationOutdated)
        settings.setRecommendedEngine(SttEngineCatalog.PARAKEET_ULTRA)
        assertFalse(settings.current().recommendationOutdated)
    }

    /** A phone that must not be asked anything. */
    private val noHardware = object : PhoneHardware {
        override fun totalMemBytes() = error("no recommendation expected")
        override fun probeMs() = error("an upgrade never measures")
    }
}
