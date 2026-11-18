package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * The transport half of the ticker: a dead URL, an error page and a malformed body are all
 * `Result.failure`, because the ticker renders an error line instead of crashing (design D6).
 */
class HttpRssRepositoryTest {

    private val url = "https://example.test/feed.xml"

    private val rss = """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0"><channel><title>Notícias</title>
          <item><title>Primeira</title><link>https://example.test/1</link></item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `a 200 answer is parsed into a feed`() = runBlocking {
        val repository = HttpRssRepository { FeedResponse(200, rss) }

        val feed = repository.feed(url).getOrThrow()

        assertEquals("Notícias", feed.title)
        assertEquals(listOf("Primeira"), feed.items.map { it.title })
    }

    @Test
    fun `a non-2xx answer is a failure carrying the status`() = runBlocking {
        for (code in listOf(301, 404, 500)) {
            val repository = HttpRssRepository { FeedResponse(code, "") }

            val result = repository.feed(url)

            assertTrue(result.isFailure, "HTTP $code should fail")
            assertEquals("HTTP $code for $url", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `an HTML error page with a 200 status is a failure, not an empty feed`() = runBlocking {
        val repository = HttpRssRepository { FeedResponse(200, "<html><body>Manutenção</body></html>") }

        assertTrue(repository.feed(url).isFailure)
    }

    @Test
    fun `malformed XML is a failure`() = runBlocking {
        val repository = HttpRssRepository { FeedResponse(200, "<rss><channel>") }

        assertTrue(repository.feed(url).isFailure)
    }

    @Test
    fun `a transport exception is a failure, never a throw`() = runBlocking {
        val repository = HttpRssRepository { throw IOException("no route to host") }

        val result = repository.feed(url)

        assertTrue(result.isFailure)
        assertEquals("no route to host", result.exceptionOrNull()?.message)
    }

    @Test
    fun `the polled flow emits the feed and keeps polling`() = runBlocking {
        var calls = 0
        val repository = HttpRssRepository {
            calls++
            FeedResponse(200, rss)
        }

        val first = repository.feedPolled(url, refreshMinutes = 15).first()

        assertEquals(1, calls)
        assertEquals("Notícias", first.getOrThrow().title)
    }

    @Test
    fun `the polled flow emits a failure instead of ending the ticker`() = runBlocking {
        val repository = HttpRssRepository { FeedResponse(503, "") }

        val first = repository.feedPolled(url, refreshMinutes = 15).first()

        assertTrue(first.isFailure)
    }
}