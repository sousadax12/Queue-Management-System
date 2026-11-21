package pro.joaosousa.queuedisplay.operator.queues

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import pro.joaosousa.queuedisplay.tv.data.mapping.ContractIssueReporter
import pro.joaosousa.queuedisplay.tv.data.mapping.QueueMapper
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.operator.OperatorScope

/** The operator's queues, as the setup and home screens render them. */
sealed interface QueueListState {
    data object Loading : QueueListState

    /** The queues in scope, sorted by name. Empty means the account really has none. */
    data class Data(val queues: List<Queue>) : QueueListState

    /** The read failed. Never turned into an empty list. */
    data class Failed(val error: QueueError) : QueueListState
}

/**
 * The queues an operator may see and advance (design, "Architecture: Queues").
 *
 * The query is built from the claims' scope exactly as Flutter's `queuesQuery`
 * (`lib/auth/operator_scope.dart`): `orgId ==` the claim org and `siteId in` the claimed sites
 * (the first 30), with no filter for a platform admin. The rules reject any wider query. Docs go
 * through M2's [QueueMapper], so legacy docs and `Long`/`Double` numbers map as on the TV.
 */
class OperatorQueueRepository @Inject constructor(
    private val source: QueueSource,
    private val reporter: ContractIssueReporter,
) {

    fun observe(scope: OperatorScope): Flow<QueueListState> =
        source.observe(scope.orgFilter, scope.siteFilter)
            .map<List<QueueDoc>, QueueListState> { docs -> QueueListState.Data(map(docs)) }
            .onStart { emit(QueueListState.Loading) }
            .catch { e ->
                if (e !is QueueSourceException) throw e
                emit(QueueListState.Failed(e.error))
            }

    /**
     * Writes [number] to the queue with a plain `update({number})`, which works offline and is
     * last writer wins. [onFailure] runs only when the server rejects the write.
     */
    fun setNumber(queueId: String, number: Int, onFailure: (QueueError) -> Unit = {}) {
        source.updateNumber(queueId, number.toLong(), onFailure)
    }

    private fun map(docs: List<QueueDoc>): List<Queue> = docs
        .mapNotNull { doc ->
            val mapped = QueueMapper.map(doc.id, doc.data)
            if (mapped.issues.isNotEmpty()) reporter.report(mapped.issues)
            mapped.value
        }
        .sortedWith(Queue.BY_NAME)
}
