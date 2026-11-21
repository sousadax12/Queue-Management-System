package pro.joaosousa.queuedisplay.tv.data.rss

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The server answered, but not with a 2xx. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** Downloads a feed body. */
fun interface FeedFetcher {
    /** @throws IOException on a network failure, a non-2xx answer or a body over the size cap. */
    suspend fun fetch(url: String): ByteArray
}

/** [FeedFetcher] over `HttpURLConnection`: no extra dependency for one GET every 15 minutes. */
class HttpFeedFetcher @Inject constructor() : FeedFetcher {

    override suspend fun fetch(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            connection.inputStream.use { it.readCapped(MAX_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readCapped(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > max) throw IOException("Feed larger than $max bytes")
            out.write(buffer, 0, read)
        }
    }

    companion object {
        private const val BUFFER_BYTES = 8 * 1024
        private const val TIMEOUT_MILLIS = 15_000

        /** The RTP feed is about 50 KB. */
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
