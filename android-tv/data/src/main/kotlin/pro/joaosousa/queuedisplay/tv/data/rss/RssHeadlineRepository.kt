package pro.joaosousa.queuedisplay.tv.data.rss

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import pro.joaosousa.queuedisplay.tv.domain.repository.HeadlineRepository

/**
 * The ticker's headlines: fetch and parse the feed at once and every
 * [HeadlineRepository.REFRESH], keeping the last good (non-empty) list. Fixes Flutter's F10 bug,
 * which fetched the feed again on every rebuild with no timer and no cache.
 */
class RssHeadlineRepository @Inject constructor(
    private val fetcher: FeedFetcher,
) : HeadlineRepository {

    override fun headlines(feedUrl: String): Flow<List<String>> = flow {
        var lastGood = emptyList<String>()
        while (true) {
            val titles = try {
                RssParser.titles(fetcher.fetch(feedUrl).inputStream())
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Network, HTTP status or parse error: keep what we have (nothing hides the ticker).
                null
            }
            if (!titles.isNullOrEmpty()) lastGood = titles
            emit(lastGood)
            delay(HeadlineRepository.REFRESH)
        }
    }.distinctUntilChanged()
}
