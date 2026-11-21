package pro.joaosousa.queuedisplay.operator

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth.Companion.operatorClaims
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth.Companion.roleLessClaims
import pro.joaosousa.queuedisplay.operator.auth.OperatorAuth
import pro.joaosousa.queuedisplay.operator.auth.OperatorAuthModule
import pro.joaosousa.queuedisplay.operator.queues.FakeQueueSource.Companion.ANALISES
import pro.joaosousa.queuedisplay.operator.ui.TestTags

/**
 * Drives the real activity with key events only, as an operator with a remote would:
 * Enter on the email moves to the password, Enter on the password submits.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(OperatorAuthModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w1280dp-h720dp-land")
class LoginFlowTest {

    private val fake = FakeOperatorAuth().apply {
        accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims())
        accounts["nobody@example.com"] = FakeOperatorAuth.Account("secret", roleLessClaims)
    }

    @BindValue
    @JvmField
    val auth: OperatorAuth = fake

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<OperatorActivity>()

    private fun typeAndPressEnter(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).assertIsFocused()
        composeRule.onNodeWithTag(tag).performTextInput(text)
        composeRule.onNodeWithTag(tag).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
    }

    private fun signInWithRemote(email: String, password: String) {
        typeAndPressEnter(TestTags.EMAIL, email)
        composeRule.onNodeWithTag(TestTags.PASSWORD).assertIsFocused()
        typeAndPressEnter(TestTags.PASSWORD, password)
    }

    @Test
    fun operatorReachesSetupWithTheRemote() {
        composeRule.onNodeWithText("Operator login").assertExists()

        signInWithRemote(" op@example.com ", "secret")

        composeRule.onNodeWithText("Select a queue:").assertExists()
        composeRule.onNodeWithText("Signed in as op@example.com").assertExists()
        assertEquals(listOf("op@example.com" to "secret"), fake.signIns)
        assertEquals(listOf(true), fake.claimRequests)
    }

    @Test
    fun roleLessAccountSeesNoOperatorAccess() {
        signInWithRemote("nobody@example.com", "secret")

        composeRule.onNodeWithText("No operator access").assertExists()
        assertNull(fake.user)

        // OK is focused: the remote's Enter returns to the login form.
        composeRule.onNodeWithTag(TestTags.MESSAGE_ACTION).assertIsFocused()
        composeRule.onNodeWithTag(TestTags.MESSAGE_ACTION).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Operator login").assertExists()
    }

    @Test
    fun wrongPasswordShowsTheFlutterMessage() {
        signInWithRemote("op@example.com", "nope")

        composeRule.onNodeWithText("Login failed").assertExists()
        composeRule.onNodeWithText("Wrong email or password.").assertExists()
    }

    @Test
    fun enterOnAnEmptyFormShowsTheValidationErrors() {
        composeRule.onNodeWithTag(TestTags.EMAIL).performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag(TestTags.PASSWORD).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Please enter an email").assertExists()
        composeRule.onNodeWithText("Please enter a password").assertExists()
        composeRule.onNodeWithTag(TestTags.EMAIL).assertIsFocused()
        assertEquals(emptyList<Pair<String, String>>(), fake.signIns)
    }

    @Test
    fun signOutReturnsToLogin() {
        signInWithRemote("op@example.com", "secret")

        // Setup focuses the first queue; D-pad up reaches Sign out.
        composeRule.onNodeWithTag(TestTags.setupTile(ANALISES)).assertIsFocused()
        composeRule.onNodeWithTag(TestTags.setupTile(ANALISES)).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.SIGN_OUT).assertIsFocused()
        composeRule.onNodeWithTag(TestTags.SIGN_OUT).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Operator login").assertExists()
        assertNull(fake.user)
    }
}
