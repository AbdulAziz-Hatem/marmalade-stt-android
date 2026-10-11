package app.marmalade.stt.speech

import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import app.marmalade.stt.textproc.TextProcConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LivePipelineTest {

    private class FakeSettings(initial: TextProcConfig) : TextProcSettings {
        override val config = MutableStateFlow(initial)
        override suspend fun setRemoveFillers(on: Boolean) = Unit
        override suspend fun setSpokenCommands(on: Boolean) = Unit
        override suspend fun setSpokenPunctuation(on: Boolean) = Unit
        override suspend fun setNumbersAsDigits(on: Boolean) = Unit
        override suspend fun setCapitalizeSentences(on: Boolean) = Unit
        override suspend fun setReplacements(rules: List<Replacement>) = Unit
        override suspend fun setSnippets(snippets: List<Snippet>) = Unit
    }

    @Test
    fun `rebuilds the pipeline when the settings change`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeSettings(TextProcConfig())
        val live = LivePipeline(backgroundScope, settings)
        assertEquals("Teh cat.", live.get().process("teh cat.").insert)

        settings.config.value = TextProcConfig(replacements = listOf(Replacement("teh", "the")))
        assertEquals("The cat.", live.get().process("teh cat.").insert)
    }

    @Test
    fun `a request's language overrides the settings' language only for that pipeline`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeSettings(TextProcConfig(language = "en", replacements = listOf(Replacement("teh", "the"))))
        val live = LivePipeline(backgroundScope, settings)
        val german = live.get("de")
        assertEquals("de", german.config.language)
        assertEquals("Er sagt the.", german.process("Er sagt teh.").insert)
        assertSame(german, live.get("de"))
        assertEquals("Sagt the.", live.get().process("Er sagt teh.").insert)
        assertSame(live.get(), live.get("en"))
    }
}
