package pro.joaosousa.queuedisplay.operator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pro.joaosousa.queuedisplay.operator.ui.OperatorApp
import pro.joaosousa.queuedisplay.tv.ui.theme.QueueDisplayTheme

/** The single activity: login → (placeholder) setup, driven by the session state. */
@AndroidEntryPoint
class OperatorActivity : ComponentActivity() {

    private val viewModel: OperatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QueueDisplayTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                OperatorApp(
                    state = state,
                    onSignIn = viewModel::signIn,
                    onSignOut = viewModel::signOut,
                    onAcknowledge = viewModel::acknowledge,
                    onRetry = viewModel::start,
                )
            }
        }
    }
}
