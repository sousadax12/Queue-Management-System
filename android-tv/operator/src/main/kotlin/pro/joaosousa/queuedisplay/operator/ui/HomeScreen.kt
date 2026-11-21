package pro.joaosousa.queuedisplay.operator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.queues.QueueError
import pro.joaosousa.queuedisplay.operator.queues.QueueListState
import pro.joaosousa.queuedisplay.tv.domain.operator.QueueNumber
import pro.joaosousa.queuedisplay.tv.ui.zones.QueueTile

/**
 * Home (design F6/F7, `lib/pages/home_page.dart`): the live queue column on the left (flex 1) and
 * the display area on the right (flex 3). D-pad right/left on key up advance the selected queue
 * by +1/−1. Back returns to setup.
 *
 * The display area stays black for now: the clock and ticker arrive in O4, the video in O5.
 */
@Composable
fun HomeScreen(
    queues: QueueListState,
    writeError: QueueError?,
    onAdvance: (QueueNumber.Direction) -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionRight -> QueueNumber.Direction.Up
                    Key.DirectionLeft -> QueueNumber.Direction.Down
                    else -> return@onKeyEvent false
                }
                // Act on key up, as Flutter does; swallow the key down too.
                if (event.type == KeyEventType.KeyUp) onAdvance(direction)
                true
            }
            .focusable()
            .testTag(TestTags.HOME),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            QueueColumn(queues, modifier = Modifier.weight(1f).fillMaxHeight())
            Box(modifier = Modifier.weight(3f).fillMaxHeight().background(Color.Black))
        }
        if (writeError != null) {
            Text(
                text = writeErrorText(writeError),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .background(Color(0xCCB00020), RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag(TestTags.WRITE_ERROR),
            )
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun QueueColumn(queues: QueueListState, modifier: Modifier = Modifier) {
    when (queues) {
        QueueListState.Loading -> CenteredIn(modifier) { QueueListMessage(QueueListMessageKind.Loading) }
        is QueueListState.Failed -> CenteredIn(modifier) { QueueListMessage(QueueListMessageKind.Failed(queues.error)) }
        is QueueListState.Data ->
            if (queues.queues.isEmpty()) {
                CenteredIn(modifier) { QueueListMessage(QueueListMessageKind.Empty) }
            } else {
                Column(modifier = modifier) {
                    queues.queues.forEach { queue ->
                        QueueTile(
                            queue = queue,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag(TestTags.homeTile(queue.id)),
                        )
                    }
                }
            }
    }
}

@Composable
private fun CenteredIn(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
}
