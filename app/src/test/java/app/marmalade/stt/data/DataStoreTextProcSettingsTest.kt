package app.marmalade.stt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.marmalade.stt.speech.SttEvent
import app.marmalade.stt.textproc.PhantomFilter
import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import app.marmalade.stt.textproc.TextPipeline
import app.marmalade.stt.textproc.TextProcConfig
import app.marmalade.stt.ui.screen.Dictation
import app.marmalade.stt.ui.screen.DictationState
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real DataStore over a temp file: what a setter writes, a fresh reader of the same file sees. */
class DataStoreTextProcSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var job: Job? = null
    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    /**
     * A new DataStore on the same file, as after a process restart. Only one
     * may be open per file, and the old one lets go once its scope has
     * finished, hence cancelAndJoin.
     */
    private suspend fun openStore(): DataStore<Preferences> {
        job?.cancelAndJoin()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob().also { job = it })
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun settings(store: DataStore<Preferences>) = DataStoreTextProcSettings(store, SpeechSettings(store))

    /** The language the default engine cleans up in on this JVM's locale. */
    private val defaultLanguage = SpeechPrefs().cleanupLanguage()

    /** Whose phantom phrases the default engine's text is checked against (#57). */
    private val defaultRecognizer = SpeechPrefs().activeEngine.family.recognizer

    @After
    fun closeStore() = runBlocking { job?.cancelAndJoin() ?: Unit }

    @Test
    fun `nothing written reads as the textproc defaults`() = runBlocking {
        assertEquals(
            TextProcConfig(language = defaultLanguage, recognizer = defaultRecognizer),
            settings(openStore()).config.first(),
        )
    }

    @Test
    fun `every setting survives a restart`() = runBlocking {
        val rules = listOf(
            Replacement("teh", "the"),
            Replacement("café", "Café"),
            Replacement("東京", "Tokyo"),
            Replacement("basically", ""),
            Replacement("teh", "shadowed"),
        )
        val snippets = listOf(
            Snippet("my address", "1 Example Road\nSpringfield\n\n"),
            Snippet("sig", "— Max 🍊 \"quoted\" \\ back"),
        )
        settings(openStore()).apply {
            setRemoveFillers(false)
            setSpokenCommands(true)
            setSpokenPunctuation(true)
            setNumbersAsDigits(false)
            setCapitalizeSentences(false)
            setReplacements(rules)
            setSnippets(snippets)
        }

        val expected = TextProcConfig(
            removeFillers = false,
            spokenCommands = true,
            spokenPunctuation = true,
            numbersAsDigits = false,
            capitalizeSentences = false,
            replacements = rules,
            snippets = snippets,
            language = defaultLanguage,
            recognizer = defaultRecognizer,
        )
        assertEquals(expected, settings(openStore()).config.first())
    }

    @Test
    fun `the phrases-in-silence filter is always NEAR_SILENT, whatever was stored before 42`() = runBlocking {
        for (stored in listOf("OFF", "ALWAYS", "NEAR_SILENT", "SOMETIMES")) {
            val store = openStore()
            store.edit { it[stringPreferencesKey("cleanup_phantom_filter")] = stored }
            assertEquals(stored, PhantomFilter.NEAR_SILENT, settings(store).config.first().phantomFilter)
        }
    }

    @Test
    fun `punctuation words apply only with voice commands on, and keep their choice`() = runBlocking {
        val settings = settings(openStore())
        settings.setSpokenPunctuation(true)
        assertTrue(settings.config.first().spokenPunctuation)

        settings.setSpokenCommands(false)
        val off = settings.config.first()
        assertFalse(off.spokenCommands)
        assertFalse("off with voice commands", off.spokenPunctuation)

        settings.setSpokenCommands(true)
        assertTrue("the stored choice is back", settings.config.first().spokenPunctuation)
    }

    @Test
    fun `lists can be emptied again`() = runBlocking {
        val settings = settings(openStore())
        settings.setReplacements(listOf(Replacement("a", "b")))
        settings.setSnippets(listOf(Snippet("x", "y")))
        settings.setReplacements(emptyList())
        settings.setSnippets(emptyList())
        val config = settings.config.first()
        assertEquals(emptyList<Replacement>(), config.replacements)
        assertEquals(emptyList<Snippet>(), config.snippets)
    }

    @Test
    fun `a damaged list falls back instead of failing`() = runBlocking {
        val store = openStore()
        store.edit { it[stringPreferencesKey("cleanup_replacements_json")] = "{broken" }
        val config = settings(store).config.first()
        assertEquals(emptyList<Replacement>(), config.replacements)
    }

    @Test
    fun `a dictionary entry saved in settings changes what Dictate writes`() = runBlocking {
        val settings = settings(openStore())
        val heard = SttEvent.Final("I pushed it to git hub.", nearSilent = false)

        fun dictate(config: TextProcConfig) = Dictation(TextPipeline(config)).reduce(DictationState(), heard).transcript

        assertEquals("I pushed it to git hub.", dictate(settings.config.first()))
        settings.setReplacements(listOf(Replacement("git hub", "GitHub")))
        assertEquals("I pushed it to GitHub.", dictate(settings.config.first()))
    }

    @Test
    fun `the recognition language reaches the cleanup, so German keeps its er`() = runBlocking {
        val store = openStore()
        val settings = settings(store)
        val heard = SttEvent.Final("Er sagt, er kommt.", nearSilent = false)

        fun dictate(config: TextProcConfig) = Dictation(TextPipeline(config)).reduce(DictationState(), heard).transcript

        SpeechSettings(store).setRecognitionLanguage("en")
        assertEquals("en", settings.config.first().language)
        assertEquals("Sagt, kommt.", dictate(settings.config.first()))
        SpeechSettings(store).setRecognitionLanguage("de")
        assertEquals("de", settings.config.first().language)
        assertEquals("Er sagt, er kommt.", dictate(settings.config.first()))
    }
}
