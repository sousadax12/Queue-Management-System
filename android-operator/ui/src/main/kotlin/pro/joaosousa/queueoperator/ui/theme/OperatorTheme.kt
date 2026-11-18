package pro.joaosousa.queueoperator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Counter-terminal palette. Light, unlike the TV build: the terminal sits on a
 * desk in a lit room and its agent reads form fields, not signage (D3), and a
 * light surface keeps the black video panel and the coloured queue tiles as the
 * only strong contrast on screen.
 *
 * The blue is the Flutter app's `Colors.blue` primarySwatch, so the ported
 * screens look like what the clerks already tap.
 */
private val OperatorColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4F7),
    onPrimaryContainer = Color(0xFF0A2A4A),
    secondary = Color(0xFF2D5B9F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF101418),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101418),
    surfaceVariant = Color(0xFFE6E9EE),
    onSurfaceVariant = Color(0xFF41474D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

/** App-wide Material 3 theme (D3). */
@Composable
fun OperatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OperatorColors, content = content)
}