package pro.joaosousa.queuedisplay.tv.domain.operator

/** The roles `firestore.rules` knows (custom claim `role`, set by the portal or `grant-operator`). */
enum class StaffRole(val claim: String) {
    PlatformAdmin("platformAdmin"),
    OrgAdmin("orgAdmin"),
    Manager("manager"),
    Operator("operator"),
    ;

    companion object {
        /** The role whose claim value is exactly [name], or null. */
        fun named(name: Any?): StaffRole? = entries.firstOrNull { it.claim == name }
    }
}

/**
 * What the signed-in account may operate, read from its custom claims `{role, org, sites}`.
 *
 * A port of `lib/auth/operator_scope.dart`. It mirrors `operates()` in `firestore.rules`:
 * platform admins act on every queue, org admins on their org's queues, managers and operators
 * on the queues of their sites. The rules reject a query that could return a queue outside that
 * scope, so the queue query always filters by [orgFilter] and [siteFilter].
 */
data class OperatorScope(
    val role: StaffRole,
    val orgId: String? = null,
    val sites: List<String> = emptyList(),
) {

    /** `orgId ==` filter for the queue query; null for platform admins (every org). */
    val orgFilter: String?
        get() = if (role == StaffRole.PlatformAdmin) null else orgId

    /** `siteId in` filter for the queue query; null when the whole org (or every org) is in scope. */
    val siteFilter: List<String>?
        get() = when (role) {
            StaffRole.PlatformAdmin, StaffRole.OrgAdmin -> null
            StaffRole.Manager, StaffRole.Operator -> sites.take(MAX_SITES_PER_QUERY)
        }

    companion object {
        /** Firestore caps `whereIn` at 30 values. */
        const val MAX_SITES_PER_QUERY = 30

        /**
         * The scope of an ID token's claims, or null when they grant no access (no role, an
         * unknown role, or a role without the org or sites it needs).
         */
        fun fromClaims(claims: Map<String, Any?>?): OperatorScope? {
            if (claims == null) return null
            val role = StaffRole.named(claims["role"]) ?: return null
            val orgId = (claims["org"] as? String)?.takeIf { it.isNotEmpty() }
            val sites = (claims["sites"] as? List<*>)
                ?.filterIsInstance<String>()
                ?.filter { it.isNotEmpty() }
                .orEmpty()

            return when (role) {
                StaffRole.PlatformAdmin -> OperatorScope(role = StaffRole.PlatformAdmin)
                StaffRole.OrgAdmin -> orgId?.let { OperatorScope(role = role, orgId = it) }
                StaffRole.Manager, StaffRole.Operator ->
                    if (orgId == null || sites.isEmpty()) null else OperatorScope(role, orgId, sites)
            }
        }
    }
}
