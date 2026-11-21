package pro.joaosousa.queuedisplay.tv.data.rss

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pro.joaosousa.queuedisplay.tv.domain.repository.HeadlineRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RssHeadlineRepositoryTest {

    private fun feed(vararg titles: String) =
        "<rss><channel>${titles.joinToString("") { "<item><title>$it</title></item>" }}</channel></rss>".toByteArray()

    /** Answers each fetch with the next scripted result. */
    private class ScriptedFetcher(vararg results: () -> ByteArray) : FeedFetcher {
        private val queue = ArrayDeque(results.toList())
        val urls = mutableListOf<String>()

        override suspend fun fetch(url: String): ByteArray {
            urls += url
            return queue.removeFirst().invoke()
        }
    }

    private fun TestScope.collect(fetcher: FeedFetcher): MutableList<List<String>> {
        val seen = mutableListOf<List<String>>()
        RssHeadlineRepository(fetcher).headlines().onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        return seen
    }

    @Test
    fun fetchesTheRtpFeedAtOnce() = runTest {
        val fetcher = ScriptedFetcher({ feed("A", "B") })

        val seen = collect(fetcher)

        assertEquals(listOf(listOf("A", "B")), seen)
        assertEquals(listOf(HeadlineRepository.RTP_FEED_URL), fetcher.urls)
    }

    @Test
    fun refreshesEveryFifteenMinutes() = runTest {
        val fetcher = ScriptedFetcher({ feed("A") }, { feed("B") })
        val seen = collect(fetcher)

        advanceTimeBy(HeadlineRepository.REFRESH.inWholeMilliseconds - 1)
        runCurrent()
        assertEquals(1, fetcher.urls.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(listOf("A"), listOf("B")), seen)
    }

    @Test
    fun aFailedRefreshKeepsTheLastGoodList() = runTest {
        val fetcher = ScriptedFetcher(
            { feed("A") },
            { throw HttpStatusException(500) },
            { "<html>".toByteArray() },
            { feed() },
            { feed("B") },
        )
        val seen = collect(fetcher)

        repeat(4) {
            advanceTimeBy(HeadlineRepository.REFRESH.inWholeMilliseconds)
            runCurrent()
        }

        assertEquals(5, fetcher.urls.size)
        assertEquals(listOf(listOf("A"), listOf("B")), seen)
    }

    @Test
    fun anHttpErrorFirstHidesTheTicker() = runTest {
        val seen = collect(ScriptedFetcher({ throw HttpStatusException(500) }))

        assertEquals(listOf(emptyList<String>()), seen)
    }

    @Test
    fun aNetworkErrorFirstHidesTheTicker() = runTest {
        val seen = collect(ScriptedFetcher({ throw IOException("offline") }))

        assertEquals(listOf(emptyList<String>()), seen)
    }

    @Test
    fun aMalformedFeedFirstHidesTheTicker() = runTest {
        val seen = collect(ScriptedFetcher({ "<rss><channel>".toByteArray() }))

        assertEquals(listOf(emptyList<String>()), seen)
    }

    @Test
    fun anEmptyFeedFirstHidesTheTicker() = runTest {
        val seen = collect(ScriptedFetcher({ feed() }))

        assertEquals(listOf(emptyList<String>()), seen)
    }

    @Test
    fun recoversAfterAFailedFirstFetch() = runTest {
        val seen = collect(ScriptedFetcher({ throw IOException("offline") }, { feed("A") }))

        advanceTimeBy(HeadlineRepository.REFRESH.inWholeMilliseconds)
        runCurrent()

        assertEquals(listOf(emptyList(), listOf("A")), seen)
    }
}
