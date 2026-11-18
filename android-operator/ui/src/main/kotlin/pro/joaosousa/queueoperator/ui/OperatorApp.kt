package pro.joaosousa.queueoperator.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pro.joaosousa.queueoperator.ui.screens.HomeScreen
import pro.joaosousa.queueoperator.ui.screens.LoginScreen
import pro.joaosousa.queueoperator.ui.screens.SetupScreen
import pro.joaosousa.queueoperator.ui.theme.OperatorTheme

/**
 * The app's single entry composable.
 *
 * Navigation is *derived* from [OperatorUiState.screen], not pushed onto a
 * NavController: the flow is a one-way kiosk (setup → login → home → out), so a
 * back stack would only let the clerk retreat into a screen whose state no
 * longer matches. The 180 ms crossfade keeps the switch from looking like a
 * crash on a wall-mounted tablet.
 *
 * The signature ([OperatorUiState] in, [OperatorEvent] out) is the contract
 * `:app` is wired against: this module never sees a repository, a ViewModel or
 * Firebase (D9).
 */
@Composable
fun OperatorApp(
    state: OperatorUiState,
    onEvent: (OperatorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OperatorTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Crossfade(
                targetState = state.screen,
                animationSpec = tween(durationMillis = 180),
                label = "operator-screen",
            ) { screen ->
                when (screen) {
                    OperatorScreen.SETUP -> SetupScreen(state = state, onEvent = onEvent)
                    OperatorScreen.LOGIN -> LoginScreen(state = state, onEvent = onEvent)
                    OperatorScreen.HOME -> HomeScreen(state = state, onEvent = onEvent)
                }
            }
        }
    }
}