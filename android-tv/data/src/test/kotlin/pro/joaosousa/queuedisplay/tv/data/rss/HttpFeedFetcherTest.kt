package pro.joaosousa.queuedisplay.tv.data.rss

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** The real `HttpURLConnection` client against a local JDK HTTP server. */
class HttpFeedFetcherTest {

    private lateinit var server: HttpServer
    private var status = 200
    private var body = ByteArray(0)

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/rss") { exchange ->
                exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }

    @After
    fun stop() = server.stop(0)

    private val url get() = "http://127.0.0.1:${server.address.port}/rss"

    @Test
    fun returnsTheBodyOfA200() = runBlocking {
        body = "<rss/>".toByteArray()

        assertArrayEquals(body, HttpFeedFetcher().fetch(url))
    }

    @Test
    fun aServerErrorIsAnHttpStatusException() {
        status = 503
        body = "<html>down</html>".toByteArray()

        val error = assertThrows(HttpStatusException::class.java) { runBlocking { HttpFeedFetcher().fetch(url) } }
        assertEquals(503, error.code)
    }

    @Test
    fun aNotFoundIsAnHttpStatusException() {
        status = 404

        val error = assertThrows(HttpStatusException::class.java) { runBlocking { HttpFeedFetcher().fetch(url) } }
        assertEquals(404, error.code)
    }

    @Test
    fun anOversizedBodyIsRejected() {
        body = ByteArray(HttpFeedFetcher.MAX_BYTES + 1) { 'a'.code.toByte() }

        assertThrows(IOException::class.java) { runBlocking { HttpFeedFetcher().fetch(url) } }
    }

    @Test
    fun anUnreachableHostIsAnIOException() {
        val closed = url
        server.stop(0)

        assertThrows(IOException::class.java) { runBlocking { HttpFeedFetcher().fetch(closed) } }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0) // for stop()
    }
}
