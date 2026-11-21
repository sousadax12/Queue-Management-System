package pro.joaosousa.queuedisplay.tv.data.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenDefaults
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenZones
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateFallback
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId

class ScreenMapperTest {

    private val screens = ContractFixtures.load("screens.json")
    private val lobbyLeft = screens.getValue("sites/site_porto_3fKq/screens/scr_lobby_left_Q2mz")

    private fun map(data: Map<String, Any?>) = ScreenMapper.map("site", "scr", data)

    @Test
    fun `fixture screen maps every zone`() {
        val s = ScreenMapper.map("site_porto_3fKq", "scr_lobby_left_Q2mz", lobbyLeft).value!!
        assertEquals(TemplateId.QUEUES_MEDIA, s.template)
        assertEquals(listOf("q_balcao_a_P4tc", "q_balcao_b_Lr6w"), s.zones.queues!!.queueIds)
        assertEquals("pl_promo_nov_Zx81", s.zones.media!!.playlistId)
        assertEquals(0.3, s.zones.media!!.volume, 0.0)
        assertEquals("HH:mm", s.zones.clock!!.pattern)
        assertEquals(true, s.zones.ticker!!.enabled)
    }

    @Test
    fun `omitted queueIds means every queue of the site`() {
        val s = ScreenMapper.map("site_lisboa_Wd2e", "scr_waiting_Hn5r", screens.getValue("sites/site_lisboa_Wd2e/screens/scr_waiting_Hn5r"))
        assertNull(s.value!!.zones.queues!!.queueIds)
        assertEquals(emptyList<ContractIssue>(), s.issues)
    }

    @Test
    fun `null or non-list queueIds also means every queue, never an empty list`() {
        listOf(null, "x", 42L, mapOf("a" to 1L)).forEach { bad ->
            val s = map(lobbyLeft + ("zones" to mapOf("queues" to mapOf("queueIds" to bad, "chime" to true))))
            assertNull("queueIds=$bad", s.value!!.zones.queues!!.queueIds)
        }
    }

    @Test
    fun `non-string queue ids are dropped and reported`() {
        val s = map(lobbyLeft + ("zones" to mapOf("queues" to mapOf("queueIds" to listOf("a", 42L, "b"), "chime" to true))))
        assertEquals(listOf("a", "b"), s.value!!.zones.queues!!.queueIds)
        assertEquals(listOf("sites/site/screens/scr.zones.queues.queueIds[1]"), s.issues.map { it.path })
    }

    @Test
    fun `unknown template falls back and is reported`() {
        val s = map(lobbyLeft + ("template" to "carousel"))
        assertEquals(TemplateId.FALLBACK, s.value!!.template)
        assertEquals(TemplateFallback("carousel"), s.value.templateFallback)
        assertEquals(1, s.issues.size)
        assertTrue(s.issues.single().problem.contains("carousel"))
    }

    @Test
    fun `missing template falls back and is reported`() {
        val s = map(lobbyLeft - "template")
        assertEquals(TemplateId.FALLBACK, s.value!!.template)
        assertEquals(TemplateFallback(null), s.value.templateFallback)
        assertEquals(listOf("sites/site/screens/scr.template"), s.issues.map { it.path })
    }

    @Test
    fun `zones of the wrong type give no zones and an issue`() {
        val s = map(lobbyLeft + ("zones" to "x"))
        assertEquals(ScreenZones(), s.value!!.zones)
        assertEquals(listOf("sites/site/screens/scr.zones"), s.issues.map { it.path })
    }

    @Test
    fun `bad zone fields take defaults`() {
        val s = map(
            lobbyLeft + mapOf(
                "zones" to mapOf(
                    "media" to mapOf("playlistId" to 7L, "volume" to "max"),
                    "clock" to mapOf("pattern" to "", "showDate" to "yes"),
                ),
                "safeAreaPercent" to 40L,
                "maxVideoHeight" to -1L,
            ),
        ).value!!
        assertNull(s.zones.media!!.playlistId)
        assertEquals(ScreenDefaults.MEDIA_VOLUME, s.zones.media!!.volume, 0.0)
        assertEquals(ScreenDefaults.CLOCK_PATTERN, s.zones.clock!!.pattern)
        assertEquals(ScreenDefaults.CLOCK_SHOW_DATE, s.zones.clock!!.showDate)
        assertEquals(ScreenDefaults.MAX_SAFE_AREA_PERCENT, s.safeAreaPercent)
        assertEquals(ScreenDefaults.MAX_VIDEO_HEIGHT, s.maxVideoHeight)
    }

    @Test
    fun `volume stored as an integer reads as a double`() {
        val s = ScreenMapper.map("site_lisboa_Wd2e", "scr_reception_Tj3u", screens.getValue("sites/site_lisboa_Wd2e/screens/scr_reception_Tj3u"))
        assertEquals(1.0, s.value!!.zones.media!!.volume, 0.0)
    }
}
