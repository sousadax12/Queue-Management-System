package pro.joaosousa.queuedisplay.tv.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueueDefaultsTest {

    @Test
    fun `defaults mirror QUEUE_DEFAULTS in the portal contract`() {
        assertEquals(0L, QueueDefaults.NUMBER)
        assertEquals(0xf007L, QueueDefaults.ICON)
        assertEquals(0xff2d5b9fL, QueueDefaults.BACKGROUND_COLOR)
        assertEquals(0xffffffffL, QueueDefaults.FOREGROUND_COLOR)
        assertEquals("", QueueDefaults.VIDEO_URL)
        assertEquals(0.0, QueueDefaults.VIDEO_VOLUME)
        assertEquals(1.0, QueueDefaults.SOUND_VOLUME)
        assertEquals(60L, QueueDefaults.VELOCITY)
    }

    @Test
    fun `ARGB colours survive as Long, above Int MAX_VALUE`() {
        // Every colour the portal writes is a Long: the fixtures carry 4282339765 (0xff3f51b5),
        // 4281162655 (0xff2d5b9f, the default below) and 4294967295 (0xffffffff, white).
        assertEquals(0xff3f51b5L, 4282339765L)
        assertEquals(0xff2d5b9fL, 4281162655L)
        assertEquals(4281162655L, QueueDefaults.BACKGROUND_COLOR)
        assertTrue(QueueDefaults.BACKGROUND_COLOR > Int.MAX_VALUE.toLong())
        assertTrue(QueueDefaults.FOREGROUND_COLOR > Int.MAX_VALUE.toLong())
    }
}
