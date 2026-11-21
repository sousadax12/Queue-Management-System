package pro.joaosousa.queuedisplay.tv.ui.zones

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pro.joaosousa.queuedisplay.tv.domain.model.Queue

@RunWith(RobolectricTestRunner::class)
class ZonesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theTickerJoinsHeadlinesAsFlutterDid() {
        composeRule.setContent { TickerZone(headlines = listOf("One", "Two", "Three"), velocity = 60.0) }

        composeRule.onNodeWithText("One   |   Two   |   Three").assertExists()
    }

    @Test
    fun theTickerHidesWithNoHeadlines() {
        composeRule.setContent { TickerZone(headlines = emptyList(), velocity = 60.0) }

        composeRule.onNodeWithTag(ZoneTags.TICKER).assertDoesNotExist()
    }

    @Test
    fun theClockShowsTheTimeInItsZone() {
        val lisbonSummer = Clock.fixed(Instant.parse("2026-07-01T08:05:59Z"), ZoneId.of("Europe/Lisbon"))
        composeRule.setContent { ClockZone(clock = lisbonSummer) }

        composeRule.onNodeWithTag(ZoneTags.CLOCK).assertExists()
        composeRule.onNodeWithText("09:05").assertExists()
    }

    @Test
    fun theClockIsTwentyFourHourWithLeadingZeros() {
        assertEquals("00:00", formatClock(LocalTime.MIDNIGHT))
        assertEquals("07:03", formatClock(LocalTime.of(7, 3, 59)))
        assertEquals("23:59", formatClock(LocalTime.of(23, 59)))
    }

    @Test
    fun theClockWakesJustAfterTheNextMinute() {
        assertEquals(60_005L, millisToNextMinute(LocalTime.of(9, 5)))
        assertEquals(1_005L, millisToNextMinute(LocalTime.of(9, 5, 59)))
        assertEquals(505L, millisToNextMinute(LocalTime.of(9, 5, 59, 500_000_000)))
    }

    @Test
    fun theTileShowsTheIconNameAndNumber() {
        composeRule.setContent { QueueTile(queue(name = "Balcão A", number = 42, icon = 0xf007)) }

        composeRule.onNodeWithText("Balcão A").assertExists()
        composeRule.onNodeWithText("42").assertExists()
        composeRule.onNodeWithTag(ZoneTags.QUEUE_ICON, useUnmergedTree = true).assertExists()
    }

    @Test
    fun anInvalidCodePointDrawsNoIcon() {
        composeRule.setContent { QueueTile(queue(icon = -1)) }

        composeRule.onNodeWithTag(ZoneTags.QUEUE_ICON, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun glyphsAreTheCodePointsAsText() {
        assertEquals("", FontAwesome.glyph(61713))
        assertNull(FontAwesome.glyph(0))
        assertNull(FontAwesome.glyph(0x110000))
    }

    private fun queue(name: String = "Q", number: Int = 1, icon: Int) = Queue(
        id = "q",
        siteId = "s",
        name = name,
        number = number,
        icon = icon,
        backgroundColor = 0xff2d5b9fL,
        foregroundColor = 0xffffffffL,
        videoUrl = "",
        videoVolume = 0.0,
        soundVolume = 1.0,
        velocity = 60.0,
    )
}
