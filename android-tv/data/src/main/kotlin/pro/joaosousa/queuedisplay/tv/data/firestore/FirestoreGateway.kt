package pro.joaosousa.queuedisplay.tv.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** One `whereEqualTo` clause: the only filter the repositories need. */
data class Filter(val field: String, val value: Any?)

/**
 * What a listener reads: the shape of every read the operators perform.
 *
 * It is a value type so a fake gateway can be keyed by it in tests, and so the query the
 * repository *intends* (filter on `siteId`, order by `name`) is assertable without Firestore.
 */
sealed interface DocumentSpec {

    /** Live documents of one collection, optionally filtered and ordered server-side. */
    data class Collection(
        val name: String,
        val filter: Filter? = null,
        val orderBy: String? = null,
    ) : DocumentSpec

    /** One live document by full path, e.g. `sites/{siteId}/screens/{screenId}`. */
    data class Document(val path: String) : DocumentSpec
}

/**
 * The slice of Firestore `:data` uses, behind an interface.
 *
 * Two reasons: the repositories then run under plain JVM tests (no emulator, no Firebase), and
 * [FirebaseFirestoreGateway] stays the only class that names the SDK — the same rule
 * [DocumentData] applies to the mappers.
 */
interface FirestoreGateway {

    /**
     * Emits the current result as soon as it arrives, then once per change. The listener is
     * removed when the flow's collector is cancelled; an SDK error closes the flow with that
     * error, and each repository decides what to show instead of crashing the screen.
     */
    fun snapshots(spec: DocumentSpec): Flow<List<DocumentData>>

    /**
     * Updates only [fields] on the document at [path]. Throws on failure — callers answer with
     * a `Result`, because this module never throws across its API.
     */
    suspend fun update(path: String, fields: Map<String, Any>)
}

/** The real gateway: Firestore snapshot listeners wired into cold flows. */
class FirebaseFirestoreGateway @Inject constructor(private val firestore: FirebaseFirestore) : FirestoreGateway {

    override fun snapshots(spec: DocumentSpec): Flow<List<DocumentData>> = callbackFlow {
        val registration = when (spec) {
            is DocumentSpec.Collection -> {
                var query: Query = firestore.collection(spec.name)
                spec.filter?.let { filter -> query = query.whereEqualTo(filter.field, filter.value) }
                spec.orderBy?.let { field -> query = query.orderBy(field) }
                query.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else {
                        trySend(snapshot?.documents?.map(::SnapshotData).orEmpty())
                    }
                }
            }

            is DocumentSpec.Document -> {
                firestore.document(spec.path).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else {
                        // A deleted document yields an empty list, which each caller maps to null.
                        trySend(listOfNotNull(snapshot?.takeIf { it.exists() }?.let(::SnapshotData)))
                    }
                }
            }
        }
        // The listener keeps a Firestore channel open; leaving the scope must close it too.
        awaitClose { registration.remove() }
    }

    override suspend fun update(path: String, fields: Map<String, Any>) {
        firestore.document(path).update(fields).await()
    }
}
