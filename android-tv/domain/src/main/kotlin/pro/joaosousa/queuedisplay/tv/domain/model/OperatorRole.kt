package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * The staff roles the portal sets as Firebase custom claims (design: "Auth and
 * rules: what pairing needs"). An anonymous sign-in never carries one.
 */
enum class OperatorRole(val raw: String) {
    PLATFORM_ADMIN("platformAdmin"),
    ORG_ADMIN("orgAdmin"),
    MANAGER("manager"),
    OPERATOR("operator");

    companion object {
        fun fromClaims(role: String?): OperatorRole? = entries.firstOrNull { it.raw == role }
    }
}

/** What a signed-in operator is allowed to touch, taken only from the verified token. */
data class OperatorIdentity(
    val uid: String,
    val email: String?,
    val role: OperatorRole?,
    /** Site ids the claim grants, already split on the claim's separator. */
    val siteIds: List<String>,
) {
    val isStaff: Boolean get() = role != null
}
