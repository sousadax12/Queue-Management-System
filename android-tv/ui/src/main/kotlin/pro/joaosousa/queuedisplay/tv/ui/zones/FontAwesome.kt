package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import pro.joaosousa.queuedisplay.tv.ui.R

/**
 * Font Awesome 7 Free Solid, the font `font_awesome_flutter` 11 maps its `FontAwesomeSolid`
 * family to (design F12). Queue docs store its code points, so they render as in Flutter.
 * SIL OFL 1.1: see `assets/licenses/font-awesome-free.txt`.
 */
object FontAwesome {
    val Solid: FontFamily = FontFamily(Font(R.font.fa_solid_900, FontWeight.Black))

    /** The glyph for [codePoint] as text, or null if it isn't a Unicode code point. */
    fun glyph(codePoint: Int): String? =
        if (codePoint > 0 && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else null
}
