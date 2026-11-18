package pro.joaosousa.queueoperator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults
import pro.joaosousa.queueoperator.ui.components.CurrentDateBar
import pro.joaosousa.queueoperator.ui.components.ErrorBanner
import pro.joaosousa.queueoperator.ui.components.OperatorTopBar
import pro.joaosousa.queueoperator.ui.components.QueueCard
import pro.joaosousa.queueoperator.ui.components.TickerBar
import pro.joaosousa.queueoperator.ui.components.VideoZone
import pro.joaosousa.queueoperator.ui.OperatorEvent
import pro.joaosousa.queueoperator.ui.OperatorUiState

/**
 * The working screen, laid out like the Flutter `HomePage`: queue column on the
 * left (flex 1), the queue's media panel on the right (flex 3) with the RSS strip
 * at its bottom edge and the clock in its top corner.
 *
 * One deliberate change of interaction: Flutter advanced the number on the arrow
 * keys, which a phone or tablet does not have. The native terminal advances on a
 * *tap of the selected queue's tile* — tapping another tile selects it instead —
 * so a clerk can drive the whole screen with one finger.
 */
@Composable
fun HomeScreen(
    state: OperatorUiState,
    onEvent: (OperatorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = remember(state.queues, state.selectedQueueId) {
        state.queues.firstOrNull { it.id == state.selectedQueueId }
    }
    val siteName = remember(state.sites, state.selectedSiteId) {
        state.sites.firstOrNull { it.id == state.selectedSiteId }?.name
    }

    Column(modifier = modifier.fillMaxSize()) {
        OperatorTopBar(
            title = "Senhas",
            subtitle = siteName,
            onSignOut = { onEvent(OperatorEvent.SignOut) },
        )

        if (state.error != null) {
            ErrorBanner(
                message = state.error,
                onRetry = { onEvent(OperatorEvent.Retry) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            QueueColumn(
                queues = state.queues,
                selectedQueueId = state.selectedQueueId,
                busy = state.busy,
                onEvent = onEvent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )

            MediaPanel(
                queue = selected,
                ticker = state,
                nowMillis = state.nowMillis,
                modifier = Modifier.weight(3f).fillMaxHeight(),
            )
        }
    }
}

/**
 * Every queue the operator may advance, stacked so each tile keeps the full width
 * of the column — the number stays legible at arm's length.
 */
@Composable
private fun QueueColumn(
    queues: List<Queue>,
    selectedQueueId: String?,
    busy: Boolean,
    onEvent: (OperatorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        queues.isEmpty() -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Nenhuma senha encontrada",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyColumn(
            modifier = modifier.background(MaterialTheme.colorScheme.background).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(queues, key = Queue::id) { queue ->
                val isSelected = queue.id == selectedQueueId
                QueueCard(
                    queue = queue,
                    selected = isSelected,
                    subtitle = if (isSelected) "Toque para avançar" else "Toque para escolher",
                    onClick = {
                        if (busy) return@QueueCard
                        if (isSelected) onEvent(OperatorEvent.AdvanceNumber)
                        else onEvent(OperatorEvent.SelectQueue(queue.id))
                    },
                )
            }
        }
    }
}

/**
 * Black media panel: video, the ticker pinned to the bottom, time in the corner.
 *
 * It is always black, even when there is nothing to play, so the layout does not
 * flash white between queue switches and the video keeps the contrast the Flutter
 * app gave it.
 */
@Composable
private fun MediaPanel(
    queue: Queue?,
    ticker: OperatorUiState,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        if (queue == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Escolha uma senha para começar",
                    color = Color(0xFFBFC6CF),
                )
            }
        } else {
            VideoZone(
                videoUrl = queue.videoUrl,
                volume = queue.videoVolume,
                modifier = Modifier.fillMaxSize(),
            )
            if (queue.videoUrl.isBlank()) {
                // A queue without a video is normal in the portal; say so instead
                // of showing an unexplained black rectangle.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sem vídeo para esta senha",
                        color = Color(0xFFBFC6CF),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        CurrentDateBar(
            nowMillis = nowMillis,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )

        TickerBar(
            items = ticker.ticker,
            velocityDpPerSec = queue?.velocity?.toDouble() ?: DEFAULT_TICKER_SPEED,
            error = ticker.tickerError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Mirrors the contract's `QUEUE_DEFAULTS.velocity` so the strip still moves. */
private val DEFAULT_TICKER_SPEED = QueueDefaults.VELOCITY.toDouble()
