package pro.joaosousa.queuedisplay.tv.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.tasks.await
import pro.joaosousa.queuedisplay.tv.data.firestore.Values
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorIdentity
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorRole

/**
 * Firebase Auth, supporting both sign-in paths the port keeps (design D8).
 *
 * The role and the site grant are read from the **ID token's custom claims**, never from a request
 * value and never from Firestore: the token is what the security rules verify, so anything else
 * could describe permissions the rules would refuse.
 */
class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { current -> trySend(current.currentUser) }
        // Fires immediately with the restored session, then on every later change.
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.mapLatest { user -> authStateOf(user) }

    override suspend fun signInAnonymously() {
        // No Result in the frozen signature: a failure surfaces as an exception the caller shows.
        auth.signInAnonymously().await()
    }

    override suspend fun signIn(email: String, password: String): Result<OperatorIdentity> =
        runCatching {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
                ?: error("Firebase accepted $email but returned no user")
            identityOf(user)
        }

    override suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Signed out reports [AuthState.Unknown]: the frozen state has no "signed out" case, and the
     * caller only needs to know there is no staff session to act on.
     */
    private suspend fun authStateOf(user: FirebaseUser?): AuthState = when {
        user == null -> AuthState.Unknown
        // An anonymous session never carries claims, so skip the token round-trip for it.
        user.isAnonymous -> AuthState.Anonymous(user.uid)
        else -> {
            val identity = readIdentity(user) ?: unsignedIdentity(user)
            if (identity.isStaff) AuthState.Staff(identity) else AuthState.Anonymous(user.uid)
        }
    }

    private suspend fun identityOf(user: FirebaseUser): OperatorIdentity =
        readIdentity(user) ?: unsignedIdentity(user)

    /**
     * A token read can fail offline. Returning null lets the caller fall back to a claim-less
     * identity instead of tearing down the auth flow; the next snapshot retries.
     */
    private suspend fun readIdentity(user: FirebaseUser): OperatorIdentity? = runCatching {
        // A restored session may hold a token minted before the role claim was granted.
        val token = user.getIdToken(false).await()
        TokenClaims.identity(uid = user.uid, claims = token.claims)
    }.getOrNull()

    private fun unsignedIdentity(user: FirebaseUser) =
        OperatorIdentity(uid = user.uid, email = user.email, role = null, siteIds = emptyList())
}

/**
 * Maps the ID token's custom claims onto [OperatorIdentity].
 *
 * The portal writes `role` and `sites`; `sites` has been seen both as a comma-separated string and
 * as a JSON array, so both shapes are accepted and a user with no `role` claim still yields an
 * identity (with a null role) — anonymous and un-hardened deployments exist (design D8).
 */
internal object TokenClaims {

    fun identity(uid: String, claims: Map<String, Any?>): OperatorIdentity = OperatorIdentity(
        uid = uid,
        email = Values.string(claims[CLAIM_EMAIL]),
        role = OperatorRole.fromClaims(Values.string(claims[CLAIM_ROLE])),
        siteIds = siteIds(claims[CLAIM_SITES]),
    )

    /** `"a,b"`, `["a","b"]` and a real list claim all mean the same thing. */
    fun siteIds(claim: Any?): List<String> = when (claim) {
        is List<*> -> claim.mapNotNull { it as? String }
        // No JSON parser on the app path (gson is a test dependency); a flat array of ids only
        // needs the brackets and the quotes removed.
        is String -> claim.removeSurrounding("[", "]").split(',')
        else -> emptyList()
    }
        .map { it.trim().trim('"', '\'') }
        .filter { it.isNotBlank() }
        .distinct()

    private const val CLAIM_EMAIL = "email"
    private const val CLAIM_ROLE = "role"
    private const val CLAIM_SITES = "sites"
}