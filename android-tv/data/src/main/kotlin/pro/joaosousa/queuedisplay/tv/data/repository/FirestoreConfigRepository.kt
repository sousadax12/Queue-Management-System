package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import pro.joaosousa.queuedisplay.tv.data.firestore.DocumentSpec
import pro.joaosousa.queuedisplay.tv.data.firestore.FirestoreGateway
import pro.joaosousa.queuedisplay.tv.data.firestore.ScreenMapper
import pro.joaosousa.queuedisplay.tv.data.firestore.SiteMapper
import pro.joaosousa.queuedisplay.tv.domain.model.Site
import javax.inject.Inject

/**
 * `sites` and their `screens`, read-only (the portal is the only writer).
 *
 * `sites` is ordered by `name` so a site picker does not reshuffle between snapshots; Firestore
 * documents have no intrinsic order otherwise.
 */
class FirestoreConfigRepository @Inject constructor(private val gateway: FirestoreGateway) : ConfigRepository {

    override fun sites(): Flow<List<Site>> =
        gateway.snapshots(DocumentSpec.Collection(COLLECTION, orderBy = FIELD_NAME))
            .map { documents -> documents.map { SiteMapper.from(it.id, it) } }
            .catch { emit(emptyList()) }

    override fun site(siteId: String): Flow<Site?> =
        gateway.snapshots(DocumentSpec.Document("$COLLECTION/$siteId"))
            .map { documents -> documents.firstOrNull()?.let { SiteMapper.from(it.id, it) } }
            .catch { emit(null) }

    override fun screen(siteId: String, screenId: String): Flow<ScreenResult?> =
        gateway.snapshots(DocumentSpec.Document("$COLLECTION/$siteId/$SCREENS/$screenId"))
            .map { documents ->
                documents.firstOrNull()?.let { document ->
                    val mapped = ScreenMapper.from(document.id, document)
                    // Invariant 5: the fallback is reported, not hidden, so the box can log it.
                    ScreenResult(mapped.screen, mapped.unknownTemplate)
                }
            }
            .catch { emit(null) }

    private companion object {
        const val COLLECTION = "sites"
        const val SCREENS = "screens"
        const val FIELD_NAME = "name"
    }
}
