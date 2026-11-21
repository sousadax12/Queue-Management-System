package pro.joaosousa.queuedisplay.tv.data.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.joaosousa.queuedisplay.tv.domain.model.PlaylistItem

class PlaylistMapperTest {

    private val playlists = ContractFixtures.load("playlists.json")
    private val promo = playlists.getValue("playlists/pl_promo_nov_Zx81")

    @Suppress("UNCHECKED_CAST")
    private val video = (promo["items"] as List<Map<String, Any?>>)[0]

    @Suppress("UNCHECKED_CAST")
    private val image = (promo["items"] as List<Map<String, Any?>>)[1]

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.rendition() = (this["renditions"] as List<Map<String, Any?>>)[0]

    private fun map(vararg items: Any?) = PlaylistMapper.map("p", promo + ("items" to items.toList()))

    @Test
    fun `fixture playlist keeps video and image items`() {
        val p = PlaylistMapper.map("pl_promo_nov_Zx81", promo).value!!
        assertNull(p.siteId)
        val v = p.items[0] as PlaylistItem.Video
        assertNull(v.durationSec)
        assertEquals(10_000L, v.durationMs)
        assertEquals("avc1.64002a", v.renditions.single().codecs)
        val i = p.items[1] as PlaylistItem.Image
        assertEquals(10.0, i.durationSec, 0.0)
        assertEquals(1920, i.renditions.single().width)
    }

    @Test
    fun `fractional durations survive`() {
        val p = PlaylistMapper.map("pl_saude_Rk42", playlists.getValue("playlists/pl_saude_Rk42")).value!!
        assertEquals(8.5, (p.items[0] as PlaylistItem.Video).durationSec!!, 0.0)
        assertEquals(7.5, (p.items[1] as PlaylistItem.Image).durationSec, 0.0)
    }

    @Test
    fun `items that are not objects are dropped and reported`() {
        val p = PlaylistMapper.map("p", promo + ("items" to listOf(42L)))
        assertEquals(emptyList<PlaylistItem>(), p.value!!.items)
        assertEquals(listOf("playlists/p.items[0]"), p.issues.map { it.path })
    }

    @Test
    fun `items of the wrong type give an empty playlist`() {
        val p = PlaylistMapper.map("p", promo + ("items" to "x"))
        assertEquals(emptyList<PlaylistItem>(), p.value!!.items)
        assertEquals(1, p.issues.size)
    }

    @Test
    fun `unknown item type is skipped`() {
        val p = map(video + ("type" to "html"), image)
        assertEquals(listOf("md_alps_19ab"), p.value!!.items.map { it.mediaId })
        assertTrue(p.issues.single().problem.contains("html"))
    }

    @Test
    fun `image without durationSec is dropped`() {
        val p = map(video, image - "durationSec", image + ("durationSec" to null), image + ("durationSec" to 0L))
        assertEquals(listOf("md_bunny_8f2c"), p.value!!.items.map { it.mediaId })
        assertEquals(3, p.issues.size)
    }

    @Test
    fun `renditions missing sizeBytes, mimeType or video codecs are dropped`() {
        val r = video.rendition()
        val p = map(
            video + ("renditions" to listOf(r - "sizeBytes", r - "mimeType", r - "codecs", r)),
        )
        val item = p.value!!.items.single()
        assertEquals(1, item.renditions.size)
        assertEquals(
            listOf("renditions[0]", "renditions[1]", "renditions[2]"),
            p.issues.map { it.path.substringAfter("items[0].") },
        )
    }

    @Test
    fun `an image rendition needs no codecs`() {
        val p = map(image)
        assertNull(p.value!!.items.single().renditions.single().codecs)
        assertEquals(emptyList<ContractIssue>(), p.issues)
    }

    @Test
    fun `an item left without renditions is dropped`() {
        val r = video.rendition()
        val p = map(video + ("renditions" to listOf(r + ("sha256" to "not-a-hash"), r + ("url" to "http://x/y.mp4"))))
        assertEquals(emptyList<PlaylistItem>(), p.value!!.items)
        assertEquals(3, p.issues.size)
    }

    @Test
    fun `video durationSec of the wrong type plays to the end`() {
        val v = map(video + ("durationSec" to "10")).value!!.items.single() as PlaylistItem.Video
        assertNull(v.durationSec)
    }

    @Test
    fun `sizes stored as doubles read through Number`() {
        val r = video.rendition() + mapOf("sizeBytes" to 1046987.0, "width" to 1920.0)
        val v = map(video + ("renditions" to listOf(r))).value!!.items.single()
        assertEquals(1046987L, v.renditions.single().sizeBytes)
        assertEquals(1920, v.renditions.single().width)
    }
}
