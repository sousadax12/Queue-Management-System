package pro.joaosousa.queueoperator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import pro.joaosousa.queueoperator.ui.OperatorApp
import pro.joaosousa.queueoperator.ui.OperatorUiState

/**
 * PLACEHOLDER shell: the operator flow's `ViewModel` wiring replaces this body.
 * Kept compiling so the skeleton build is green before the port lands.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var state by remember { mutableStateOf(OperatorUiState()) }
            OperatorApp(state = state, onEvent = { state = state })
        }
    }
}
