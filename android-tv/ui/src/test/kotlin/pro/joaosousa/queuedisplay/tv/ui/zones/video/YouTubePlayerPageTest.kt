package pro.joaosousa.queuedisplay.tv.ui.zones.video

import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlayerPageTest {

    private val page = youTubePlayerPage(LegacyVideo.YouTube("dQw4w9WgXcQ"), "https://pro.example.app")

    private fun assertHas(fragment: String) = assertTrue("missing `$fragment`", fragment in page)

    @Test
    fun `plays the video muted, autoplaying and looping`() {
        assertHas("videoId: 'dQw4w9WgXcQ'")
        assertHas("mute: 1")
        assertHas("e.target.mute()")
        assertHas("autoplay: 1")
        assertHas("loop: 1, playlist: 'dQw4w9WgXcQ'")
        assertHas("YT.PlayerState.ENDED")
    }

    @Test
    fun `embeds with the app origin`() {
        assertHas("origin: 'https://pro.example.app'")
    }

    @Test
    fun `reports player and loading errors through the bridge`() {
        assertHas("$YOUTUBE_BRIDGE.onError(code)")
        assertHas("onError: function (e) { fail(e.data); }")
        assertHas("""src="https://www.youtube.com/iframe_api" onerror="fail(-1)"""")
    }
}
