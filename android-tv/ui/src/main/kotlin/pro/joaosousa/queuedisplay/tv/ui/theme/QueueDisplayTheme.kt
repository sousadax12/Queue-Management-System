package pro.joaosousa.queuedisplay.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val DisplayColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
)

/** App-wide theme. Signage is always dark: a black background reads best on TVs and burns in least. */
@Composable
fun QueueDisplayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DisplayColors, content = content)
}
