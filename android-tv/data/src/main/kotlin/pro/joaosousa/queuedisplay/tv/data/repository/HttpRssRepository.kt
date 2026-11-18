package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import pro.joaosousa.queuedisplay.tv.data.rss.FeedParser
import pro.joaosousa.queuedisplay.tv.domain.model.Feed
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** One HTTP answer, kept separate from [Feed] so the parser never sees transport concerns. */
data class FeedResponse(val code: Int, val body: String)

/**
 * The ticker's feed source: fetch, parse, and never throw (design D6, `RssRepository`).
 *
 * Everything is a `Result` because a dead or malformed feed is an expected state on a box in a
 * shop — the ticker shows an error line and keeps polling, it does not crash the screen.
 *
 * [fetch] is injectable so tests exercise the failure paths without a network; production uses
 * [httpGet], which is `HttpURLConnection` and so needs no HTTP dependency.
 */
class HttpRssRepository(
    private val fetch: suspend (String) -> FeedResponse = ::httpGet,
) : RssRepository {

    override suspend fun feed(url: String): Result<Feed> = runCatching {
        val response = fetch(url)
        if (response.code !in SUCCESS_CODES) {
            throw IOException("HTTP ${response.code} for $url")
        }
        FeedParser.parse(response.body)
    }

    /**
     * Re-reads on the site's own `refreshMinutes`. A failure is emitted like any other value and
     * the loop continues, so one bad poll does not silence the ticker for the rest of the day.
     */
    override fun feedPolled(url: String, refreshMinutes: Long): Flow<Result<Feed>> = flow {
        val period = (refreshMinutes.takeIf { it > 0 } ?: DEFAULT_REFRESH_MINUTES) * MILLIS_PER_MINUTE
        while (true) {
            emit(feed(url))
            delay(period)
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        /** The portal's default when a site carries no `refreshMinutes`. */
        const val DEFAULT_REFRESH_MINUTES = 15L
    }
}

/** GET [url] on the IO dispatcher; redirects followed, body decoded as UTF-8. */
private suspend fun httpGet(url: String): FeedResponse = withContext(Dispatchers.IO) {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, */*")
    }
    try {
        val code = connection.responseCode
        // The error body is not parsed, but it is drained so the connection can be reused.
        val stream = if (code in SUCCESS_CODES) connection.inputStream else connection.errorStream
        FeedResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    } finally {
        connection.disconnect()
    }
}

private const val TIMEOUT_MS = 10_000
private val SUCCESS_CODES = 200..299