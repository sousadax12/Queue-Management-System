package pro.joaosousa.queuedisplay.tv.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import pro.joaosousa.queuedisplay.tv.data.firebase.snapshotFlow
import pro.joaosousa.queuedisplay.tv.data.mapping.Collections
import pro.joaosousa.queuedisplay.tv.data.mapping.ContractIssueReporter
import pro.joaosousa.queuedisplay.tv.data.mapping.DeviceMapper
import pro.joaosousa.queuedisplay.tv.data.mapping.DeviceWrites
import pro.joaosousa.queuedisplay.tv.data.mapping.unwrap
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo
import pro.joaosousa.queuedisplay.tv.domain.repository.DeviceRepository

class FirestoreDeviceRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val reporter: ContractIssueReporter,
) : DeviceRepository {

    override suspend fun signIn(): String {
        auth.currentUser?.let { return it.uid }
        val user = checkNotNull(auth.signInAnonymously().await().user) { "anonymous sign-in returned no user" }
        return user.uid
    }

    override suspend fun registerIfMissing(uid: String, info: DeviceInfo, freeBytes: Long) {
        val ref = db.document(Collections.device(uid))
        // Ask the server: a cache miss while offline must not lead to a create, and a set()
        // on an existing doc would be an update that clears the portal's fields.
        if (ref.get(Source.SERVER).await().exists()) return
        ref.set(DeviceWrites.create(info, freeBytes, FieldValue.serverTimestamp())).await()
    }

    override fun device(uid: String): Flow<Device?> =
        db.document(Collections.device(uid)).snapshotFlow().map { snapshot ->
            snapshot.data?.let { reporter.unwrap(DeviceMapper.map(uid, it)) }
        }
}
