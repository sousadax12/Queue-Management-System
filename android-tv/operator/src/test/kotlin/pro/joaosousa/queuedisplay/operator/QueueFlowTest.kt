package pro.joaosousa.queuedisplay.operator

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth.Companion.operatorClaims
import pro.joaosousa.queuedisplay.operator.auth.OperatorAuth
import pro.joaosousa.queuedisplay.operator.auth.OperatorAuthModule
import pro.joaosousa.queuedisplay.operator.queues.FakeChime
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ANALISES
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.BALCAO_A
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.CONSULTAS
import pro.joaosousa.queuedisplay.operator.queues.FakeSelectedQueueStore
import pro.joaosousa.queuedisplay.operator.queues.QueueError
import pro.joaosousa.queuedisplay.operator.ui.TestTags

/**
 * Setup → home → advancing → Back, with key events only, as an operator with a remote would. The
 * session is restored (an org B operator: Análises at 99 and Consultas at 0 in the fixtures).
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(OperatorAuthModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w1280dp-h720dp-land")
class QueueFlowTest {

    @BindValue
    @JvmField
    val auth: OperatorAuth = FakeOperatorAuth().apply { restore("op@example.com", operatorClaims()) }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<OperatorActivity>()

    @Inject
    lateinit var source: FakeQueueSource

    @Inject
    lateinit var store: FakeSelectedQueueStore

    @Inject
    lateinit var chime: FakeChime

    @Before
    fun inject() = hiltRule.inject()

    private fun press(tag: String, key: Key) {
        composeRule.onNodeWithTag(tag).performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun homeNumber(queueId: String, number: Int) {
        composeRule.onNode(hasText(number.toString()) and hasAnyAncestor(hasTestTag(TestTags.homeTile(queueId))))
            .assertExists()
    }

    @Test
    fun setupListsOnlyTheOperatorsSiteQueues() {
        composeRule.onNodeWithText("Select a queue:").assertExists()
        composeRule.onNodeWithTag(TestTags.setupTile(CONSULTAS)).assertExists()
        composeRule.onNodeWithTag(TestTags.setupTile(ANALISES)).assertExists()
        composeRule.onNodeWithTag(TestTags.setupTile(BALCAO_A)).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.setupTile(ANALISES)).assertIsFocused()
    }

    @Test
    fun selectAdvanceWrapAndBack() {
        // D-pad right moves the highlight, select picks.
        press(TestTags.setupTile(ANALISES), Key.DirectionRight)
        composeRule.onNodeWithTag(TestTags.setupTile(CONSULTAS)).assertIsFocused()
        press(TestTags.setupTile(CONSULTAS), Key.DirectionCenter)

        composeRule.onNodeWithTag(TestTags.HOME).assertIsFocused()
        assertEquals(CONSULTAS, store.selected.value)
        homeNumber(ANALISES, 99)
        homeNumber(CONSULTAS, 0)

        // 0 → 1 → 0 → 99: right is +1, left is −1, with the wrap.
        press(TestTags.HOME, Key.DirectionRight)
        homeNumber(CONSULTAS, 1)
        press(TestTags.HOME, Key.DirectionLeft)
        press(TestTags.HOME, Key.DirectionLeft)
        homeNumber(CONSULTAS, 99)
        homeNumber(ANALISES, 99)
        assertEquals(listOf(CONSULTAS to 1L, CONSULTAS to 0L, CONSULTAS to 99L), source.writes)
        assertEquals(listOf(1.0, 1.0, 1.0), chime.plays)

        // Back returns to setup and forgets the queue.
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select a queue:").assertExists()
        assertNull(store.selected.value)
    }

    @Test
    fun aRememberedQueueOpensOnHome() {
        composeRule.runOnIdle { store.selected.value = ANALISES }
        composeRule.waitForIdle()

        // 99 → 0.
        composeRule.onNodeWithTag(TestTags.HOME).assertIsFocused()
        press(TestTags.HOME, Key.DirectionRight)
        homeNumber(ANALISES, 0)
        assertEquals(listOf(ANALISES to 0L), source.writes)
    }

    @Test
    fun aRejectedWriteShowsAccessDenied() {
        composeRule.runOnIdle {
            store.selected.value = CONSULTAS
            source.writeFailure = QueueError.PermissionDenied
        }
        composeRule.waitForIdle()

        press(TestTags.HOME, Key.DirectionRight)

        composeRule.onNodeWithTag(TestTags.WRITE_ERROR).assertExists()
        composeRule.onNodeWithText("Access denied: the number was not saved.").assertExists()
    }
}
