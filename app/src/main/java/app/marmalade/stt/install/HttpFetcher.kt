package app.marmalade.stt.install

import android.util.Log
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one network call the installer makes. Production is [UrlHttpFetcher];
 * unit tests serve bytes from memory.
 */
interface HttpFetcher {
    /**
     * GET [url] starting at byte [fromBytes] (0 = the whole file). Follows
     * redirects. Throws [InstallException] ([InstallFailure.NOT_AVAILABLE])
     * for 404/410, [HttpStatusException] for any other non-2xx, and
     * [IOException] for a connection error. A server that ignores the Range header still returns a working
     * stream, with [RangeResult.startedAtBytes] = 0. The caller closes the
     * stream; closing it from another thread aborts a blocked read.
     */
    @Throws(IOException::class)
    fun openRange(url: String, fromBytes: Long): RangeResult
}

/**
 * @property stream the body.
 * @property startedAtBytes the file offset of the body's first byte:
 *   `fromBytes` when the server honoured the range (HTTP 206), 0 when it
 *   sent the whole file (HTTP 200).
 * @property totalBytes the size of the whole file as the server states it
 *   (a 200's Content-Length, a 206's Content-Range total), or null when it
 *   doesn't. A captive portal's login page fails the installer's check of
 *   it against the catalog size (#74).
 */
data class RangeResult(val stream: InputStream, val startedAtBytes: Long, val totalBytes: Long? = null)

/**
 * A non-2xx answer other than 404/410 (those are [InstallFailure.NOT_AVAILABLE]).
 * Only [retryable] codes are worth asking again (#74): a timeout, a rate
 * limit or a server error; 401, 403, 416 and the other 4xx won't change.
 */
class HttpStatusException(val code: Int) : IOException("HTTP $code") {
    val retryable: Boolean get() = code == 408 || code == 429 || code in 500..599
}

/**
 * [HttpFetcher] over `HttpURLConnection`. A GitHub release asset answers with
 * a 302 to a short-lived signed CDN URL, which `instanceFollowRedirects`
 * follows (https → https); every request starts again from the release URL.
 *
 * [connect] is a seam so tests can hand in a fake connection instead of
 * opening a socket.
 */
class UrlHttpFetcher internal constructor(
    private val connect: (String) -> HttpURLConnection,
) : HttpFetcher {

    constructor() : this({ url -> URL(url).openConnection() as HttpURLConnection })

    override fun openRange(url: String, fromBytes: Long): RangeResult {
        val conn = connect(url).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            // Byte offsets must refer to the file itself, not a gzip of it.
            setRequestProperty("Accept-Encoding", "identity")
            if (fromBytes > 0L) setRequestProperty("Range", "bytes=$fromBytes-")
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code for a model file")
                Log.d(TAG, "HTTP $code fetching $url")
                if (code == 404 || code == 410) {
                    throw InstallException(InstallFailure.NOT_AVAILABLE, NOT_AVAILABLE_MESSAGE)
                }
                throw HttpStatusException(code)
            }
            val contentRange = conn.getHeaderField("Content-Range")
            val startedAt = if (code == 206 && fromBytes > 0L) contentRangeStart(contentRange) ?: fromBytes else 0L
            val total = if (code == 206) {
                contentRangeTotal(contentRange)
            } else {
                conn.getHeaderField("Content-Length")?.toLongOrNull()
            }
            val body = object : FilterInputStream(conn.inputStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        conn.disconnect()
                    }
                }
            }
            return RangeResult(body, startedAt, total)
        } catch (e: IOException) {
            conn.disconnect()
            throw e
        }
    }

    private companion object {
        const val TAG = "UrlHttpFetcher"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

/** The first byte offset in a `Content-Range: bytes 100-299/300` header, or null. */
internal fun contentRangeStart(header: String?): Long? =
    header?.let { Regex("""bytes\s+(\d+)-""").find(it)?.groupValues?.get(1)?.toLongOrNull() }

/** The whole file's size in a `Content-Range: bytes 100-299/300` header, or null (also when it is `*`, unknown). */
internal fun contentRangeTotal(header: String?): Long? =
    header?.let { Regex("""/(\d+)\s*$""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
