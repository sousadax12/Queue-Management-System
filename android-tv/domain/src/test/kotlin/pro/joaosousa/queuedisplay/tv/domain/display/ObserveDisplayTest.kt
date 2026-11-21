package pro.joaosousa.queuedisplay.tv.domain.display

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.domain.model.ChimeSettings
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo
import pro.joaosousa.queuedisplay.tv.domain.model.MediaZone
import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenZones
import pro.joaosousa.queuedisplay.tv.domain.model.Site
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId
import pro.joaosousa.queuedisplay.tv.domain.model.TickerSettings
import pro.joaosousa.queuedisplay.tv.domain.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.domain.repository.DeviceRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDisplayTest {

    private val info = DeviceInfo("TV", 34, "0.1.0 (1)", 1, decoders = emptyList(), maxDisplay = "1920x1080")

    private class FakeDevices : DeviceRepository {
        val doc = MutableStateFlow<Device?>(null)
        val registered = mutableListOf<String>()
        var signInFailures = 0

        override suspend fun signIn(): String {
            if (signInFailures > 0) {
                signInFailures--
                error("offline")
            }
            return "uid1"
        }

        override suspend fun registerIfMissing(uid: String, info: DeviceInfo, freeBytes: Long) {
            registered += uid
        }

        override fun device(uid: String): Flow<Device?> = doc
    }

    private class FakeConfig : ConfigRepository {
        val sites = MutableStateFlow<Map<String, Site>>(emptyMap())
        val screens = MutableStateFlow<Map<String, Screen>>(emptyMap())
        val queues = MutableStateFlow<List<Queue>>(emptyList())
        val queueQueries = mutableListOf<String>()
        val deniedPlaylists = mutableSetOf<String>()

        override fun site(siteId: String) = sites.map { it[siteId] }
        override fun screen(siteId: String, screenId: String) = screens.map { it["$siteId/$screenId"] }
        override fun playlist(playlistId: String): Flow<Playlist?> = flow {
            if (playlistId in deniedPlaylists) error("PERMISSION_DENIED")
            emit(Playlist(playlistId, "P", null, emptyList()))
        }

        override fun queues(siteId: String): Flow<List<Queue>> {
            queueQueries += siteId
            return queues.map { all -> all.filter { it.siteId == siteId } }
        }
    }

    private fun site(id: String, defaultScreen: String?) = Site(
        id, "Site $id", "Europe/Lisbon", "pt-PT", defaultScreen,
        TickerSettings(emptyList(), 60.0, 15), ChimeSettings(true, 1.0), 4,
    )

    private fun screen(site: String, id: String, playlistId: String?) = Screen(
        id, site, "Screen $id", TemplateId.QUEUES_MEDIA, null,
        ScreenZones(media = MediaZone(playlistId, 0.3)), 1080, 0,
    )

    private fun queue(id: String, site: String, number: Int) = Queue(
        id, site, "Q $id", number, 0xf007, 0xff2d5b9fL, 0xffffffffL, "", 0.0, 1.0, 60.0,
    )

    @Test
    fun `unclaimed box registers itself and waits`() = runTest {
        val devices = FakeDevices()
        val state = ObserveDisplay(devices, FakeConfig())(info) { 0 }
            .filterIsInstance<DisplayState.Unclaimed>().first()
        assertEquals("uid1", state.uid)
        assertEquals(listOf("uid1"), devices.registered)
    }

    @Test
    fun `claiming shows the site's queues, live, on the default screen`() = runTest {
        val devices = FakeDevices()
        val config = FakeConfig().apply {
            sites.value = mapOf("s1" to site("s1", defaultScreen = "scr"))
            screens.value = mapOf("s1/scr" to screen("s1", "scr", playlistId = "pl"))
            queues.value = listOf(queue("a", "s1", 1), queue("b", "s2", 9))
        }
        val seen = mutableListOf<DisplayState>()
        val job = launch { ObserveDisplay(devices, config)(info) { 0 }.collect { seen += it } }
        advanceUntilIdle()
        assertTrue(seen.last() is DisplayState.Unclaimed)

        devices.doc.value = Device("uid1", siteId = "s1", screenId = null, name = "Box", identifyUntilEpochMillis = null)
        advanceUntilIdle()
        val claimed = seen.last() as DisplayState.Claimed
        assertEquals("scr", claimed.screenId)
        assertEquals(PlaylistState.Loaded(Playlist("pl", "P", null, emptyList())), claimed.playlist)
        assertEquals(listOf("a"), claimed.queues.map { it.id })
        assertEquals(listOf("s1"), config.queueQueries)

        config.queues.value = listOf(queue("a", "s1", 2))
        advanceUntilIdle()
        assertEquals(2, (seen.last() as DisplayState.Claimed).queues.single().number)

        // A device doc write that changes nothing the TV maps (a heartbeat) keeps the listeners.
        devices.doc.value = devices.doc.value!!.copy()
        advanceUntilIdle()
        assertEquals(listOf("s1"), config.queueQueries)

        devices.doc.value = devices.doc.value!!.copy(siteId = null)
        advanceUntilIdle()
        assertTrue(seen.last() is DisplayState.Unclaimed)
        job.cancel()
    }

    @Test
    fun `an unreadable playlist does not hide the queues`() = runTest {
        val devices = FakeDevices().apply { doc.value = Device("uid1", "s1", "scr", null, null) }
        val config = FakeConfig().apply {
            sites.value = mapOf("s1" to site("s1", null))
            screens.value = mapOf("s1/scr" to screen("s1", "scr", playlistId = "other_site"))
            queues.value = listOf(queue("a", "s1", 5))
            deniedPlaylists += "other_site"
        }
        val claimed = ObserveDisplay(devices, config)(info) { 0 }.filterIsInstance<DisplayState.Claimed>().first()
        assertEquals(PlaylistState.Failed("PERMISSION_DENIED"), claimed.playlist)
        assertEquals(5, claimed.queues.single().number)
    }

    @Test
    fun `a failure is shown and retried`() = runTest {
        val devices = FakeDevices().apply { signInFailures = 1 }
        val seen = mutableListOf<DisplayState>()
        val job = launch { ObserveDisplay(devices, FakeConfig(), backoffMillis = { 1_000 })(info) { 0 }.collect { seen += it } }
        advanceUntilIdle()
        assertEquals(DisplayState.Failed("offline", 1_000), seen[1])
        assertTrue(seen.last() is DisplayState.Unclaimed)
        job.cancel()
    }

    @Test
    fun `backoff doubles up to a minute`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), (0L..6L).map(ObserveDisplay::defaultBackoffMillis))
    }
}
