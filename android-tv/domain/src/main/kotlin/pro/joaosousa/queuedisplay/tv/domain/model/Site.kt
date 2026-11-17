package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * RSS ticker for a site. All fields optional: the portal may write a partial object.
 * [speedDpPerSec] is fractional in the contract (the fixtures carry `52.5`), so it is a Double.
 */
data class Ticker(
    val feedUrls: List<String>,
    val speedDpPerSec: Double?,
    val refreshMinutes: Long?,
)

/** Site-wide chime. [volume] is a 0..1 gain, and arrives as a Long or a Double (invariant 11). */
data class Chime(
    val enabled: Boolean,
    val volume: Double?,
)

/** `sites/{siteId}`, written by the portal. The TV only reads it. */
data class Site(
    val id: String,
    val name: String,
    val timeZone: String?,
    val locale: String?,
    val defaultScreenId: String?,
    val ticker: Ticker?,
    val chime: Chime?,
    val maintenanceHour: Long?,
)
