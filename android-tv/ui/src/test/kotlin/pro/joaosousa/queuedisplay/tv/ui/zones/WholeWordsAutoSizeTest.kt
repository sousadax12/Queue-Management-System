package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Needs native graphics: the legacy mode doesn't measure text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WholeWordsAutoSizeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun layout(text: String, widthDp: Int): TextLayoutResult {
        var result: TextLayoutResult? = null
        composeRule.setContent {
            Box(Modifier.width(widthDp.dp)) {
                BasicText(
                    text = text,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = WholeWordsAutoSize(min = 28.sp, oneLineMin = 36.sp, max = 45.sp),
                    onTextLayout = { result = it },
                )
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(result)
    }

    @Test
    fun keepsTheFullSizeWhenItFits() {
        assertEquals(45.sp, layout("A", 300).layoutInput.style.fontSize)
    }

    @Test
    fun shrinksALittleToStayOnOneLine() {
        val result = layout("Balcão A", 150)

        assertEquals(1, result.lineCount)
        assertTrue(result.layoutInput.style.fontSize.value in 36f..44f)
    }

    @Test
    fun wrapsOnlyBetweenWordsWhenOneLineWouldBeTooSmall() {
        val text = "Sala de Espera"
        val result = layout(text, 150)

        assertEquals(2, result.lineCount)
        assertTrue("broke inside a word", text[result.getLineEnd(0) - 1].isWhitespace())
        assertTrue(result.layoutInput.style.fontSize.value >= 28f)
    }
}
