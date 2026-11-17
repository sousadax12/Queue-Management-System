package pro.joaosousa.queuedisplay.tv.data.firestore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.data.fixtures.ContractFixtures

class SiteMapperTest {

    private fun site(id: String, file: String = "sites.json", path: String = "sites/$id") =
        SiteMapper.from(id, ContractFixtures.doc(file, path))

    @Test
    fun `maps the Porto site, whose chime volume is an integer in the fixture`() {
        val mapped = site("site_porto_3fKq")

        assertEquals("Loja Porto Centro", mapped.name)
        assertEquals("Europe/Lisbon", mapped.timeZone)
        assertEquals("pt-PT", mapped.locale)
        assertEquals("scr_lobby_left_Q2mz", mapped.defaultScreenId)
        assertEquals(4L, mapped.maintenanceHour)

        assertNotNull(mapped.ticker)
        assertEquals(listOf("https://www.rtp.pt/noticias/rss"), mapped.ticker!!.feedUrls)
        assertEquals(60.0, mapped.ticker!!.speedDpPerSec)
        assertEquals(15L, mapped.ticker!!.refreshMinutes)

        assertNotNull(mapped.chime)
        assertEquals(1.0, mapped.chime!!.volume)      // written as `1`, read as a Double
        assertEquals(true, mapped.chime!!.enabled)
    }

    @Test
    fun `maps the Lisboa site, whose chime volume and ticker speed are fractional`() {
        val mapped = site("site_lisboa_Wd2e")

        assertNotNull(mapped.chime)
        assertEquals(0.6, mapped.chime!!.volume)
        assertNotNull(mapped.ticker)
        assertEquals(52.5, mapped.ticker!!.speedDpPerSec)     // why speedDpPerSec is a Double
        assertEquals(emptyList<String>(), mapped.ticker!!.feedUrls)
        assertEquals(3L, mapped.maintenanceHour)
    }

    @Test
    fun `a site missing its optional objects reads as nulls, not zeroes`() {
        val mapped = SiteMapper.from("bare", ContractFixtures.MapDocument("bare", mapOf("name" to "Bare")))

        assertNull(mapped.ticker)
        assertNull(mapped.chime)
        assertNull(mapped.defaultScreenId)
        assertNull(mapped.timeZone)
        assertEquals("Bare", mapped.name)
    }

    @Test
    fun `a partial chime object defaults enabled to false`() {
        val doc = ContractFixtures.MapDocument("partial", mapOf("chime" to mapOf("volume" to 0.25)))

        val mapped = SiteMapper.from(doc.id, doc)

        assertNotNull(mapped.chime)
        assertFalse(mapped.chime!!.enabled)
        assertEquals(0.25, mapped.chime!!.volume)
    }
}
