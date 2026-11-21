package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * A centred message and one focused button: the start-up failure. Flutter shows the bare
 * `Error: …` text in the middle of the screen (`lib/main.dart`); the button is the remote's way
 * out of it.
 */
@Composable
internal fun MessageScreen(message: String, action: String, onAction: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        FlutterButton(
            text = action,
            onClick = onAction,
            modifier = Modifier.focusRequester(focus).testTag(TestTags.MESSAGE_ACTION),
        )
    }
    LaunchedEffect(message) { focus.requestFocus() }
}
