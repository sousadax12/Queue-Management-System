package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.captureRoboImage
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pro.joaosousa.queuedisplay.tv.ui.R

/**
 * Queue docs store `font_awesome_flutter` code points. Flutter reads them as
 * `IconData(icon, fontFamily: 'FontAwesomeSolid', fontPackage: 'font_awesome_flutter')`
 * (`lib/mappers/queue_doc_mapper.dart`); in font_awesome_flutter 11.0.0 (`pubspec.lock`) that
 * family is `lib/fonts/Font-Awesome-7-Free-Solid-900.otf`, and `FontAwesomeIcons.solidCircle` is
 * `0xf111` = 61713. So the same glyph means: the same font file, and a filled circle on screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontAwesomeGlyphTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theBundledFontIsFontAwesomeFlutters() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val bytes = resources.openRawResource(R.font.fa_solid_900).use { it.readBytes() }

        assertEquals(FONT_AWESOME_FLUTTER_11_SOLID_SHA256, sha256(bytes))
    }

    @Test
    fun codePoint61713IsTheSolidCircle() {
        composeRule.setContent {
            Box(Modifier.background(Color.White)) {
                QueueIcon(codePoint = SOLID_CIRCLE, color = Color.Black, size = 120.sp)
            }
        }
        val icon = composeRule.onNodeWithTag(ZoneTags.QUEUE_ICON, useUnmergedTree = true)
        icon.captureRoboImage("src/test/screenshots/fa_solid_61713.png")

        val pixels = icon.captureToImage().toPixelMap()
        var left = Int.MAX_VALUE
        var right = -1
        var top = Int.MAX_VALUE
        var bottom = -1
        var ink = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y].alpha > 0.5f && pixels[x, y].red < 0.5f) {
                    ink++
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        val width = right - left + 1
        val height = bottom - top + 1
        assertTrue("nothing drawn: the font has no glyph for $SOLID_CIRCLE", ink > 0)
        // A circle: as wide as it is tall, solid in the middle, filling π/4 of its box.
        assertEquals("not round: ${width}x$height", 1.0, width.toDouble() / height, 0.05)
        val centre = pixels[(left + right) / 2, (top + bottom) / 2]
        assertTrue("hollow centre", centre.red < 0.5f)
        val fill = ink.toDouble() / (width * height)
        assertEquals("not a filled disc: fill $fill", Math.PI / 4, fill, 0.03)
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOLID_CIRCLE = 61713

        /** `font_awesome_flutter-11.0.0/lib/fonts/Font-Awesome-7-Free-Solid-900.otf` (Font Awesome 7.2.0). */
        const val FONT_AWESOME_FLUTTER_11_SOLID_SHA256 =
            "c1091147299a846195bbca8b26528de6c9af842f236e7db44a1c2e8c9df52372"
    }
}
