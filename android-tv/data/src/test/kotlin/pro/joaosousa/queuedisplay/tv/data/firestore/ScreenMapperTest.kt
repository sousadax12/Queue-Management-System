package pro.joaosousa.queuedisplay.tv.data.firestore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.data.fixtures.ContractFixtures
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId

class ScreenMapperTest {

    private fun mapped(path: String) = ScreenMapper.from(
        path.substringAfterLast('/'),
        ContractFixtures.doc("screens.json", path),
    )

    @Test
    fun `maps the queues_media screen with all four zones`() {
        val result = mapped("sites/site_porto_3fKq/screens/scr_lobby_left_Q2mz")
        val zones = result.screen.zones

        assertEquals(TemplateId.QUEUES_MEDIA, result.screen.template)
        assertNull(result.unknownTemplate)
        assertEquals("Lobby left", result.screen.name)
        assertEquals(1080L, result.screen.maxVideoHeight)
        assertEquals(0L, result.screen.safeAreaPercent)

        assertNotNull(zones.queues)
        assertEquals(listOf("q_balcao_a_P4tc", "q_balcao_b_Lr6w"), zones.queues!!.queueIds)
        assertTrue(zones.queues!!.chime)
        assertNotNull(zones.media)
        assertEquals("pl_promo_nov_Zx81", zones.media!!.playlistId)
        assertEquals(0.3, zones.media!!.volume)
        assertNotNull(zones.ticker)
        assertTrue(zones.ticker!!.enabled)
        assertNotNull(zones.clock)
        assertEquals("HH:mm", zones.clock!!.pattern)
        assertEquals(false, zones.clock!!.showDate)
    }

    @Test
    fun `a screen that uses only media has null for the zones it does not use`() {
        val result = mapped("sites/site_porto_3fKq/screens/scr_lobby_right_Vb8n")

        assertEquals(TemplateId.FULLSCREEN_MEDIA, result.screen.template)
        assertNull(result.screen.zones.queues)
        assertNull(result.screen.zones.ticker)
        assertNotNull(result.screen.zones.media)
        assertEquals(0.0, result.screen.zones.media!!.volume)
        assertEquals(3L, result.screen.safeAreaPercent)
    }

    @Test
    fun `an absent queueIds means every queue of the site, so it stays null (invariant 7)`() {
        val result = mapped("sites/site_lisboa_Wd2e/screens/scr_waiting_Hn5r")

        assertEquals(TemplateId.QUEUES_ONLY, result.screen.template)
        val queues = result.screen.zones.queues
        assertNotNull(queues)
        assertNull(queues!!.queueIds)        // NOT emptyList(): null renders all queues
        assertTrue(queues!!.chime)
        assertNull(result.screen.zones.media)
    }

    @Test
    fun `accepts a zone volume stored as an integer`() {
        val result = mapped("sites/site_lisboa_Wd2e/screens/scr_reception_Tj3u")
        val zones = result.screen.zones

        assertNotNull(zones.media)
        assertEquals(1.0, zones.media!!.volume)
        assertNotNull(zones.ticker)
        assertEquals(false, zones.ticker!!.enabled)
        assertNotNull(zones.clock)
        assertEquals(true, zones.clock!!.showDate)
    }

    @Test
    fun `an unknown template falls back to queues_media and is reported (invariant 5)`() {
        val doc = ContractFixtures.MapDocument(
            "future",
            mapOf("template" to "queues_media_v2", "name" to "From the future"),
        )

        val result = ScreenMapper.from(doc.id, doc)

        assertEquals(TemplateId.QUEUES_MEDIA, result.screen.template)
        assertEquals("queues_media_v2", result.unknownTemplate)
    }

    @Test
    fun `a missing template falls back silently, because nothing was there to report`() {
        val doc = ContractFixtures.MapDocument("bare", mapOf("name" to "Bare"))

        val result = ScreenMapper.from(doc.id, doc)

        assertEquals(TemplateId.QUEUES_MEDIA, result.screen.template)
        assertNull(result.unknownTemplate)
        assertNull(result.screen.zones.queues)
    }
}
