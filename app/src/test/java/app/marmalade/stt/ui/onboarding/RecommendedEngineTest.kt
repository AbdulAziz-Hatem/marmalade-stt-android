package app.marmalade.stt.ui.onboarding

import app.marmalade.stt.R
import app.marmalade.stt.install.EngineRecommender.Reason
import app.marmalade.stt.install.EngineRecommender.Recommendation
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendedEngineTest {

    private val parakeet = checkNotNull(SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA))
    private val small = checkNotNull(SttEngineCatalog.byId("parakeet-tdt-110m-en"))

    private fun rec(reason: Reason, language: String = "en", covered: Boolean = true) =
        Recommendation(parakeet, reason, language, covered)

    // --- The line of why ---------------------------------------------------------

    @Test
    fun `best fit names the phone's language`() {
        assertEquals(
            StringWithArgs(R.string.onboarding_recommend_best_fit, listOf("English")),
            recommendationText(rec(Reason.BEST_FIT), Locale.ENGLISH),
        )
    }

    @Test
    fun `a lighter engine says it suits the phone, without a language`() {
        assertEquals(
            StringWithArgs(R.string.onboarding_recommend_lighter),
            recommendationText(rec(Reason.LIGHTER_FOR_PHONE, "de"), Locale.ENGLISH),
        )
    }

    @Test
    fun `may be slow and low memory name the language`() {
        assertEquals(
            StringWithArgs(R.string.onboarding_recommend_may_be_slow, listOf("Japanese")),
            recommendationText(rec(Reason.MAY_BE_SLOW, "ja"), Locale.ENGLISH),
        )
        assertEquals(
            StringWithArgs(R.string.onboarding_recommend_low_memory, listOf("Thai")),
            recommendationText(rec(Reason.LOW_MEMORY, "th"), Locale.ENGLISH),
        )
    }

    @Test
    fun `an uncovered language says so and that the engine is for English, whatever the reason`() {
        for (reason in Reason.entries) {
            assertEquals(
                StringWithArgs(R.string.onboarding_recommend_uncovered, listOf("Zulu", "English")),
                recommendationText(rec(reason, "zu", covered = false), Locale.ENGLISH),
            )
        }
    }

    @Test
    fun `language names are in the user's own language, as written mid-sentence`() {
        assertEquals(listOf("Französisch"), recommendationText(rec(Reason.BEST_FIT, "fr"), Locale.GERMAN).args)
        // #51: French writes language names in lower case inside a sentence.
        assertEquals(listOf("français"), recommendationText(rec(Reason.BEST_FIT, "fr"), Locale.FRENCH).args)
    }

    // --- Selecting on install ------------------------------------------------------

    @Test
    fun `finished installing fires on the change to Installed, not for an engine installed already`() = runTest {
        val downloading = InstallState.Downloading(1L, 10L, "model.onnx")
        assertEquals(
            1,
            finishedInstalling(flowOf(InstallState.NotInstalled, downloading, downloading, InstallState.Installed)).toList().size,
        )
        assertEquals(0, finishedInstalling(flowOf(InstallState.Installed)).toList().size)
        assertEquals(0, finishedInstalling(flowOf(InstallState.Installed, InstallState.NotInstalled)).toList().size)
        assertEquals(0, finishedInstalling(flowOf(InstallState.NotInstalled, downloading)).toList().size)
    }

    private class FakeInstaller {
        val flows = mutableMapOf<String, MutableStateFlow<InstallState>>()
        fun state(id: String): StateFlow<InstallState> = flows.getOrPut(id) { MutableStateFlow(InstallState.NotInstalled) }
    }

    @Test
    fun `checking, then the recommended engine's state, and it is selected once installed`() = runTest {
        val installer = FakeInstaller()
        val answer = CompletableDeferred<Recommendation>()
        val selected = mutableListOf<String>()
        val engine = RecommendedEngine(
            scope = backgroundScope,
            recommend = { answer.await() },
            installState = installer::state,
            select = { selected += it },
        )

        assertNull(engine.recommendation.value)
        assertEquals(InstallState.NotInstalled, engine.state.value)

        val rec = Recommendation(small, Reason.LIGHTER_FOR_PHONE, "en", true)
        answer.complete(rec)
        testScheduler.runCurrent()
        assertEquals(rec, engine.recommendation.value)

        val flow = installer.flows.getValue(small.id)
        flow.value = InstallState.Downloading(1L, 10L, "model.onnx")
        testScheduler.runCurrent()
        assertEquals(flow.value, engine.state.value)
        assertEquals(emptyList<String>(), selected)

        flow.value = InstallState.Installed
        testScheduler.runCurrent()
        assertEquals(InstallState.Installed, engine.state.value)
        assertEquals(listOf(small.id), selected)
    }

    @Test
    fun `a recommended engine installed already isn't selected over the user's choice`() = runTest {
        val installer = FakeInstaller()
        installer.state(parakeet.id)
        installer.flows.getValue(parakeet.id).value = InstallState.Installed
        val selected = mutableListOf<String>()
        val engine = RecommendedEngine(
            scope = backgroundScope,
            recommend = { rec(Reason.BEST_FIT) },
            installState = installer::state,
            select = { selected += it },
        )
        testScheduler.runCurrent()
        assertEquals(InstallState.Installed, engine.state.value)
        assertEquals(emptyList<String>(), selected)
    }
}
