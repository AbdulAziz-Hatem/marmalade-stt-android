package app.marmalade.stt.install

import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [ModelDownloads]: every start asks for the foreground service, and [ModelDownloads.running] tracks the jobs (#74). */
class ModelDownloadsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun aStartKeepsTheAppAliveAndRunningEmptiesWhenItEnds() = runBlocking {
        val fetcher = FakeFetcher()
        val bytes = Random(4).nextBytes(200_000)
        val url = "https://test/e/enc.onnx"
        fetcher.files[url] = bytes
        val gate = fetcher.blockAfter(url, 50_000)
        val installer = TestInstaller(ModelFilesDir { temp.root }, fetcher, EngineRelease { }).also {
            it.engines[ENGINE] = SttEngineDescriptor(
                id = ENGINE, displayName = "Test", description = 0, family = EngineFamily.WHISPER,
                languages = listOf("en"), licenseId = "MIT", licenseKey = "test",
                files = listOf(ModelFile(FileRole.ENCODER, "enc.onnx", url, sha256(bytes), bytes.size.toLong())),
            )
        }
        var keepAlives = 0
        val downloads = ModelDownloads(installer) { keepAlives++ }

        downloads.start(ENGINE)
        assertTrue("download reached the stall", gate.await(10, TimeUnit.SECONDS))
        assertEquals(setOf(ENGINE), downloads.running.value)
        downloads.start(ENGINE) // already running: no second job, no second service start
        assertEquals(1, keepAlives)

        downloads.cancel(ENGINE)
        withTimeout(10_000) { downloads.running.first { it.isEmpty() } }
        assertTrue(installer.state(ENGINE).value is InstallState.Paused)

        downloads.start(ENGINE)
        withTimeout(10_000) { downloads.running.first { it.isEmpty() } }
        assertEquals(2, keepAlives)
        assertEquals(InstallState.Installed, installer.state(ENGINE).value)
    }

    private companion object {
        const val ENGINE = "test-engine"
    }
}
