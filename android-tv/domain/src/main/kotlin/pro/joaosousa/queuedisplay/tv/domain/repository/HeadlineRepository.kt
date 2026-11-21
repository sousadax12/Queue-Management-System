package pro.joaosousa.queuedisplay.tv.domain.repository

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow

/**
 * News headlines for the ticker zone (operator F10, TV M3): the item titles of an RSS feed.
 */
interface HeadlineRepository {
    /**
     * Fetches [feedUrl] at once and then every [REFRESH]. Emits an empty list while no good list
     * has been fetched yet (the ticker hides itself). A failed or empty refresh keeps the last
     * good list. Never fails.
     */
    fun headlines(feedUrl: String = RTP_FEED_URL): Flow<List<String>>

    companion object {
        /** Flutter's hard-coded feed (`lib/pages/home_page.dart`), kept by design Q12. */
        const val RTP_FEED_URL = "https://www.rtp.pt/noticias/rss"

        val REFRESH: Duration = 15.minutes
    }
}
