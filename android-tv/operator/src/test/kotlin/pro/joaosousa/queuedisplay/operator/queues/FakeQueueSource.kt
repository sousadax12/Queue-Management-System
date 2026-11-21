package pro.joaosousa.queuedisplay.operator.queues

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pro.joaosousa.queuedisplay.tv.data.mapping.Collections
import pro.joaosousa.queuedisplay.tv.data.mapping.ContractFixtures

/**
 * An in-memory `queues` collection that filters like Firestore (`orgId ==` drops docs without
 * `orgId`; `siteId in`) and, like its local cache, re-emits a write at once (latency compensation).
 */
class FakeQueueSource(initial: Map<String, Map<String, Any?>> = emptyMap()) : QueueSource {

    private val docs = MutableStateFlow(initial)

    /** Thrown by the next [observe] instead of emitting. */
    var readFailure: QueueError? = null

    /** The server's answer to every write: null accepts it. A rejected write is rolled back. */
    var writeFailure: QueueError? = null

    /**
     * False simulates a listener that hasn't reported this box's writes back yet: writes are
     * recorded but not applied.
     */
    var echoWrites = true

    val queries = mutableListOf<Pair<String?, List<String>?>>()
    val writes = mutableListOf<Pair<String, Long>>()

    fun number(queueId: String): Any? = docs.value[queueId]?.get(NUMBER)

    /** Another client (or the portal) changes a field. */
    fun setField(queueId: String, field: String, value: Any?) {
        docs.update { all -> all + (queueId to all.getValue(queueId) + (field to value)) }
    }

    fun put(queueId: String, data: Map<String, Any?>) {
        docs.update { it + (queueId to data) }
    }

    fun remove(queueId: String) {
        docs.update { it - queueId }
    }

    override fun observe(orgFilter: String?, siteFilter: List<String>?): Flow<List<QueueDoc>> = flow {
        queries += orgFilter to siteFilter
        readFailure?.let { throw QueueSourceException(it) }
        emitAll(
            docs.map { all ->
                all.filter { (_, data) ->
                    (orgFilter == null || data["orgId"] == orgFilter) &&
                        (siteFilter == null || data["siteId"] in siteFilter)
                }.map { (id, data) -> QueueDoc(id, data) }
            },
        )
    }

    override fun updateNumber(queueId: String, number: Long, onFailure: (QueueError) -> Unit) {
        writes += queueId to number
        val failure = writeFailure
        when {
            failure != null -> onFailure(failure)
            echoWrites -> setField(queueId, NUMBER, number)
        }
    }

    companion object {
        private const val NUMBER = "number"

        /** Every queue fixture, both orgs and the Flutter-era legacy docs, keyed by id. */
        fun fromFixtures(): FakeQueueSource {
            val all = ContractFixtures.load("queues.json") + ContractFixtures.load("queues.legacy.json")
            return FakeQueueSource(all.mapKeys { (path, _) -> path.removePrefix("${Collections.QUEUES}/") })
        }

        const val ORG_A = "org_lojas_7Hq2"
        const val SITE_A = "site_porto_3fKq"
        const val BALCAO_A = "q_balcao_a_P4tc" // 42
        const val BALCAO_B = "q_balcao_b_Lr6w" // 7
        const val ORG_B = "org_clinica_K9xP"
        const val SITE_B = "site_lisboa_Wd2e"
        const val CONSULTAS = "q_consultas_Ma9v" // 0
        const val ANALISES = "q_analises_Ce7y" // 99
        const val LEGACY_MANUAL = "legacy_manual_3"
    }
}

class FakeSelectedQueueStore(initial: String? = null) : SelectedQueueStore {
    override val selected = MutableStateFlow(initial)

    override suspend fun select(queueId: String) {
        selected.value = queueId
    }

    override suspend fun clear() {
        selected.value = null
    }
}

class FakeChime : Chime {
    val plays = mutableListOf<Double>()

    override fun play(volume: Double) {
        plays += volume
    }
}
