package pro.joaosousa.queuedisplay.tv.data.firestore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.data.fixtures.ContractFixtures
import pro.joaosousa.queuedisplay.tv.domain.model.MediaType

class PlaylistMapperTest {

    @Test
    fun `maps the org-wide playlist, keeping siteId null`() {
        val playlist = PlaylistMapper.from(
            "pl_promo_nov_Zx81",
            ContractFixtures.doc("playlists.json", "playlists/pl_promo_nov_Zx81"),
        )

        assertEquals("Promo Novembro", playlist.name)
        assertNull(playlist.siteId)              // org-wide, never across customers (C10)
        assertEquals(2, playlist.items.size)

        val video = playlist.items[0]
        assertEquals(MediaType.VIDEO, video.type)
        assertEquals("md_bunny_8f2c", video.mediaId)
        assertNull(video.durationSec)            // null for a video means "play to the end"
        assertEquals(10000L, video.durationMs)
        assertEquals(1, video.renditions.size)
        val rendition = video.renditions[0]
        assertEquals(1046987L, rendition.sizeBytes)
        assertEquals("avc1.64002a", rendition.codecs)
        assertEquals(1920L, rendition.width)
        assertEquals(1080L, rendition.height)
        assertEquals(64, rendition.sha256.length)   // a hex digest, which the cache keys on

        val image = playlist.items[1]
        assertEquals(MediaType.IMAGE, image.type)
        assertEquals(10.0, image.durationSec)
        assertNull(image.renditions[0].codecs)      // images carry no codecs
    }

    @Test
    fun `keeps a fractional video duration, which is why durationSec is a Double`() {
        val playlist = PlaylistMapper.from(
            "pl_saude_Rk42",
            ContractFixtures.doc("playlists.json", "playlists/pl_saude_Rk42"),
        )

        assertEquals("site_lisboa_Wd2e", playlist.siteId)
        assertEquals(8.5, playlist.items[0].durationSec)
        assertEquals(7.5, playlist.items[1].durationSec)
    }

    @Test
    fun `skips an item whose type this build does not know (invariant 3)`() {
        val doc = docWith(
            item("a", "audio", listOf(rendition("https://cdn/a.mp3", "aa"))),
            item("b", "image", listOf(rendition("https://cdn/b.jpg", "bb")), durationSec = 5L),
        )

        val items = PlaylistMapper.from(doc.id, doc).items

        assertEquals(listOf("b"), items.map { it.mediaId })
    }

    @Test
    fun `skips an item whose renditions are missing, empty or unusable (invariant 3)`() {
        val doc = docWith(
            item("no-renditions", "video", null),
            item("empty", "video", emptyList()),
            item("no-sha", "video", listOf(mapOf("url" to "https://cdn/x.mp4", "sizeBytes" to 10L))),
            item("no-url", "video", listOf(mapOf("sha256" to "dd", "sizeBytes" to 10L))),
            item("ok", "video", listOf(rendition("https://cdn/ok.mp4", "cc"))),
        )

        val items = PlaylistMapper.from(doc.id, doc).items

        assertEquals(listOf("ok"), items.map { it.mediaId })
    }

    @Test
    fun `an empty or missing items array is an empty playlist, not a crash`() {
        val empty = PlaylistMapper.from(
            "p1",
            ContractFixtures.MapDocument("p1", mapOf("name" to "P", "items" to emptyList<Any>())),
        )
        val missing = PlaylistMapper.from(
            "p2",
            ContractFixtures.MapDocument("p2", mapOf("name" to "P")),
        )

        assertEquals(0, empty.items.size)
        assertEquals(0, missing.items.size)
        assertEquals("P", missing.name)
    }

    private fun item(
        mediaId: String,
        type: String,
        renditions: List<Map<String, Any?>>?,
        durationSec: Any? = null,
    ): Map<String, Any?> = buildMap {
        put("mediaId", mediaId)
        put("type", type)
        if (renditions != null) put("renditions", renditions)
        if (durationSec != null) put("durationSec", durationSec)
    }

    private fun rendition(url: String, sha: String): Map<String, Any?> = mapOf(
        "url" to url,
        "sha256" to sha,
        "sizeBytes" to 1L,
        "mimeType" to "video/mp4",
        "width" to 1L,
        "height" to 1L,
    )

    private fun docWith(vararg items: Map<String, Any?>) = ContractFixtures.MapDocument(
        id = "edge",
        data = mapOf("name" to "Edge", "items" to items.toList()),
    )
}
