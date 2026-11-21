package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Surface
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.queues.QueuesUi
import pro.joaosousa.queuedisplay.operator.session.SessionState
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults
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
    headlines: List<String>,
    queueActions: QueueActions,
    onSignIn: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onAcknowledge: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
        when (state) {
            SessionState.Starting -> CenteredSpinner()
            is SessionState.StartFailed -> MessageScreen(
                message = stringResource(R.string.start_error, state.message),
                action = stringResource(R.string.retry),
                onAction = onRetry,
            )
            // One branch for the three, so the form keeps what was typed while signing in and after
            // a failure, and "No operator access" is a dialog over it, as in Flutter.
            is SessionState.SignedOut, SessionState.SigningIn, is SessionState.NoAccess -> LoginScreen(
                signingIn = state == SessionState.SigningIn,
                failure = (state as? SessionState.SignedOut)?.failure,
                noAccess = (state as? SessionState.NoAccess)?.reason,
                onSubmit = onSignIn,
                onDismissFailure = onAcknowledge,
            )
            is SessionState.Ready -> when (queues.route) {
                QueuesUi.Route.Loading -> CenteredSpinner()
                QueuesUi.Route.Setup -> SetupScreen(
                    queues = queues.queues,
                    onSelect = queueActions::select,
                    onRetry = queueActions::retry,
                    onSignOut = onSignOut,
                )
                QueuesUi.Route.Home -> HomeScreen(
                    queues = queues.queues,
                    writeError = queues.writeError,
                    headlines = headlines,
                    tickerVelocity = queues.selectedQueue?.velocity ?: QueueDefaults.VELOCITY,
                    onAdvance = queueActions::advance,
                    onBack = queueActions::backToSetup,
                    video = { HomeVideoSlot(queues.selectedQueue) },
                )
            }
        }
    }
}

/** Flutter's `Center(child: CircularProgressIndicator())`. */
@Composable
internal fun CenteredSpinner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
}
