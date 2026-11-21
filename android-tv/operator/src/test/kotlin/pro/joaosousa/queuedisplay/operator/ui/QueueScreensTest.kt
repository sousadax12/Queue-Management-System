package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pro.joaosousa.queuedisplay.operator.queues.QueueError
import pro.joaosousa.queuedisplay.operator.queues.QueueListState


/** The setup and home screens' empty and error states: "Access denied" is never "No queues". */
@RunWith(RobolectricTestRunner::class)
class QueueScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setup(queues: QueueListState, onRetry: () -> Unit = {}) = composeRule.setContent {
        OperatorTheme {
            SetupScreen(queues = queues, onSelect = {}, onRetry = onRetry, onSignOut = {})
        }
    }

    private fun home(queues: QueueListState) = composeRule.setContent {
        OperatorTheme { HomeScreen(queues = queues, writeError = null, headlines = emptyList(), tickerVelocity = 60.0, onAdvance = {}, onBack = {}) }
    }

    @Test
    fun setupSaysAccessDeniedForADeniedReadAndOffersRetry() {
        var retries = 0
        setup(QueueListState.Failed(QueueError.PermissionDenied), onRetry = { retries++ })

        composeRule.onNodeWithText("Access denied").assertExists()
        composeRule.onNodeWithTag(TestTags.QUEUES_EMPTY).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.RETRY).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, retries)
    }

    @Test
    fun setupSaysNoQueuesOnlyForAnEmptyList() {
        setup(QueueListState.Data(emptyList()))

        composeRule.onNodeWithText("No queues for this account").assertExists()
        composeRule.onNodeWithTag(TestTags.QUEUES_ERROR).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun homeSaysAccessDeniedForADeniedRead() {
        home(QueueListState.Failed(QueueError.PermissionDenied))

        composeRule.onNodeWithText("Access denied").assertExists()
        composeRule.onNodeWithTag(TestTags.QUEUES_EMPTY).assertDoesNotExist()
    }

    @Test
    fun otherErrorsShowTheirMessage() {
        home(QueueListState.Failed(QueueError.Other("backend down")))

        composeRule.onNodeWithText("Error: backend down").assertExists()
    }
}
