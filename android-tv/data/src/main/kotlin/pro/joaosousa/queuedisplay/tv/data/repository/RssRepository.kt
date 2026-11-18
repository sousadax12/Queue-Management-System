package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.Flow
import pro.joaosousa.queuedisplay.tv.domain.model.Feed

/**
 * The RSS ticker. The Flutter app uses `webfeed`; Android has no equivalent, so
 * this is our own parser (design: ":data — RSS client").
 */
interface RssRepository {

    /** Parses `url`; failures arrive as `Result.failure` rather than throwing. */
    suspend fun feed(url: String): Result<Feed>

    /** Re-reads on the feed's own refresh interval. */
    fun feedPolled(url: String, refreshMinutes: Long): Flow<Result<Feed>>
}
