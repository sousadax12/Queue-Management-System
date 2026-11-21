package pro.joaosousa.queuedisplay.operator.auth

/** The signed-in Firebase user, as far as the operator app cares. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean,
)

/**
 * A failed auth call. [code] uses FlutterFire's spelling (`invalid-credential`,
 * `network-request-failed`, ...), so the messages match the Flutter login screen.
 */
class AuthException(
    val code: String,
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Email/password Firebase Auth, behind a seam so the session runs in JVM tests.
 * Every suspend call throws [AuthException] on failure, never a Firebase type.
 */
interface OperatorAuth {

    /** The restored or signed-in user, or null. */
    fun currentUser(): AuthUser?

    suspend fun signIn(email: String, password: String): AuthUser

    /**
     * The current user's ID token claims, including `email_verified` and the custom
     * `{role, org, sites}`. With [forceRefresh], a fresh token is fetched, so claims
     * granted since the last sign-in are seen.
     */
    suspend fun claims(forceRefresh: Boolean): Map<String, Any?>

    fun signOut()
}
