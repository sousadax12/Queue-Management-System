package pro.joaosousa.queuedisplay.tv.data.mapping

import pro.joaosousa.queuedisplay.tv.domain.model.ChimeSettings
import pro.joaosousa.queuedisplay.tv.domain.model.Site
import pro.joaosousa.queuedisplay.tv.domain.model.SiteDefaults
import pro.joaosousa.queuedisplay.tv.domain.model.TickerSettings

/** `sites/{siteId}` → [Site]. Portal-written, so a missing field is reported. */
object SiteMapper {
    private const val HTTPS = "https://"

    fun map(siteId: String, data: Map<String, Any?>): Mapped<Site> {
        val issues = mutableListOf<ContractIssue>()
        val f = Fields.of(data, Collections.site(siteId), issues)

        val ticker = f.obj("ticker", required = true)
        val feedUrls = ticker?.stringList("feedUrls", required = true).orEmpty().filterIndexed { i, url ->
            url.startsWith(HTTPS).also { ok -> if (!ok) ticker?.issue("feedUrls[$i]", "not an https URL; dropped") }
        }
        val chime = f.obj("chime", required = true)

        val site = Site(
            id = siteId,
            name = f.string("name", required = true) ?: SiteDefaults.NAME,
            timeZone = f.string("timeZone", required = true)?.takeIf { it.isNotBlank() } ?: SiteDefaults.TIME_ZONE,
            locale = f.string("locale", required = true)?.takeIf { it.isNotBlank() } ?: SiteDefaults.LOCALE,
            defaultScreenId = f.string("defaultScreenId")?.takeIf { it.isNotEmpty() },
            ticker = TickerSettings(
                feedUrls = feedUrls,
                speedDpPerSec = ticker?.positiveDouble("speedDpPerSec") ?: SiteDefaults.TICKER_SPEED_DP_PER_SEC,
                refreshMinutes = ticker?.positiveInt("refreshMinutes") ?: SiteDefaults.TICKER_REFRESH_MINUTES,
            ),
            chime = ChimeSettings(
                enabled = chime?.boolean("enabled", required = true) ?: SiteDefaults.CHIME_ENABLED,
                volume = chime?.gain("volume", required = true) ?: SiteDefaults.CHIME_VOLUME,
            ),
            maintenanceHour = f.int("maintenanceHour", required = true)?.let { h ->
                h.takeIf { it in 0..23 } ?: run { f.issue("maintenanceHour", "$h is not an hour"); null }
            } ?: SiteDefaults.MAINTENANCE_HOUR,
        )
        return Mapped(site, issues)
    }
}

internal fun Fields.positiveDouble(key: String): Double? = double(key, required = true)?.let { d ->
    d.takeIf { it > 0 } ?: run { issue(key, "$d is not positive"); null }
}

internal fun Fields.positiveInt(key: String): Int? = int(key, required = true)?.let { n ->
    n.takeIf { it > 0 } ?: run { issue(key, "$n is not positive"); null }
}
