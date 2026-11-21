package pro.joaosousa.queuedisplay.operator.queues

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ANALISES
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.BALCAO_A
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.BALCAO_B
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.CONSULTAS
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ORG_A
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ORG_B
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.SITE_A
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.SITE_B
import pro.joaosousa.queuedisplay.operator.queues.QueuesUi.Route
import pro.joaosousa.queuedisplay.operator.session.OperatorSession
import pro.joaosousa.queuedisplay.tv.domain.operator.QueueNumber.Direction

@OptIn(ExperimentalCoroutinesApi::class)
class QueuesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = FakeOperatorAuth()
    private val session = OperatorSession(auth)
    private val source = FakeQueueSource.fromFixtures()
    private val store = FakeSelectedQueueStore()
    private val chime = FakeChime()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun claims(org: String, site: String) = mapOf(
        "role" to "operator",
        "org" to org,
        "sites" to listOf(site),
        "email_verified" to true,
    )

    /** A restored operator session of [org]/[site], then the view model. */
    private suspend fun signedIn(org: String = ORG_A, site: String = SITE_A): QueuesViewModel {
        auth.restore("op@example.com", claims(org, site))
        session.start()
        return QueuesViewModel(session, OperatorQueueRepository(source) {}, store, chime)
    }

    private fun QueuesViewModel.numberOf(queueId: String): Int =
        (ui.value.queues as QueueListState.Data).queues.single { it.id == queueId }.number

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    // --- setup and the remembered queue (F5, Q9) ---

    @Test
    fun `without a remembered queue the operator lands on setup`() = test {
        val vm = signedIn()

        assertEquals(Route.Setup, vm.ui.value.route)
        assertEquals(listOf(BALCAO_A, BALCAO_B), (vm.ui.value.queues as QueueListState.Data).queues.map { it.id })
    }

    @Test
    fun `picking a queue opens home and remembers it`() = test {
        val vm = signedIn()

        vm.select(BALCAO_B)

        assertEquals(Route.Home, vm.ui.value.route)
        assertEquals(BALCAO_B, vm.ui.value.selectedQueue?.id)
        assertEquals(BALCAO_B, store.selected.value)
    }

    @Test
    fun `a remembered queue opens on home after a restart`() = test {
        store.selected.value = BALCAO_A

        val vm = signedIn()

        assertEquals(Route.Home, vm.ui.value.route)
        assertEquals(BALCAO_A, vm.ui.value.selectedQueue?.id)
    }

    @Test
    fun `a remembered queue that is no longer in scope goes back to setup and is forgotten`() = test {
        store.selected.value = CONSULTAS // org B's queue, but an org A operator signs in

        val vm = signedIn()

        assertEquals(Route.Setup, vm.ui.value.route)
        assertNull(store.selected.value)
    }

    @Test
    fun `a queue deleted while on home returns to setup`() = test {
        val vm = signedIn()
        vm.select(BALCAO_A)

        source.remove(BALCAO_A)

        assertEquals(Route.Setup, vm.ui.value.route)
        assertNull(store.selected.value)
    }

    @Test
    fun `Back on home returns to setup and forgets the queue`() = test {
        val vm = signedIn()
        vm.select(BALCAO_A)

        vm.backToSetup()

        assertEquals(Route.Setup, vm.ui.value.route)
        assertNull(store.selected.value)
    }

    @Test
    fun `signing out forgets the queue`() = test {
        val vm = signedIn()
        vm.select(BALCAO_A)

        session.signOut()

        assertNull(store.selected.value)
        assertEquals(Route.Setup, vm.ui.value.route)
    }

    // --- advancing (F7, Q6) and the chime (F8) ---

    @Test
    fun `right is +1 and left is -1, written as a plain update`() = test {
        val vm = signedIn()
        vm.select(BALCAO_A)

        vm.advance(Direction.Up)
        assertEquals(43, vm.numberOf(BALCAO_A))
        vm.advance(Direction.Down)
        vm.advance(Direction.Down)

        assertEquals(listOf(BALCAO_A to 43L, BALCAO_A to 42L, BALCAO_A to 41L), source.writes)
        assertEquals(41, vm.numberOf(BALCAO_A))
        assertEquals(41L, source.number(BALCAO_A))
    }

    @Test
    fun `99 wraps to 0 and 0 wraps to 99`() = test {
        val vm = signedIn(ORG_B, SITE_B)

        vm.select(ANALISES) // 99
        vm.advance(Direction.Up)
        vm.select(CONSULTAS) // 0
        vm.advance(Direction.Down)

        assertEquals(listOf(ANALISES to 0L, CONSULTAS to 99L), source.writes)
        assertEquals(0, vm.numberOf(ANALISES))
        assertEquals(99, vm.numberOf(CONSULTAS))
    }

    @Test
    fun `a press builds on a change made elsewhere, not on a stale copy`() = test {
        val vm = signedIn()
        vm.select(BALCAO_A)

        source.setField(BALCAO_A, "number", 70L) // the portal, or another box
        vm.advance(Direction.Up)

        assertEquals(listOf(BALCAO_A to 71L), source.writes)
    }

    @Test
    fun `presses the listener hasn't reported yet still build on each other and show at once`() = test {
        val vm = signedIn()
        vm.select(BALCAO_A)
        source.echoWrites = false // offline-like: nothing comes back yet

        vm.advance(Direction.Up)
        vm.advance(Direction.Up)
        vm.advance(Direction.Up)

        assertEquals(listOf(43L, 44L, 45L), source.writes.map { it.second })
        assertEquals(45, vm.numberOf(BALCAO_A))
    }

    @Test
    fun `each press chimes at the queue's soundVolume`() = test {
        source.setField(BALCAO_B, "soundVolume", 0L)
        val vm = signedIn()

        vm.select(BALCAO_A)
        vm.advance(Direction.Up)
        vm.select(BALCAO_B)
        vm.advance(Direction.Down)

        assertEquals(listOf(1.0, 0.0), chime.plays)
    }

    @Test
    fun `a rejected write chimes, shows a short message and clears it`() = test {
        val vm = signedIn()
        vm.select(BALCAO_A)
        source.writeFailure = QueueError.PermissionDenied

        vm.advance(Direction.Up)

        assertEquals(1, chime.plays.size)
        assertEquals(QueueError.PermissionDenied, vm.ui.value.writeError)
        advanceTimeBy(QueuesViewModel.WRITE_ERROR_MILLIS + 1)
        runCurrent()
        assertNull(vm.ui.value.writeError)
    }

    @Test
    fun `presses before the queues load do nothing`() = test {
        source.readFailure = QueueError.PermissionDenied
        store.selected.value = BALCAO_A
        val vm = signedIn()

        vm.advance(Direction.Up)

        assertEquals(emptyList<Pair<String, Long>>(), source.writes)
        assertEquals(emptyList<Double>(), chime.plays)
    }

    // --- typed errors ---

    @Test
    fun `a denied read is Access denied, and Retry subscribes again`() = test {
        source.readFailure = QueueError.PermissionDenied
        val vm = signedIn()

        assertEquals(QueueListState.Failed(QueueError.PermissionDenied), vm.ui.value.queues)
        assertEquals(Route.Setup, vm.ui.value.route)

        source.readFailure = null
        vm.retry()

        assertEquals(2, (vm.ui.value.queues as QueueListState.Data).queues.size)
    }

    @Test
    fun `nothing is read before sign-in`() = test {
        session.start() // no user
        QueuesViewModel(session, OperatorQueueRepository(source) {}, store, chime)

        assertEquals(emptyList<Pair<String?, List<String>?>>(), source.queries)
    }
}
