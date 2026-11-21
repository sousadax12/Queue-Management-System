package pro.joaosousa.queuedisplay.operator.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth.Companion.ORG
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth.Companion.SITE
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth.Companion.operatorClaims
import pro.joaosousa.queuedisplay.operator.FakeOperatorAuth.Companion.roleLessClaims
import pro.joaosousa.queuedisplay.operator.auth.AuthException
import pro.joaosousa.queuedisplay.tv.domain.operator.OperatorScope
import pro.joaosousa.queuedisplay.tv.domain.operator.StaffRole

class OperatorSessionTest {

    private val auth = FakeOperatorAuth()
    private val session = OperatorSession(auth)

    private val operatorScope = OperatorScope(StaffRole.Operator, ORG, listOf(SITE))

    // --- start-up (F1) ---

    @Test
    fun `starts signed out without a user`() = runTest {
        session.start()

        assertEquals(SessionState.SignedOut(), session.state.value)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun `an anonymous user is signed out at start-up`() = runTest {
        auth.restore("anon", claims = emptyMap(), anonymous = true)

        session.start()

        assertEquals(SessionState.SignedOut(), session.state.value)
        assertEquals(1, auth.signOuts)
        assertNull(auth.user)
        assertEquals(emptyList<Boolean>(), auth.claimRequests)
    }

    @Test
    fun `restores an operator session from its cached token`() = runTest {
        auth.restore("op@example.com", operatorClaims())

        session.start()

        assertEquals(SessionState.Ready(operatorScope, "op@example.com"), session.state.value)
        assertEquals(listOf(false), auth.claimRequests)
    }

    @Test
    fun `a restored account without access is signed out to the login form`() = runTest {
        auth.restore("nobody@example.com", roleLessClaims)

        session.start()

        assertEquals(SessionState.SignedOut(), session.state.value)
        assertEquals(1, auth.signOuts)
    }

    @Test
    fun `a restored unverified account is signed out`() = runTest {
        auth.restore("op@example.com", operatorClaims(verified = false))

        session.start()

        assertEquals(SessionState.SignedOut(), session.state.value)
        assertEquals(1, auth.signOuts)
    }

    @Test
    fun `a start-up token error keeps the session and can be retried`() = runTest {
        auth.restore("op@example.com", operatorClaims())
        auth.claimsFailure = AuthException("network-request-failed", "A network error occurred")

        session.start()

        assertEquals(SessionState.StartFailed("A network error occurred"), session.state.value)
        assertEquals(0, auth.signOuts)

        auth.claimsFailure = null
        session.start()
        assertEquals(SessionState.Ready(operatorScope, "op@example.com"), session.state.value)
    }

    // --- sign-in (F3) ---

    @Test
    fun `signs in, force-refreshes the token and is ready`() = runTest {
        auth.accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims())
        session.start()

        session.signIn("  op@example.com ", "secret")

        assertEquals(SessionState.Ready(operatorScope, "op@example.com"), session.state.value)
        assertEquals(listOf("op@example.com" to "secret"), auth.signIns)
        assertEquals(listOf(true), auth.claimRequests)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun `claims granted after the cached token are seen thanks to the refresh`() = runTest {
        // The fake only returns the claims on a refresh, as a stale cached token would.
        val stale = object : pro.joaosousa.queuedisplay.operator.auth.OperatorAuth by auth {
            override suspend fun claims(forceRefresh: Boolean): Map<String, Any?> =
                if (forceRefresh) auth.claims(true) else roleLessClaims
        }
        auth.accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims())
        val session = OperatorSession(stale)

        session.signIn("op@example.com", "secret")

        assertEquals(SessionState.Ready(operatorScope, "op@example.com"), session.state.value)
    }

    @Test
    fun `an unverified email gets no access and is signed out`() = runTest {
        auth.accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims(verified = false))

        session.signIn("op@example.com", "secret")

        assertEquals(SessionState.NoAccess(NoAccessReason.EmailNotVerified), session.state.value)
        assertEquals(1, auth.signOuts)
        assertNull(auth.user)
    }

    @Test
    fun `a missing email_verified claim counts as unverified`() = runTest {
        auth.accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims() - "email_verified")

        session.signIn("op@example.com", "secret")

        assertEquals(SessionState.NoAccess(NoAccessReason.EmailNotVerified), session.state.value)
    }

    @Test
    fun `an account without a role gets no access and is signed out`() = runTest {
        auth.accounts["nobody@example.com"] = FakeOperatorAuth.Account("secret", roleLessClaims)

        session.signIn("nobody@example.com", "secret")

        assertEquals(SessionState.NoAccess(NoAccessReason.NoOperatorRole), session.state.value)
        assertEquals(1, auth.signOuts)
        assertNull(auth.user)
    }

    @Test
    fun `an operator without sites gets no access`() = runTest {
        auth.accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims() + ("sites" to emptyList<String>()))

        session.signIn("op@example.com", "secret")

        assertEquals(SessionState.NoAccess(NoAccessReason.NoOperatorRole), session.state.value)
    }

    @Test
    fun `acknowledging no access returns to the login form`() = runTest {
        auth.accounts["nobody@example.com"] = FakeOperatorAuth.Account("secret", roleLessClaims)
        session.signIn("nobody@example.com", "secret")

        session.acknowledge()

        assertEquals(SessionState.SignedOut(), session.state.value)
    }

    @Test
    fun `a wrong password is a login failure`() = runTest {
        auth.accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims())

        session.signIn("op@example.com", "wrong")

        assertEquals(SessionState.SignedOut(LoginFailure.Known.WrongCredentials), session.state.value)

        session.acknowledge()
        assertEquals(SessionState.SignedOut(), session.state.value)
    }

    @Test
    fun `a failed token refresh after sign-in signs out and reports the error`() = runTest {
        auth.accounts["op@example.com"] = FakeOperatorAuth.Account("secret", operatorClaims())
        auth.claimsFailure = AuthException("network-request-failed", "A network error occurred")

        session.signIn("op@example.com", "secret")

        assertEquals(SessionState.SignedOut(LoginFailure.Known.NoNetwork), session.state.value)
        assertEquals(1, auth.signOuts)
        assertNull(auth.user)
    }

    // --- sign-out ---

    @Test
    fun `signs out back to the login form`() = runTest {
        auth.restore("op@example.com", operatorClaims())
        session.start()

        session.signOut()

        assertEquals(SessionState.SignedOut(), session.state.value)
        assertNull(auth.user)
    }
}
