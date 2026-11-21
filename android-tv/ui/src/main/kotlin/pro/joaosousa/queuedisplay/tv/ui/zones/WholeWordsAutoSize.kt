package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The largest font size from [max] down to [min], in [step]s, at which the text fits its lines
 * and every line break falls between words. A queue name like "Consultas" shrinks a little on a
 * narrow column instead of breaking into "Consul / tas". At [min] it gives up and lets the
 * text wrap and ellipsise.
 */
internal class WholeWordsAutoSize(
    private val min: TextUnit,
    private val max: TextUnit,
    private val step: TextUnit = 1.sp,
) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > min.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.hasVisualOverflow && layout.breaksOnlyBetweenWords(text.text)) return size.sp
            size -= step.value
        }
        return min
    }

    private fun TextLayoutResult.breaksOnlyBetweenWords(text: String): Boolean =
        (0 until lineCount - 1).all { line ->
            val end = getLineEnd(line)
            end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end - 1] == '-'
        }

    override fun equals(other: Any?) =
        other is WholeWordsAutoSize && other.min == min && other.max == max && other.step == step

    override fun hashCode() = (min.hashCode() * 31 + max.hashCode()) * 31 + step.hashCode()
}
