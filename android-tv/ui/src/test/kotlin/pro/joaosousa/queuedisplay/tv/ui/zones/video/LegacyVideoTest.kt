package pro.joaosousa.queuedisplay.tv.ui.zones.video

import org.junit.Assert.assertEquals
import org.junit.Test
import pro.joaosousa.queuedisplay.tv.ui.zones.video.LegacyVideo.Reason

class LegacyVideoTest {

    private fun assertClassifies(expected: LegacyVideo, url: String) =
        assertEquals(url, expected, classifyLegacyVideoUrl(url))

    @Test
    fun `youtube watch links play the v parameter`() {
        val video = LegacyVideo.YouTube("dQw4w9WgXcQ")
        assertClassifies(video, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertClassifies(video, "https://youtube.com/watch?v=dQw4w9WgXcQ&t=42s")
        assertClassifies(video, "http://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ")
        assertClassifies(video, "  https://WWW.YouTube.com/watch?v=dQw4w9WgXcQ  ")
    }

    @Test
    fun `a watch link inside a playlist plays its video, as in Flutter`() {
        assertClassifies(
            LegacyVideo.YouTube("dQw4w9WgXcQ"),
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL590L5WQmH8fJ54F369BLDSqIwcs-TCfs",
        )
    }

    @Test
    fun `youtu_be links play their first path segment`() {
        val video = LegacyVideo.YouTube("dQw4w9WgXcQ")
        assertClassifies(video, "https://youtu.be/dQw4w9WgXcQ")
        assertClassifies(video, "https://youtu.be/dQw4w9WgXcQ?si=abc&t=10")
    }

    @Test
    fun `playlist-only links are rejected`() {
        val rejected = LegacyVideo.Rejected(Reason.PlaylistOnly)
        assertClassifies(rejected, "https://www.youtube.com/playlist?list=PL590L5WQmH8fJ54F369BLDSqIwcs-TCfs")
        assertClassifies(rejected, "https://www.youtube.com/watch?list=PL590L5WQmH8fJ54F369BLDSqIwcs-TCfs")
        assertClassifies(rejected, "https://youtu.be/?list=PL590L5WQmH8fJ54F369BLDSqIwcs-TCfs")
    }

    @Test
    fun `youtube links without a usable video id are rejected`() {
        val rejected = LegacyVideo.Rejected(Reason.NoVideoId)
        assertClassifies(rejected, "https://www.youtube.com/")
        assertClassifies(rejected, "https://www.youtube.com/watch?v=")
        // Flutter reads only `v`, so embed and shorts links failed there too.
        assertClassifies(rejected, "https://www.youtube.com/embed/dQw4w9WgXcQ")
        // Not an 11-character id: it would otherwise be written into the player's HTML.
        assertClassifies(rejected, "https://www.youtube.com/watch?v=abc'%3Balert(1)%3B'")
        assertClassifies(rejected, "https://youtu.be/short")
    }

    @Test
    fun `other http links are direct media`() {
        assertClassifies(
            LegacyVideo.Direct("https://storage.example.com/videos/promo.mp4"),
            "https://storage.example.com/videos/promo.mp4",
        )
        assertClassifies(
            LegacyVideo.Direct("http://10.0.2.2:8000/loop.mp4?token=a%20b"),
            " http://10.0.2.2:8000/loop.mp4?token=a%20b ",
        )
        // Only the host decides: a YouTube URL in the query of another site is not YouTube.
        assertClassifies(
            LegacyVideo.Direct("https://notyoutube.com/watch?v=dQw4w9WgXcQ"),
            "https://notyoutube.com/watch?v=dQw4w9WgXcQ",
        )
    }

    @Test
    fun `hls and dash links are direct media, never youtube`() {
        listOf(
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" to LegacyVideo.Format.Hls,
            "https://cdn.example.com/live/master.m3u8?token=abc&v=dQw4w9WgXcQ" to LegacyVideo.Format.Hls,
            "https://cdn.example.com/LIVE/MASTER.M3U8" to LegacyVideo.Format.Hls,
            "https://cdn.example.com/vod/manifest.mpd" to LegacyVideo.Format.Dash,
            "https://cdn.example.com/vod/manifest.mpd?sig=a%2Fb#t=10" to LegacyVideo.Format.Dash,
        ).forEach { (url, format) ->
            val video = classifyLegacyVideoUrl(url)
            assertEquals(url, LegacyVideo.Direct(url), video)
            assertEquals(url, format, (video as LegacyVideo.Direct).format)
        }
    }

    @Test
    fun `only the path extension makes a link a stream`() {
        listOf(
            "https://storage.example.com/videos/promo.mp4",
            "https://storage.example.com/videos/promo",
            // The extension is in the query or the host, not the path.
            "https://storage.example.com/promo.mp4?next=playlist.m3u8",
            "https://m3u8.example.com/manifest.mpd.mp4",
        ).forEach { url ->
            assertEquals(url, LegacyVideo.Format.Progressive, LegacyVideo.Direct(url).format)
        }
    }

    @Test
    fun `an empty url plays nothing`() {
        assertClassifies(LegacyVideo.None, "")
        assertClassifies(LegacyVideo.None, "   ")
    }

    @Test
    fun `garbage is rejected as malformed`() {
        val rejected = LegacyVideo.Rejected(Reason.Malformed)
        assertClassifies(rejected, "not a url")
        assertClassifies(rejected, "garbage")
        assertClassifies(rejected, "youtube.com/watch?v=dQw4w9WgXcQ")
        assertClassifies(rejected, "ftp://example.com/promo.mp4")
        assertClassifies(rejected, "file:///sdcard/promo.mp4")
        assertClassifies(rejected, "https://")
        assertClassifies(rejected, "http://exa mple.com/a.mp4")
    }
}
