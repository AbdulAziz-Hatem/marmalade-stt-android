package app.marmalade.stt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.LanguageOptions
import app.marmalade.stt.install.SpeedBudget
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.speech.EndOfDictation
import app.marmalade.stt.speech.ListenSurface
import app.marmalade.stt.speech.OrtTuning
import app.marmalade.stt.speech.Patience
import app.marmalade.stt.speech.SessionOptions
import app.marmalade.stt.speech.turn.TurnDetector
import app.marmalade.stt.textproc.TextEdit
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
 * Which engine runs and with which language hint, as the speech layer reads
 * them from [SpeechPrefs], plus the DataStore round trip of the per-engine
 * language keys (parsed back out of the key names).
 */
class SpeechPrefsTest {

    private val default = SttEngineCatalog.default
    private val granite = SttEngineCatalog.byId("granite-speech-5-470m")!!
    private val turbo = SttEngineCatalog.byId("whisper-turbo")!!
    private val canary = SttEngineCatalog.byId("canary-180m-flash")!!

    @Test
    fun `nothing selected runs the default`() {
        assertEquals(default, SpeechPrefs().activeEngine)
        assertEquals(default, SpeechPrefs(developerEngines = true).activeEngine)
    }

    @Test
    fun `a developer engine runs only while developer engines are shown`() {
        assertEquals(granite, SpeechPrefs(selectedEngineId = granite.id, developerEngines = true).activeEngine)
        // Toggle off: the default runs, but the choice is remembered for later.
        val hidden = SpeechPrefs(selectedEngineId = granite.id, developerEngines = false)
        assertEquals(default, hidden.activeEngine)
        assertEquals(granite.id, hidden.selectedEngineId)
    }

    @Test
    fun `an unknown engine id runs the default`() {
        assertEquals(default, SpeechPrefs(selectedEngineId = "removed-in-an-update", developerEngines = true).activeEngine)
    }

    private val nemotron = SttEngineCatalog.byId(SttEngineCatalog.NEMOTRON_35_1120MS)!!
    private val dev = SpeechPrefs(developerEngines = true)

    @Test
    fun `the developer override is the hint while it is an option, else the engine default`() {
        assertEquals("en", dev.languageFor(canary, "en-US"))
        assertEquals("de", dev.copy(engineLanguages = mapOf(canary.id to "de")).languageFor(canary, "en-US"))
        // Japanese isn't one of Canary's languages (a stale or hand-edited value).
        assertEquals("en", dev.copy(engineLanguages = mapOf(canary.id to "ja")).languageFor(canary, "en-US"))
        // Auto-detect is a pick like any other where it is offered, and not where it isn't.
        assertEquals("fr", dev.copy(engineLanguages = mapOf(turbo.id to "fr")).languageFor(turbo, "en-US"))
        assertEquals(LanguageOptions.AUTO, dev.copy(engineLanguages = mapOf(turbo.id to LanguageOptions.AUTO)).languageFor(turbo, "en-US"))
        assertEquals("en", dev.copy(engineLanguages = mapOf(canary.id to LanguageOptions.AUTO)).languageFor(canary, "en-US"))
        // Another engine's pick doesn't leak.
        assertEquals(LanguageOptions.AUTO, dev.copy(engineLanguages = mapOf(canary.id to "de")).languageFor(turbo, "en-US"))
        // It beats the recognition language.
        assertEquals("fr", dev.copy(recognitionLanguage = "de", engineLanguages = mapOf(turbo.id to "fr")).languageFor(turbo, "en-US"))
    }

    @Test
    fun `the developer override is ignored with developer engines off`() {
        val prefs = SpeechPrefs(engineLanguages = mapOf(nemotron.id to "hi"))
        assertEquals(LanguageOptions.AUTO, prefs.languageFor(nemotron, "en-US"))
        assertEquals("ar", prefs.copy(recognitionLanguage = "ar").languageFor(nemotron, "en-US"))
    }

    @Test
    fun `a chosen recognition language is the hint where the engine takes it`() {
        val japanese = SpeechPrefs(recognitionLanguage = "ja")
        assertEquals("ja", japanese.languageFor(turbo, "en-US"))
        // Nemotron 3.5 has no Japanese since #37: it detects. Canary has no Japanese and no
        // auto-detect: its default.
        assertEquals(LanguageOptions.AUTO, japanese.languageFor(nemotron, "en-US"))
        assertEquals("en", japanese.languageFor(canary, "en-US"))
        // Nemotron 3.5 takes each of its three languages.
        assertEquals("hi", SpeechPrefs(recognitionLanguage = "hi").languageFor(nemotron, "en-US"))
        assertEquals("ar", SpeechPrefs(recognitionLanguage = "ar").languageFor(nemotron, "en-US"))
        assertEquals("vi", SpeechPrefs(recognitionLanguage = "vi").languageFor(nemotron, "en-US"))
        // Turbo takes the hint for the languages it shares only with Nemotron (#37), Norwegian
        // as the app's "nb" (SherpaBackends sends Whisper "no"); others, such as Thai, fall
        // back to detection.
        assertEquals("hi", SpeechPrefs(recognitionLanguage = "hi").languageFor(turbo, "en-US"))
        assertEquals("nb", SpeechPrefs(recognitionLanguage = "nb").languageFor(turbo, "en-US"))
        assertEquals(LanguageOptions.AUTO, SpeechPrefs(recognitionLanguage = "th").languageFor(turbo, "en-US"))
    }

    @Test
    fun `on Auto engines detect, and the system language only fills in where they can't`() {
        val auto = SpeechPrefs()
        assertEquals(LanguageOptions.AUTO, auto.languageFor(nemotron, "ja-JP"))
        assertEquals(LanguageOptions.AUTO, auto.languageFor(turbo, "de-DE"))
        assertEquals("de", auto.languageFor(canary, "de-DE"))
        assertEquals("en", auto.languageFor(canary, "ja-JP"))
    }

    @Test
    fun `engines without language options get no hint`() {
        assertEquals("", SpeechPrefs().languageFor(default, "de-DE"))
        assertEquals("", SpeechPrefs(recognitionLanguage = "de").languageFor(default, "en-US"))
        assertEquals("", dev.copy(engineLanguages = mapOf(granite.id to "de")).languageFor(granite, "en-US"))
    }

    @Test
    fun `the spoken language is the chosen one, else the system's`() {
        assertEquals("pt", SpeechPrefs().spokenLanguage("pt-BR"))
        assertEquals("nb", SpeechPrefs().spokenLanguage("no-NO"))
        assertEquals("ja", SpeechPrefs(recognitionLanguage = "ja").spokenLanguage("de-DE"))
    }

    // ---- End of speech (#43) ------------------------------------------------

    @Test
    fun `defaults - keyboard Tap to stop, other apps Smart Turn at Normal, Dictate Pause, over, 1_2 s`() {
        val prefs = SpeechPrefs()
        assertEquals(EndOfDictation.TAP_STOP, prefs.keyboardEnd)
        assertEquals(EndOfDictation.SMART_TURN, prefs.appsEnd)
        assertEquals(Patience.NORMAL, prefs.patience)
        assertEquals(listOf("over"), prefs.endWords)
        assertEquals(1.2f, prefs.minSilenceSeconds)
        assertEquals(1.2f, SessionOptions.DEFAULT_MIN_SILENCE_SECONDS)
        assertEquals(EndOfDictation.PAUSE, prefs.endFor(ListenSurface.DICTATE))
        assertEquals(EndOfDictation.TAP_STOP, prefs.endFor(ListenSurface.KEYBOARD))
        assertEquals(EndOfDictation.SMART_TURN, prefs.endFor(ListenSurface.APPS))
    }

    @Test
    fun `the keyboard allows all four ends, other apps never Tap to stop, Dictate is always Pause`() {
        assertEquals(EndOfDictation.entries.toSet(), SpeechPrefs.KEYBOARD_ENDS.toSet())
        assertEquals(listOf(EndOfDictation.SMART_TURN, EndOfDictation.PAUSE, EndOfDictation.END_WORD), SpeechPrefs.APPS_ENDS)
        for (mode in EndOfDictation.entries) {
            val prefs = SpeechPrefs(keyboardEnd = mode, appsEnd = mode)
            assertEquals(mode, prefs.endFor(ListenSurface.KEYBOARD))
            assertEquals(if (mode == EndOfDictation.TAP_STOP) EndOfDictation.SMART_TURN else mode, prefs.endFor(ListenSurface.APPS))
            assertEquals(EndOfDictation.PAUSE, prefs.endFor(ListenSurface.DICTATE))
        }
    }

    @Test
    fun `a stored end is read only when the surface allows it, else its default`() {
        assertEquals(EndOfDictation.END_WORD, SpeechPrefs.endFrom("END_WORD", SpeechPrefs.KEYBOARD_ENDS))
        assertEquals(EndOfDictation.TAP_STOP, SpeechPrefs.endFrom(null, SpeechPrefs.KEYBOARD_ENDS))
        assertEquals(EndOfDictation.TAP_STOP, SpeechPrefs.endFrom("SOMETHING_NEW", SpeechPrefs.KEYBOARD_ENDS))
        assertEquals(EndOfDictation.PAUSE, SpeechPrefs.endFrom("PAUSE", SpeechPrefs.APPS_ENDS))
        assertEquals(EndOfDictation.SMART_TURN, SpeechPrefs.endFrom(null, SpeechPrefs.APPS_ENDS))
        assertEquals(EndOfDictation.SMART_TURN, SpeechPrefs.endFrom("TAP_STOP", SpeechPrefs.APPS_ENDS))
        assertEquals(Patience.RELAXED, SpeechPrefs.patienceFrom("RELAXED"))
        assertEquals(Patience.NORMAL, SpeechPrefs.patienceFrom(null))
        assertEquals(Patience.NORMAL, SpeechPrefs.patienceFrom("SLEEPY"))
    }

    @Test
    fun `stored end words are one per line, normalised, never empty`() {
        assertEquals(listOf("over", "send it"), SpeechPrefs.endWordsFrom(" Over \nsend  it\n\nOVER"))
        assertEquals(listOf("over"), SpeechPrefs.endWordsFrom(null))
        assertEquals(listOf("over"), SpeechPrefs.endWordsFrom(" \n "))
    }

    @Test
    fun `session options follow the surface`() {
        val prefs = SpeechPrefs(
            keyboardEnd = EndOfDictation.END_WORD,
            appsEnd = EndOfDictation.PAUSE,
            patience = Patience.RELAXED,
            minSilenceSeconds = 0.7f,
            endWords = listOf("send it"),
        )
        assertEquals(EndOfDictation.PAUSE, prefs.sessionOptions(ListenSurface.DICTATE).end)
        assertEquals(EndOfDictation.END_WORD, prefs.sessionOptions(ListenSurface.KEYBOARD).end)
        assertEquals(EndOfDictation.PAUSE, prefs.sessionOptions(ListenSurface.APPS).end)
        for (surface in ListenSurface.entries) {
            val options = prefs.sessionOptions(surface)
            assertEquals(0.7f, options.minSilenceSeconds)
            assertEquals(listOf("send it"), options.endWords)
            assertEquals(Patience.RELAXED, options.patience)
        }
        // The detector goes only to Smart Turn sessions: never Dictate.
        val detector = TurnDetector { 1f }
        val smart = SpeechPrefs(keyboardEnd = EndOfDictation.SMART_TURN)
        assertEquals(detector, smart.sessionOptions(ListenSurface.KEYBOARD, detector).turnDetector)
        assertEquals(detector, smart.sessionOptions(ListenSurface.APPS, detector).turnDetector)
        assertNull(smart.sessionOptions(ListenSurface.DICTATE, detector).turnDetector)
        assertNull(SpeechPrefs().sessionOptions(ListenSurface.KEYBOARD, detector).turnDetector)
    }

    @Test
    fun `Smart Turn without its model runs as Pause with the slider, with it the VAD proposes at the patience's pause`() {
        val without = SpeechPrefs(minSilenceSeconds = 1.3f).sessionOptions(ListenSurface.APPS)
        assertEquals(EndOfDictation.PAUSE, without.effectiveEnd)
        assertEquals(1.3f, without.vadMinSilenceSeconds)
        for (patience in Patience.entries) {
            val with = SpeechPrefs(minSilenceSeconds = 1.3f, patience = patience).sessionOptions(ListenSurface.APPS) { 0f }
            assertEquals(EndOfDictation.SMART_TURN, with.effectiveEnd)
            assertEquals(patience.proposeSeconds, with.vadMinSilenceSeconds)
        }
        for (mode in listOf(EndOfDictation.END_WORD, EndOfDictation.TAP_STOP)) {
            val options = SpeechPrefs(keyboardEnd = mode, minSilenceSeconds = 1.3f).sessionOptions(ListenSurface.KEYBOARD)
            assertEquals(1.3f, options.vadMinSilenceSeconds)
        }
        assertEquals(1.3f, SpeechPrefs(minSilenceSeconds = 1.3f).sessionOptions(ListenSurface.DICTATE) { 0f }.vadMinSilenceSeconds)
    }

    @Test
    fun `the Smart Turn model is warmed up while any surface uses it`() {
        assertTrue(SpeechPrefs().usesSmartTurn) // other apps, by default
        assertTrue(SpeechPrefs(appsEnd = EndOfDictation.PAUSE, keyboardEnd = EndOfDictation.SMART_TURN).usesSmartTurn)
        assertFalse(SpeechPrefs(appsEnd = EndOfDictation.PAUSE).usesSmartTurn)
    }

    @Test
    fun `usesEndWord is true only while a surface ends at an end word`() {
        assertFalse(SpeechPrefs().usesEndWord)
        assertTrue(SpeechPrefs(keyboardEnd = EndOfDictation.END_WORD).usesEndWord)
        assertTrue(SpeechPrefs(appsEnd = EndOfDictation.END_WORD).usesEndWord)
        // Tap to stop isn't an Other-apps choice: it reads as Smart Turn there.
        assertFalse(SpeechPrefs(keyboardEnd = EndOfDictation.PAUSE, appsEnd = EndOfDictation.TAP_STOP).usesEndWord)
    }

    @Test
    fun `the cleanup language is the spoken one for an engine without a hint`() {
        assertEquals("de", SpeechPrefs().cleanupLanguage("de-DE"))
        assertEquals("en", SpeechPrefs(recognitionLanguage = "en").cleanupLanguage("de-DE"))
        assertEquals("ja", SpeechPrefs(recognitionLanguage = "ja").cleanupLanguage("en-US"))
    }

    @Test
    fun `an English-only engine cleans up as English whatever the phone's language`() {
        assertEquals("en", SpeechPrefs(selectedEngineId = "parakeet-tdt-110m-en").cleanupLanguage("de-DE"))
        assertEquals("en", dev.copy(selectedEngineId = granite.id, recognitionLanguage = "fr").cleanupLanguage("fr-FR"))
    }

    @Test
    fun `an engine's language hint is the cleanup language`() {
        val onCanary = dev.copy(selectedEngineId = canary.id)
        assertEquals("de", onCanary.cleanupLanguage("de-DE"))
        // Canary can't do Japanese, so it runs (and writes) English.
        assertEquals("en", onCanary.cleanupLanguage("ja-JP"))
        assertEquals("fr", onCanary.copy(engineLanguages = mapOf(canary.id to "fr")).cleanupLanguage("en-US"))
        // Whisper turbo on Auto detects the language: fall back to the spoken one.
        val onTurbo = dev.copy(selectedEngineId = turbo.id)
        assertEquals("de", onTurbo.cleanupLanguage("de-DE"))
        assertEquals("es", onTurbo.copy(recognitionLanguage = "es").cleanupLanguage("en-US"))
    }

    @Test
    fun `English heard on a Japanese phone stays plain English`() {
        // Ultra takes no hint and turbo on Auto detects: both clean up in the phone's Japanese (#60).
        for (prefs in listOf(SpeechPrefs(), dev.copy(selectedEngineId = turbo.id))) {
            val language = prefs.cleanupLanguage("ja-JP")
            assertEquals("ja", language)
            val pipeline = TextPipeline(TextProcConfig(language = language, lowerCaseLatin = prefs.activeEngine.lowerCaseLatin))
            assertEquals(TextEdit(0, "The app is ok"), pipeline.process("the app is ok"))
            assertEquals(TextEdit(0, " And it was great"), pipeline.process("and it was great", "東京。"))
        }
    }

    @Test
    fun `cleanupLanguageFor is the cleanup language with that engine active, whatever is selected`() {
        val onCanary = dev.copy(selectedEngineId = canary.id)
        // Canary on a German phone writes German; an English-only engine would write English.
        assertEquals("de", onCanary.cleanupLanguage("de-DE"))
        assertEquals("en", onCanary.cleanupLanguageFor(granite, "de-DE"))
        assertEquals(onCanary.copy(selectedEngineId = turbo.id).cleanupLanguage("de-DE"), onCanary.cleanupLanguageFor(turbo, "de-DE"))
        assertEquals(onCanary.cleanupLanguage("de-DE"), onCanary.cleanupLanguageFor(canary, "de-DE"))
    }

    @Test
    fun `a caller's language is the hint and the cleanup language for its request only`() {
        val onNemotron = SpeechPrefs(selectedEngineId = nemotron.id)
        val request = onNemotron.forRequestLanguage("hi-IN")
        assertEquals("hi", request.languageFor(nemotron, "en-US"))
        assertEquals("hi", request.cleanupLanguage("en-US"))
        // The stored settings are untouched: back on Auto, Nemotron detects.
        assertEquals(LanguageOptions.AUTO, onNemotron.languageFor(nemotron, "en-US"))
        // It beats a chosen recognition language and a developer override.
        val pinned = dev.copy(selectedEngineId = turbo.id, recognitionLanguage = "fr", engineLanguages = mapOf(turbo.id to "es"))
        assertEquals("ja", pinned.forRequestLanguage("ja").languageFor(turbo, "en-US"))
        // An English-only engine still writes English.
        assertEquals("en", SpeechPrefs(selectedEngineId = "parakeet-tdt-110m-en").forRequestLanguage("de").cleanupLanguage("de-DE"))
    }

    @Test
    fun `a new recommendation pins the installed engine running on the old one`() {
        val tiny = "parakeet-tdt-110m-en"
        val nemotronId = nemotron.id
        val onTiny = SpeechPrefs(recommendedEngineId = tiny)
        // 110M runs as the recommendation and is installed: pinned before Nemotron takes over.
        assertEquals(tiny, onTiny.engineToPin(nemotronId) { it == tiny })
        // Not installed: nothing to keep.
        assertNull(onTiny.engineToPin(nemotronId) { false })
        // The user picked one, or the recommendation doesn't change: nothing to do.
        assertNull(onTiny.copy(selectedEngineId = tiny).engineToPin(nemotronId) { true })
        assertNull(onTiny.engineToPin(tiny) { true })
        // No recommendation yet: the default runs, and is pinned if installed.
        assertEquals(default.id, SpeechPrefs().engineToPin(nemotronId) { true })
    }

    // ---- DataStore round trip ---------------------------------------------

    @get:Rule
    val tmp = TemporaryFolder()

    private var job: Job? = null

    private suspend fun openStore(): DataStore<Preferences> {
        job?.cancelAndJoin()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob().also { job = it })
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "settings.preferences_pb") })
    }

    @After
    fun closeStore() = runBlocking { job?.cancelAndJoin() ?: Unit }

    @Test
    fun `selection, developer toggle and per-engine languages survive a restart`() = runBlocking {
        val qwen = SttEngineCatalog.byId(SttEngineCatalog.QWEN3)!! // takes a language hint
        val granite = SttEngineCatalog.byId("granite-speech-5-470m")!! // a developer engine
        val settings = SpeechSettings(openStore())
        settings.setDeveloperEngines(true)
        settings.setSelectedEngine(granite.id)
        settings.setEngineLanguage(qwen.id, "fr")
        settings.setEngineLanguage(canary.id, "de")
        settings.setEngineLanguage(qwen.id, LanguageOptions.AUTO)
        settings.setRecognitionLanguage("ja")

        val prefs = SpeechSettings(openStore()).current()
        assertEquals("ja", prefs.recognitionLanguage)
        assertEquals(granite, prefs.activeEngine)
        // Only the engine_language_ keys, by engine id; the last write wins, "" included.
        assertEquals(mapOf(qwen.id to LanguageOptions.AUTO, canary.id to "de"), prefs.engineLanguages)
        assertEquals(LanguageOptions.AUTO, prefs.languageFor(qwen, "en-US"))
        assertEquals("de", prefs.languageFor(canary, "en-US"))

        SpeechSettings(openStore()).setDeveloperEngines(false)
        assertEquals(default, SpeechSettings(openStore()).current().activeEngine)
    }

    @Test
    fun `the ends, patience and end words survive a restart`() = runBlocking {
        SpeechSettings(openStore()).setKeyboardEnd(EndOfDictation.END_WORD)
        SpeechSettings(openStore()).setAppsEnd(EndOfDictation.PAUSE)
        SpeechSettings(openStore()).setPatience(Patience.QUICK)
        SpeechSettings(openStore()).setEndWords(listOf(" Send it", "OVER", "", "over"))
        var prefs = SpeechSettings(openStore()).current()
        assertEquals(EndOfDictation.END_WORD, prefs.keyboardEnd)
        assertEquals(EndOfDictation.PAUSE, prefs.appsEnd)
        assertEquals(Patience.QUICK, prefs.patience)
        assertEquals(listOf("send it", "over"), prefs.endWords)

        // An empty list is never stored.
        SpeechSettings(openStore()).setEndWords(listOf(" ", ""))
        prefs = SpeechSettings(openStore()).current()
        assertEquals(listOf("send it", "over"), prefs.endWords)

        // Tap to stop isn't one of the other apps' ends: stored anyway, it reads as the default.
        SpeechSettings(openStore()).setAppsEnd(EndOfDictation.TAP_STOP)
        assertEquals(EndOfDictation.SMART_TURN, SpeechSettings(openStore()).current().appsEnd)
    }

    @Test
    fun `a fresh install gets the defaults, and patient mode from before #43 is Tap to stop on the keyboard`() = runBlocking {
        val fresh = SpeechSettings(openStore()).current()
        assertEquals(EndOfDictation.TAP_STOP, fresh.keyboardEnd)
        assertEquals(EndOfDictation.SMART_TURN, fresh.appsEnd)
        assertEquals(Patience.NORMAL, fresh.patience)
        for (patient in listOf(true, false)) {
            openStore().edit { it[booleanPreferencesKey("speech_patient_mode")] = patient }
            val prefs = SpeechSettings(openStore()).current()
            assertEquals(EndOfDictation.TAP_STOP, prefs.keyboardEnd)
            assertEquals(EndOfDictation.SMART_TURN, prefs.appsEnd)
        }
    }

    @Test
    fun `an unmoved slider follows the default, a moved one keeps its value`() = runBlocking {
        // Nothing stored (never moved, including everyone who had 0.9 s by default before #43): 1.2 s.
        assertEquals(1.2f, SpeechSettings(openStore()).current().minSilenceSeconds)
        // Someone who moved it, even to the old default, keeps what they chose.
        SpeechSettings(openStore()).setMinSilenceSeconds(0.9f)
        assertEquals(0.9f, SpeechSettings(openStore()).current().minSilenceSeconds)
        openStore().edit { it[floatPreferencesKey("speech_min_silence_seconds")] = 0.7f }
        assertEquals(0.7f, SpeechSettings(openStore()).current().minSilenceSeconds)
    }

    @Test
    fun `the probe and the recommendation survive a restart and set the budget`() = runBlocking {
        val unmeasured = SpeechSettings(openStore()).current()
        assertNull(unmeasured.deviceProbeMs)
        assertNull(unmeasured.deviceSpeedFactor)
        assertNull(unmeasured.recommendedEngineId)
        assertEquals(SpeedBudget.REFERENCE_BUDGET, unmeasured.speedBudget, 0.0)

        // Half the reference time: twice a Pixel 8a.
        SpeechSettings(openStore()).setDeviceProbe(SpeedBudget.PROBE_REFERENCE_MS / 2, SpeedBudget.REFERENCE_THREADS)
        SpeechSettings(openStore()).setRecommendedEngine("qwen3-asr-0-6b")
        val prefs = SpeechSettings(openStore()).current()
        assertEquals(SpeedBudget.PROBE_REFERENCE_MS / 2, prefs.deviceProbeMs)
        assertEquals(SpeedBudget.REFERENCE_THREADS, prefs.deviceProbeThreads)
        assertEquals(2.0, prefs.deviceSpeedFactor!!, 0.02) // the reference / 2 rounds down to whole ms
        assertEquals(prefs.deviceSpeedFactor!! * SpeedBudget.REFERENCE_BUDGET, prefs.speedBudget, 1e-9)
        assertEquals("qwen3-asr-0-6b", prefs.recommendedEngineId)
        // Stored with the rules it was made under (#50).
        assertEquals(EngineRecommender.RULES_VERSION, prefs.recommendationRules)
        assertFalse(prefs.recommendationOutdated)
        assertEquals(SpeedBudget.REFERENCE, prefs.engineCosts)
        // The RAM (#47) is stored with the recommendation. On a 4 GB phone Ultra runs without ORT's
        // arena: its card counts the lean memory and the slower cost; an 8 GB phone's doesn't.
        assertNull(prefs.deviceTotalMemBytes)
        val fourGb = (3.6 * (1L shl 30)).toLong()
        SpeechSettings(openStore()).setDeviceTotalMem(fourGb)
        val small = SpeechSettings(openStore()).current()
        assertEquals(fourGb, small.deviceTotalMemBytes)
        val ultra = SttEngineCatalog.PARAKEET_ULTRA
        val lean = SpeedBudget.REFERENCE.getValue(ultra).lean!!
        assertEquals(lean.peakRssMb, small.engineCosts.getValue(ultra).peakRssMb)
        assertEquals(lean.costFactor, small.engineCosts.getValue(ultra).relativeCost, 1e-9)
        val big = small.copy(deviceTotalMemBytes = 8L shl 30)
        assertEquals(SpeedBudget.REFERENCE.getValue(ultra).peakRssMb, big.engineCosts.getValue(ultra).peakRssMb)
        assertEquals(1.0, big.engineCosts.getValue(ultra).relativeCost, 0.0)
    }

    @Test
    fun `a phone with fewer recognizer threads pays each engine's thread penalty`() {
        val parakeet = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA)!!
        val pixel = SpeechPrefs(deviceProbeMs = SpeedBudget.PROBE_REFERENCE_MS, deviceProbeThreads = 4)
        val twoThreads = pixel.copy(deviceProbeThreads = 2)
        // The same probe time is the same budget: the thread count moves the costs instead.
        assertEquals(pixel.speedBudget, twoThreads.speedBudget, 0.0)
        val base = SpeedBudget.REFERENCE.getValue(parakeet.id).relativeCost
        assertEquals(base * 1.45, twoThreads.engineCosts.getValue(parakeet.id).relativeCost, 1e-9)
        assertEquals(
            SpeedBudget.speedTier(parakeet, twoThreads.speedBudget, twoThreads.engineCosts),
            twoThreads.speedTier(parakeet),
        )
    }

    @Test
    fun `engines that fell behind survive a restart until cleared`() = runBlocking {
        assertEquals(emptySet<String>(), SpeechSettings(openStore()).current().tooSlowEngineIds)
        SpeechSettings(openStore()).addTooSlowEngine(turbo.id)
        SpeechSettings(openStore()).addTooSlowEngine(canary.id)
        SpeechSettings(openStore()).addTooSlowEngine(turbo.id)
        assertEquals(setOf(turbo.id, canary.id), SpeechSettings(openStore()).current().tooSlowEngineIds)
        SpeechSettings(openStore()).clearTooSlowEngines()
        assertEquals(emptySet<String>(), SpeechSettings(openStore()).current().tooSlowEngineIds)
    }

    @Test
    fun `a developer engine picked with Developer engines off is kept, one picked with them on isn't`() = runBlocking {
        val granite = SttEngineCatalog.byId("granite-speech-5-470m")!! // a developer engine
        // Off: only the recommended one can be picked, and it stays once the recommendation moves (#50).
        SpeechSettings(openStore()).setRecommendedEngine(granite.id)
        SpeechSettings(openStore()).setSelectedEngine(granite.id)
        SpeechSettings(openStore()).setRecommendedEngine(SttEngineCatalog.PARAKEET_ULTRA)
        var prefs = SpeechSettings(openStore()).current()
        assertTrue(prefs.selectionKept)
        assertEquals(granite, prefs.activeEngine)
        assertTrue(granite in prefs.visibleEngines)
        // Picking a user engine drops the mark.
        SpeechSettings(openStore()).setSelectedEngine(canary.id)
        assertFalse(SpeechSettings(openStore()).current().selectionKept)
        // On: picked as a developer engine; turning them off stops it, as before.
        SpeechSettings(openStore()).setDeveloperEngines(true)
        SpeechSettings(openStore()).setSelectedEngine(granite.id)
        SpeechSettings(openStore()).setDeveloperEngines(false)
        prefs = SpeechSettings(openStore()).current()
        assertFalse(prefs.selectionKept)
        assertEquals(SttEngineCatalog.PARAKEET_ULTRA, prefs.activeEngine.id)
        assertFalse(granite in prefs.visibleEngines)
    }

    @Test
    fun `the running engine is pinned only when it would otherwise stop`() {
        val granite = "granite-speech-5-470m"
        val installed = { _: String -> true }
        // Nothing selected, the recommendation runs: pinned before it moves.
        assertEquals(granite, SpeechPrefs(recommendedEngineId = granite).engineToPin("whisper-turbo", installed))
        // A developer engine selected only as the recommendation: pinned again, so it is kept.
        assertEquals(granite, SpeechPrefs(selectedEngineId = granite, recommendedEngineId = granite).engineToPin("whisper-turbo", installed))
        // Already kept, or a user engine selected, or Developer engines on: it runs on by itself.
        assertNull(SpeechPrefs(selectedEngineId = granite, selectionKept = true, recommendedEngineId = granite).engineToPin("whisper-turbo", installed))
        assertNull(SpeechPrefs(selectedEngineId = canary.id, recommendedEngineId = granite).engineToPin("whisper-turbo", installed))
        assertNull(SpeechPrefs(selectedEngineId = granite, developerEngines = true, recommendedEngineId = granite).engineToPin("whisper-turbo", installed))
        // Not downloaded, or the recommendation doesn't change: nothing.
        assertNull(SpeechPrefs(recommendedEngineId = granite).engineToPin("whisper-turbo") { false })
        assertNull(SpeechPrefs(recommendedEngineId = granite).engineToPin(granite, installed))
    }

    @Test
    fun `a recommended developer engine can run with developer engines off`() = runBlocking {
        val granite = SttEngineCatalog.byId("granite-speech-5-470m")!! // a developer engine
        SpeechSettings(openStore()).setSelectedEngine(granite.id)
        assertEquals(default, SpeechSettings(openStore()).current().activeEngine)
        SpeechSettings(openStore()).setRecommendedEngine(granite.id)
        assertEquals(granite, SpeechSettings(openStore()).current().activeEngine)
    }

    @Test
    fun `the Other engines state survives a restart`() = runBlocking {
        assertEquals(false, SpeechSettings(openStore()).current().otherEnginesExpanded)
        SpeechSettings(openStore()).setOtherEnginesExpanded(true)
        assertEquals(true, SpeechSettings(openStore()).current().otherEnginesExpanded)
    }

    @Test
    fun `the runtime tuning is a developer setting`() {
        val noSpin = OrtTuning(spinning = false)
        assertEquals(OrtTuning.DEFAULT, SpeechPrefs().ortTuning)
        assertEquals(OrtTuning.DEFAULT, SpeechPrefs(ortTuningId = noSpin.id).ortTuning)
        assertEquals(noSpin, SpeechPrefs(ortTuningId = noSpin.id, developerEngines = true).ortTuning)
        assertEquals(OrtTuning.DEFAULT, SpeechPrefs(developerEngines = true).ortTuning)
        assertEquals(OrtTuning.DEFAULT, SpeechPrefs(ortTuningId = "gpu", developerEngines = true).ortTuning)
    }

    @Test
    fun `the runtime tuning survives a restart and clears`() = runBlocking {
        assertNull(SpeechSettings(openStore()).current().ortTuningId)
        val tuning = OrtTuning(spinning = false, pin = true)
        SpeechSettings(openStore()).setOrtTuning(tuning.id)
        SpeechSettings(openStore()).setDeveloperEngines(true)
        SpeechSettings(openStore()).current().let {
            assertEquals(tuning.id, it.ortTuningId)
            assertEquals(tuning, it.ortTuning)
        }
        SpeechSettings(openStore()).setOrtTuning(null)
        SpeechSettings(openStore()).current().let {
            assertNull(it.ortTuningId)
            assertEquals(OrtTuning.DEFAULT, it.ortTuning)
        }
    }

    @Test
    fun `a stored runtime tuning that isn't a preset reads as none`() = runBlocking {
        openStore().edit { it[stringPreferencesKey("ort_tuning")] = "gpu-turbo" }
        assertNull(SpeechSettings(openStore()).current().ortTuningId)
        // Parses, but a benchmark-only core override isn't a Settings choice.
        openStore().edit { it[stringPreferencesKey("ort_tuning")] = "cpu-pin:0.1" }
        assertNull(SpeechSettings(openStore()).current().ortTuningId)
    }
}
