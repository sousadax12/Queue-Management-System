package pro.joaosousa.queueoperator.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** `HH:mm`, built once: the bar is re-rendered every tick. */
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Wall-clock time in the corner of the media panel, like the Flutter
 * `CurrentDateWidget` (which showed `HH:mm` only).
 *
 * The time is *passed in* ([nowMillis]) instead of read here: the state holder
 * owns the clock, so this composable is pure, previewable and testable, and the
 * app ticks once for the whole screen instead of once per widget.
 */
@Composable
fun CurrentDateBar(
    nowMillis: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val label = remember(nowMillis) { formatTime(nowMillis) }
    Text(
        text = label,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** `--:--` before the first tick, so the bar never shows a 1970 timestamp. */
private fun formatTime(nowMillis: Long): String {
    if (nowMillis <= 0L) return "--:--"
    return TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()))
}