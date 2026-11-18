package pro.joaosousa.queueoperator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.joaosousa.queueoperator.ui.OperatorEvent
import pro.joaosousa.queueoperator.ui.OperatorScreen

/**
 * The whole operator flow, exercised on the JVM: the holder takes the four
 * repositories as interfaces, so nothing here needs Firebase, Firestore or an
 * emulator.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperatorStateHolderTest {

    private val auth = FakeAuthRepository()
    private val config = FakeConfigRepository()
    private val queues = FakeQueueRepository()
    private val rss = FakeRssRepository()

    private fun TestScope.holder() = OperatorStateHolder(
        auth = auth,
        config = config,
        queues = queues,
        rss = rss,
        // backgroundScope: its children (the endless clock) are cancelled when the test ends.
        scope = backgroundScope,
        clock = { 1_700_000_000_000L },
        clockTickMillis = 60_000L,
    )

    @Test
    fun `sites from the contract fixtures arrive and clear the spinner`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        runCurrent()

        assertEquals(1, holder.state.value.sites.size)
        assertEquals("Loja Porto Centro", holder.state.value.sites.first().name)
        assertEquals(false, holder.state.value.busy)
        assertEquals(OperatorScreen.SETUP, holder.state.value.screen)
    }

    @Test
    fun `choosing a site offers its default screen and preselects it`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        runCurrent()

        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        runCurrent()

        assertEquals(FakeConfigRepository.SCREEN_ID, holder.state.value.selectedScreenId)
        assertEquals(listOf("Lobby left"), holder.state.value.screens.map { it.name })
    }

    @Test
    fun `setup without a site is refused, and with one it moves to login`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        runCurrent()

        holder.onEvent(OperatorEvent.ConfirmSetup)
        runCurrent()
        assertEquals(OperatorScreen.SETUP, holder.state.value.screen)
        assertNotNull(holder.state.value.error)

        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        runCurrent()
        assertEquals(OperatorScreen.LOGIN, holder.state.value.screen)
    }

    @Test
    fun `signing in anonymously reaches home with the site's queues and a selection`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        runCurrent()
        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        holder.onEvent(OperatorEvent.ContinueAnonymously)
        runCurrent()

        val state = holder.state.value
        assertEquals(OperatorScreen.HOME, state.screen)
        assertEquals(1, auth.anonymousSignIns)
        assertEquals(1, state.queues.size)
        assertEquals("q_balcao_a_P4tc", state.selectedQueueId)
        assertEquals(1_700_000_000_000L, state.nowMillis)
    }

    @Test
    fun `the staff sign-in path carries the claim's identity into the state`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        runCurrent()
        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        holder.onEvent(OperatorEvent.EmailChanged("admin@lojas.example"))
        holder.onEvent(OperatorEvent.PasswordChanged("segredo"))
        holder.onEvent(OperatorEvent.SignIn)
        runCurrent()

        val identity = holder.state.value.identity
        assertNotNull(identity)
        assertEquals(FakeAuthRepository.STAFF.uid, identity!!.uid)
        assertEquals(pro.joaosousa.queuedisplay.tv.domain.model.OperatorRole.OPERATOR, identity.role)
        assertEquals(OperatorScreen.HOME, holder.state.value.screen)
        // The password never lingers in the state after a successful sign-in.
        assertEquals("", holder.state.value.password)
    }

    @Test
    fun `a failed sign-in shows an error and keeps the clerk on login`() = runTest(UnconfinedTestDispatcher()) {
        auth.signInResult = Result.failure(IllegalStateException("INVALID_PASSWORD"))
        val holder = holder()
        runCurrent()
        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        holder.onEvent(OperatorEvent.EmailChanged("a@b.c"))
        holder.onEvent(OperatorEvent.PasswordChanged("wrong"))
        holder.onEvent(OperatorEvent.SignIn)
        runCurrent()

        assertEquals(OperatorScreen.LOGIN, holder.state.value.screen)
        assertNotNull(holder.state.value.error)
        assertEquals(false, holder.state.value.busy)
    }

    @Test
    fun `advancing writes the next number for the selected queue`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        runCurrent()
        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        holder.onEvent(OperatorEvent.ContinueAnonymously)
        runCurrent()

        holder.onEvent(OperatorEvent.AdvanceNumber)
        runCurrent()

        assertEquals(43L, queues.written["q_balcao_a_P4tc"])
        assertEquals(43L, holder.state.value.queues.single().number)
    }

    @Test
    fun `a refused write surfaces as an error and leaves the number alone`() = runTest(UnconfinedTestDispatcher()) {
        queues.failWrites = true
        val holder = holder()
        runCurrent()
        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        holder.onEvent(OperatorEvent.ContinueAnonymously)
        runCurrent()

        holder.onEvent(OperatorEvent.AdvanceNumber)
        runCurrent()

        assertEquals(0, queues.written.size)
        assertNotNull(holder.state.value.error)
        assertEquals(42L, holder.state.value.queues.single().number)
    }

    @Test
    fun `a dead feed degrades the ticker without hiding the counter`() = runTest(UnconfinedTestDispatcher()) {
        val holder = OperatorStateHolder(
            auth = auth,
            config = FakeConfigRepository(
                sites = listOf(
                    FakeConfigRepository.site(
                        FakeConfigRepository.SITE_ID, "Loja Porto Centro",
                        FakeConfigRepository.SCREEN_ID, feedUrl = "https://www.rtp.pt/noticias/rss",
                    ),
                ),
            ),
            queues = queues,
            rss = FakeRssRepository(Result.failure(IllegalStateException("unreachable"))),
            scope = backgroundScope,
            clockTickMillis = 60_000L,
        )
        runCurrent()
        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        holder.onEvent(OperatorEvent.ContinueAnonymously)
        runCurrent()

        val state = holder.state.value
        assertEquals(OperatorScreen.HOME, state.screen)
        assertNotNull(state.tickerError)
        assertTrue(state.ticker.isEmpty())
        assertNull(state.error)                       // the counter is still usable
        assertEquals(1, state.queues.size)
    }

    @Test
    fun `signing out returns to setup and clears the queues`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        runCurrent()
        holder.onEvent(OperatorEvent.SelectSite(FakeConfigRepository.SITE_ID))
        holder.onEvent(OperatorEvent.ConfirmSetup)
        holder.onEvent(OperatorEvent.ContinueAnonymously)
        runCurrent()

        holder.onEvent(OperatorEvent.SignOut)
        runCurrent()

        assertEquals(OperatorScreen.SETUP, holder.state.value.screen)
        assertTrue(holder.state.value.queues.isEmpty())
        assertNull(holder.state.value.identity)
        assertEquals(1, auth.signOuts)
    }
}
