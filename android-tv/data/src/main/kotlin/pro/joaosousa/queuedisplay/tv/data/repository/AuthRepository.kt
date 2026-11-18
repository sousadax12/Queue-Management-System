package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.Flow
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorIdentity

/** Who the app is signed in as. Anonymous is the box/legacy path; staff carry claims. */
sealed interface AuthState {
    /** Firebase has not answered yet. */
    data object Unknown : AuthState

    /** Signed in, but with no staff claim: can read nothing that matters. */
    data class Anonymous(val uid: String) : AuthState

    data class Staff(val identity: OperatorIdentity) : AuthState
}

interface AuthRepository {

    val authState: Flow<AuthState>

    /** The box/legacy path. Keeps working until rules v1 and the claims rollout land. */
    suspend fun signInAnonymously()

    /** Email/password, then reads the role and sites off the ID token's claims. */
    suspend fun signIn(email: String, password: String): Result<OperatorIdentity>

    suspend fun signOut()
}
