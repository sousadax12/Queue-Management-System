package pro.joaosousa.queuedisplay.tv.data.firestore

import pro.joaosousa.queuedisplay.tv.domain.model.ClockZone
import pro.joaosousa.queuedisplay.tv.domain.model.MediaZone
import pro.joaosousa.queuedisplay.tv.domain.model.QueuesZone
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenZones
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId
import pro.joaosousa.queuedisplay.tv.domain.model.TickerZone

/**
 * `sites/{siteId}/screens/{screenId}` -> [Screen].
 *
 * Invariant 5: a `template` this build does not know falls back to [TemplateId.DEFAULT] *and is
 * reported* via [Mapped.unknownTemplate], so the box can log it instead of silently rendering the
 * wrong layout. Invariant 7: an absent `zones.queues.queueIds` means "every queue of the site",
 * which is why the mapper distinguishes absent from empty.
 */
object ScreenMapper {

    data class Mapped(val screen: Screen, val unknownTemplate: String?)

    fun from(id: String, doc: DocumentData): Mapped {
        val templateRaw = Values.string(doc.raw("template"))
        val template = TemplateId.fromRaw(templateRaw) ?: TemplateId.DEFAULT
        val zones = Values.map(doc.raw("zones"))

        val queuesZone = Values.map(zones?.get("queues"))?.let { zone ->
            QueuesZone(
                queueIds = if (Values.has(zone, "queueIds")) Values.stringList(zone["queueIds"]) else null,
                chime = Values.bool(zone["chime"], false),
            )
        }
        val mediaZone = Values.map(zones?.get("media"))?.let { zone ->
            MediaZone(
                playlistId = Values.string(zone["playlistId"]),
                volume = Values.doubleOrNull(zone["volume"]),
            )
        }
        val tickerZone = Values.map(zones?.get("ticker"))?.let { zone ->
            TickerZone(enabled = Values.bool(zone["enabled"], false))
        }
        val clockZone = Values.map(zones?.get("clock"))?.let { zone ->
            ClockZone(
                pattern = Values.string(zone["pattern"]),
                showDate = Values.bool(zone["showDate"], false),
            )
        }

        val screen = Screen(
            id = id,
            name = Values.string(doc.raw("name")).orEmpty(),
            template = template,
            zones = ScreenZones(queuesZone, mediaZone, tickerZone, clockZone),
            maxVideoHeight = Values.longOrNull(doc.raw("maxVideoHeight")),
            safeAreaPercent = Values.longOrNull(doc.raw("safeAreaPercent")),
        )
        // Report the raw value only when it was there and this build did not know it.
        val unknown = templateRaw?.takeIf { it.isNotBlank() && TemplateId.fromRaw(it) == null }
        return Mapped(screen, unknown)
    }
}
