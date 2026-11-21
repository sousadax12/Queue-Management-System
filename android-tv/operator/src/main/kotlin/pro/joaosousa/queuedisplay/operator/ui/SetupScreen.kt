package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.queues.QueueListState
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.ui.zones.QueueTile

private val TileWidth = 300.dp

/**
 * The queue picker (design F5, `lib/pages/setup_page.dart`), laid out as Flutter's: the app bar
 * with "Select a queue:" and the sign-out icon, then the scope's queues in one row of 300 dp
 * tiles, as tall as the screen allows and spaced evenly. D-pad left/right moves the green
 * highlight, select (or Enter, or a tap) picks; up reaches sign-out. A failed read offers Retry in
 * the app bar. More tiles than fit scroll instead of overflowing.
 */
@Composable
fun SetupScreen(
    queues: QueueListState,
    onSelect: (queueId: String) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    val signOutFocus = remember { FocusRequester() }
    val firstTileFocus = remember { FocusRequester() }
    val hasTiles = (queues as? QueueListState.Data)?.queues?.isNotEmpty() == true
    FlutterScaffold(
        title = stringResource(R.string.setup_title),
        actions = {
            if (queues is QueueListState.Failed) {
                FlutterButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(end = 8.dp).testTag(TestTags.RETRY),
                )
            }
            FlutterIconButton(
                icon = LogoutIcon,
                contentDescription = stringResource(R.string.sign_out),
                onClick = onSignOut,
                modifier = Modifier
                    .focusRequester(signOutFocus)
                    .focusProperties { if (hasTiles) down = firstTileFocus }
                    .testTag(TestTags.SIGN_OUT),
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (queues) {
                QueueListState.Loading -> QueueListMessage(QueueListMessageKind.Loading)
                is QueueListState.Failed -> QueueListMessage(QueueListMessageKind.Failed(queues.error))
                is QueueListState.Data ->
                    if (queues.queues.isEmpty()) {
                        QueueListMessage(QueueListMessageKind.Empty)
                    } else {
                        QueueRow(queues.queues, firstTileFocus, signOutFocus, onSelect)
                    }
            }
        }
    }
    LaunchedEffect(hasTiles) {
        if (hasTiles) firstTileFocus.requestFocus() else signOutFocus.requestFocus()
    }
}

@Composable
private fun QueueRow(
    queues: List<Queue>,
    firstTileFocus: FocusRequester,
    signOutFocus: FocusRequester,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag(TestTags.SETUP_ROW)) {
        val tile: @Composable (Int, Queue) -> Unit = { index, queue ->
            SetupTile(
                queue = queue,
                onSelect = { onSelect(queue.id) },
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier)
                    .focusProperties { up = signOutFocus },
            )
        }
        if (TileWidth * queues.size <= maxWidth) {
            // Flutter's `Row(mainAxisAlignment: spaceEvenly)`.
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                queues.forEachIndexed { index, queue -> tile(index, queue) }
            }
        } else {
            // Flutter overflows off the screen here; scroll instead.
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(queues, key = { _, q -> q.id }) { index, queue -> tile(index, queue) }
            }
        }
    }
}

/** Flutter's tile `Container`: 50 dp above and below, 300 dp wide, an 8 dp frame around the queue. */
@Composable
private fun SetupTile(queue: Queue, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .padding(vertical = 50.dp)
            .width(TileWidth)
            .fillMaxHeight()
            .onFocusChanged { focused = it.isFocused }
            // Clickable handles DPAD_CENTER, Enter and NumPad Enter on key up, and taps. No
            // indication: the green frame is the highlight, as in Flutter.
            .clickable(interactionSource = null, indication = null, onClick = onSelect)
            .background(if (focused) FlutterColors.GreenAccent else Color.Transparent, shape)
            .border(1.dp, if (focused) FlutterColors.GreenAccent else FlutterColors.Grey, shape)
            .padding(8.dp)
            .testTag(TestTags.setupTile(queue.id)),
    ) {
        QueueTile(queue, modifier = Modifier.fillMaxSize(), textStyle = FlutterText.Inherited)
    }
}
