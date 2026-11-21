package pro.joaosousa.queuedisplay.tv.ui.zones.video

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * What a queue's legacy `videoURL` asks the video zone to play. A port of the URL handling in
 * Flutter's `VideoPlayerWidget` (`lib/widgets/video_player_widget.dart`, design F9):
 *
 * - `youtube.com` links play the `v` query parameter, `youtu.be` links their first path segment.
 * - A YouTube link with a `list` parameter and no video id is a playlist, which Flutter rejects.
 * - Anything else is a direct URL for the media player.
 *
 * Rejected and empty URLs leave the zone black, as Flutter's errors did.
 */
sealed interface LegacyVideo {
    /** No `videoURL`: nothing to play. */
    data object None : LegacyVideo

    /** A direct media URL (MP4, HLS, …) for Media3. */
    data class Direct(val url: String) : LegacyVideo

    /** A single YouTube video, played muted in the IFrame player. */
    data class YouTube(val videoId: String) : LegacyVideo

    data class Rejected(val reason: Reason) : LegacyVideo

    enum class Reason {
        /** Not an absolute http(s) URL. */
        Malformed,

        /** A YouTube playlist link without a video, rejected as in Flutter. */
        PlaylistOnly,

        /** A YouTube link whose video id is missing or not an 11-character id. */
        NoVideoId,
    }
}

/** Classifies a queue's `videoURL`. Pure: no I/O, no Android types. */
fun classifyLegacyVideoUrl(url: String): LegacyVideo {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return LegacyVideo.None
    val uri = try {
        URI(trimmed)
    } catch (_: URISyntaxException) {
        return LegacyVideo.Rejected(LegacyVideo.Reason.Malformed)
    }
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase()
    if ((scheme != "http" && scheme != "https") || host.isNullOrEmpty()) {
        return LegacyVideo.Rejected(LegacyVideo.Reason.Malformed)
    }
    val videoId = when {
        host.isHost("youtube.com") -> uri.queryParameter("v")
        host.isHost("youtu.be") -> uri.path?.split('/')?.firstOrNull { it.isNotEmpty() }
        else -> return LegacyVideo.Direct(trimmed)
    }
    return when {
        videoId.isNullOrEmpty() && uri.queryParameter("list") != null ->
            LegacyVideo.Rejected(LegacyVideo.Reason.PlaylistOnly)
        // The id ends up in the player's HTML, so only a well-formed one gets through.
        videoId == null || !YOUTUBE_ID.matches(videoId) ->
            LegacyVideo.Rejected(LegacyVideo.Reason.NoVideoId)
        else -> LegacyVideo.YouTube(videoId)
    }
}

private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

/** `youtube.com`, `www.youtube.com`, `m.youtube.com`, … */
private fun String.isHost(domain: String) = this == domain || endsWith(".$domain")

private fun URI.queryParameter(name: String): String? =
    rawQuery?.split('&')?.firstNotNullOfOrNull { pair ->
        val key = pair.substringBefore('=')
        if (key == name) URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") else null
    }
