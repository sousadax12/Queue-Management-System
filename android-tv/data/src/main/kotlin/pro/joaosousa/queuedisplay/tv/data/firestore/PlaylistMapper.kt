package pro.joaosousa.queuedisplay.tv.data.firestore

import pro.joaosousa.queuedisplay.tv.domain.model.MediaType
import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.PlaylistItem
import pro.joaosousa.queuedisplay.tv.domain.model.Rendition

/**
 * `playlists/{playlistId}` -> [Playlist].
 *
 * Invariant 3: an item with an unknown `type` or no usable rendition is **skipped** rather than
 * crashing the playlist, and `url`/`sha256` are required for a rendition to be usable at all.
 * `siteId: null` is meaningful — the playlist is org-wide (C10) — so it stays null.
 */
object PlaylistMapper {

    fun from(id: String, doc: DocumentData): Playlist {
        val items = Values.list(doc.raw("items")).orEmpty().mapNotNull { item ->
            Values.map(item)?.let { map -> item(map) }
        }
        return Playlist(
            id = id,
            name = Values.string(doc.raw("name")).orEmpty(),
            siteId = Values.string(doc.raw("siteId")),
            items = items,
        )
    }

    private fun item(map: Map<String, Any?>): PlaylistItem? {
        val type = MediaType.fromRaw(Values.string(map["type"])) ?: return null
        val renditions = Values.list(map["renditions"]).orEmpty().mapNotNull { rendition ->
            Values.map(rendition)?.let { r -> rendition(r) }
        }
        if (renditions.isEmpty()) return null
        return PlaylistItem(
            mediaId = Values.string(map["mediaId"]).orEmpty(),
            type = type,
            renditions = renditions,
            durationSec = Values.doubleOrNull(map["durationSec"]),
            durationMs = Values.longOrNull(map["durationMs"]),
        )
    }

    private fun rendition(map: Map<String, Any?>): Rendition? {
        val url = Values.string(map["url"])?.takeIf { it.isNotBlank() } ?: return null
        val sha256 = Values.string(map["sha256"])?.takeIf { it.isNotBlank() } ?: return null
        return Rendition(
            url = url,
            sha256 = sha256,
            sizeBytes = Values.long(map["sizeBytes"], 0L),
            mimeType = Values.string(map["mimeType"]).orEmpty(),
            codecs = Values.string(map["codecs"]),
            width = Values.longOrNull(map["width"]),
            height = Values.longOrNull(map["height"]),
        )
    }
}
