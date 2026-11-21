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
import pro.joaosousa.queuedisplay.operator.session.SessionState

/** One screen per [SessionState]. */
@Composable
fun OperatorApp(
    state: SessionState,
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
            is SessionState.Ready -> SetupPlaceholderScreen(
                email = state.email,
                scope = state.scope,
                onSignOut = onSignOut,
            )
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
