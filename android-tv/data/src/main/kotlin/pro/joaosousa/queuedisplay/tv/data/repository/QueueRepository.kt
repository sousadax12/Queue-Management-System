package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.Flow
import pro.joaosousa.queuedisplay.tv.domain.model.Queue

/**
 * `queues`, scoped to one site. `queues` is the only collection the operator
 * writes: advancing a number is the app's whole purpose.
 */
interface QueueRepository {

    /** Live, ordered by `name` (invariant 7), for one site. */
    fun queues(siteId: String): Flow<List<Queue>>

    /** One queue, live. Null when it disappears or is outside the site. */
    fun queue(queueId: String): Flow<Queue?>

    /**
     * Writes `number`. Only this field is touched, so the audit fields the
     * portal owns are left alone (the Flutter app does `update({'number': n})`).
     */
    suspend fun setNumber(queueId: String, number: Long): Result<Unit>
}
