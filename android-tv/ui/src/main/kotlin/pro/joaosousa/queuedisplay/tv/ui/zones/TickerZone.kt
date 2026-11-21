package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * The news ticker (Flutter `RssFeedWidget`, design F10): the headlines joined by `   |   `,
 * white 20 sp in a 40 dp strip, scrolling endlessly at the queue's [velocity] (Flutter logical
 * pixels per second, so dp/s). With no headlines it shows nothing at all, as Flutter did on any
 * error. Headlines that fit the width stand still, as with Flutter's `TextScroll`.
 */
@Composable
fun TickerZone(headlines: List<String>, velocity: Double, modifier: Modifier = Modifier) {
    if (headlines.isEmpty()) return
    Box(
        modifier = modifier.fillMaxWidth().height(40.dp).testTag(ZoneTags.TICKER),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = headlines.joinToString(SEPARATOR),
            style = TextStyle(color = Color.White, fontSize = 20.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = if (velocity > 0) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 0,
                    repeatDelayMillis = 0,
                    spacing = MarqueeSpacing(48.dp),
                    velocity = velocity.dp,
                )
            } else {
                Modifier
            },
        )
    }
}

/** Flutter's separator between headlines. */
private const val SEPARATOR = "   |   "
