package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
 * Q7). On a narrow column the name shrinks to stay on one line (down to 36 sp), then to wrap only
 * between words (down to 28 sp); it takes at most two lines, then ends in an ellipsis.
 *
 * Shared by the operator's setup and home screens and, later, the TV's queue zone.
 *
 * @param textStyle merged under the name's and number's own colour, size and weight: what the
 *   texts inherit from their surroundings (letter spacing, line height). The operator passes
 *   Flutter's.
 */
@Composable
fun QueueTile(queue: Queue, modifier: Modifier = Modifier, textStyle: TextStyle = TextStyle.Default) {
    val foreground = Color(queue.foregroundColor.toInt())
    Column(
        modifier = modifier.background(Color(queue.backgroundColor.toInt())),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // With an em line height, keep the row as tall as the name's line at full size, so a name
        // that shrinks next to its icon doesn't pull the number up.
        val nameLine = textStyle.lineHeight
        val minNameHeight = if (nameLine.isEm) with(LocalDensity.current) { (NAME_MAX_SIZE * nameLine.value).toDp() } else 0.dp
        Row(
            modifier = Modifier.padding(8.dp).heightIn(min = minNameHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueIcon(codePoint = queue.icon, color = foreground, size = ICON_SIZE)
            BasicText(
                text = queue.name,
                style = textStyle.merge(TextStyle(color = foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = NAME_AUTO_SIZE,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = queue.number.toString(),
            style = textStyle.merge(TextStyle(color = foreground, fontSize = 70.sp, fontWeight = FontWeight.Bold)),
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
private val NAME_MAX_SIZE = 45.sp
private val NAME_AUTO_SIZE = WholeWordsAutoSize(min = 28.sp, oneLineMin = 36.sp, max = NAME_MAX_SIZE)
