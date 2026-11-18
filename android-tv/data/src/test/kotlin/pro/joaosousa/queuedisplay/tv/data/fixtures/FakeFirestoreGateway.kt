package pro.joaosousa.queuedisplay.tv.data.fixtures

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import pro.joaosousa.queuedisplay.tv.data.firestore.DocumentData
import pro.joaosousa.queuedisplay.tv.data.firestore.DocumentSpec
import pro.joaosousa.queuedisplay.tv.data.firestore.FirestoreGateway

/**
 * A [FirestoreGateway] backed by canned snapshots.
 *
 * The gateway exists precisely so the repositories can be tested on the JVM: this records the
 * query each repository *asks* for and the write it *performs*, which is what the port's
 * invariants are about (filtered and ordered reads, a single-field write).
 */
class FakeFirestoreGateway(
    private val snapshotsBySpec: Map<DocumentSpec, List<DocumentData>> = emptyMap(),
    private val snapshotFailure: Throwable? = null,
) : FirestoreGateway {

    data class Update(val path: String, val fields: Map<String, Any>)

    /** Every spec a repository listened to, in order. */
    val requested = mutableListOf<DocumentSpec>()

    /** Every update that reached Firestore, in order. */
    val updates = mutableListOf<Update>()

    /** Set to make the next writes fail, as an offline device would. */
    var updateFailure: Throwable? = null

    override fun snapshots(spec: DocumentSpec): Flow<List<DocumentData>> {
        requested += spec
        if (snapshotFailure != null) {
            val failure = snapshotFailure
            return flow { throw failure }
        }
        return flowOf(snapshotsBySpec[spec].orEmpty())
    }

    override suspend fun update(path: String, fields: Map<String, Any>) {
        updateFailure?.let { throw it }
        updates += Update(path, fields)
    }
}