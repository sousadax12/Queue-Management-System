package pro.joaosousa.queuedisplay.tv.data.firestore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.data.fixtures.ContractFixtures
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults

/** Real-shaped documents: the shared fixtures, plus the bad shapes the contract allows for. */
class QueueMapperTest {

    @Test
    fun `maps a portal-written queue exactly, including Long ARGB colours`() {
        val queue = QueueMapper.from("q_balcao_a_P4tc", ContractFixtures.doc("queues.json", "queues/q_balcao_a_P4tc"))

        assertEquals("q_balcao_a_P4tc", queue.id)
        assertEquals("org_lojas_7Hq2", queue.orgId)
        assertEquals("site_porto_3fKq", queue.siteId)
        assertEquals(42L, queue.number)
        assertEquals("Balcão A", queue.name)
        assertEquals(61713L, queue.icon)
        assertEquals(4282339765L, queue.backgroundColor)
        assertEquals(4294967295L, queue.foregroundColor)
        assertEquals("", queue.videoUrl)
        assertEquals(0.0, queue.videoVolume)
        assertEquals(1.0, queue.soundVolume)
        assertEquals(60L, queue.velocity)
    }

    @Test
    fun `a queue without foregroundColor gets white, not a crash`() {
        // q_analises_Ce7y and q_balcao_b_Lr6w carry no foregroundColor in the fixtures.
        val queue = QueueMapper.from("q_analises_Ce7y", ContractFixtures.doc("queues.json", "queues/q_analises_Ce7y"))

        assertEquals(QueueDefaults.FOREGROUND_COLOR, queue.foregroundColor)
        assertEquals(99L, queue.number)
        assertEquals("Análises", queue.name)

        val other = QueueMapper.from("q_balcao_b_Lr6w", ContractFixtures.doc("queues.json", "queues/q_balcao_b_Lr6w"))
        assertEquals(QueueDefaults.FOREGROUND_COLOR, other.foregroundColor)
        assertEquals(7L, other.number)
    }

    @Test
    fun `an explicit black foreground is kept, not replaced by the default`() {
        // q_consultas_Ma9v sets foregroundColor to 0xff000000 (4278190080), which is not
        // "absent" — the default must not overwrite a colour the portal chose.
        val queue = QueueMapper.from("q_consultas_Ma9v", ContractFixtures.doc("queues.json", "queues/q_consultas_Ma9v"))

        assertEquals(4278190080L, queue.foregroundColor)
        assertEquals(0L, queue.number)
        assertEquals("Consultas", queue.name)
    }

    @Test
    fun `legacy hand-made docs default every missing legacy field (invariant 6)`() {
        // queues.legacy.json "legacy_manual_3" holds only number/name/backgroundColor/soundVolume.
        val queue = QueueMapper.from("legacy_manual_3", ContractFixtures.doc("queues.legacy.json", "queues/legacy_manual_3"))

        assertEquals(5L, queue.number)
        assertEquals("Senhas (criada à mão)", queue.name)
        assertEquals(4283215696L, queue.backgroundColor)
        assertEquals(0.5, queue.soundVolume)          // a fractional gain, straight from the fixture
        assertEquals(QueueDefaults.ICON, queue.icon)
        assertEquals(QueueDefaults.VIDEO_URL, queue.videoUrl)
        assertEquals(QueueDefaults.VIDEO_VOLUME, queue.videoVolume)
        assertEquals(QueueDefaults.VELOCITY, queue.velocity)
        assertEquals(QueueDefaults.FOREGROUND_COLOR, queue.foregroundColor)
        assertNull(queue.orgId)                        // pre-backfill: no orgId, no siteId
        assertNull(queue.siteId)
    }

    @Test
    fun `accepts a number stored as a Double, which JavaScript would write (invariant 11)`() {
        val doc = ContractFixtures.MapDocument(
            id = "js_written",
            data = mapOf(
                "name" to "Balcão JS",
                "number" to 7.0,          // JS `update({number: 7})` can land as a double
                "icon" to 61713.0,
                "backgroundColor" to 4.282339765E9,
                "videoVolume" to 1L,      // ...and the reverse: a Long where a Double is expected
                "velocity" to 60.0,
            ),
        )

        val queue = QueueMapper.from(doc.id, doc)

        assertEquals(7L, queue.number)
        assertEquals(61713L, queue.icon)
        assertEquals(4282339765L, queue.backgroundColor)
        assertEquals(1.0, queue.videoVolume)
        assertEquals(60L, queue.velocity)
    }

    @Test
    fun `an empty document still yields a usable queue`() {
        val queue = QueueMapper.from("empty", ContractFixtures.MapDocument("empty", emptyMap()))

        assertEquals(QueueDefaults.NAME, queue.name)
        assertEquals(QueueDefaults.NUMBER, queue.number)
        assertEquals(QueueDefaults.BACKGROUND_COLOR, queue.backgroundColor)
        assertEquals(QueueDefaults.SOUND_VOLUME, queue.soundVolume)
    }
}
