package pro.joaosousa.queuedisplay.tv.data.mapping

import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.PlaylistItem
import pro.joaosousa.queuedisplay.tv.domain.model.Rendition

/**
 * `playlists/{playlistId}` → [Playlist]. Keeps only items the TV can cache and play
 * (TV invariant 3); every dropped item or rendition is reported. `orgId`, `mediaIds` and
 * `publishedBy` are portal-only and ignored.
 */
object PlaylistMapper {
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private const val HTTPS = "https://"

    fun map(playlistId: String, data: Map<String, Any?>): Mapped<Playlist> {
        val issues = mutableListOf<ContractIssue>()
        val f = Fields.of(data, Collections.playlist(playlistId), issues)
        val items = f.list("items", required = true).orEmpty().mapIndexedNotNull { i, raw ->
            item(f, "items[$i]", raw, issues)
        }
        val playlist = Playlist(
            id = playlistId,
            name = f.string("name", required = true).orEmpty(),
            siteId = f.string("siteId")?.takeIf { it.isNotEmpty() },
            items = items,
        )
        return Mapped(playlist, issues)
    }

    private fun item(parent: Fields, key: String, raw: Any?, issues: MutableList<ContractIssue>): PlaylistItem? {
        if (raw !is Map<*, *>) {
            parent.issue(key, "expected an object, got ${Fields.describe(raw)}; dropped")
            return null
        }
        val f = Fields(raw.stringKeys(), parent.pathOf(key), issues)
        fun drop(why: String): PlaylistItem? {
            parent.issue(key, "$why; dropped")
            return null
        }

        val type = f.string("type", required = true) ?: return drop("no type")
        if (type != VIDEO && type != IMAGE) return drop("unknown type \"$type\"")
        val mediaId = f.string("mediaId", required = true)?.takeIf { it.isNotEmpty() } ?: return drop("no mediaId")

        val renditions = f.list("renditions", required = true).orEmpty().mapIndexedNotNull { i, r ->
            rendition(f, "renditions[$i]", r, isVideo = type == VIDEO, issues)
        }
        if (renditions.isEmpty()) return drop("no usable rendition")

        return if (type == VIDEO) {
            PlaylistItem.Video(
                mediaId = mediaId,
                durationSec = f.double("durationSec")?.let { d ->
                    d.takeIf { it > 0 } ?: run { f.issue("durationSec", "$d is not positive; playing to the end"); null }
                },
                durationMs = f.long("durationMs", required = true)?.let { ms ->
                    ms.takeIf { it > 0 } ?: run { f.issue("durationMs", "$ms is not positive"); null }
                },
                renditions = renditions,
            )
        } else {
            // An image has no natural end, so it needs a duration (TV invariant 3).
            val durationSec = f.double("durationSec") ?: return drop("image without durationSec")
            if (durationSec <= 0) return drop("image durationSec $durationSec is not positive")
            PlaylistItem.Image(mediaId = mediaId, durationSec = durationSec, renditions = renditions)
        }
    }

    private fun rendition(
        parent: Fields,
        key: String,
        raw: Any?,
        isVideo: Boolean,
        issues: MutableList<ContractIssue>,
    ): Rendition? {
        if (raw !is Map<*, *>) {
            parent.issue(key, "expected an object, got ${Fields.describe(raw)}; dropped")
            return null
        }
        // Field problems are folded into one "dropped" issue per rendition.
        val scratch = mutableListOf<ContractIssue>()
        val f = Fields(raw.stringKeys(), parent.pathOf(key), scratch)
        val missing = mutableListOf<String>()
        fun <T> need(name: String, value: T?): T? = value.also { if (it == null) missing += name }

        val url = need("url", f.string("url")?.takeIf { it.startsWith(HTTPS) })
        val sha256 = need("sha256", f.string("sha256")?.takeIf { SHA256.matches(it) })
        val sizeBytes = need("sizeBytes", f.long("sizeBytes")?.takeIf { it > 0 })
        val mimeType = need("mimeType", f.string("mimeType")?.takeIf { it.contains('/') })
        val width = need("width", f.int("width")?.takeIf { it > 0 })
        val height = need("height", f.int("height")?.takeIf { it > 0 })
        val codecs = f.string("codecs")?.takeIf { it.isNotBlank() }
        if (isVideo && codecs == null) missing += "codecs"

        if (missing.isNotEmpty()) {
            issues += ContractIssue(parent.pathOf(key), "missing or invalid ${missing.joinToString()}; dropped")
            return null
        }
        return Rendition(
            url = url!!,
            sha256 = sha256!!,
            sizeBytes = sizeBytes!!,
            mimeType = mimeType!!,
            codecs = codecs,
            width = width!!,
            height = height!!,
        )
    }

    private const val VIDEO = "video"
    private const val IMAGE = "image"
}
