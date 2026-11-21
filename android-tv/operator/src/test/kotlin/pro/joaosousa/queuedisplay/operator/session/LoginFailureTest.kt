package pro.joaosousa.queuedisplay.operator.session

import org.junit.Assert.assertEquals
import org.junit.Test
import pro.joaosousa.queuedisplay.operator.auth.FirebaseOperatorAuth

/** The codes the Flutter login screen maps (lib/pages/login_page.dart, `_messageFor`). */
class LoginFailureTest {

    @Test
    fun `maps the Flutter login screen's codes`() {
        val cases = mapOf(
            "invalid-credential" to LoginFailure.Known.WrongCredentials,
            "user-not-found" to LoginFailure.Known.WrongCredentials,
            "wrong-password" to LoginFailure.Known.WrongCredentials,
            "invalid-email" to LoginFailure.Known.InvalidEmail,
            "user-disabled" to LoginFailure.Known.UserDisabled,
            "too-many-requests" to LoginFailure.Known.TooManyRequests,
            "network-request-failed" to LoginFailure.Known.NoNetwork,
        )
        cases.forEach { (code, expected) -> assertEquals(code, expected, LoginFailure.from(code, "ignored")) }
    }

    @Test
    fun `any other code shows its message, or the code without one`() {
        assertEquals(LoginFailure.Other("Something broke"), LoginFailure.from("internal-error", "Something broke"))
        assertEquals(LoginFailure.Other("internal-error"), LoginFailure.from("internal-error", null))
        assertEquals(LoginFailure.Other("internal-error"), LoginFailure.from("internal-error", " "))
    }

    @Test
    fun `Android SDK error codes are spelled as FlutterFire spells them`() {
        val cases = mapOf(
            "ERROR_INVALID_CREDENTIAL" to "invalid-credential",
            "ERROR_USER_NOT_FOUND" to "user-not-found",
            "ERROR_WRONG_PASSWORD" to "wrong-password",
            "ERROR_INVALID_EMAIL" to "invalid-email",
            "ERROR_USER_DISABLED" to "user-disabled",
        )
        cases.forEach { (sdk, flutter) -> assertEquals(flutter, FirebaseOperatorAuth.flutterFireCode(sdk)) }
    }
}
