package pro.joaosousa.queuedisplay.operator.session

import pro.joaosousa.queuedisplay.operator.auth.AuthException

/** Why a sign-in failed, mapped from the auth error code as the Flutter login screen does. */
sealed interface LoginFailure {

    enum class Known : LoginFailure {
        WrongCredentials,
        InvalidEmail,
        UserDisabled,
        TooManyRequests,
        NoNetwork,
    }

    /** Any other error: shown as its message, or its code when it has none. */
    data class Other(val text: String) : LoginFailure

    companion object {
        fun from(code: String, message: String?): LoginFailure = when (code) {
            "invalid-credential", "user-not-found", "wrong-password" -> Known.WrongCredentials
            "invalid-email" -> Known.InvalidEmail
            "user-disabled" -> Known.UserDisabled
            "too-many-requests" -> Known.TooManyRequests
            "network-request-failed" -> Known.NoNetwork
            else -> Other(message?.takeIf { it.isNotBlank() } ?: code)
        }

        fun from(e: AuthException): LoginFailure = from(e.code, e.message)
    }
}
