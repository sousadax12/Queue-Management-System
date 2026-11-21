package pro.joaosousa.queuedisplay.tv.data.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults

class QueueMapperTest {

    private val legacy = ContractFixtures.load("queues.legacy.json")
    private val queues = ContractFixtures.load("queues.json")

    @Test
    fun `portal queue keeps Long colours above Int MAX_VALUE`() {
        val q = QueueMapper.map("q_balcao_a_P4tc", queues.getValue("queues/q_balcao_a_P4tc")).value!!
        assertEquals(42, q.number)
        assertEquals(4282339765L, q.backgroundColor)
        assertTrue(q.backgroundColor > Int.MAX_VALUE)
        assertEquals(4294967295L, q.foregroundColor)
        assertEquals("site_porto_3fKq", q.siteId)
    }

    @Test
    fun `legacy Flutter docs map like the Flutter mapper does`() {
        val q = QueueMapper.map("legacy_balcao_1", legacy.getValue("queues/legacy_balcao_1"))
        assertEquals(emptyList<ContractIssue>(), q.issues)
        with(q.value!!) {
            assertEquals(12, number)
            assertEquals(61713, icon)
            assertEquals("Balcão 1", name)
            assertEquals(4294198070L, backgroundColor)
            // Flutter hard-codes white; the field didn't exist then.
            assertEquals(QueueDefaults.FOREGROUND_COLOR, foregroundColor)
            assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ", videoUrl)
            assertEquals(1.0, videoVolume, 0.0)
            assertEquals(50.0, velocity, 0.0)
            assertEquals(null, siteId)
        }
    }

    @Test
    fun `hand-made doc with missing fields takes the defaults silently`() {
        val q = QueueMapper.map("legacy_manual_3", legacy.getValue("queues/legacy_manual_3"))
        assertEquals(emptyList<ContractIssue>(), q.issues)
        with(q.value!!) {
            assertEquals(5, number)
            assertEquals(QueueDefaults.ICON, icon)
            assertEquals(QueueDefaults.VIDEO_URL, videoUrl)
            assertEquals(QueueDefaults.VIDEO_VOLUME, videoVolume, 0.0)
            assertEquals(QueueDefaults.VELOCITY, velocity, 0.0)
            assertEquals(0.5, soundVolume, 0.0)
        }
    }

    @Test
    fun `an empty doc is all defaults`() {
        val q = QueueMapper.map("empty", emptyMap()).value!!
        assertEquals(QueueDefaults.NAME, q.name)
        assertEquals(QueueDefaults.NUMBER, q.number)
        assertEquals(QueueDefaults.BACKGROUND_COLOR, q.backgroundColor)
        assertEquals(QueueDefaults.SOUND_VOLUME, q.soundVolume, 0.0)
    }

    @Test
    fun `number as a string is reported and defaulted`() {
        val q = QueueMapper.map("q", mapOf("number" to "7", "name" to "A"))
        assertEquals(QueueDefaults.NUMBER, q.value!!.number)
        assertEquals(listOf("queues/q.number"), q.issues.map { it.path })
    }

    @Test
    fun `integer-valued doubles are read through Number`() {
        val q = QueueMapper.map(
            "q",
            mapOf("number" to 7.0, "icon" to 61713.0, "backgroundColor" to 4282339765.0, "soundVolume" to 1L, "velocity" to 60L),
        )
        assertEquals(emptyList<ContractIssue>(), q.issues)
        with(q.value!!) {
            assertEquals(7, number)
            assertEquals(61713, icon)
            assertEquals(4282339765L, backgroundColor)
            assertEquals(1.0, soundVolume, 0.0)
            assertEquals(60.0, velocity, 0.0)
        }
    }

    @Test
    fun `a fractional number is reported, not truncated`() {
        val q = QueueMapper.map("q", mapOf("number" to 7.5))
        assertEquals(QueueDefaults.NUMBER, q.value!!.number)
        assertEquals(1, q.issues.size)
    }

    @Test
    fun `a signed 32-bit colour keeps its bit pattern`() {
        val q = QueueMapper.map("q", mapOf("backgroundColor" to 0xff2d5b9f.toInt().toLong()))
        assertEquals(0xff2d5b9fL, q.value!!.backgroundColor)
    }

    @Test
    fun `out-of-range colours and volumes are reported`() {
        val q = QueueMapper.map("q", mapOf("backgroundColor" to 0x1_0000_0000L, "soundVolume" to 3L, "videoVolume" to "loud"))
        with(q.value!!) {
            assertEquals(QueueDefaults.BACKGROUND_COLOR, backgroundColor)
            assertEquals(1.0, soundVolume, 0.0)
            assertEquals(QueueDefaults.VIDEO_VOLUME, videoVolume, 0.0)
        }
        assertEquals(
            listOf("queues/q.backgroundColor", "queues/q.videoVolume", "queues/q.soundVolume"),
            q.issues.map { it.path },
        )
    }
}
