package pro.joaosousa.queuedisplay.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo
import pro.joaosousa.queuedisplay.tv.ui.theme.QueueDisplayTheme

@RunWith(RobolectricTestRunner::class)
class DisplayPlaceholderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAppNameAndBuildVersion() {
        composeRule.setContent {
            QueueDisplayTheme {
                DisplayPlaceholder(
                    appName = "Queue Display",
                    buildInfo = BuildInfo(versionName = "0.1.0", versionCode = 3, buildType = "debug"),
                )
            }
        }

        composeRule.onNodeWithText("Queue Display").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0 (3) debug").assertIsDisplayed()
    }
}
