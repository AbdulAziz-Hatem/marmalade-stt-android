package app.marmalade.stt.install

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** [UrlHttpFetcher]'s status/header mapping, over a fake connection (no sockets). */
class UrlHttpFetcherTest {

    private class FakeConnection(
        private val code: Int,
        private val headers: Map<String, String> = emptyMap(),
    ) : HttpURLConnection(URL("https://example.invalid/model.onnx")) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?): String? = headers[name]
        override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
    }

    private fun fetch(conn: FakeConnection, fromBytes: Long): RangeResult =
        UrlHttpFetcher { conn }.openRange("https://example.invalid/model.onnx", fromBytes)

    @Test
    fun notFoundAndGoneAreNotAvailable() {
        for (code in listOf(404, 410)) {
            val conn = FakeConnection(code)
            try {
                fetch(conn, 0L)
                fail("HTTP $code should throw")
            } catch (e: InstallException) {
                assertEquals(InstallFailure.NOT_AVAILABLE, e.failure)
                assertFalse("no URL in the message", e.message!!.contains("http"))
            }
            assertTrue(conn.disconnected)
        }
    }

    @Test
    fun otherErrorsCarryTheirStatus() {
        for (code in listOf(401, 403, 408, 416, 429, 500, 503)) {
            try {
                fetch(FakeConnection(code), 0L)
                fail("HTTP $code should throw")
            } catch (e: HttpStatusException) {
                assertEquals(code, e.code)
            }
        }
    }

    @Test
    fun onlyTimeoutsRateLimitsAndServerErrorsAreRetryable() {
        for (code in listOf(408, 429, 500, 502, 503)) assertTrue("$code", HttpStatusException(code).retryable)
        for (code in listOf(400, 401, 403, 416, 451)) assertFalse("$code", HttpStatusException(code).retryable)
    }

    @Test
    fun theStatedFileSizeComesFromContentLengthOrTheContentRangeTotal() {
        assertEquals(300L, fetch(FakeConnection(200, mapOf("Content-Length" to "300")), 0L).totalBytes)
        assertEquals(300L, fetch(FakeConnection(206, mapOf("Content-Range" to "bytes 100-299/300", "Content-Length" to "200")), 100L).totalBytes)
        assertNull(fetch(FakeConnection(200), 0L).totalBytes)
        assertNull(fetch(FakeConnection(206, mapOf("Content-Range" to "bytes 100-299/*")), 100L).totalBytes)
    }

    @Test
    fun aHonouredRangeStartsWhereContentRangeSays() {
        val conn = FakeConnection(206, mapOf("Content-Range" to "bytes 100-299/300"))

        val result = fetch(conn, 100L)

        assertEquals(100L, result.startedAtBytes)
        assertEquals("bytes=100-", conn.getRequestProperty("Range"))
        assertEquals("identity", conn.getRequestProperty("Accept-Encoding"))
        assertTrue(conn.instanceFollowRedirects)
        result.stream.close()
        assertTrue("closing the body disconnects", conn.disconnected)
    }

    @Test
    fun a206WithoutContentRangeIsTakenAtItsWord() {
        assertEquals(100L, fetch(FakeConnection(206), 100L).startedAtBytes)
    }

    @Test
    fun anIgnoredRangeStartsAtZero() {
        assertEquals(0L, fetch(FakeConnection(200), 100L).startedAtBytes)
    }

    @Test
    fun aFreshDownloadSendsNoRange() {
        val conn = FakeConnection(200)
        assertEquals(0L, fetch(conn, 0L).startedAtBytes)
        assertNull(conn.getRequestProperty("Range"))
    }

    @Test
    fun contentRangeParsing() {
        assertEquals(5L, contentRangeStart("bytes 5-9/10"))
        assertNull(contentRangeStart("bytes */10"))
        assertNull(contentRangeStart(null))
        assertEquals(10L, contentRangeTotal("bytes 5-9/10"))
        assertEquals(10L, contentRangeTotal("bytes */10"))
        assertNull(contentRangeTotal("bytes 5-9/*"))
        assertNull(contentRangeTotal(null))
    }
}
