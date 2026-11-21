package pro.joaosousa.queuedisplay.operator.queues

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import pro.joaosousa.queuedisplay.tv.data.mapping.Collections

/** [QueueSource] over the Firestore SDK, with its default offline persistence. */
class FirestoreQueueSource @Inject constructor(
    private val db: FirebaseFirestore,
) : QueueSource {

    override fun observe(orgFilter: String?, siteFilter: List<String>?): Flow<List<QueueDoc>> = callbackFlow {
        // The query of `queuesQuery` in lib/auth/operator_scope.dart: equality and `in` filters
        // and no orderBy, so single-field indexes serve it (the caller sorts).
        var query: Query = db.collection(Collections.QUEUES)
        if (orgFilter != null) query = query.whereEqualTo(ORG_ID, orgFilter)
        if (siteFilter != null) query = query.whereIn(SITE_ID, siteFilter)
        val registration = query.addSnapshotListener { snapshot, error ->
            when {
                error != null -> close(QueueSourceException(errorOf(error), error))
                snapshot != null -> trySend(snapshot.documents.map { QueueDoc(it.id, it.data.orEmpty()) })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun updateNumber(queueId: String, number: Long, onFailure: (QueueError) -> Unit) {
        // Not a transaction: a transaction needs the server, and the owner wants offline presses
        // (design Q6). The returned task completes only once the server has the write, which
        // never happens while offline, so nothing waits on it.
        db.document(Collections.queue(queueId))
            .update(mapOf(NUMBER to number))
            .addOnFailureListener { e -> onFailure(errorOf(e)) }
    }

    private companion object {
        const val ORG_ID = "orgId"
        const val SITE_ID = "siteId"
        const val NUMBER = "number"

        fun errorOf(e: Exception): QueueError =
            if (e is FirebaseFirestoreException) {
                queueErrorOf(e.code.name, e.message)
            } else {
                QueueError.Other(e.message ?: e.javaClass.simpleName)
            }
    }
}

/**
 * Maps a Firestore status code (`FirebaseFirestoreException.Code.name`) to what the operator UI
 * distinguishes. By name, because the SDK's enum can't load in plain JVM tests.
 */
fun queueErrorOf(code: String, message: String?): QueueError = when (code) {
    "PERMISSION_DENIED", "UNAUTHENTICATED" -> QueueError.PermissionDenied
    "NOT_FOUND" -> QueueError.NotFound
    else -> QueueError.Other(message?.takeIf { it.isNotBlank() } ?: code)
}
