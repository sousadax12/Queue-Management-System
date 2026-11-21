package pro.joaosousa.queuedisplay.tv.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreenModelTest {

    private fun queue(id: String, name: String) = Queue(
        id = id, siteId = "s", name = name, number = 0, icon = QueueDefaults.ICON,
        backgroundColor = QueueDefaults.BACKGROUND_COLOR, foregroundColor = QueueDefaults.FOREGROUND_COLOR,
        videoUrl = "", videoVolume = 0.0, soundVolume = 1.0, velocity = 60.0,
    )

    private val queues = listOf(queue("q3", "Caixa"), queue("q1", "Balcão B"), queue("q2", "Balcão A"))

    @Test
    fun `null queueIds shows every queue of the site by name`() {
        val zone = QueuesZone(queueIds = null, chime = true)
        assertEquals(listOf("q2", "q1", "q3"), zone.select(queues).map { it.id })
    }

    @Test
    fun `listed queueIds keep their order and skip unknown ids`() {
        val zone = QueuesZone(queueIds = listOf("q3", "gone", "q2"), chime = true)
        assertEquals(listOf("q3", "q2"), zone.select(queues).map { it.id })
    }

    @Test
    fun `an empty list shows no queues, unlike null`() {
        assertEquals(emptyList<Queue>(), QueuesZone(queueIds = emptyList(), chime = true).select(queues))
    }

    @Test
    fun `device shows its own screen, else the site default`() {
        val site = Site(
            id = "s", name = "S", timeZone = "UTC", locale = "pt-PT", defaultScreenId = "def",
            ticker = TickerSettings(emptyList(), 60.0, 15), chime = ChimeSettings(true, 1.0), maintenanceHour = 4,
        )
        val own = Device("u", siteId = "s", screenId = "mine", name = null, identifyUntilEpochMillis = null)
        assertEquals("mine", own.effectiveScreenId(site))
        assertEquals("def", own.copy(screenId = null).effectiveScreenId(site))
        assertNull(own.copy(screenId = null).effectiveScreenId(null))
    }

    @Test
    fun `template wire values round trip and unknown values are null`() {
        TemplateId.entries.forEach { assertEquals(it, TemplateId.fromWire(it.wire)) }
        assertNull(TemplateId.fromWire("carousel"))
        assertEquals(TemplateId.QUEUES_MEDIA, TemplateId.FALLBACK)
    }
}
