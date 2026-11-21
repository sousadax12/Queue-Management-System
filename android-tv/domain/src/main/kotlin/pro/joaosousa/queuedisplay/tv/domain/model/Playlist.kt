package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * `playlists/{playlistId}`. [siteId] null = shared by every site of the org (C10).
 * Only playable items are kept: see [PlaylistItem].
 */
data class Playlist(
    val id: String,
    val name: String,
    val siteId: String?,
    val items: List<PlaylistItem>,
)

/**
 * A snapshot of a ready media doc (TV invariants 2 and 3). The mapper drops items with an
 * unknown `type`, images without a positive `durationSec`, and items left with no valid
 * rendition.
 */
sealed interface PlaylistItem {
    val mediaId: String
    /** Never empty. */
    val renditions: List<Rendition>

    data class Video(
        override val mediaId: String,
        /** Null = play to the end. */
        val durationSec: Double?,
        /** Used for cache planning and progress; null when the doc lacks it. */
        val durationMs: Long?,
        override val renditions: List<Rendition>,
    ) : PlaylistItem

    data class Image(
        override val mediaId: String,
        val durationSec: Double,
        override val renditions: List<Rendition>,
    ) : PlaylistItem
}

/**
 * One immutable CDN object (TV invariant 1). The mapper drops a rendition that misses any of
 * `url`, `sha256`, `sizeBytes`, `mimeType`, `width`, `height`, or `codecs` for video.
 */
data class Rendition(
    val url: String,
    /** Lowercase hex SHA-256 of the bytes behind [url]; the cache key. */
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
    /** RFC 6381 codecs; always set for video. */
    val codecs: String?,
    val width: Int,
    val height: Int,
)
