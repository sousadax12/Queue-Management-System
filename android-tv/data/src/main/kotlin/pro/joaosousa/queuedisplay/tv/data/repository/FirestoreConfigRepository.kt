package pro.joaosousa.queuedisplay.tv.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pro.joaosousa.queuedisplay.tv.data.firebase.snapshotFlow
import pro.joaosousa.queuedisplay.tv.data.mapping.Collections
import pro.joaosousa.queuedisplay.tv.data.mapping.ContractIssueReporter
import pro.joaosousa.queuedisplay.tv.data.mapping.PlaylistMapper
import pro.joaosousa.queuedisplay.tv.data.mapping.QueueMapper
import pro.joaosousa.queuedisplay.tv.data.mapping.ScreenMapper
import pro.joaosousa.queuedisplay.tv.data.mapping.SiteMapper
import pro.joaosousa.queuedisplay.tv.data.mapping.unwrap
import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.Site
import pro.joaosousa.queuedisplay.tv.domain.repository.ConfigRepository

class FirestoreConfigRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val reporter: ContractIssueReporter,
) : ConfigRepository {

    override fun site(siteId: String): Flow<Site?> =
        db.document(Collections.site(siteId)).snapshotFlow().map { s ->
            s.data?.let { reporter.unwrap(SiteMapper.map(siteId, it)) }
        }

    override fun screen(siteId: String, screenId: String): Flow<Screen?> =
        db.document(Collections.screen(siteId, screenId)).snapshotFlow().map { s ->
            s.data?.let { reporter.unwrap(ScreenMapper.map(siteId, screenId, it)) }
        }

    override fun playlist(playlistId: String): Flow<Playlist?> =
        db.document(Collections.playlist(playlistId)).snapshotFlow().map { s ->
            s.data?.let { reporter.unwrap(PlaylistMapper.map(playlistId, it)) }
        }

    /** Filtered on `siteId`: the rules only let a box read its own site's queues. */
    override fun queues(siteId: String): Flow<List<Queue>> =
        db.collection(Collections.QUEUES).whereEqualTo(SITE_ID, siteId).snapshotFlow().map { qs ->
            qs.documents
                .mapNotNull { d -> d.data?.let { reporter.unwrap(QueueMapper.map(d.id, it)) } }
                .sortedWith(Queue.BY_NAME)
        }

    private companion object {
        const val SITE_ID = "siteId"
    }
}
