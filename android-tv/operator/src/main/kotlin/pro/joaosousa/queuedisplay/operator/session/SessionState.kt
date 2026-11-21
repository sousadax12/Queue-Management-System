package pro.joaosousa.queuedisplay.operator.session

import pro.joaosousa.queuedisplay.tv.domain.operator.OperatorScope

/** Where the operator's session is. The UI renders one screen per state. */
sealed interface SessionState {

    /** Restoring the session from a previous run (Flutter's start-up spinner). */
    data object Starting : SessionState

    /** No usable account: show the login form, with the last [failure] if sign-in just failed. */
    data class SignedOut(val failure: LoginFailure? = null) : SessionState

    data object SigningIn : SessionState

    /** Signed in, but the account can't operate anything. The account is already signed out. */
    data class NoAccess(val reason: NoAccessReason) : SessionState

    /** Signed in with a usable [scope]. */
    data class Ready(val scope: OperatorScope, val email: String?) : SessionState

    /** Restoring a session failed (for example no network to refresh an expired token). */
    data class StartFailed(val message: String) : SessionState
}

enum class NoAccessReason {
    /** No role, an unknown role, or a role without the org or sites it needs. */
    NoOperatorRole,

    /** `email_verified` is false: the rules ignore the account's role (`firestore.rules`). */
    EmailNotVerified,
}
