package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * A screen is a logical slot ("Lobby left"), not a box: several boxes may show the same screen
 * and each box shows exactly one.
 */
data class Screen(
    val id: String,
    val name: String,
    val template: TemplateId,
    val zones: ScreenZones,
    val maxVideoHeight: Long?,
    val safeAreaPercent: Long?,
)

/** The four optional zones a template may render. Absent means the template does not use it. */
data class ScreenZones(
    val queues: QueuesZone?,
    val media: MediaZone?,
    val ticker: TickerZone?,
    val clock: ClockZone?,
)

/**
 * [queueIds] is null when the screen shows **every** queue of its site, ordered by name
 * (invariant 7) — distinct from an empty list, which would show nothing.
 */
data class QueuesZone(
    val queueIds: List<String>?,
    val chime: Boolean,
)

data class MediaZone(
    val playlistId: String?,
    val volume: Double?,
)

data class TickerZone(
    val enabled: Boolean,
)

data class ClockZone(
    val pattern: String?,
    val showDate: Boolean,
)
