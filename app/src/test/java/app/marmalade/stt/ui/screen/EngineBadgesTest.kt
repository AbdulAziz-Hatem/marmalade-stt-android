package app.marmalade.stt.ui.screen

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.CorruptProblem
import app.marmalade.stt.install.InstallFailure
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.ui.screen.EngineBadge.DEVELOPER
import app.marmalade.stt.ui.screen.EngineBadge.RECOMMENDED
import app.marmalade.stt.ui.screen.EngineBadge.STREAMING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineBadgesTest {

    private val default = SttEngineCatalog.default
    /** A developer engine, whatever [SttEngineCatalog.QWEN3_FOR_USERS] says. */
    private val granite = SttEngineCatalog.byId("granite-speech-5-470m")!!
    private val turbo = SttEngineCatalog.byId("whisper-turbo")!!
    private val nemotron = SttEngineCatalog.byId(SttEngineCatalog.NEMOTRON_35_1120MS)!!

    @Test
    fun `badges follow the descriptor and the recommended engine`() {
        assertEquals(emptyList<EngineBadge>(), engineBadges(default, recommendedId = null, showDeveloper = false))
        assertEquals(listOf(RECOMMENDED), engineBadges(default, recommendedId = default.id, showDeveloper = false))
        assertEquals(emptyList<EngineBadge>(), engineBadges(default, recommendedId = turbo.id, showDeveloper = false))
        assertEquals(emptyList<EngineBadge>(), engineBadges(turbo, recommendedId = null, showDeveloper = false))
        assertEquals(listOf(DEVELOPER), engineBadges(granite, recommendedId = default.id, showDeveloper = false))
        assertEquals(listOf(DEVELOPER), engineBadges(granite, recommendedId = null, showDeveloper = true))
        assertEquals(listOf(STREAMING), engineBadges(nemotron, recommendedId = null, showDeveloper = false))
        // A developer engine recommended for the phone (as Qwen3 was before #37).
        assertEquals(listOf(RECOMMENDED, DEVELOPER), engineBadges(granite, recommendedId = granite.id, showDeveloper = false))
    }

    @Test
    fun `the active engine is in use whatever its install state`() {
        val prefs = SpeechPrefs(selectedEngineId = turbo.id, developerEngines = true)
        assertEquals(EngineUse.IN_USE, engineUse(turbo, InstallState.Installed, prefs))
        assertEquals(EngineUse.IN_USE, engineUse(turbo, InstallState.Corrupt(CorruptProblem.MISSING, "x"), prefs))
        assertEquals(EngineUse.IN_USE, engineUse(default, InstallState.NotInstalled, SpeechPrefs()))
    }

    @Test
    fun `an installed visible engine can be picked, anything else can't`() {
        val shown = SpeechPrefs(developerEngines = true)
        assertEquals(EngineUse.AVAILABLE, engineUse(granite, InstallState.Installed, shown))
        assertEquals(EngineUse.UNAVAILABLE, engineUse(granite, InstallState.NotInstalled, shown))
        assertEquals(EngineUse.UNAVAILABLE, engineUse(granite, InstallState.Paused(1L, 10L), shown))
        assertEquals(EngineUse.UNAVAILABLE, engineUse(granite, InstallState.Downloading(1L, 10L, "x"), shown))
        assertEquals(EngineUse.UNAVAILABLE, engineUse(granite, InstallState.Failed("net", InstallFailure.NETWORK), shown))
        assertEquals(EngineUse.UNAVAILABLE, engineUse(granite, InstallState.Corrupt(CorruptProblem.MISSING, "x"), shown))
        // Hidden developer engine: picking it would be ignored, so it isn't offered.
        assertEquals(EngineUse.UNAVAILABLE, engineUse(granite, InstallState.Installed, SpeechPrefs()))
        // Unless it is the one recommended for this phone, which is visible anyway.
        val recommendedGranite = SpeechPrefs(selectedEngineId = default.id, recommendedEngineId = granite.id)
        assertEquals(EngineUse.AVAILABLE, engineUse(granite, InstallState.Installed, recommendedGranite))
        // Back to the default from a developer engine.
        val onGranite = SpeechPrefs(selectedEngineId = granite.id, developerEngines = true)
        assertEquals(EngineUse.AVAILABLE, engineUse(default, InstallState.Installed, onGranite))
        // A user engine runs when picked, so it can be.
        assertEquals(EngineUse.AVAILABLE, engineUse(nemotron, InstallState.Installed, SpeechPrefs()))
    }

    @Test
    fun `removing the selected engine selects the default again`() {
        val onGranite = SpeechPrefs(selectedEngineId = granite.id, developerEngines = true)
        assertEquals(default.id, selectionAfterRemoving(granite.id, onGranite))
        // Hidden but still selected: reset too, so showing developer engines
        // again doesn't bring back an engine with no files.
        assertEquals(default.id, selectionAfterRemoving(granite.id, onGranite.copy(developerEngines = false)))
        // Removing some other engine, or the default itself, leaves the choice alone.
        assertNull(selectionAfterRemoving(turbo.id, onGranite))
        assertNull(selectionAfterRemoving(default.id, SpeechPrefs(selectedEngineId = default.id)))
        assertNull(selectionAfterRemoving(granite.id, SpeechPrefs()))
        // With a recommendation, removing the selected engine falls back to it...
        val nemotron = SttEngineCatalog.NEMOTRON_35_1120MS
        assertEquals(nemotron, selectionAfterRemoving(granite.id, onGranite.copy(recommendedEngineId = nemotron)))
        // ...unless the recommended engine is the one removed.
        assertEquals(
            default.id,
            selectionAfterRemoving(nemotron, SpeechPrefs(selectedEngineId = nemotron, recommendedEngineId = nemotron)),
        )
    }
}
