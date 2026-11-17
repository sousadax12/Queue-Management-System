package pro.joaosousa.queuedisplay.tv.domain.model

/** Item kind. Anything else is skipped by the mapper (invariant 3). */
enum class MediaType(val raw: String) {
    VIDEO("video"),
    IMAGE("image");

    companion object {
        fun fromRaw(raw: String?): MediaType? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * One file behind a URL. [sha256] is the hex digest of exactly those bytes and [url] is
 * immutable (invariant 1), so the cache keys on the hash and verifies after download.
 */
data class Rendition(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
    val codecs: String?,
    val width: Long?,
    val height: Long?,
)

/**
 * An item in a playlist. Media metadata here is a **snapshot** copied from `media/{id}` when
 * the playlist was written, so the TV never reads the media library (invariant 2).
 */
data class PlaylistItem(
    val mediaId: String,
    val type: MediaType,
    val renditions: List<Rendition>,
    /**
     * Display seconds. Required for images, and *fractional* for videos in the contract
     * (the fixtures carry `8.5`), so it is a Double; null for a video means "play to the end".
     */
    val durationSec: Double?,
    /** Videos only: used for cache planning and progress. */
    val durationMs: Long?,
)

/** `playlists/{playlistId}`. `siteId == null` means org-wide, never across customers (C10). */
data class Playlist(
    val id: String,
    val name: String,
    val siteId: String?,
    val items: List<PlaylistItem>,
)
