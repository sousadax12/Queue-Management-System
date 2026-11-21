package pro.joaosousa.queuedisplay.operator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pro.joaosousa.queuedisplay.operator.queues.QueuesViewModel
import pro.joaosousa.queuedisplay.operator.ticker.TickerViewModel
import pro.joaosousa.queuedisplay.operator.ui.OperatorApp
import pro.joaosousa.queuedisplay.operator.ui.OperatorTheme
import pro.joaosousa.queuedisplay.operator.ui.QueueActions
import pro.joaosousa.queuedisplay.tv.domain.operator.QueueNumber

/** The single activity: login → setup → home, driven by the session and the selected queue. */
@AndroidEntryPoint
class OperatorActivity : ComponentActivity() {

    private val viewModel: OperatorViewModel by viewModels()
    private val queuesViewModel: QueuesViewModel by viewModels()
    private val tickerViewModel: TickerViewModel by viewModels()

    private val queueActions = object : QueueActions {
        override fun select(queueId: String) = queuesViewModel.select(queueId)
        override fun advance(direction: QueueNumber.Direction) = queuesViewModel.advance(direction)
        override fun backToSetup() = queuesViewModel.backToSetup()
        override fun retry() = queuesViewModel.retry()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OperatorTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val queues = queuesViewModel.ui.collectAsStateWithLifecycle().value
                val headlines = tickerViewModel.headlines.collectAsStateWithLifecycle().value
                OperatorApp(
                    state = state,
                    queues = queues,
                    headlines = headlines,
                    queueActions = queueActions,
                    onSignIn = viewModel::signIn,
                    onSignOut = viewModel::signOut,
                    onAcknowledge = viewModel::acknowledge,
                    onRetry = viewModel::start,
                )
            }
        }
    }
}
