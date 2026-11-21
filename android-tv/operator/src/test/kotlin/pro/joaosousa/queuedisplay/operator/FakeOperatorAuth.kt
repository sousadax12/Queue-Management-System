package pro.joaosousa.queuedisplay.operator

import pro.joaosousa.queuedisplay.operator.auth.AuthException
import pro.joaosousa.queuedisplay.operator.auth.AuthUser
import pro.joaosousa.queuedisplay.operator.auth.OperatorAuth

/** An in-memory Auth: accounts by email, each with a password and the claims its token carries. */
class FakeOperatorAuth : OperatorAuth {

    data class Account(val password: String, val claims: Map<String, Any?>)

    val accounts = mutableMapOf<String, Account>()
    var user: AuthUser? = null

    /** Thrown by the next [claims] call instead of returning the claims. */
    var claimsFailure: AuthException? = null

    /** Thrown by [signIn] before it checks the password. */
    var signInFailure: AuthException? = null

    val signIns = mutableListOf<Pair<String, String>>()
    val claimRequests = mutableListOf<Boolean>()
    var signOuts = 0
        private set

    fun restore(email: String, claims: Map<String, Any?>, anonymous: Boolean = false) {
        accounts[email] = Account(password = "unused", claims = claims)
        user = AuthUser(uid = "uid-$email", email = email.takeUnless { anonymous }, isAnonymous = anonymous)
    }

    override fun currentUser(): AuthUser? = user

    override suspend fun signIn(email: String, password: String): AuthUser {
        signIns += email to password
        signInFailure?.let { throw it }
        val account = accounts[email]
        if (account == null || account.password != password) {
            throw AuthException("invalid-credential", "The supplied auth credential is incorrect.")
        }
        return AuthUser(uid = "uid-$email", email = email, isAnonymous = false).also { user = it }
    }

    override suspend fun claims(forceRefresh: Boolean): Map<String, Any?> {
        claimRequests += forceRefresh
        claimsFailure?.let { throw it }
        val email = checkNotNull(user) { "claims() without a user" }.email
        return accounts[email]?.claims.orEmpty()
    }

    override fun signOut() {
        signOuts++
        user = null
    }

    companion object {
        const val ORG = "org_clinica_K9xP"
        const val SITE = "site_lisboa_Wd2e"

        fun operatorClaims(verified: Boolean = true): Map<String, Any?> = mapOf(
            "role" to "operator",
            "org" to ORG,
            "sites" to listOf(SITE),
            "email_verified" to verified,
        )

        val roleLessClaims: Map<String, Any?> = mapOf("email_verified" to true)
    }
}
