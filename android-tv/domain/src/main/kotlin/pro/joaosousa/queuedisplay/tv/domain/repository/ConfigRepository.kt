package pro.joaosousa.queuedisplay.tv.domain.repository

import kotlinx.coroutines.flow.Flow
import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.Site

/**
 * What a claimed box reads, reached from its device doc by id or by `siteId` (TV invariant 10).
 * Each flow is live and emits null while its doc doesn't exist. A flow fails with the
 * backend's error (for example a permission error before the box is claimed).
 */
interface ConfigRepository {
    fun site(siteId: String): Flow<Site?>

    fun screen(siteId: String, screenId: String): Flow<Screen?>

    fun playlist(playlistId: String): Flow<Playlist?>

    /** Every queue of [siteId]. The query filters on `siteId`, which the rules require. */
    fun queues(siteId: String): Flow<List<Queue>>
}
