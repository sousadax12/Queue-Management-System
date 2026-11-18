package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import pro.joaosousa.queuedisplay.tv.data.firestore.DocumentSpec
import pro.joaosousa.queuedisplay.tv.data.firestore.Filter
import pro.joaosousa.queuedisplay.tv.data.firestore.FirestoreGateway
import pro.joaosousa.queuedisplay.tv.data.firestore.QueueMapper
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import javax.inject.Inject

/**
 * `queues` over Firestore.
 *
 * The filter and the ordering are pushed to Firestore rather than applied in Kotlin (invariant 7):
 * a site may hold many queues, and the box only wants its own.
 */
class FirestoreQueueRepository @Inject constructor(private val gateway: FirestoreGateway) : QueueRepository {

    override fun queues(siteId: String): Flow<List<Queue>> =
        gateway.snapshots(
            DocumentSpec.Collection(
                name = COLLECTION,
                filter = Filter(FIELD_SITE_ID, siteId),
                orderBy = FIELD_NAME,
            ),
        )
            .map { documents -> documents.map { QueueMapper.from(it.id, it) } }
            // A permission or network error must degrade to "no queues yet", not kill the screen.
            .catch { emit(emptyList()) }

    override fun queue(queueId: String): Flow<Queue?> =
        gateway.snapshots(DocumentSpec.Document("$COLLECTION/$queueId"))
            .map { documents -> documents.firstOrNull()?.let { QueueMapper.from(it.id, it) } }
            .catch { emit(null) }

    override suspend fun setNumber(queueId: String, number: Long): Result<Unit> = runCatching {
        gateway.update("$COLLECTION/$queueId", numberUpdate(number))
    }

    private companion object {
        const val COLLECTION = "queues"
        const val FIELD_SITE_ID = "siteId"
        const val FIELD_NAME = "name"
    }
}

/**
 * The write the operator performs, kept separate so a test can pin it: one field, and nothing
 * else. The Flutter app does `update({'number': n})`; a whole-document `set()` here would wipe
 * the portal's `updatedAt`/`updatedBy` and every field this build does not model.
 */
internal fun numberUpdate(number: Long): Map<String, Any> = mapOf("number" to number)
