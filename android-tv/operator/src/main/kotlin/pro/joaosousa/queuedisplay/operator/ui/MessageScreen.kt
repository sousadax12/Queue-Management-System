package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.session.NoAccessReason

/** A title, an optional message and one focused button (Flutter's alert dialogs, full screen on TV). */
@Composable
internal fun MessageScreen(title: String, message: String?, action: String, onAction: () -> Unit) {
    val focus = remember { FocusRequester() }
    Centered {
        Title(title)
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAction, modifier = Modifier.focusRequester(focus).testTag(TestTags.MESSAGE_ACTION)) {
            Text(action)
        }
    }
    LaunchedEffect(title) { focus.requestFocus() }
}

/** "No operator access": the account was signed out; OK returns to the login form. */
@Composable
fun NoAccessScreen(reason: NoAccessReason, onOk: () -> Unit) {
    MessageScreen(
        title = stringResource(R.string.no_access_title),
        message = stringResource(
            when (reason) {
                NoAccessReason.NoOperatorRole -> R.string.no_access_no_role
                NoAccessReason.EmailNotVerified -> R.string.no_access_unverified
            },
        ),
        action = stringResource(R.string.ok),
        onAction = onOk,
    )
}
