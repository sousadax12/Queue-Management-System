package pro.joaosousa.queuedisplay.tv.data.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.Site

/**
 * Maps every doc of every contract fixture file the TV reads, both orgs and the legacy queues
 * included. Each must map with no contract issue, and the values the TV uses must match the
 * raw fixture. A new fixture doc is picked up without touching this test.
 */
@RunWith(Parameterized::class)
class FixtureMappingTest(private val file: String, private val path: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
        fun docs(): List<Array<String>> = ContractFixtures.TV_FILES.flatMap { file ->
            ContractFixtures.load(file).keys.map { path -> arrayOf(file, path) }
        }
    }

    private val raw: Map<String, Any?> = ContractFixtures.load(file).getValue(path)

    @Test
    fun mapsWithoutIssues() {
        val mapped = ContractFixtures.map(path, raw)
        assertEquals("issues in $path", emptyList<ContractIssue>(), mapped.issues)
        val value = checkNotNull(mapped.value) { "$path did not map" }
        when (value) {
            is Device -> checkDevice(value)
            is Site -> checkSite(value)
            is Screen -> checkScreen(value)
            is Playlist -> checkPlaylist(value)
            is Queue -> checkQueue(value)
            else -> error("unexpected ${value::class}")
        }
    }

    private fun num(map: Map<String, Any?>, key: String): Number? = map[key] as Number?

    @Suppress("UNCHECKED_CAST")
    private fun obj(map: Map<String, Any?>, key: String): Map<String, Any?>? = map[key] as Map<String, Any?>?

    private fun checkDevice(d: Device) {
        assertEquals(raw["siteId"], d.siteId)
        assertEquals(raw["screenId"], d.screenId)
        assertEquals(raw["name"], d.name)
        assertEquals(raw["siteId"] != null, d.isClaimed)
        if (raw["identifyUntil"] == null) assertNull(d.identifyUntilEpochMillis) else assertNotNull(d.identifyUntilEpochMillis)
    }

    private fun checkSite(s: Site) {
        assertEquals(raw["name"], s.name)
        assertEquals(raw["defaultScreenId"], s.defaultScreenId)
        assertEquals(num(obj(raw, "chime")!!, "volume")!!.toDouble(), s.chime.volume, 0.0)
        assertEquals(num(obj(raw, "ticker")!!, "speedDpPerSec")!!.toDouble(), s.ticker.speedDpPerSec, 0.0)
        assertEquals(obj(raw, "ticker")!!["feedUrls"], s.ticker.feedUrls)
    }

    private fun checkScreen(s: Screen) {
        assertEquals(raw["template"], s.template.wire)
        assertNull(s.templateFallback)
        val zones = obj(raw, "zones")!!
        assertEquals(obj(zones, "queues")?.get("queueIds"), s.zones.queues?.queueIds)
        assertEquals(zones.containsKey("queues"), s.zones.queues != null)
        assertEquals(zones.containsKey("media"), s.zones.media != null)
        assertEquals(zones.containsKey("ticker"), s.zones.ticker != null)
        assertEquals(zones.containsKey("clock"), s.zones.clock != null)
        obj(zones, "media")?.let { assertEquals(num(it, "volume")!!.toDouble(), s.zones.media!!.volume, 0.0) }
    }

    private fun checkPlaylist(p: Playlist) {
        val items = raw["items"] as List<*>
        assertEquals("no item may be dropped", items.size, p.items.size)
        items.zip(p.items).forEach { (r, item) ->
            @Suppress("UNCHECKED_CAST")
            r as Map<String, Any?>
            assertEquals(r["mediaId"], item.mediaId)
            assertEquals((r["renditions"] as List<*>).size, item.renditions.size)
        }
    }

    private fun checkQueue(q: Queue) {
        assertEquals(num(raw, "number")?.toInt() ?: QueueDefaults.NUMBER, q.number)
        assertEquals(num(raw, "backgroundColor")?.toLong() ?: QueueDefaults.BACKGROUND_COLOR, q.backgroundColor)
        assertEquals(num(raw, "foregroundColor")?.toLong() ?: QueueDefaults.FOREGROUND_COLOR, q.foregroundColor)
        assertEquals(num(raw, "icon")?.toInt() ?: QueueDefaults.ICON, q.icon)
        assertEquals(raw["name"], q.name)
        assertEquals(raw["siteId"], q.siteId)
    }
}
