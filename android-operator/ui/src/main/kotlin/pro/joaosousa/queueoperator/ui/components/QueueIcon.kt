package pro.joaosousa.queueoperator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults

/**
 * Renders a queue's icon, which the contract carries as a **FontAwesome solid
 * code point** (`queues.icon`, e.g. `0xf007` = `fa-user`, D7).
 *
 * Those glyphs only appear if the FontAwesome TTF is shipped in `res/font/` and
 * loaded with `FontFamily(Font(R.font.fa_solid_900))`, and the operator `:ui`
 * module has no font asset and no icon dependency (adding one is a build change
 * this port must not make). So the code point is drawn as a hand-rolled vector
 * stand-in: the portal's default `fa-user` gets a real person silhouette, and
 * every other code point gets a neutral badge. Swapping in the glyph later is a
 * one-line change here, not a contract change.
 *
 * TODO(design #17): ship `ui/src/main/res/font/fa_solid_900.ttf` (Font Awesome
 * 6 Free Solid) and replace the Canvas with a `Text(icon.toChar().toString())`
 * in that FontFamily — the code point stays the source of truth.
 */
@Composable
fun QueueIcon(
    codePoint: Long,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Ícone da senha"
        },
    ) {
        val unit = size.minDimension / 10f
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = Stroke(width = unit.coerceAtLeast(1.5f))

        if (codePoint == QueueDefaults.ICON) {
            // fa-user: head above shoulders, the portal's default (QUEUE_DEFAULTS).
            drawCircle(
                color = tint,
                radius = unit * 2f,
                center = Offset(center.x, center.y - unit * 1.6f),
                style = stroke,
            )
            drawArc(
                color = tint,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(center.x - unit * 3.2f, center.y + unit * 0.4f),
                size = Size(unit * 6.4f, unit * 4.6f),
                style = stroke,
            )
        } else {
            // Unknown code point: an empty badge still marks the column, so the
            // card's layout does not jump when the TTF lands.
            drawCircle(color = tint, radius = unit * 4f, center = center, style = stroke)
            drawCircle(color = tint, radius = unit * 0.9f, center = center)
        }
    }
}