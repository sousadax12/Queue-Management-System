package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Sizes a queue name next to its icon, from [max] down in [step]s:
 * 1. the largest size down to [oneLineMin] at which it fits on one line ("Balcão A" shrinks a
 *    little rather than drop the "A" to a second line);
 * 2. else the largest size down to [min] at which it fits and every line break falls between
 *    words ("Consultas" never becomes "Consul / tas");
 * 3. else [min], wrapping and ellipsising as the text's own settings say.
 */
internal class WholeWordsAutoSize(
    private val min: TextUnit,
    private val oneLineMin: TextUnit,
    private val max: TextUnit,
    private val step: TextUnit = 1.sp,
) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val layout = { size: Float -> performLayout(constraints, text, size.sp) }
        largest(oneLineMin, layout) { !it.hasVisualOverflow && it.lineCount == 1 }?.let { return it }
        largest(min, layout) { !it.hasVisualOverflow && it.breaksOnlyBetweenWords(text.text) }?.let { return it }
        return min
    }

    private inline fun largest(
        floor: TextUnit,
        layout: (Float) -> TextLayoutResult,
        fits: (TextLayoutResult) -> Boolean,
    ): TextUnit? {
        var size = max.value
        while (size >= floor.value) {
            if (fits(layout(size))) return size.sp
            size -= step.value
        }
        return null
    }

    private fun TextLayoutResult.breaksOnlyBetweenWords(text: String): Boolean =
        (0 until lineCount - 1).all { line ->
            val end = getLineEnd(line)
            end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end - 1] == '-'
        }

    override fun equals(other: Any?) =
        other is WholeWordsAutoSize && other.min == min && other.oneLineMin == oneLineMin &&
            other.max == max && other.step == step

    override fun hashCode() = ((min.hashCode() * 31 + oneLineMin.hashCode()) * 31 + max.hashCode()) * 31 + step.hashCode()
}
