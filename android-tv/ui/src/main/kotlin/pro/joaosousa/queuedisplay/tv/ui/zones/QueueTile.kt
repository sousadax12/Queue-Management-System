package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.tv.domain.model.Queue

/**
 * One queue: its Font Awesome icon and name over its number, on the queue's colours. A port of the
 * Flutter `QueueWidget` (`lib/widgets/queue_widget.dart`): name 45 sp bold, number 70 sp bold.
 * Flutter mapped the icon but never drew it (design F12); here it sits before the name (design
 * Q7). On a narrow column the name shrinks (down to 28 sp) rather than break inside a word, and
 * wraps to at most two lines, then ends in an ellipsis.
 *
 * Shared by the operator's setup and home screens and, later, the TV's queue zone.
 */
@Composable
fun QueueTile(queue: Queue, modifier: Modifier = Modifier) {
    val foreground = Color(queue.foregroundColor.toInt())
    Column(
        modifier = modifier.background(Color(queue.backgroundColor.toInt())),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueIcon(codePoint = queue.icon, color = foreground, size = ICON_SIZE)
            BasicText(
                text = queue.name,
                style = TextStyle(color = foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = NAME_AUTO_SIZE,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = queue.number.toString(),
            style = TextStyle(color = foreground, fontSize = 70.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** A Font Awesome Solid glyph by code point; nothing for an invalid one. Decorative, so no semantics. */
@Composable
fun QueueIcon(codePoint: Int, color: Color, size: TextUnit, modifier: Modifier = Modifier) {
    val glyph = FontAwesome.glyph(codePoint) ?: return
    Text(
        text = glyph,
        style = TextStyle(color = color, fontSize = size, fontFamily = FontAwesome.Solid, fontWeight = FontWeight.Black),
        modifier = modifier.testTag(ZoneTags.QUEUE_ICON).clearAndSetSemantics { },
    )
}

private val ICON_SIZE = 36.sp
private val NAME_AUTO_SIZE = WholeWordsAutoSize(min = 28.sp, max = 45.sp)
