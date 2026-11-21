package pro.joaosousa.queuedisplay.tv.ui.zones.video

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the zone composes for each kind of URL, and that it follows the URL and the lifecycle.
 * Media3 playback itself is checked on the TV emulator, not here.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyVideoZoneTest {

    @get:Rule
    val compose = createComposeRule()

    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
    }

    private var url by mutableStateOf("")

    private fun setZone(initialUrl: String) {
        url = initialUrl
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LegacyVideoZone(videoUrl = url, videoVolume = 0.0, modifier = Modifier.size(640.dp, 360.dp))
            }
        }
    }

    private fun assertPlaying(tag: String?) {
        compose.onNodeWithTag(LegacyVideoZoneTags.ZONE).assertIsDisplayed()
        listOf(LegacyVideoZoneTags.YOUTUBE, LegacyVideoZoneTags.DIRECT).forEach {
            if (it == tag) {
                compose.onNodeWithTag(it).assertExists()
            } else {
                compose.onNodeWithTag(it).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a youtube link composes the iframe player`() {
        setZone(YOUTUBE)
        assertPlaying(LegacyVideoZoneTags.YOUTUBE)
    }

    @Test
    fun `empty, playlist-only and garbage urls leave the zone black`() {
        setZone("")
        assertPlaying(null)
        url = "https://www.youtube.com/playlist?list=PL590L5WQmH8fJ54F369BLDSqIwcs-TCfs"
        assertPlaying(null)
        url = "garbage"
        assertPlaying(null)
    }

    @Test
    fun `a new url switches the player in place`() {
        setZone("garbage")
        assertPlaying(null)
        url = YOUTUBE
        assertPlaying(LegacyVideoZoneTags.YOUTUBE)
        url = ""
        assertPlaying(null)
    }

    @Test
    fun `the player is released on stop and back on start`() {
        setZone(YOUTUBE)
        assertPlaying(LegacyVideoZoneTags.YOUTUBE)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        assertPlaying(null)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        assertPlaying(LegacyVideoZoneTags.YOUTUBE)
    }

    private companion object {
        const val YOUTUBE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }
}
