package pro.joaosousa.queuedisplay.tv.ui.zones.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The media source Media3 builds for each kind of direct URL, through the same
 * [DefaultMediaSourceFactory] that `ExoPlayer.Builder(context)` uses. It finds the HLS and DASH
 * modules by reflection, so this also fails if either dependency goes missing.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class LegacyVideoMediaSourceTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun mediaSourceFor(url: String): MediaSource {
        val video = classifyLegacyVideoUrl(url) as LegacyVideo.Direct
        return DefaultMediaSourceFactory(context).createMediaSource(video.toMediaItem())
    }

    private inline fun <reified T : MediaSource> assertSource(url: String) {
        val source = mediaSourceFor(url)
        assertTrue("$url built ${source.javaClass.simpleName}", source is T)
    }

    @Test
    fun `m3u8 links build an hls source`() {
        assertSource<HlsMediaSource>("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        assertSource<HlsMediaSource>("https://cdn.example.com/live/master.m3u8?token=abc")
        assertSource<HlsMediaSource>("https://cdn.example.com/LIVE/MASTER.M3U8")
    }

    @Test
    fun `mpd links build a dash source`() {
        assertSource<DashMediaSource>("https://cdn.example.com/vod/manifest.mpd")
        assertSource<DashMediaSource>("https://cdn.example.com/vod/manifest.mpd?sig=a%2Fb")
    }

    @Test
    fun `other links stay progressive`() {
        assertSource<ProgressiveMediaSource>("https://storage.example.com/videos/promo.mp4")
        assertSource<ProgressiveMediaSource>("https://storage.example.com/promo.mp4?next=playlist.m3u8")
    }
}
