package pro.joaosousa.queueoperator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import pro.joaosousa.queuedisplay.tv.domain.model.FeedItem

/** Same separator the Flutter ticker used between headlines. */
private const val SEPARATOR = "   |   "

/**
 * The RSS ticker strip under the video.
 *
 * Flutter used `text_scroll` in endless mode; this is the same behaviour on
 * Compose primitives, because the module has no marquee dependency: one
 * [Animatable] drives an x-offset from "just off the right edge" to "just past
 * the left edge" at the queue's own speed, then repeats.
 *
 * A broken feed must not blank the counter screen, so [error] renders an inline
 * notice instead of throwing, and an empty [items] renders an empty strip — the
 * Flutter widget's behaviour while the feed was still loading.
 */
@Composable
fun TickerBar(
    items: List<FeedItem>,
    velocityDpPerSec: Double,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val headline = remember(items) {
        items.map { it.title.trim() }.filter { it.isNotEmpty() }.joinToString(SEPARATOR)
    }

    Surface(
        modifier = modifier.fillMaxSize().height(44.dp),
        color = Color.Black,
    ) {
        when {
            error != null -> Text(
                text = "Ticker indisponível: $error",
                color = Color(0xFFFFB4AB),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxSize(),
            )

            headline.isNotEmpty() -> MarqueeText(text = headline, speedDpPerSec = velocityDpPerSec)
            else -> Unit
        }
    }
}

/**
 * Scrolls [text] right-to-left forever at [speedDpPerSec]. The animation length
 * is derived from the measured text width, so a long feed scrolls at the same
 * speed as a short one instead of racing.
 */
@Composable
private fun MarqueeText(text: String, speedDpPerSec: Double) {
    val density = LocalDensity.current
    var textWidthPx by remember(text) { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val viewportPx = with(density) { maxWidth.toPx() }
        // 10 dp/s floor: a queue may store velocity 0, and a frozen ticker reads
        // as a bug at the counter.
        val speedPx = with(density) { speedDpPerSec.coerceAtLeast(10.0).dp.toPx() }
        val distancePx = viewportPx + textWidthPx
        val durationMillis = ((distancePx / speedPx) * 1000f).roundToInt().coerceAtLeast(1_000)

        val progress = remember(text) { Animatable(0f) }
        LaunchedEffect(text, durationMillis) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        }

        Text(
            text = text,
            color = Color.White,
            fontSize = 20.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { textWidthPx = it.width }
                .offset { IntOffset((viewportPx - progress.value * distancePx).roundToInt(), 0) },
        )
    }
}