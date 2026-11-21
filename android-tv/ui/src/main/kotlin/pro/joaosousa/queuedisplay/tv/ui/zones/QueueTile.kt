package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.tv.domain.model.Queue

/**
 * One queue: its name over its number, on the queue's colours. A port of the Flutter
 * `QueueWidget` (`lib/widgets/queue_widget.dart`): name 45 sp bold, number 70 sp bold.
 *
 * Shared by the operator's setup and home screens and, later, the TV's queue zone. The Font Awesome
 * icon (design F12) arrives with the display zones (operator O4).
 */
@Composable
fun QueueTile(queue: Queue, modifier: Modifier = Modifier) {
    val foreground = Color(queue.foregroundColor.toInt())
    Column(
        modifier = modifier.background(Color(queue.backgroundColor.toInt())),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = queue.name,
            color = foreground,
            fontSize = 45.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = queue.number.toString(),
            color = foreground,
            fontSize = 70.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp),
        )
    }
}
