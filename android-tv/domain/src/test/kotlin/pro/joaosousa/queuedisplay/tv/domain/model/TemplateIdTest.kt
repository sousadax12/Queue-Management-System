package pro.joaosousa.queuedisplay.tv.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TemplateIdTest {

    @Test
    fun `every template the portal writes is recognised`() {
        assertEquals(TemplateId.QUEUES_MEDIA, TemplateId.fromRaw("queues_media"))
        assertEquals(TemplateId.FULLSCREEN_MEDIA, TemplateId.fromRaw("fullscreen_media"))
        assertEquals(TemplateId.MEDIA_TICKER_CLOCK, TemplateId.fromRaw("media_ticker_clock"))
        assertEquals(TemplateId.QUEUES_ONLY, TemplateId.fromRaw("queues_only"))
    }

    @Test
    fun `an unknown or missing template is null, so the caller can fall back and report`() {
        assertNull(TemplateId.fromRaw("queues_media_v2"))
        assertNull(TemplateId.fromRaw(""))
        assertNull(TemplateId.fromRaw(null))
    }

    @Test
    fun `the fallback is queues_media (invariant 5)`() {
        assertEquals("queues_media", TemplateId.DEFAULT.raw)
    }
}
