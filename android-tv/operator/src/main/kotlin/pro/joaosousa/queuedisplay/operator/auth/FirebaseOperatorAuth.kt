package pro.joaosousa.queuedisplay.operator.auth

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** [OperatorAuth] over the Firebase Auth SDK. No anonymous sign-in, ever. */
class FirebaseOperatorAuth @Inject constructor(
    private val auth: FirebaseAuth,
) : OperatorAuth {

    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override suspend fun signIn(email: String, password: String): AuthUser = mapped {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
        checkNotNull(user) { "Sign-in returned no user" }.toAuthUser()
    }

    override suspend fun claims(forceRefresh: Boolean): Map<String, Any?> = mapped {
        val user = auth.currentUser ?: throw AuthException(CODE_NO_USER, "Not signed in")
        user.getIdToken(forceRefresh).await().claims
    }

    override fun signOut() = auth.signOut()

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, isAnonymous = isAnonymous)

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthException) {
        throw e
    } catch (e: FirebaseAuthException) {
        throw AuthException(flutterFireCode(e.errorCode), e.message, e)
    } catch (e: FirebaseTooManyRequestsException) {
        throw AuthException(CODE_TOO_MANY_REQUESTS, e.message, e)
    } catch (e: FirebaseNetworkException) {
        throw AuthException(CODE_NETWORK, e.message, e)
    } catch (e: FirebaseException) {
        throw AuthException(CODE_UNKNOWN, e.message, e)
    } catch (e: IllegalStateException) {
        throw AuthException(CODE_UNKNOWN, e.message, e)
    }

    companion object {
        const val CODE_NO_USER = "no-current-user"
        const val CODE_TOO_MANY_REQUESTS = "too-many-requests"
        const val CODE_NETWORK = "network-request-failed"
        const val CODE_UNKNOWN = "unknown"

        /** `ERROR_INVALID_CREDENTIAL` → `invalid-credential`, as FlutterFire spells the codes. */
        fun flutterFireCode(errorCode: String): String =
            errorCode.removePrefix("ERROR_").lowercase().replace('_', '-')
    }
}
