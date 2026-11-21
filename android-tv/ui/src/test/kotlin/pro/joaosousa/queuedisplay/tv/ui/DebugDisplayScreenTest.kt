package pro.joaosousa.queuedisplay.tv.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo
import pro.joaosousa.queuedisplay.tv.domain.display.DisplayState
import pro.joaosousa.queuedisplay.tv.domain.display.PlaylistState
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenZones
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateFallback
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId
import pro.joaosousa.queuedisplay.tv.ui.debug.DebugDisplayScreen
import pro.joaosousa.queuedisplay.tv.ui.theme.QueueDisplayTheme

@RunWith(RobolectricTestRunner::class)
class DebugDisplayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val build = BuildInfo("0.1.0", 1, "debug")

    private fun queue(number: Int) =
        Queue("q1", "s1", "Balcão A", number, 0xf007, 0xff3f51b5L, 0xffffffffL, "", 0.0, 1.0, 60.0)

    private fun claimed(number: Int) = DisplayState.Claimed(
        uid = "uid1",
        device = Device("uid1", "s1", "scr", "Box 3", null),
        site = null,
        screenId = "scr",
        screen = Screen("scr", "s1", "Lobby", TemplateId.FALLBACK, TemplateFallback("carousel"), ScreenZones(), 1080, 0),
        playlist = PlaylistState.None,
        queues = listOf(queue(number)),
    )

    @Test
    fun unclaimedShowsTheDeviceId() {
        composeRule.setContent {
            QueueDisplayTheme { DebugDisplayScreen(DisplayState.Unclaimed("uidXYZ"), "Queue Display", build) }
        }
        composeRule.onNodeWithText("Not claimed yet").assertIsDisplayed()
        composeRule.onNodeWithText("uidXYZ").assertIsDisplayed()
    }

    @Test
    fun claimedShowsLiveNumbersAndTheTemplateFallback() {
        val state = mutableStateOf<DisplayState>(claimed(7))
        composeRule.setContent { QueueDisplayTheme { DebugDisplayScreen(state.value, "Queue Display", build) } }

        composeRule.onNodeWithText("Balcão A").assertIsDisplayed()
        composeRule.onNodeWithText("7").assertIsDisplayed()
        composeRule.onNodeWithText("\"carousel\" is unknown; showing queues_media").assertIsDisplayed()

        state.value = claimed(8)
        composeRule.onNodeWithText("8").assertIsDisplayed()
    }
}
