package pro.joaosousa.queuedisplay.operator.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pro.joaosousa.queuedisplay.operator.auth.AuthException
import pro.joaosousa.queuedisplay.operator.auth.AuthUser
import pro.joaosousa.queuedisplay.operator.auth.OperatorAuth
import pro.joaosousa.queuedisplay.tv.domain.operator.OperatorScope

/**
 * The operator's sign-in session (design F1/F3, "Architecture: Session").
 *
 * - There is no anonymous sign-in. A restored anonymous session (from a pre-#6 Flutter build)
 *   is signed out.
 * - A scope comes only from [OperatorScope.fromClaims], and only for a verified email: the rules
 *   ignore the role of an unverified account.
 * - After an email/password sign-in the ID token is force-refreshed, so claims granted since the
 *   last sign-in are seen.
 * - An account without access is signed out at once. After a sign-in it lands on [SessionState.NoAccess];
 *   at start-up it lands on the login form, as in Flutter.
 */
@Singleton
class OperatorSession @Inject constructor(
    private val auth: OperatorAuth,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Starting)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** Restores the session left by a previous run. */
    suspend fun start() = mutex.withLock {
        _state.value = SessionState.Starting
        val user = auth.currentUser()
        _state.value = when {
            user == null -> SessionState.SignedOut()
            user.isAnonymous -> {
                auth.signOut()
                SessionState.SignedOut()
            }
            else -> try {
                when (val access = accessOf(auth.claims(forceRefresh = false))) {
                    is Access.Granted -> SessionState.Ready(access.scope, user.email)
                    is Access.Denied -> {
                        auth.signOut()
                        SessionState.SignedOut()
                    }
                }
            } catch (e: AuthException) {
                // Keep the session: this is usually a box that booted before its network.
                SessionState.StartFailed(e.message ?: e.code)
            }
        }
    }

    /** Signs in with email and password. Ignored while a sign-in is already running. */
    suspend fun signIn(email: String, password: String) {
        if (_state.value == SessionState.SigningIn) return
        mutex.withLock {
            _state.value = SessionState.SigningIn
            _state.value = try {
                val user = auth.signIn(email.trim(), password)
                resolveAfterSignIn(user)
            } catch (e: AuthException) {
                // Sign-in may have succeeded and only the token refresh failed: don't keep a
                // half-checked session around.
                auth.signOut()
                SessionState.SignedOut(LoginFailure.from(e))
            }
        }
    }

    /** Signs out and returns to the login form. */
    suspend fun signOut() = mutex.withLock {
        auth.signOut()
        _state.value = SessionState.SignedOut()
    }

    /** Dismisses the no-access screen or a login error. */
    fun acknowledge() {
        _state.value = when (val current = _state.value) {
            is SessionState.NoAccess -> SessionState.SignedOut()
            is SessionState.SignedOut -> current.copy(failure = null)
            else -> current
        }
    }

    private suspend fun resolveAfterSignIn(user: AuthUser): SessionState {
        if (user.isAnonymous) {
            auth.signOut()
            return SessionState.SignedOut()
        }
        return when (val access = accessOf(auth.claims(forceRefresh = true))) {
            is Access.Granted -> SessionState.Ready(access.scope, user.email)
            is Access.Denied -> {
                auth.signOut()
                SessionState.NoAccess(access.reason)
            }
        }
    }

    private sealed interface Access {
        data class Granted(val scope: OperatorScope) : Access
        data class Denied(val reason: NoAccessReason) : Access
    }

    private fun accessOf(claims: Map<String, Any?>): Access {
        if (claims[EMAIL_VERIFIED] != true) return Access.Denied(NoAccessReason.EmailNotVerified)
        val scope = OperatorScope.fromClaims(claims) ?: return Access.Denied(NoAccessReason.NoOperatorRole)
        return Access.Granted(scope)
    }

    private companion object {
        const val EMAIL_VERIFIED = "email_verified"
    }
}
