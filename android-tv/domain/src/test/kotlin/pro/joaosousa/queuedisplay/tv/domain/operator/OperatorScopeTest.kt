package pro.joaosousa.queuedisplay.tv.domain.operator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// A port of test/operator_scope_test.dart. Claims as the portal's media-api sets them; the same
// shapes as admin-portal/packages/contract/fixtures/users.json.
private const val ORG = "org_clinica_K9xP"
private const val SITE = "site_lisboa_Wd2e"

class OperatorScopeTest {

    private fun scopeOf(claims: Map<String, Any?>): OperatorScope {
        val scope = OperatorScope.fromClaims(claims)
        assertNotNull(scope, "expected a scope for $claims")
        return scope!!
    }

    @Test
    fun `an operator acts on the queues of its sites in its org`() {
        val scope = scopeOf(mapOf("role" to "operator", "org" to ORG, "sites" to listOf(SITE)))

        assertEquals(StaffRole.Operator, scope.role)
        assertEquals(ORG, scope.orgFilter)
        assertEquals(listOf(SITE), scope.siteFilter)
    }

    @Test
    fun `a manager is scoped like an operator`() {
        val scope = scopeOf(mapOf("role" to "manager", "org" to ORG, "sites" to listOf(SITE, "site_other_1")))

        assertEquals(ORG, scope.orgFilter)
        assertEquals(listOf(SITE, "site_other_1"), scope.siteFilter)
    }

    @Test
    fun `an org admin sees its whole org`() {
        val scope = scopeOf(mapOf("role" to "orgAdmin", "org" to ORG, "sites" to emptyList<String>()))

        assertEquals(ORG, scope.orgFilter)
        assertNull(scope.siteFilter)
    }

    @Test
    fun `a platform admin sees every org`() {
        val scope = scopeOf(mapOf("role" to "platformAdmin", "org" to null, "sites" to emptyList<String>()))

        assertNull(scope.orgFilter)
        assertNull(scope.siteFilter)
    }

    @Test
    fun `no claims, no role or an unknown role grant nothing`() {
        assertNull(OperatorScope.fromClaims(null))
        assertNull(OperatorScope.fromClaims(emptyMap()))
        assertNull(OperatorScope.fromClaims(mapOf("role" to "")))
        assertNull(OperatorScope.fromClaims(mapOf("role" to "superuser", "org" to ORG, "sites" to listOf(SITE))))
    }

    @Test
    fun `a role without the org or sites it needs grants nothing`() {
        assertNull(OperatorScope.fromClaims(mapOf("role" to "orgAdmin")))
        assertNull(OperatorScope.fromClaims(mapOf("role" to "operator", "org" to ORG, "sites" to emptyList<String>())))
        assertNull(OperatorScope.fromClaims(mapOf("role" to "operator", "sites" to listOf(SITE))))
        assertNull(OperatorScope.fromClaims(mapOf("role" to "operator", "org" to ORG, "sites" to "not-a-list")))
    }

    @Test
    fun `ignores malformed site entries`() {
        val scope = scopeOf(mapOf("role" to "operator", "org" to ORG, "sites" to listOf(SITE, 7, "", null)))

        assertEquals(listOf(SITE), scope.siteFilter)
    }

    @Test
    fun `caps the site filter at the whereIn limit`() {
        val sites = List(40) { "site_$it" }
        val scope = scopeOf(mapOf("role" to "operator", "org" to ORG, "sites" to sites))

        assertEquals(sites.take(OperatorScope.MAX_SITES_PER_QUERY), scope.siteFilter)
    }

    @Test
    fun `role names match the claim exactly`() {
        // Kotlin's enum names are PascalCase; the claim is camelCase, as in firestore.rules.
        assertNull(OperatorScope.fromClaims(mapOf("role" to "Operator", "org" to ORG, "sites" to listOf(SITE))))
        assertEquals(StaffRole.entries.toList(), StaffRole.entries.map { StaffRole.named(it.claim) })
    }
}
