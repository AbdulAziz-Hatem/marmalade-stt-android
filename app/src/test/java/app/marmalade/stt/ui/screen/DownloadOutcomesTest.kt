package app.marmalade.stt.ui.screen

import app.marmalade.stt.install.CorruptProblem
import app.marmalade.stt.install.InstallFailure
import app.marmalade.stt.install.InstallState
import org.junit.Assert.assertEquals
import org.junit.Test

/** #68: which install-state changes the Engines tab and the setup guide announce to TalkBack. */
class DownloadOutcomesTest {

    private val downloading = InstallState.Downloading(500L, 1_000L, "model.onnx")
    private val failed = InstallState.Failed("network", InstallFailure.NETWORK, 500L)

    @Test
    fun `a finished download is ready`() {
        assertEquals(
            listOf("a" to DownloadOutcome.READY),
            downloadOutcomes(mapOf("a" to downloading), mapOf("a" to InstallState.Installed)),
        )
    }

    @Test
    fun `a cancelled or stopped download is paused`() {
        assertEquals(
            listOf("a" to DownloadOutcome.PAUSED),
            downloadOutcomes(mapOf("a" to downloading), mapOf("a" to InstallState.Paused(500L, 1_000L))),
        )
    }

    @Test
    fun `a failure is announced whatever came before it`() {
        for (before in listOf(downloading, InstallState.Paused(500L, 1_000L), InstallState.NotInstalled)) {
            assertEquals(
                listOf("a" to DownloadOutcome.FAILED),
                downloadOutcomes(mapOf("a" to before), mapOf("a" to failed)),
            )
        }
        // A retry that fails again at once, with other numbers.
        assertEquals(
            listOf("a" to DownloadOutcome.FAILED),
            downloadOutcomes(mapOf("a" to failed), mapOf("a" to failed.copy(bytesOnDisk = 0L))),
        )
    }

    @Test
    fun `nothing that isn't the end of a download is announced`() {
        val quiet = listOf(
            // Another engine's change re-emits the map: the failure was already said.
            failed to failed,
            // The disk probe on opening the tab, Check files (its snackbar says
            // "damaged"), progress, a resume.
            InstallState.NotInstalled to InstallState.Installed,
            InstallState.Corrupt(CorruptProblem.MISSING, "x") to InstallState.Installed,
            InstallState.Installed to InstallState.Corrupt(CorruptProblem.DAMAGED, "x"),
            downloading to downloading.copy(bytesDone = 600L),
            InstallState.Paused(500L, 1_000L) to downloading,
            InstallState.Paused(500L, 1_000L) to InstallState.Paused(400L, 1_000L),
            InstallState.Installed to InstallState.NotInstalled,
        )
        for ((before, after) in quiet) {
            assertEquals(emptyList<Pair<String, DownloadOutcome>>(), downloadOutcomes(mapOf("a" to before), mapOf("a" to after)))
        }
    }

    @Test
    fun `the first look says nothing, and several engines keep the current order`() {
        assertEquals(emptyList<Pair<String, DownloadOutcome>>(), downloadOutcomes(emptyMap(), mapOf("a" to failed)))
        assertEquals(
            listOf("b" to DownloadOutcome.FAILED, "a" to DownloadOutcome.READY),
            downloadOutcomes(
                previous = mapOf("a" to downloading, "b" to downloading, "c" to downloading),
                current = linkedMapOf("b" to failed, "a" to InstallState.Installed, "c" to downloading),
            ),
        )
    }
}
