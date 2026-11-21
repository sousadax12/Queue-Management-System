package pro.joaosousa.queuedisplay.tv

import android.os.Looper
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pro.joaosousa.queuedisplay.tv.display.DisplayViewModel
import pro.joaosousa.queuedisplay.tv.domain.display.DisplayState

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DisplayActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun showsTheDeviceIdWhileUnclaimed() {
        ActivityScenario.launch(DisplayActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                assertEquals(DisplayState.Unclaimed(FakeDataModule.UID), activity.viewModelState())
            }
        }
    }

    @Test
    fun injectsBuildInfoAndKeepsScreenOn() {
        ActivityScenario.launch(DisplayActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(BuildConfig.VERSION_NAME, activity.buildInfo.versionName)

                val flags = activity.window.attributes.flags
                assertTrue(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            }
        }
    }

    private fun DisplayActivity.viewModelState(): DisplayState {
        val vm: DisplayViewModel by viewModels()
        return vm.state.value
    }
}
