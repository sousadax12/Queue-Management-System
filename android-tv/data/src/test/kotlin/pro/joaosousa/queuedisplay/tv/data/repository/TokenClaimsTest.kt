package pro.joaosousa.queuedisplay.tv.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorRole

/**
 * The claim shapes the portal actually produces (`users.json` carries `role` + a `sites` array),
 * plus the string form a claims migration would write.
 */
class TokenClaimsTest {

    @Test
    fun `a full claim set becomes a staff identity`() {
        val identity = TokenClaims.identity(
            uid = "uid_clinica_operator_Z7tw",
            claims = mapOf(
                "email" to "balcao@clinica.example",
                "role" to "operator",
                "sites" to listOf("site_lisboa_Wd2e"),
            ),
        )

        assertEquals("uid_clinica_operator_Z7tw", identity.uid)
        assertEquals("balcao@clinica.example", identity.email)
        assertEquals(OperatorRole.OPERATOR, identity.role)
        assertEquals(listOf("site_lisboa_Wd2e"), identity.siteIds)
        assertTrue(identity.isStaff)
    }

    @Test
    fun `a user with no role claim is still returned, with a null role (design D8)`() {
        val identity = TokenClaims.identity(uid = "uid_plain", claims = mapOf("email" to "plain@example"))

        assertNull(identity.role)
        assertFalse(identity.isStaff)
        assertTrue(identity.siteIds.isEmpty())
    }

    @Test
    fun `an unrecognised role is treated as no claim`() {
        val identity = TokenClaims.identity(uid = "u", claims = mapOf("role" to "superuser"))

        assertNull(identity.role)
    }

    @Test
    fun `sites split on commas when the claim is a string`() {
        val identity = TokenClaims.identity(
            uid = "u",
            claims = mapOf("role" to "manager", "sites" to "site_a, site_b"),
        )

        assertEquals(listOf("site_a", "site_b"), identity.siteIds)
    }

    @Test
    fun `sites are read out of a JSON array string too`() {
        val identity = TokenClaims.identity(
            uid = "u",
            claims = mapOf("role" to "orgAdmin", "sites" to "[\"site_a\",\"site_b\"]"),
        )

        assertEquals(listOf("site_a", "site_b"), identity.siteIds)
    }

    @Test
    fun `blank and repeated entries are dropped`() {
        val identity = TokenClaims.identity(
            uid = "u",
            claims = mapOf("role" to "operator", "sites" to " site_a , , site_a ,"),
        )

        assertEquals(listOf("site_a"), identity.siteIds)
    }

    @Test
    fun `a missing sites claim is an empty grant, not every site`() {
        assertEquals(emptyList<String>(), TokenClaims.siteIds(null))
        assertEquals(emptyList(), TokenClaims.siteIds(42L))
        assertEquals(emptyList(), TokenClaims.siteIds("[]"))
    }

    @Test
    fun `non-string entries in a list claim are ignored`() {
        assertEquals(listOf("site_a"), TokenClaims.siteIds(listOf("site_a", 7L, null)))
    }
}