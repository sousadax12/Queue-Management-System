package pro.joaosousa.queuedisplay.operator.queues

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ANALISES
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.BALCAO_A
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.BALCAO_B
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.CONSULTAS
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.LEGACY_MANUAL
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ORG_A
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ORG_B
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.SITE_A
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.SITE_B
import pro.joaosousa.queuedisplay.tv.data.mapping.ContractIssue
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults
import pro.joaosousa.queuedisplay.tv.domain.operator.OperatorScope
import pro.joaosousa.queuedisplay.tv.domain.operator.StaffRole

/** [OperatorQueueRepository] over the shared contract fixtures (both orgs and the legacy docs). */
class OperatorQueueRepositoryTest {

    private val source = FakeQueueSource.fromFixtures()
    private val issues = mutableListOf<ContractIssue>()
    private val repository = OperatorQueueRepository(source) { issues += it }

    private val operatorA = OperatorScope(StaffRole.Operator, ORG_A, listOf(SITE_A))
    private val operatorB = OperatorScope(StaffRole.Operator, ORG_B, listOf(SITE_B))

    private suspend fun queuesOf(scope: OperatorScope): List<Queue> {
        val states = repository.observe(scope).take(2).toList()
        assertEquals(QueueListState.Loading, states[0])
        return (states[1] as QueueListState.Data).queues
    }

    @Test
    fun `an operator's query is its org and its sites, as Flutter's queuesQuery`() = runTest {
        queuesOf(operatorA)
        queuesOf(OperatorScope(StaffRole.Manager, ORG_A, listOf(SITE_A, "site_other")))

        assertEquals(listOf(ORG_A to listOf(SITE_A), ORG_A to listOf(SITE_A, "site_other")), source.queries)
    }

    @Test
    fun `org A's operator sees only org A's site queues, sorted by name`() = runTest {
        val queues = queuesOf(operatorA)

        assertEquals(listOf(BALCAO_A, BALCAO_B), queues.map { it.id })
        val a = queues[0]
        assertEquals("Balcão A", a.name)
        assertEquals(42, a.number)
        assertEquals(SITE_A, a.siteId)
        assertEquals(4282339765L, a.backgroundColor)
        assertEquals(1.0, a.soundVolume, 0.0)
        // Balcão B has no foregroundColor: the contract default.
        assertEquals(QueueDefaults.FOREGROUND_COLOR, queues[1].foregroundColor)
        assertEquals(emptyList<ContractIssue>(), issues)
    }

    @Test
    fun `org B's operator sees only org B's queues, at both ends of the range`() = runTest {
        val queues = queuesOf(operatorB)

        assertEquals(listOf("Análises", "Consultas"), queues.map { it.name })
        assertEquals(listOf(99, 0), queues.map { it.number })
        assertTrue(queues.none { it.id == BALCAO_A || it.id == BALCAO_B })
    }

    @Test
    fun `an org admin's query has no site filter`() = runTest {
        val queues = queuesOf(OperatorScope(StaffRole.OrgAdmin, ORG_B))

        assertEquals(ORG_B to null, source.queries.single())
        assertEquals(setOf(ANALISES, CONSULTAS), queues.map { it.id }.toSet())
    }

    @Test
    fun `a platform admin sees every queue, legacy docs included, mapped with defaults`() = runTest {
        val queues = queuesOf(OperatorScope(StaffRole.PlatformAdmin))

        assertEquals(null to null, source.queries.single())
        assertEquals(7, queues.size)
        val manual = queues.single { it.id == LEGACY_MANUAL }
        assertEquals(null, manual.siteId)
        assertEquals(5, manual.number)
        // A Double in the doc (0.5), read through Number.
        assertEquals(0.5, manual.soundVolume, 0.0)
        assertEquals(QueueDefaults.ICON, manual.icon)
        assertEquals(QueueDefaults.FOREGROUND_COLOR, manual.foregroundColor)
        assertEquals(emptyList<ContractIssue>(), issues)
    }

    @Test
    fun `legacy docs without orgId and siteId never reach an operator`() = runTest {
        val queues = queuesOf(operatorA)

        assertTrue(queues.none { it.siteId == null })
    }

    @Test
    fun `numbers arrive as Long or Double`() = runTest {
        source.setField(BALCAO_A, "number", 98.0)
        source.setField(BALCAO_B, "number", 3L)
        source.setField(BALCAO_B, "soundVolume", 0L)

        val queues = queuesOf(operatorA)

        assertEquals(98, queues[0].number)
        assertEquals(3, queues[1].number)
        assertEquals(0.0, queues[1].soundVolume, 0.0)
        assertEquals(emptyList<ContractIssue>(), issues)
    }

    @Test
    fun `a broken field is reported and defaulted, and the queue stays`() = runTest {
        source.setField(BALCAO_A, "number", "forty-two")

        val queues = queuesOf(operatorA)

        assertEquals(QueueDefaults.NUMBER, queues.single { it.id == BALCAO_A }.number)
        assertEquals(listOf("queues/$BALCAO_A.number"), issues.map { it.path })
    }

    @Test
    fun `a site without queues is an empty list`() = runTest {
        val queues = queuesOf(OperatorScope(StaffRole.Operator, ORG_A, listOf("site_empty_1")))

        assertEquals(emptyList<Queue>(), queues)
    }

    @Test
    fun `a permission denial is an error, never an empty list`() = runTest {
        source.readFailure = QueueError.PermissionDenied

        val states = repository.observe(operatorA).take(2).toList()

        assertEquals(listOf(QueueListState.Loading, QueueListState.Failed(QueueError.PermissionDenied)), states)
    }

    @Test
    fun `the list follows changes made elsewhere`() = runTest {
        val states = mutableListOf<QueueListState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observe(operatorA).toList(states) }

        source.setField(BALCAO_A, "number", 50L)

        val numbers = states.filterIsInstance<QueueListState.Data>().map { it.queues.first { q -> q.id == BALCAO_A }.number }
        assertEquals(listOf(42, 50), numbers)
    }

    @Test
    fun `setNumber is a plain update of number as a Long`() {
        repository.setNumber(BALCAO_A, 0)

        assertEquals(listOf(BALCAO_A to 0L), source.writes)
        assertEquals(0L, source.number(BALCAO_A))
    }

    @Test
    fun `a rejected write reaches the caller typed`() {
        source.writeFailure = QueueError.PermissionDenied
        val failures = mutableListOf<QueueError>()

        repository.setNumber(CONSULTAS, 1) { failures += it }

        assertEquals(listOf<QueueError>(QueueError.PermissionDenied), failures)
    }

    @Test
    fun `Firestore codes map to the errors the UI tells apart`() {
        assertEquals(QueueError.PermissionDenied, queueErrorOf("PERMISSION_DENIED", "x"))
        assertEquals(QueueError.PermissionDenied, queueErrorOf("UNAUTHENTICATED", null))
        assertEquals(QueueError.NotFound, queueErrorOf("NOT_FOUND", "gone"))
        assertEquals(QueueError.Other("backend down"), queueErrorOf("UNAVAILABLE", "backend down"))
        assertEquals(QueueError.Other("INTERNAL"), queueErrorOf("INTERNAL", ""))
    }
}
