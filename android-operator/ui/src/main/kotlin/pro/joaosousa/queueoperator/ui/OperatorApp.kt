package pro.joaosousa.queueoperator.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The app's single entry composable. PLACEHOLDER: `:ui` implements the three
 * screens here; the signature ([OperatorUiState] in, [OperatorEvent] out) is the
 * contract `:app` is already wired against, so it must not change.
 */
@Composable
fun OperatorApp(
    state: OperatorUiState,
    onEvent: (OperatorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Text(
                text = "Queue operator — ${state.screen}",
                modifier = Modifier.wrapContentSize(Alignment.Center),
            )
        }
    }
}
