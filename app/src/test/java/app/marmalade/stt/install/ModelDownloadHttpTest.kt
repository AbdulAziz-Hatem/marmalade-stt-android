package app.marmalade.stt.install

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The installer over real sockets (#74): [UrlHttpFetcher] against a local
 * server shaped like a GitHub release download. The asset URL answers 302
 * to a signed URL that works once and has expired after that, so any
 * resume that reused an old redirect would get 403. [server] is a few lines
 * of HTTP/1.1 over a [ServerSocket], one request per connection
 * (`com.sun.net.httpserver` isn't on the Android unit-test classpath).
 */
class ModelDownloadHttpTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: ServerSocket
    private val port get() = server.localPort
    private lateinit var filesDir: File
    private lateinit var installer: TestInstaller

    private val payload = Random(7).nextBytes(400_000)

    /** Close the next signed response after this many bytes (one-shot). */
    @Volatile private var dropAfter: Int? = null

    /** The asset URL answers 503: the network is down. */
    @Volatile private var down = false

    /** The signed URL ignores Range and sends the whole file with 200. */
    @Volatile private var ignoreRange = false

    /** A captive portal: the asset URL answers 200 with a login page. */
    @Volatile private var portal = false

    /** The asset URL answers with this status instead of the redirect. */
    @Volatile private var status: Int? = null

    /** The signed URL's file has this many bytes instead of the payload's (it shrank on the server). */
    @Volatile private var servedSize: Int? = null

    private val loginPage = "<html><body>Sign in to Free WiFi</body></html>".toByteArray()

    private val issued = ConcurrentHashMap.newKeySet<String>()
    private val used = ConcurrentHashMap.newKeySet<String>()
    private val expiredHits = CopyOnWriteArrayList<String>()

    /** The Range header of every request the signed URL served (null: none). */
    private val ranges = CopyOnWriteArrayList<String?>()

    private val assetPath = "/maxwhipw/engines/releases/download/v1/enc.onnx"

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        Thread {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                Thread { socket.use { handle(it) } }.start()
            }
        }.apply { isDaemon = true }.start()
        filesDir = temp.newFolder("files")
        installer = newInstaller()
    }

    @After
    fun tearDown() = server.close()

    private fun newInstaller(): TestInstaller {
        val url = "http://127.0.0.1:$port$assetPath"
        val engine = SttEngineDescriptor(
            id = ENGINE, displayName = "Test", description = 0, family = EngineFamily.WHISPER,
            languages = listOf("en"), licenseId = "MIT", licenseKey = "test",
            files = listOf(ModelFile(FileRole.ENCODER, "enc.onnx", url, sha256(payload), payload.size.toLong())),
        )
        return TestInstaller(ModelFilesDir { filesDir }, UrlHttpFetcher(), EngineRelease { })
            .also { it.engines[ENGINE] = engine }
    }

    /** One request: the request line and headers, then [redirect] or [serveSigned]; the connection closes after. */
    private fun handle(socket: Socket) {
        val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val path = input.readLine()?.split(" ")?.getOrNull(1) ?: return
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim() }
        val out = socket.getOutputStream()
        try {
            when {
                path == assetPath -> redirect(out)
                path.startsWith("/signed/") -> serveSigned(out, path, headers["range"])
                else -> respond(out, 404)
            }
            out.flush()
        } catch (_: IOException) {
            // The client went away.
        }
    }

    private fun respond(out: OutputStream, code: Int, headers: Map<String, String> = emptyMap(), length: Long = 0) {
        val head = buildString {
            append("HTTP/1.1 $code X\r\n")
            headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Content-Length: $length\r\nConnection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
    }

    private fun redirect(out: OutputStream) {
        if (down) return respond(out, 503)
        status?.let { return respond(out, it) }
        if (portal) {
            respond(out, 200, mapOf("Content-Type" to "text/html"), loginPage.size.toLong())
            return out.write(loginPage)
        }
        val sig = UUID.randomUUID().toString()
        issued += sig
        respond(out, 302, mapOf("Location" to "http://127.0.0.1:$port/signed/enc.onnx?sig=$sig"))
    }

    private fun serveSigned(out: OutputStream, path: String, range: String?) {
        val sig = path.substringAfter("sig=")
        if (sig !in issued || !used.add(sig)) {
            expiredHits += sig
            return respond(out, 403)
        }
        ranges += range
        val asked = range?.let { Regex("""bytes=(\d+)-""").find(it)?.groupValues?.get(1)?.toInt() } ?: 0
        val size = servedSize ?: payload.size
        if (asked >= size) return respond(out, 416, mapOf("Content-Range" to "bytes */$size"))
        val start = if (ignoreRange) 0 else asked
        val length = size - start
        val headers = if (start > 0) mapOf("Content-Range" to "bytes $start-${size - 1}/$size") else emptyMap()
        respond(out, if (start > 0) 206 else 200, headers, length.toLong())
        // A drop: fewer bytes than Content-Length, then the connection closes.
        out.write(payload, start, minOf(dropAfter?.also { dropAfter = null } ?: length, length))
    }

    private fun assertInstalled() {
        assertEquals(InstallState.Installed, installer.state(ENGINE).value)
        assertArrayEquals(payload, installer.installedModel(ENGINE)!!.path(FileRole.ENCODER).readBytes())
        assertTrue("no request reused an expired redirect: $expiredHits", expiredHits.isEmpty())
    }

    @Test
    fun aDropMidDownloadResumesThroughAFreshRedirect() = runTest {
        dropAfter = 150_000

        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(listOf(null, "bytes=150000-"), ranges)
        assertEquals(1, installer.retryDelays.size)
        assertInstalled()
    }

    @Test
    fun aNetworkCutThenRetryResumesAfterTheOldRedirectExpired() = runTest {
        dropAfter = 120_000
        // The cut: the connection drops and every retry finds the network down.
        installer.onRetryDelay = { down = true }

        val result = installer.install(ENGINE)

        assertEquals(InstallFailure.NETWORK, (result.exceptionOrNull() as InstallException).failure)
        assertEquals(InstallState.Failed(NETWORK_MESSAGE, InstallFailure.NETWORK, 120_000L), installer.state(ENGINE).value)
        assertEquals(ModelInstaller.NETWORK_RETRY_DELAYS_MS.size, installer.retryDelays.size)

        // Back online; the user taps Retry. The first redirect has long expired.
        down = false
        installer.onRetryDelay = {}
        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(listOf(null, "bytes=120000-"), ranges)
        assertInstalled()
    }

    @Test
    fun anAppRestartMidDownloadResumesFromTheBytesOnDisk() = runTest {
        dropAfter = 90_000
        installer.onRetryDelay = { down = true }
        installer.install(ENGINE)

        // A new process: a fresh installer over the same files.
        installer = newInstaller()
        down = false
        assertEquals(InstallState.Paused(90_000L, payload.size.toLong()), installer.state(ENGINE).value)
        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(listOf(null, "bytes=90000-"), ranges)
        assertInstalled()
    }

    @Test
    fun aServerAnswering200ToARangeRestartsTheFile() = runTest {
        dropAfter = 100_000
        installer.onRetryDelay = { ignoreRange = true }

        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(listOf(null, "bytes=100000-"), ranges)
        assertInstalled()
    }

    private val part get() = File(filesDir, "models/$ENGINE.partial/enc.onnx.part")

    @Test
    fun aCaptivePortalPageNeverTouchesThePartial() = runTest {
        dropAfter = 120_000
        // The phone joins a Wi-Fi with a login page.
        installer.onRetryDelay = { portal = true }

        installer.install(ENGINE)

        assertEquals(InstallState.Failed(NETWORK_MESSAGE, InstallFailure.NETWORK, 120_000L), installer.state(ENGINE).value)
        assertArrayEquals("the partial is the file's first bytes, untouched", payload.copyOf(120_000), part.readBytes())

        portal = false
        installer.onRetryDelay = {}
        assertTrue(installer.install(ENGINE).isSuccess)
        assertInstalled()
    }

    @Test
    fun aForbiddenAnswerFailsAtOnce() = runTest {
        status = 403

        val result = installer.install(ENGINE)

        assertEquals(InstallFailure.NETWORK, (result.exceptionOrNull() as InstallException).failure)
        assertTrue("403 isn't retried", installer.retryDelays.isEmpty())
    }

    @Test
    fun aServerErrorIsRetried() = runTest {
        status = 503
        installer.onRetryDelay = { status = null }

        assertTrue(installer.install(ENGINE).isSuccess)

        assertEquals(1, installer.retryDelays.size)
        assertInstalled()
    }

    @Test
    fun aRangePastTheServersFileDeletesThePartialAsDamaged() = runTest {
        dropAfter = 120_000
        // The server's file is now shorter than our partial: 416 on the resume.
        installer.onRetryDelay = { servedSize = 100_000 }

        val result = installer.install(ENGINE)

        assertEquals(InstallFailure.CHECKSUM, (result.exceptionOrNull() as InstallException).failure)
        assertEquals(1, installer.retryDelays.size)
        assertEquals(InstallState.Failed(CHECKSUM_MESSAGE, InstallFailure.CHECKSUM, 0L), installer.state(ENGINE).value)
        assertFalse(part.exists())
    }

    private companion object {
        const val ENGINE = "http-engine"
    }
}
