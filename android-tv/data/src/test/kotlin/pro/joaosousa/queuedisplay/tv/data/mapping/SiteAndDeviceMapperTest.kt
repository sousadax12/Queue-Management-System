package pro.joaosousa.queuedisplay.tv.data.mapping

import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo
import pro.joaosousa.queuedisplay.tv.domain.model.SiteDefaults

class SiteAndDeviceMapperTest {

    private val sites = ContractFixtures.load("sites.json")
    private val devices = ContractFixtures.load("devices.json")

    @Test
    fun `chime volume is a double whether stored as 1 or 0_6`() {
        val porto = SiteMapper.map("site_porto_3fKq", sites.getValue("sites/site_porto_3fKq")).value!!
        val lisboa = SiteMapper.map("site_lisboa_Wd2e", sites.getValue("sites/site_lisboa_Wd2e")).value!!
        assertEquals(1.0, porto.chime.volume, 0.0)
        assertEquals(0.6, lisboa.chime.volume, 0.0)
        assertEquals(52.5, lisboa.ticker.speedDpPerSec, 0.0)
        assertEquals(emptyList<String>(), lisboa.ticker.feedUrls)
        assertEquals("scr_waiting_Hn5r", lisboa.defaultScreenId)
    }

    @Test
    fun `a site with bad fields takes the defaults and reports them`() {
        val s = SiteMapper.map(
            "s",
            mapOf(
                "name" to "S",
                "ticker" to "x",
                "chime" to mapOf("enabled" to "yes", "volume" to 2L),
                "maintenanceHour" to 25L,
                "defaultScreenId" to null,
            ),
        )
        with(s.value!!) {
            assertEquals(SiteDefaults.TIME_ZONE, timeZone)
            assertEquals(SiteDefaults.TICKER_SPEED_DP_PER_SEC, ticker.speedDpPerSec, 0.0)
            assertEquals(SiteDefaults.CHIME_ENABLED, chime.enabled)
            assertEquals(1.0, chime.volume, 0.0)
            assertEquals(SiteDefaults.MAINTENANCE_HOUR, maintenanceHour)
            assertNull(defaultScreenId)
        }
        assertEquals(
            setOf("timeZone", "locale", "ticker", "chime.enabled", "chime.volume", "maintenanceHour"),
            s.issues.map { it.path.removePrefix("sites/s.") }.toSet(),
        )
    }

    @Test
    fun `claimed device with a screen`() {
        val d = DeviceMapper.map("tvPortoLeft01uidAAAAAAAAAA", devices.getValue("devices/tvPortoLeft01uidAAAAAAAAAA")).value!!
        assertTrue(d.isClaimed)
        assertEquals("site_porto_3fKq", d.siteId)
        assertEquals("scr_lobby_left_Q2mz", d.screenId)
        assertNull(d.identifyUntilEpochMillis)
    }

    @Test
    fun `claimed device on the site's default screen`() {
        val d = DeviceMapper.map("tvLisboa01uidBBBBBBBBBBBBB", devices.getValue("devices/tvLisboa01uidBBBBBBBBBBBBB")).value!!
        assertTrue(d.isClaimed)
        assertNull(d.screenId)
        assertEquals(Instant.parse("2026-11-15T09:42:07Z").toEpochMilli(), d.identifyUntilEpochMillis)
    }

    @Test
    fun `unclaimed device`() {
        val d = DeviceMapper.map("tvUnclaimed01uidCCCCCCCCCC", devices.getValue("devices/tvUnclaimed01uidCCCCCCCCCC"))
        assertFalse(d.value!!.isClaimed)
        assertEquals(emptyList<ContractIssue>(), d.issues)
    }

    @Test
    fun `a siteId of the wrong type leaves the box unclaimed`() {
        val d = DeviceMapper.map("u", mapOf("siteId" to 42L, "screenId" to "scr"))
        assertFalse(d.value!!.isClaimed)
        assertNull(d.value.screenId)
        assertEquals(listOf("devices/u.siteId", "devices/u.screenId"), d.issues.map { it.path })
    }

    @Test
    fun `identifyUntil accepts a Timestamp`() {
        val d = DeviceMapper.map("u", mapOf("identifyUntil" to Timestamp(10, 500_000_000))).value!!
        assertEquals(10_500L, d.identifyUntilEpochMillis)
    }

    @Test
    fun `the TV's create holds only its own keys and matches the unclaimed fixture's shape`() {
        val info = DeviceInfo(
            model = "Chromecast HD", sdkInt = 34, appVersion = "0.1.0 (1)", appVersionCode = 1,
            decoders = listOf("avc"), maxDisplay = "1920x1080",
        )
        val doc = DeviceWrites.create(info, freeBytes = 1_000L, serverTimestamp = "SERVER_TS")
        assertEquals(DeviceWrites.TV_KEYS, doc.keys)
        val fixture = devices.getValue("devices/tvUnclaimed01uidCCCCCCCCCC")
        assertEquals(fixture.keys, doc.keys)
        @Suppress("UNCHECKED_CAST")
        assertEquals((fixture["info"] as Map<String, Any?>).keys, (doc["info"] as Map<String, Any?>).keys)
        @Suppress("UNCHECKED_CAST")
        assertEquals((fixture["status"] as Map<String, Any?>).keys, (doc["status"] as Map<String, Any?>).keys)
        assertEquals(600L, (doc["info"] as Map<*, *>)["heartbeatSec"])
    }
}
