package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.queues.QueuesUi
import pro.joaosousa.queuedisplay.operator.session.SessionState
import pro.joaosousa.queuedisplay.tv.domain.operator.QueueNumber

/** What the signed-in screens can do. */
interface QueueActions {
    fun select(queueId: String)
    fun advance(direction: QueueNumber.Direction)
    fun backToSetup()
    fun retry()
}

/** One screen per [SessionState]; once signed in, setup or home by [QueuesUi.route]. */
@Composable
fun OperatorApp(
    state: SessionState,
    queues: QueuesUi,
    queueActions: QueueActions,
    onSignIn: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onAcknowledge: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            SessionState.Starting -> Centered { Text(stringResource(R.string.loading)) }
            is SessionState.StartFailed -> MessageScreen(
                title = stringResource(R.string.start_error, state.message),
                message = null,
                action = stringResource(R.string.retry),
                onAction = onRetry,
            )
            // One branch for both, so the form keeps what was typed while signing in and after a failure.
            is SessionState.SignedOut, SessionState.SigningIn -> LoginScreen(
                signingIn = state == SessionState.SigningIn,
                failure = (state as? SessionState.SignedOut)?.failure,
                onSubmit = onSignIn,
                onDismissFailure = onAcknowledge,
            )
            is SessionState.NoAccess -> NoAccessScreen(reason = state.reason, onOk = onAcknowledge)
            is SessionState.Ready -> when (queues.route) {
                QueuesUi.Route.Loading -> Centered { Text(stringResource(R.string.loading)) }
                QueuesUi.Route.Setup -> SetupScreen(
                    email = state.email,
                    scope = state.scope,
                    queues = queues.queues,
                    onSelect = queueActions::select,
                    onRetry = queueActions::retry,
                    onSignOut = onSignOut,
                )
                QueuesUi.Route.Home -> HomeScreen(
                    queues = queues.queues,
                    writeError = queues.writeError,
                    onAdvance = queueActions::advance,
                    onBack = queueActions::backToSetup,
                )
            }
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 640.dp)) { content() }
    }
}

@Composable
internal fun Title(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineMedium)
}
