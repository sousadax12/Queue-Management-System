package pro.joaosousa.queueoperator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import pro.joaosousa.queuedisplay.tv.data.repository.AuthRepository
import pro.joaosousa.queuedisplay.tv.data.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.data.repository.QueueRepository
import pro.joaosousa.queuedisplay.tv.data.repository.RssRepository
import pro.joaosousa.queueoperator.ui.OperatorApp

/**
 * The whole operator app: one activity, one state holder, three screens.
 *
 * The repositories come from Hilt (bound in `:data`'s `DataModule`), so this
 * activity is the only place that knows both halves of the port.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var config: ConfigRepository
    @Inject lateinit var queues: QueueRepository
    @Inject lateinit var rss: RssRepository

    private var holder: OperatorStateHolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stateHolder = OperatorStateHolder(
            auth = auth,
            config = config,
            queues = queues,
            rss = rss,
            scope = lifecycleScope,
        )
        holder = stateHolder

        setContent {
            val state by stateHolder.state.collectAsStateWithLifecycle()
            OperatorApp(state = state, onEvent = stateHolder::onEvent)
        }
    }
}
