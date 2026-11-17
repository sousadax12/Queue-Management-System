package pro.joaosousa.queuedisplay.tv.data.firestore

import pro.joaosousa.queuedisplay.tv.domain.model.Chime
import pro.joaosousa.queuedisplay.tv.domain.model.Site
import pro.joaosousa.queuedisplay.tv.domain.model.Ticker

/** `sites/{siteId}` -> [Site]. Every optional field defaults to null rather than throwing. */
object SiteMapper {

    fun from(id: String, doc: DocumentData): Site {
        val ticker = Values.map(doc.raw("ticker"))?.let { map ->
            Ticker(
                feedUrls = Values.stringList(map["feedUrls"]),
                speedDpPerSec = Values.doubleOrNull(map["speedDpPerSec"]),
                refreshMinutes = Values.longOrNull(map["refreshMinutes"]),
            )
        }
        val chime = Values.map(doc.raw("chime"))?.let { map ->
            Chime(
                enabled = Values.bool(map["enabled"], false),
                volume = Values.doubleOrNull(map["volume"]),
            )
        }
        return Site(
            id = id,
            name = Values.string(doc.raw("name")).orEmpty(),
            timeZone = Values.string(doc.raw("timeZone")),
            locale = Values.string(doc.raw("locale")),
            defaultScreenId = Values.string(doc.raw("defaultScreenId")),
            ticker = ticker,
            chime = chime,
            maintenanceHour = Values.longOrNull(doc.raw("maintenanceHour")),
        )
    }
}
