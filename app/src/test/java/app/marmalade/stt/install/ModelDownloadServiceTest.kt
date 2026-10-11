package app.marmalade.stt.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [goForegroundOrPause]: what [ModelDownloadService] does when Android refuses the foreground (#74 review). */
class ModelDownloadServiceTest {

    private val calls = mutableListOf<String>()

    @Test
    fun goingForegroundLeavesTheDownloadsRunning() {
        val foreground = goForegroundOrPause(goForeground = { calls += "foreground" }, pause = { calls += "pause" }, stop = { calls += "stop" })

        assertTrue(foreground)
        assertEquals(listOf("foreground"), calls)
    }

    @Test
    fun aRefusedForegroundPausesTheDownloadsThenStops() {
        // ForegroundServiceStartNotAllowedException is an IllegalStateException.
        val foreground = goForegroundOrPause(
            goForeground = { throw IllegalStateException("startForegroundService() not allowed") },
            pause = { calls += "pause" },
            stop = { calls += "stop" },
        )

        assertFalse(foreground)
        assertEquals(listOf("pause", "stop"), calls)
    }
}
