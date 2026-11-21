package pro.joaosousa.queuedisplay.operator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import java.time.Clock
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.operator.QueueNumber
import pro.joaosousa.queuedisplay.tv.ui.zones.ClockZone
import pro.joaosousa.queuedisplay.tv.ui.zones.QueueTile
import pro.joaosousa.queuedisplay.tv.ui.zones.TickerZone
import pro.joaosousa.queuedisplay.tv.ui.zones.video.LegacyVideoZone

/**
 * Home (design F6/F7, `lib/pages/home_page.dart`): the live queue column on the left (flex 1) and
 * the display area on the right (flex 3). The display area is black, with the [video] filling it,
 * the ticker along its bottom and the clock in its top-right corner, as in Flutter. D-pad
 * right/left on key up advance the selected queue by +1/−1. Back returns to setup.
 *
 * @param headlines the ticker's; empty hides it.
 * @param tickerVelocity the selected queue's `velocity`, in dp per second.
 * @param video the video slot, laid out over the whole display area under the ticker and clock.
 *   The app passes [HomeVideoSlot]; previews and goldens leave it empty.
 */
@Composable
fun HomeScreen(
    queues: QueueListState,
    writeError: QueueError?,
    headlines: List<String>,
    tickerVelocity: Double,
    onAdvance: (QueueNumber.Direction) -> Unit,
    onBack: () -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
    video: @Composable BoxScope.() -> Unit = {},
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
            DisplayArea(
                headlines = headlines,
                tickerVelocity = tickerVelocity,
                clock = clock,
                video = video,
                modifier = Modifier.weight(3f).fillMaxHeight(),
            )
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

/** Flutter's `Stack`: video at the bottom, then the ticker, then the clock. */
@Composable
private fun DisplayArea(
    headlines: List<String>,
    tickerVelocity: Double,
    clock: Clock,
    video: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black).testTag(TestTags.DISPLAY_AREA)) {
        Box(
            modifier = Modifier.fillMaxSize().testTag(TestTags.VIDEO_SLOT),
            contentAlignment = Alignment.Center,
            content = video,
        )
        TickerZone(headlines = headlines, velocity = tickerVelocity, modifier = Modifier.align(Alignment.BottomCenter))
        ClockZone(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp), clock = clock)
    }
}

/**
 * The video area of home (design F9), following the live [queue] doc (`videoUrl`, `videoVolume`):
 * a new URL switches the video in place. Black while no queue is selected.
 */
@Composable
fun BoxScope.HomeVideoSlot(queue: Queue?) {
    if (queue == null) return
    LegacyVideoZone(
        videoUrl = queue.videoUrl,
        videoVolume = queue.videoVolume,
        modifier = Modifier.matchParentSize(),
    )
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
