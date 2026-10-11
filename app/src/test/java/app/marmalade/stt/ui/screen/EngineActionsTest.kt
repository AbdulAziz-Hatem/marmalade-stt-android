package app.marmalade.stt.ui.screen

import app.marmalade.stt.install.CorruptProblem
import app.marmalade.stt.install.InstallFailure
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.ui.screen.EngineAction.CANCEL
import app.marmalade.stt.ui.screen.EngineAction.CHECK_FILES
import app.marmalade.stt.ui.screen.EngineAction.DOWNLOAD
import app.marmalade.stt.ui.screen.EngineAction.DOWNLOAD_AGAIN
import app.marmalade.stt.ui.screen.EngineAction.REMOVE
import app.marmalade.stt.ui.screen.EngineAction.REMOVE_DOWNLOAD
import app.marmalade.stt.ui.screen.EngineAction.RESUME
import app.marmalade.stt.ui.screen.EngineAction.RETRY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineActionsTest {

    private val downloading = InstallState.Downloading(10L, 100L, "encoder.onnx")
    private val paused = InstallState.Paused(10L, 100L)
    private val failedWithBytes = InstallState.Failed("net", InstallFailure.NETWORK, bytesOnDisk = 10L)
    private val failedEmpty = InstallState.Failed("space", InstallFailure.NO_SPACE, bytesOnDisk = 0L)
    private val corrupt = InstallState.Corrupt(CorruptProblem.MISSING, "x")

    @Test
    fun `setup guide actions follow the install lifecycle`() {
        assertEquals(listOf(DOWNLOAD), engineActions(InstallState.NotInstalled, onDetail = false))
        assertEquals(listOf(CANCEL), engineActions(downloading, onDetail = false))
        assertEquals(listOf(REMOVE_DOWNLOAD, RESUME), engineActions(paused, onDetail = false))
        assertEquals(listOf(DOWNLOAD_AGAIN), engineActions(corrupt, onDetail = false))
        // Installed: the setup guide just reads "Ready".
        assertEquals(emptyList<EngineAction>(), engineActions(InstallState.Installed, onDetail = false))
    }

    @Test
    fun `remove download is offered only when a failure kept bytes`() {
        assertEquals(listOf(REMOVE_DOWNLOAD, RETRY), engineActions(failedWithBytes, onDetail = false))
        assertEquals(listOf(RETRY), engineActions(failedEmpty, onDetail = false))
    }

    @Test
    fun `the info sheet adds file management for a model on disk`() {
        assertEquals(listOf(REMOVE, CHECK_FILES), engineActions(InstallState.Installed, onDetail = true))
        assertEquals(listOf(REMOVE, DOWNLOAD_AGAIN), engineActions(corrupt, onDetail = true))
        // Everything else matches the setup guide.
        for (state in listOf(InstallState.NotInstalled, downloading, paused, failedWithBytes, failedEmpty)) {
            assertEquals(engineActions(state, onDetail = false), engineActions(state, onDetail = true))
        }
    }

    @Test
    fun `at most one primary action, and it comes last`() {
        val states = listOf(InstallState.NotInstalled, downloading, paused, failedWithBytes, failedEmpty, corrupt, InstallState.Installed)
        for (onDetail in listOf(false, true)) {
            for (state in states) {
                val actions = engineActions(state, onDetail)
                val primaries = actions.filter { it.primary }
                assertTrue("$state: ${actions}", primaries.size <= 1)
                if (primaries.isNotEmpty()) assertEquals("$state", primaries.single(), actions.last())
            }
        }
    }

    @Test
    fun `source label is the GitHub repo`() {
        assertEquals(
            "github.com/maxwhipw/marmalade-stt-engines",
            modelSourceLabel(SttEngineCatalog.byId("whisper-turbo")!!.files.first().url),
        )
        assertEquals("example.com/models", modelSourceLabel("https://example.com/models/a.onnx"))
    }
}
