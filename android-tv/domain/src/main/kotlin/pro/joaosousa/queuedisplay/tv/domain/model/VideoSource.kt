package pro.joaosousa.queuedisplay.tv.domain.model

import java.net.URI

/**
 * Where a queue's `videoURL` actually points.
 *
 * The Flutter app reads this field with `videoUrl` and hands it to
 * `video_player` (a direct file) or `youtube_player_flutter` (an embedded
 * iframe player). Android has no supported native YouTube player — the YouTube
 * Android Player API is deprecated and requires the YouTube app — so a YouTube
 * URL is played in a WebView iframe instead, decided here so both callers agree.
 */
sealed interface VideoSource {

    /** `""` or an unusable value: the zone renders nothing. */
    data object None : VideoSource

    /** A direct media URL for Media3/ExoPlayer. */
    data class Direct(val url: String) : VideoSource

    /** A YouTube watch/short/embed URL, reduced to its video id. */
    data class YouTube(val videoId: String) : VideoSource
}

object VideoSourceResolver {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be",
    )

    /** Never throws: an unparseable URL is [VideoSource.None] so a zone degrades. */
    fun resolve(raw: String?): VideoSource {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty()) return VideoSource.None

        val uri = runCatching { URI(url) }.getOrNull() ?: return VideoSource.None
        val host = uri.host?.lowercase()
        if (host != null && host in YOUTUBE_HOSTS) {
            youtubeId(uri)?.let { return VideoSource.YouTube(it) }
            return VideoSource.None     // a YouTube page we cannot reduce to an id
        }
        return if (uri.scheme?.lowercase() in setOf("http", "https")) {
            VideoSource.Direct(url)
        } else {
            VideoSource.None
        }
    }

    private fun youtubeId(uri: URI): String? {
        val path = uri.path.orEmpty()
        if (uri.host?.lowercase() == "youtu.be") {
            return path.trim('/').takeIf { it.isNotBlank() }
        }
        if (path.startsWith("/embed/") || path.startsWith("/shorts/") || path.startsWith("/v/")) {
            return path.substringAfterLast('/').takeIf { it.isNotBlank() }
        }
        // /watch?v=ID
        return uri.query?.split('&')
            ?.firstOrNull { it.startsWith("v=") }
            ?.removePrefix("v=")
            ?.takeIf { it.isNotBlank() }
    }
}
