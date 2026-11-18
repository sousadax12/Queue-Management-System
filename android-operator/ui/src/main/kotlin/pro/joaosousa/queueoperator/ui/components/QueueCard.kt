package pro.joaosousa.queueoperator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pro.joaosousa.queuedisplay.tv.domain.model.Queue

/**
 * One counter's tile: the queue name over its current number, painted with the
 * colours the portal stores on the queue.
 *
 * Same information hierarchy as the Flutter `QueueWidget` (name, then a much
 * larger number), plus the queue's icon so a clerk can tell two queues apart at
 * a glance. [selected] marks the queue the terminal is advancing; the home
 * screen and the setup list both use it.
 */
@Composable
fun QueueCard(
    queue: Queue,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    // The contract stores ARGB as an unsigned 32-bit Long, so the low 32 bits are
    // the colour the portal wrote (Queue's KDoc).
    val background = Color(queue.backgroundColor.toInt())
    val foreground = Color(queue.foregroundColor.toInt())
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(width = if (selected) 3.dp else 1.dp, color = border, shape = RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QueueIcon(
            codePoint = queue.icon,
            tint = foreground,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = queue.name,
            color = foreground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = queue.number.toString(),
            color = foreground,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = foreground.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}