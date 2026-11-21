package pro.joaosousa.queuedisplay.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import pro.joaosousa.queuedisplay.tv.display.DisplayViewModel
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo
import pro.joaosousa.queuedisplay.tv.ui.debug.DebugDisplayScreen
import pro.joaosousa.queuedisplay.tv.ui.theme.QueueDisplayTheme

/** The single activity: renders the screen full-screen and keeps the display awake. */
@AndroidEntryPoint
class DisplayActivity : ComponentActivity() {

    @Inject
    lateinit var buildInfo: BuildInfo

    private val viewModel: DisplayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            QueueDisplayTheme {
                // M2: the read-path debug screen. Templates take over in M3.
                DebugDisplayScreen(state = state, appName = getString(R.string.app_name), buildInfo = buildInfo)
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
