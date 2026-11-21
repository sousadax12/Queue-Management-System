package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The time as `HH:mm`, white, bold 16 sp (Flutter `CurrentDateWidget`, design F11). Flutter's
 * `DateFormat('HH:mm')` is locale-neutral, so it's pinned to [Locale.ROOT] here. It shows the
 * time only, no date, as in Flutter. It redraws on minute boundaries, not every frame.
 *
 * @param textStyle merged under the zone's own colour, size and weight: what the text inherits
 *   from its surroundings (letter spacing, line height). The operator passes Flutter's.
 */
@Composable
fun ClockZone(
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
    color: Color = Color.White,
    textStyle: TextStyle = TextStyle.Default,
) {
    var now by remember(clock) { mutableStateOf(LocalTime.now(clock)) }
    LaunchedEffect(clock) {
        while (true) {
            now = LocalTime.now(clock)
            delay(millisToNextMinute(now))
        }
    }
    Text(
        text = formatClock(now),
        // A plain style: the TV theme's 20 sp line height doesn't belong to Flutter's zones.
        style = textStyle.merge(TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)),
        modifier = modifier.testTag(ZoneTags.CLOCK),
    )
}

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** `HH:mm`, 24-hour, as Flutter's `DateFormat('HH:mm')`. */
fun formatClock(time: LocalTime): String = time.format(CLOCK_FORMAT)

/** Until the next minute starts, plus a little so the new minute has begun. */
internal fun millisToNextMinute(time: LocalTime): Long =
    60_000L - (time.second * 1_000L + time.nano / 1_000_000L) + 5L
