package app.marmalade.stt.install

import app.marmalade.stt.speech.modelFilePaths
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM tests for [ModelInstaller] against an in-memory [FakeFetcher] (no
 * sockets). Synthetic engines are served through [TestInstaller]'s catalog
 * seam, so every public entry point runs exactly as in production.
 */
class ModelInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var fetcher: FakeFetcher
    private val released = mutableListOf<String>()
    private var onRelease: (String) -> Unit = {}
    private lateinit var installer: TestInstaller

    private val encoder = Random(1).nextBytes(300_000)
    private val decoder = Random(2).nextBytes(500_000)
    private val tokens = Random(3).nextBytes(20_000)
    private lateinit var engine: SttEngineDescriptor

    private val models get() = File(filesDir, "models")
    private val finalDir get() = File(models, ENGINE)
    private val staging get() = File(models, "$ENGINE.partial")

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        fetcher = FakeFetcher()
        engine = engine(encoder, decoder, tokens)
        installer = newInstaller()
    }

    private fun newInstaller() = TestInstaller(
        ModelFilesDir { filesDir },
        fetcher,
        EngineRelease { id -> onRelease(id); released += id },
    ).also { it.engines[ENGINE] = engine }

    /** A three-file WHISPER-shaped engine; registers the bytes with [fetcher]. */
    private fun engine(enc: ByteArray, dec: ByteArray, tok: ByteArray): SttEngineDescriptor {
        val files = listOf(
            Triple(FileRole.ENCODER, "enc.onnx", enc),
            Triple(FileRole.DECODER, "dec.onnx", dec),
            Triple(FileRole.TOKENS, "tokens.txt", tok),
        ).map { (role, name, bytes) ->
            val url = "https://test/$ENGINE/${sha256(bytes)}/$name"
            fetcher.files[url] = bytes
            ModelFile(role, name, url, sha256(bytes), bytes.size.toLong())
        }
        return SttEngineDescriptor(
            id = ENGINE, displayName = "Test", description = 0, family = EngineFamily.WHISPER,
            languages = listOf("en"), licenseId = "MIT", licenseKey = "test", files = files,
        )
    }

    private fun url(role: FileRole) = engine.file(role).url

    private fun assertInstalledCorrectly() {
        assertEquals(InstallState.Installed, installer.state(ENGINE).value)
        assertFalse("staging is gone", staging.exists())
        val model = installer.installedModel(ENGINE)!!
        assertArrayEquals(encoder, model.path(FileRole.ENCODER).readBytes())
        assertArrayEquals(decoder, model.path(FileRole.DECODER).readBytes())
        assertArrayEquals(tokens, model.path(FileRole.TOKENS).readBytes())
    }

    // -- models that left the catalog (#44, #45) ---------------------------

    @Test
    fun removeUnknownModelsDeletesWhatLeftTheCatalogAndKeepsCatalogEngines() = runTest {
        assertTrue(installer.install(ENGINE).isSuccess)
        // Engines cut in #45, installed or half-downloaded, and the Smart Turn download from before #44.
        val gone = listOf("distil-small-en", "parakeet-tdt-0-6b-v3", "smart-turn-v3").map { id ->
            File(models, id).apply { mkdirs(); File(this, "encoder.int8.onnx").writeBytes(Random(4).nextBytes(1_000)) }
        }
        val partial = File(models, "parakeet-tdt-0-6b-v2.partial").apply { mkdirs() }
        File(partial, "encoder.int8.onnx.part").writeBytes(Random(5).nextBytes(500))
        // A catalog engine's own partial download is left alone (invariant 2).
        val keptStaging = File(models, "$OTHER.partial").apply { mkdirs() }
        installer.engines[OTHER] = engine.copy(id = OTHER)

        assertEquals(
            setOf("distil-small-en", "parakeet-tdt-0-6b-v3", "smart-turn-v3", "parakeet-tdt-0-6b-v2"),
            installer.removeUnknownModels().toSet(),
        )

        gone.forEach { assertFalse("$it is gone", it.exists()) }
        assertFalse(partial.exists())
        assertTrue(keptStaging.exists())
        assertInstalledCorrectly()
        // Nothing left to remove.
        assertEquals(emptyList<String>(), installer.removeUnknownModels())
    }

    @Test
    fun removeUnknownModelsCopesWithNoModelsDirectory() = runTest {
        assertFalse(models.exists())
        assertEquals(emptyList<String>(), installer.removeUnknownModels())
    }

    // -- import / export ---------------------------------------------------

    @Test
    fun importArchiveInstallsEveryFileAndRenamesStagingIntoPlace() = runTest {
        assertEquals(InstallState.NotInstalled, installer.state(ENGINE).value)

        val zipBytes = createTestZip(
            "enc.onnx" to encoder,
            "dec.onnx" to decoder,
            "tokens.txt" to tokens,
        )

        val result = installer.importArchive(ENGINE, ByteArrayInputStream(zipBytes))
        assertTrue(result.isSuccess)

        assertInstalledCorrectly()
        assertEquals(InstallState.Installed, installer.state(ENGINE).value)
        assertEquals(finalDir, installer.installedModel(ENGINE)!!.dir)
        val record = File(finalDir, ".install.json").readText()
        engine.files.forEach { assertTrue(record.contains("\"${it.name}\": \"${it.sha256}\"")) }
        // The network was never touched
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun importArchiveWithChecksumMismatchFailsAndCleansStaging() = runTest {
        val tamperedEncoder = encoder.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val zipBytes = createTestZip(
            "enc.onnx" to tamperedEncoder,
            "dec.onnx" to decoder,
            "tokens.txt" to tokens,
        )

        val result = installer.importArchive(ENGINE, ByteArrayInputStream(zipBytes))
        assertFalse(result.isSuccess)
        assertEquals(InstallFailure.CHECKSUM, (result.exceptionOrNull() as InstallException).failure)
        assertFalse(staging.exists())
        assertFalse(finalDir.exists())
    }

    @Test
    fun exportArchiveWritesValidZipOfInstalledFiles() = runTest {
        assertTrue(installer.install(ENGINE).isSuccess)

        val out = java.io.ByteArrayOutputStream()
        val result = installer.exportArchive(ENGINE, out)
        assertTrue(result.isSuccess)

        val exportedBytes = out.toByteArray()
        assertTrue(exportedBytes.isNotEmpty())

        val readEntries = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(exportedBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                readEntries[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }

        assertArrayEquals(encoder, readEntries["enc.onnx"])
        assertArrayEquals(decoder, readEntries["dec.onnx"])
        assertArrayEquals(tokens, readEntries["tokens.txt"])
    }

    private fun createTestZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    // -- happy path --------------------------------------------------------

    @Test
    fun installsEveryFileAndRenamesStagingIntoPlace() = runTest {
        assertEquals(InstallState.NotInstalled, installer.state(ENGINE).value)

        assertTrue(installer.install(ENGINE).isSuccess)

        assertInstalledCorrectly()
        assertEquals(finalDir, installer.installedModel(ENGINE)!!.dir)
        assertEquals(File(finalDir, "dec.onnx"), installer.installedModel(ENGINE)!!.path(FileRole.DECODER))
        val record = File(finalDir, ".install.json").readText()
        engine.files.forEach { assertTrue(record.contains("\"${it.name}\": \"${it.sha256}\"")) }
        assertEquals(engine.files.map { it.url to 0L }, fetcher.requests)
        // Already installed: a second install is a no-op without the network.
        assertTrue(installer.install(ENGINE).isSuccess)
        assertEquals(3, fetcher.requests.size)
    }

    // -- resume ------------------------------------------------------------

    @Test
    fun aDroppedConnectionKeepsThePartialAndTheRetryResumesWithRange() = runTest {
        fetcher.failAfter[url(FileRole.DECODER)] = 100_000

        val result = installer.install(ENGINE)

        // The network stayed down: the automatic retries ran out, every one a resume.
        assertEquals(ModelInstaller.NETWORK_RETRY_DELAYS_MS.toList(), installer.retryDelays)
        assertEquals(
            listOf(url(FileRole.DECODER) to 0L) + List(4) { url(FileRole.DECODER) to 100_000L },
            fetcher.requests.filter { it.first == url(FileRole.DECODER) },
        )

        assertEquals(InstallFailure.NETWORK, (result.exceptionOrNull() as InstallException).failure)
        val failed = installer.state(ENGINE).value as InstallState.Failed
        assertEquals(InstallFailure.NETWORK, failed.failure)
        assertEquals(encoder.size + 100_000L, failed.bytesOnDisk)
        assertTrue(File(staging, "enc.onnx").isFile)
        assertEquals(100_000L, File(staging, "dec.onnx.part").length())
        assertFalse(finalDir.exists())

        fetcher.offline = false
        fetcher.requests.clear()
        assertTrue(installer.install(ENGINE).isSuccess)

        // The verified encoder isn't fetched again; the decoder resumes.
        assertEquals(listOf(url(FileRole.DECODER) to 100_000L, url(FileRole.TOKENS) to 0L), fetcher.requests)
        assertInstalledCorrectly()
    }

    @Test
    fun aDropIsRetriedAutomaticallyAndResumes() = runTest {
        fetcher.failAfter[url(FileRole.DECODER)] = 100_000
        fetcher.offlineAfterDrop = false

        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(listOf(ModelInstaller.NETWORK_RETRY_DELAYS_MS.first()), installer.retryDelays)
        assertEquals(
            listOf(url(FileRole.DECODER) to 0L, url(FileRole.DECODER) to 100_000L),
            fetcher.requests.filter { it.first == url(FileRole.DECODER) },
        )
        assertInstalledCorrectly()
    }

    @Test
    fun dropsWithProgressInBetweenNeverRunOutOfRetries() = runTest {
        // A flaky link: every connection drops after 40 kB, far more drops than retries.
        fetcher.dropEvery = 40_000

        assertTrue(installer.install(ENGINE).isSuccess)

        assertTrue(installer.retryDelays.size > ModelInstaller.NETWORK_RETRY_DELAYS_MS.size)
        assertTrue("each drop made progress", installer.retryDelays.all { it == ModelInstaller.NETWORK_RETRY_DELAYS_MS.first() })
        assertInstalledCorrectly()
    }

    @Test
    fun cancellingDuringARetryPauseLeavesItPaused() = runTest {
        fetcher.failAfter[url(FileRole.DECODER)] = 100_000
        val pausing = CountDownLatch(1)
        installer.onRetryDelay = {
            pausing.countDown()
            awaitCancellation()
        }
        val job = CoroutineScope(Dispatchers.Default).launch { installer.install(ENGINE) }
        assertTrue("reached the retry pause", pausing.await(10, TimeUnit.SECONDS))

        job.cancelAndJoin()

        assertEquals(InstallState.Paused(encoder.size + 100_000L, engine.sizeBytes), installer.state(ENGINE).value)
    }

    @Test
    fun aPartialDownloadSurvivesAProcessRestartAsPaused() = runTest {
        fetcher.failAfter[url(FileRole.DECODER)] = 123_456
        installer.install(ENGINE)

        // A new process: a fresh installer over the same files dir.
        installer = newInstaller()
        fetcher.offline = false

        val total = engine.sizeBytes
        assertEquals(InstallState.Paused(encoder.size + 123_456L, total), installer.state(ENGINE).value)
        assertNull(installer.installedModel(ENGINE))
        fetcher.requests.clear()
        assertTrue(installer.install(ENGINE).isSuccess)
        assertEquals(url(FileRole.DECODER) to 123_456L, fetcher.requests.first())
        assertInstalledCorrectly()
    }

    @Test
    fun aServerIgnoringRangeRestartsThatFileFromZero() = runTest {
        fetcher.failAfter[url(FileRole.ENCODER)] = 50_000
        installer.install(ENGINE)
        fetcher.offline = false
        fetcher.ignoreRange = true
        fetcher.requests.clear()

        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(url(FileRole.ENCODER) to 50_000L, fetcher.requests.first())
        assertInstalledCorrectly()
    }

    @Test
    fun aFullyDownloadedPartIsHashedWithoutTheNetwork() = runTest {
        // Process died after the last byte but before the rename.
        staging.mkdirs()
        File(staging, "enc.onnx.part").writeBytes(encoder)

        assertTrue(installer.install(ENGINE).isSuccess)

        assertFalse(fetcher.requests.any { it.first == url(FileRole.ENCODER) })
        assertInstalledCorrectly()
    }

    // -- checksum ----------------------------------------------------------

    @Test
    fun aChecksumMismatchDeletesThatPartAndFails() = runTest {
        fetcher.corrupt += url(FileRole.DECODER)

        val result = installer.install(ENGINE)

        assertEquals(InstallFailure.CHECKSUM, (result.exceptionOrNull() as InstallException).failure)
        val failed = installer.state(ENGINE).value as InstallState.Failed
        assertEquals(InstallFailure.CHECKSUM, failed.failure)
        assertEquals(encoder.size.toLong(), failed.bytesOnDisk)
        assertFalse(File(staging, "dec.onnx.part").exists())
        assertFalse(File(staging, "dec.onnx").exists())
        assertFalse(finalDir.exists())
        assertNull(installer.installedModel(ENGINE))
    }

    // -- cancel ------------------------------------------------------------

    @Test
    fun cancellingMidDownloadPausesAndKeepsTheBytes() = runTest {
        val gate = fetcher.blockAfter(url(FileRole.DECODER), 70_000)
        val job = CoroutineScope(Dispatchers.Default).launch { installer.install(ENGINE) }
        assertTrue("download reached the stall", gate.await(10, TimeUnit.SECONDS))

        // The stream stalls in read(); cancelling must end it without waiting
        // for a timeout (the fake stalls up to 30 s unless the stream is closed).
        val started = System.nanoTime()
        job.cancelAndJoin()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("cancel took $tookMs ms", tookMs < 5_000)

        assertEquals(
            InstallState.Paused(encoder.size + 70_000L, engine.sizeBytes),
            installer.state(ENGINE).value,
        )
        assertEquals(70_000L, File(staging, "dec.onnx.part").length())
        assertFalse(finalDir.exists())

        fetcher.requests.clear()
        assertTrue(installer.install(ENGINE).isSuccess)
        assertEquals(url(FileRole.DECODER) to 70_000L, fetcher.requests.first())
        assertInstalledCorrectly()
    }

    @Test
    fun aProbeDuringADownloadLeavesTheProgressAlone() = runTest {
        val gate = fetcher.blockAfter(url(FileRole.DECODER), 1_000)
        val install = CoroutineScope(Dispatchers.Default).async { installer.install(ENGINE) }
        assertTrue(gate.await(10, TimeUnit.SECONDS))

        val probed = installer.verify(ENGINE)

        assertTrue("probe reports the live download, got $probed", probed.isInFlight)
        assertTrue(installer.state(ENGINE).value.isInFlight)
        install.cancelAndJoin()
    }

    // -- update / uninstall ------------------------------------------------

    @Test
    fun aFailedUpdateLeavesTheOldInstallUntouchedAndReleaseComesBeforeTheSwap() = runTest {
        assertTrue(installer.install(ENGINE).isSuccess)
        val oldRecord = File(finalDir, ".install.json").readText()

        // The catalog ships a new decoder: the old install no longer matches.
        val newDecoder = Random(9).nextBytes(400_000)
        engine = engine(encoder, newDecoder, tokens)
        installer = newInstaller()
        assertTrue(installer.state(ENGINE).value is InstallState.Corrupt)

        fetcher.failAfter[url(FileRole.DECODER)] = 10_000
        assertTrue(installer.install(ENGINE).isFailure)
        fetcher.offline = false

        assertArrayEquals(decoder, File(finalDir, "dec.onnx").readBytes())
        assertEquals(oldRecord, File(finalDir, ".install.json").readText())
        assertTrue("nothing was replaced, so nothing was released", released.isEmpty())

        var decoderAtRelease: ByteArray? = null
        onRelease = { decoderAtRelease = File(finalDir, "dec.onnx").readBytes() }
        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(listOf(ENGINE), released)
        assertArrayEquals("released while the old files were still in place", decoder, decoderAtRelease)
        assertArrayEquals(newDecoder, installer.installedModel(ENGINE)!!.path(FileRole.DECODER).readBytes())
    }

    @Test
    fun uninstallReleasesThenDeletesEverything() = runTest {
        assertTrue(installer.install(ENGINE).isSuccess)
        staging.mkdirs()
        File(staging, "enc.onnx.part").writeBytes(ByteArray(10))

        assertTrue(installer.uninstall(ENGINE).isSuccess)

        assertEquals(listOf(ENGINE), released)
        assertFalse(finalDir.exists())
        assertFalse(staging.exists())
        assertEquals(InstallState.NotInstalled, installer.state(ENGINE).value)
    }

    @Test
    fun discardDownloadDeletesOnlyThePartial() = runTest {
        fetcher.failAfter[url(FileRole.ENCODER)] = 1_000
        installer.install(ENGINE)

        assertTrue(installer.discardDownload(ENGINE).isSuccess)

        assertFalse(staging.exists())
        assertEquals(InstallState.NotInstalled, installer.state(ENGINE).value)
    }

    @Test
    fun unknownEnginesAreRejected() = runTest {
        assertTrue(installer.install("../../etc").isFailure)
        assertTrue(installer.uninstall("../../etc").isFailure)
        assertEquals(InstallState.NotInstalled, installer.state("nope").value)
        assertNull(installer.installedModel("nope"))
        assertTrue(fetcher.requests.isEmpty())
    }

    // -- probe / verify ----------------------------------------------------

    @Test
    fun aMissingFileIsCorrupt() = runTest {
        installer.install(ENGINE)
        File(finalDir, "tokens.txt").delete()

        assertTrue(installer.verify(ENGINE) is InstallState.Corrupt)
        assertTrue(newInstaller().state(ENGINE).value is InstallState.Corrupt)
        assertNull(installer.installedModel(ENGINE))
    }

    @Test
    fun aFileOfTheWrongSizeIsCorrupt() = runTest {
        installer.install(ENGINE)
        File(finalDir, "enc.onnx").appendBytes(byteArrayOf(0))

        assertTrue(installer.verify(ENGINE) is InstallState.Corrupt)
        assertNull(installer.installedModel(ENGINE))
    }

    @Test
    fun anInstallRecordThatDoesNotMatchTheCatalogIsCorrupt() = runTest {
        installer.install(ENGINE)
        val record = File(finalDir, ".install.json")
        record.writeText(record.readText().replace(engine.file(FileRole.TOKENS).sha256, "0".repeat(64)))

        assertTrue(installer.verify(ENGINE) is InstallState.Corrupt)

        record.delete()
        assertTrue(installer.verify(ENGINE) is InstallState.Corrupt)
    }

    @Test
    fun onlyADeepVerifyCatchesASameSizeFlippedByte() = runTest {
        installer.install(ENGINE)
        val file = File(finalDir, "dec.onnx")
        val bytes = file.readBytes().also { it[250_000] = (it[250_000] + 1).toByte() }
        file.writeBytes(bytes)

        assertEquals(InstallState.Installed, installer.verify(ENGINE))
        val deep = installer.verify(ENGINE, deep = true)
        assertTrue(deep is InstallState.Corrupt)
        assertEquals(deep, installer.state(ENGINE).value)
    }

    // -- previous files: a format change keeps old installs (#78) ------------

    private val encoderOrt = Random(11).nextBytes(310_000)
    private val decoderOrt = Random(12).nextBytes(490_000)

    /**
     * [engine] (enc.onnx, dec.onnx, tokens.txt) after the catalog switched it
     * to .ort graphs under a new tag, the same tokens.txt kept.
     */
    private fun switchedToOrt(): SttEngineDescriptor {
        val ort = listOf(
            Triple(FileRole.ENCODER, "enc.ort", encoderOrt),
            Triple(FileRole.DECODER, "dec.ort", decoderOrt),
        ).map { (role, name, bytes) ->
            val url = "https://test/v9/$ENGINE/$name"
            fetcher.files[url] = bytes
            ModelFile(role, name, url, sha256(bytes), bytes.size.toLong())
        } + engine.file(FileRole.TOKENS)
        return with(SttEngineCatalog) { engine.replacingFiles(ort) }
    }

    /** Install the .onnx set, then start a new process whose catalog has the .ort set. */
    private suspend fun installOldThenSwitch(): SttEngineDescriptor {
        assertTrue(installer.install(ENGINE).isSuccess)
        val onnx = engine
        engine = switchedToOrt()
        installer = newInstaller()
        fetcher.requests.clear()
        return onnx
    }

    @Test
    fun anInstallOfThePreviousFilesStaysInstalledAndLoadsThem() = runTest {
        val onnx = installOldThenSwitch()

        assertEquals(InstallState.Installed, installer.state(ENGINE).value)
        val model = installer.installedModel(ENGINE)!!
        assertTrue(model.previousFormat)
        assertEquals(onnx.files, model.descriptor.files)
        assertEquals(onnx.sizeBytes, model.descriptor.sizeBytes)
        assertEquals(File(finalDir, "enc.onnx"), model.path(FileRole.ENCODER))
        assertArrayEquals(decoder, model.path(FileRole.DECODER).readBytes())
        assertEquals(
            mapOf(
                FileRole.ENCODER to File(finalDir, "enc.onnx"),
                FileRole.DECODER to File(finalDir, "dec.onnx"),
                FileRole.TOKENS to File(finalDir, "tokens.txt"),
            ),
            modelFilePaths(model.descriptor, model.dir),
        )
        // Installed, so install is a no-op: nothing is downloaded behind the user's back.
        assertTrue(installer.install(ENGINE).isSuccess)
        assertTrue(fetcher.requests.isEmpty())
        assertEquals(InstallState.Installed, installer.verify(ENGINE))
        assertEquals(InstallState.Installed, installer.verify(ENGINE, deep = true))
        assertTrue(installer.installedModel(ENGINE)!!.previousFormat)
    }

    @Test
    fun aDeepVerifyHashesThePreviousFiles() = runTest {
        installOldThenSwitch()
        val file = File(finalDir, "enc.onnx")
        file.writeBytes(file.readBytes().also { it[1_000] = (it[1_000] + 1).toByte() })

        assertEquals(InstallState.Installed, installer.verify(ENGINE))
        assertEquals(InstallState.Corrupt(CorruptProblem.DAMAGED, "enc.onnx"), installer.verify(ENGINE, deep = true))
    }

    @Test
    fun theCurrentFilesInstallAndLoadAsUsual() = runTest {
        engine = switchedToOrt()
        installer = newInstaller()

        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(engine.files.map { it.url to 0L }, fetcher.requests)
        assertEquals(InstallState.Installed, installer.state(ENGINE).value)
        val model = installer.installedModel(ENGINE)!!
        assertFalse(model.previousFormat)
        assertEquals(engine, model.descriptor)
        assertArrayEquals(encoderOrt, model.path(FileRole.ENCODER).readBytes())
        assertEquals(setOf("enc.ort", "dec.ort", "tokens.txt", ".install.json"), finalDir.list()!!.toSet())
        assertEquals(InstallState.Installed, installer.verify(ENGINE, deep = true))
    }

    @Test
    fun aRecordMatchingNeitherSetIsOutdated() = runTest {
        installOldThenSwitch()
        val record = File(finalDir, ".install.json")
        val onnxRecord = record.readText()
        val outdated = InstallState.Corrupt(CorruptProblem.OUTDATED)

        // Half one set, half the other.
        record.writeText(onnxRecord.replace("\"enc.onnx\": \"${sha256(encoder)}\"", "\"enc.ort\": \"${sha256(encoderOrt)}\""))
        assertEquals(outdated, installer.verify(ENGINE))
        assertNull(installer.installedModel(ENGINE))
        // The previous set less a file.
        record.writeText(onnxRecord.replace(Regex(""",\s*"tokens\.txt": "[0-9a-f]+""""), ""))
        assertEquals(outdated, installer.verify(ENGINE))
        // Garbled.
        record.writeText("{ \"files\": { not json")
        assertEquals(outdated, installer.verify(ENGINE))
        assertNull(installer.installedModel(ENGINE))
        // The right record over a damaged file set is judged against that set.
        record.writeText(onnxRecord)
        File(finalDir, "dec.onnx").delete()
        assertEquals(InstallState.Corrupt(CorruptProblem.MISSING, "dec.onnx"), installer.verify(ENGINE))
        assertNull(installer.installedModel(ENGINE))
    }

    @Test
    fun anEngineWithoutPreviousFilesNeverMatchesAnEmptyRecord() = runTest {
        installer.install(ENGINE)
        File(finalDir, ".install.json").writeText("{\n  \"files\": {\n  }\n}\n")

        assertEquals(InstallState.Corrupt(CorruptProblem.OUTDATED), installer.verify(ENGINE))
    }

    @Test
    fun anInstallDeletesAPartialDownloadOfThePreviousFiles() = runTest {
        // Half way through the .onnx download when the app updated.
        fetcher.failAfter[url(FileRole.DECODER)] = 100_000
        assertTrue(installer.install(ENGINE).isFailure)
        fetcher.offline = false
        assertTrue(File(staging, "enc.onnx").isFile)
        assertTrue(File(staging, "dec.onnx.part").isFile)
        File(staging, "tokens.txt.part").writeBytes(tokens.copyOf(5_000)) // shared with the new set: kept
        engine = switchedToOrt()
        installer = newInstaller()
        fetcher.requests.clear()

        // Only the current set's bytes count as a partial download.
        assertEquals(InstallState.Paused(5_000L, engine.sizeBytes), installer.state(ENGINE).value)
        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(
            listOf(url(FileRole.ENCODER) to 0L, url(FileRole.DECODER) to 0L, url(FileRole.TOKENS) to 5_000L),
            fetcher.requests,
        )
        assertFalse(staging.exists())
        assertEquals(setOf("enc.ort", "dec.ort", "tokens.txt", ".install.json"), finalDir.list()!!.toSet())
        assertFalse(installer.installedModel(ENGINE)!!.previousFormat)
    }

    @Test
    fun uninstallRemovesThePreviousFilesAndAReinstallGetsTheCurrentOnes() = runTest {
        installOldThenSwitch()

        assertTrue(installer.uninstall(ENGINE).isSuccess)

        assertEquals(listOf(ENGINE), released)
        assertFalse(finalDir.exists())
        assertEquals(InstallState.NotInstalled, installer.state(ENGINE).value)
        assertNull(installer.installedModel(ENGINE))

        assertTrue(installer.install(ENGINE).isSuccess)
        assertEquals(engine.files.map { it.url to 0L }, fetcher.requests)
        assertFalse(installer.installedModel(ENGINE)!!.previousFormat)
        assertArrayEquals(decoderOrt, installer.installedModel(ENGINE)!!.path(FileRole.DECODER).readBytes())
    }

    // -- space / availability ----------------------------------------------

    @Test
    fun notEnoughFreeSpaceFailsBeforeFetchingAByte() = runTest {
        installer.usableSpace = engine.sizeBytes + ModelInstaller.FREE_SPACE_MARGIN_BYTES - 1

        val result = installer.install(ENGINE)

        assertEquals(InstallFailure.NO_SPACE, (result.exceptionOrNull() as InstallException).failure)
        assertEquals(InstallFailure.NO_SPACE, (installer.state(ENGINE).value as InstallState.Failed).failure)
        assertTrue(fetcher.requests.isEmpty())

        installer.usableSpace = engine.sizeBytes + ModelInstaller.FREE_SPACE_MARGIN_BYTES
        assertTrue(installer.install(ENGINE).isSuccess)
    }

    @Test
    fun aResumeOnlyNeedsRoomForTheRest() = runTest {
        fetcher.failAfter[url(FileRole.ENCODER)] = 100_000
        installer.install(ENGINE)
        fetcher.offline = false
        installer.usableSpace = engine.sizeBytes - 100_000 + ModelInstaller.FREE_SPACE_MARGIN_BYTES

        assertTrue(installer.install(ENGINE).isSuccess)
    }

    @Test
    fun aFullDiskMidDownloadFailsAsNoSpaceAndDeletesThePartial() = runTest {
        fetcher.failAfter[url(FileRole.DECODER)] = 1_000
        fetcher.failure = IOException("write failed: ENOSPC (No space left on device)")

        installer.install(ENGINE)

        val failed = installer.state(ENGINE).value as InstallState.Failed
        assertEquals(InstallFailure.NO_SPACE, failed.failure)
        assertTrue("a full disk isn't retried", installer.retryDelays.isEmpty())
        assertEquals(0L, failed.bytesOnDisk)
        assertFalse(failed.reason.contains("ENOSPC"))
        assertFalse(staging.exists())
    }

    @Test
    fun aMissingFileOnTheServerFailsAsNotAvailable() = runTest {
        fetcher.files.remove(url(FileRole.TOKENS))

        val result = installer.install(ENGINE)

        assertEquals(InstallFailure.NOT_AVAILABLE, (result.exceptionOrNull() as InstallException).failure)
        val failed = installer.state(ENGINE).value as InstallState.Failed
        assertEquals(InstallFailure.NOT_AVAILABLE, failed.failure)
        assertEquals((encoder.size + decoder.size).toLong(), failed.bytesOnDisk)
    }

    private companion object {
        const val ENGINE = "test-engine"
        const val OTHER = "other-engine"
    }
}

/** [ModelInstaller] with a synthetic catalog and a fake free-space figure. */
internal class TestInstaller(
    filesDir: ModelFilesDir,
    fetcher: HttpFetcher,
    release: EngineRelease,
) : ModelInstaller(filesDir, fetcher, release) {
    val engines = mutableMapOf<String, SttEngineDescriptor>()
    var usableSpace: Long = Long.MAX_VALUE

    /** The automatic retries' pauses, recorded instead of waited out. */
    val retryDelays = mutableListOf<Long>()
    var onRetryDelay: suspend () -> Unit = {}

    override fun descriptor(engineId: String): SttEngineDescriptor? = engines[engineId]
    override fun usableSpaceBytes(dir: File): Long = usableSpace
    override suspend fun networkRetryDelay(millis: Long) {
        retryDelays += millis
        onRetryDelay()
    }
}

/**
 * In-memory [HttpFetcher]. Unregistered URLs are 404 (NOT_AVAILABLE). Honours
 * Range unless [ignoreRange]; can fail a URL's next body after N bytes
 * ([failAfter], one-shot; the network then stays down, [offline], until the
 * test brings it back, unless [offlineAfterDrop] is off), drop every body
 * after [dropEvery] bytes, serve wrong bytes ([corrupt]) or stall after N
 * bytes until the stream is closed ([blockAfter]).
 */
internal class FakeFetcher : HttpFetcher {
    val files = mutableMapOf<String, ByteArray>()
    val requests = mutableListOf<Pair<String, Long>>()
    var ignoreRange = false
    val failAfter = mutableMapOf<String, Int>()
    var failure = IOException("connection reset")
    var offline = false
    var offlineAfterDrop = true
    var dropEvery: Int? = null
    val corrupt = mutableSetOf<String>()
    private val stalls = mutableMapOf<String, Pair<Int, CountDownLatch>>()

    /** Stall [url]'s next body after [bytes]; the returned latch opens when it stalls. */
    fun blockAfter(url: String, bytes: Int): CountDownLatch =
        CountDownLatch(1).also { stalls[url] = bytes to it }

    @Synchronized
    override fun openRange(url: String, fromBytes: Long): RangeResult {
        requests += url to fromBytes
        if (offline) throw IOException("network is unreachable")
        val payload = files[url]
            ?: throw InstallException(InstallFailure.NOT_AVAILABLE, NOT_AVAILABLE_MESSAGE)
        val body = if (url in corrupt) payload.copyOf().also { it[0] = (it[0] + 1).toByte() } else payload
        val start = if (ignoreRange) 0 else fromBytes.toInt()
        val stream: InputStream = ByteArrayInputStream(body, start, body.size - start)
        val total = body.size.toLong()
        failAfter.remove(url)?.let {
            return RangeResult(
                Truncated(stream, it) {
                    if (offlineAfterDrop) offline = true
                    throw failure
                },
                start.toLong(),
                total,
            )
        }
        dropEvery?.let { n ->
            if (body.size - start > n) return RangeResult(Truncated(stream, n) { throw IOException("connection reset") }, start.toLong(), total)
        }
        stalls.remove(url)?.let { (n, reached) ->
            val closed = CountDownLatch(1)
            val stalling = object : Truncated(stream, n, {
                reached.countDown()
                closed.await(30, TimeUnit.SECONDS)
                throw IOException("stream closed")
            }) {
                override fun close() = closed.countDown()
            }
            return RangeResult(stalling, start.toLong(), total)
        }
        return RangeResult(stream, start.toLong(), total)
    }
}

/** Serves [limit] bytes of [inner], then calls [atLimit] (which must throw) on every read. */
internal open class Truncated(
    private val inner: InputStream,
    private val limit: Int,
    private val atLimit: () -> Nothing,
) : InputStream() {
    private var served = 0

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (served >= limit) atLimit()
        val n = inner.read(b, off, minOf(len, limit - served))
        if (n > 0) served += n
        return n
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
