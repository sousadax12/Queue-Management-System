package pro.joaosousa.queuedisplay.tv.domain.model

/** `sites/{siteId}`, written by the portal. */
data class Site(
    val id: String,
    val name: String,
    /** IANA zone, e.g. `Europe/Lisbon`. */
    val timeZone: String,
    /** BCP 47 tag, e.g. `pt-PT`. */
    val locale: String,
    /** Shown by boxes whose `screenId` is null (TV invariant 8). */
    val defaultScreenId: String?,
    val ticker: TickerSettings,
    val chime: ChimeSettings,
    /** Local hour (0-23) for daily maintenance. */
    val maintenanceHour: Int,
)

data class TickerSettings(
    val feedUrls: List<String>,
    val speedDpPerSec: Double,
    val refreshMinutes: Int,
)

data class ChimeSettings(
    val enabled: Boolean,
    /** 0..1 gain. */
    val volume: Double,
)

/**
 * Values used when a site field is missing or has the wrong type. Every current site is
 * in Portugal, which is where the zone and locale defaults come from.
 */
object SiteDefaults {
    const val NAME = ""
    const val TIME_ZONE = "Europe/Lisbon"
    const val LOCALE = "pt-PT"
    const val TICKER_SPEED_DP_PER_SEC = 60.0
    const val TICKER_REFRESH_MINUTES = 15
    const val CHIME_ENABLED = true
    const val CHIME_VOLUME = 1.0
    const val MAINTENANCE_HOUR = 4
}
