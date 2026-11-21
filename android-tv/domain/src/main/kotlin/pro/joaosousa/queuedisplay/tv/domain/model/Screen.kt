package pro.joaosousa.queuedisplay.tv.domain.model

/** `sites/{siteId}/screens/{screenId}`: a logical slot that one or more boxes show. */
data class Screen(
    val id: String,
    val siteId: String,
    val name: String,
    /** The template to render; [TemplateId.FALLBACK] when [templateFallback] is set. */
    val template: TemplateId,
    /** Set when the configured template was missing or unknown (TV invariant 5). */
    val templateFallback: TemplateFallback?,
    val zones: ScreenZones,
    val maxVideoHeight: Int,
    /** 0..25: percentage inset on every edge for overscanning TVs. */
    val safeAreaPercent: Int,
)

/** Zone settings. A null zone was not configured; the template decides what that means. */
data class ScreenZones(
    val queues: QueuesZone? = null,
    val media: MediaZone? = null,
    val ticker: TickerZone? = null,
    val clock: ClockZone? = null,
)

data class QueuesZone(
    /**
     * Queues of the same site, in display order. **Null means every queue of the site**,
     * ordered by name (TV invariant 7). It is never replaced by an empty list.
     */
    val queueIds: List<String>?,
    val chime: Boolean,
) {
    /** The queues this zone shows, picked from all of the site's [queues]. */
    fun select(queues: List<Queue>): List<Queue> {
        val ids = queueIds ?: return queues.sortedWith(Queue.BY_NAME)
        val byId = queues.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }
}

data class MediaZone(
    /** Null = nothing to play. */
    val playlistId: String?,
    /** 0..1 gain. */
    val volume: Double,
)

data class TickerZone(val enabled: Boolean)

data class ClockZone(
    /** A date-time pattern, e.g. `HH:mm`. */
    val pattern: String,
    val showDate: Boolean,
)

/** Values used when a screen field is missing or has the wrong type. */
object ScreenDefaults {
    const val NAME = ""
    const val MAX_VIDEO_HEIGHT = 1080
    const val SAFE_AREA_PERCENT = 0
    const val MAX_SAFE_AREA_PERCENT = 25
    const val QUEUES_CHIME = true
    /** Muted, like a new queue's `videoVolume` (contract `QUEUE_DEFAULTS`). */
    const val MEDIA_VOLUME = 0.0
    const val TICKER_ENABLED = true
    const val CLOCK_PATTERN = "HH:mm"
    const val CLOCK_SHOW_DATE = false
}
