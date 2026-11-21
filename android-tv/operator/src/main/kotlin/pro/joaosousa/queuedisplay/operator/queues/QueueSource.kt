package pro.joaosousa.queuedisplay.operator.queues

import kotlinx.coroutines.flow.Flow

/** One `queues/{id}` document as the listener delivers it: the id and `getData()`. */
data class QueueDoc(val id: String, val data: Map<String, Any?>)

/** Why a queue read or write failed, as the operator needs to tell them apart. */
sealed interface QueueError {
    /** The rules refused it (`PERMISSION_DENIED`, or no valid sign-in). Shown as "Access denied". */
    data object PermissionDenied : QueueError

    /** The queue doc is gone (a write to a deleted queue). */
    data object NotFound : QueueError

    /** Anything else, with the SDK's message. */
    data class Other(val message: String) : QueueError
}

/** A failed read, carrying its typed [error]. */
class QueueSourceException(val error: QueueError, cause: Throwable? = null) :
    Exception(error.toString(), cause)

/**
 * The Firestore side of [OperatorQueueRepository], behind a seam so the repository runs in JVM
 * tests over the contract fixtures.
 */
interface QueueSource {

    /**
     * Live `queues` docs matching `orgId == orgFilter` (when not null) and `siteId in siteFilter`
     * (when not null). Includes this device's pending writes, so a local write shows at once,
     * also offline. Fails with [QueueSourceException].
     */
    fun observe(orgFilter: String?, siteFilter: List<String>?): Flow<List<QueueDoc>>

    /**
     * A plain `update({number})`: applied to the local cache at once and synced when the network
     * allows (offline, last writer wins). [onFailure] runs only if the server rejects it.
     */
    fun updateNumber(queueId: String, number: Long, onFailure: (QueueError) -> Unit)
}
