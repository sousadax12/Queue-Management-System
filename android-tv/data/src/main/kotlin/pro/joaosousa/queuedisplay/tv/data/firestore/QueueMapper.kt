package pro.joaosousa.queuedisplay.tv.data.firestore

import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults

/**
 * `queues/{queueId}` -> [Queue], manually and defensively.
 *
 * Invariant 6: the Flutter operator app reads these same documents with `document['x']` and
 * throws when a field is missing, so a queue the portal creates must carry every legacy field.
 * A *reader* here defaults instead: legacy and hand-made docs exist (see `queues.legacy.json`),
 * and one missing field must not blank a zone.
 */
object QueueMapper {

    fun from(id: String, doc: DocumentData): Queue {
        val raw = { field: String -> doc.raw(field) }
        return Queue(
            id = id,
            orgId = Values.string(raw("orgId")),
            siteId = Values.string(raw("siteId")),
            number = Values.long(raw("number"), QueueDefaults.NUMBER),
            name = Values.string(raw("name")) ?: QueueDefaults.NAME,
            icon = Values.long(raw("icon"), QueueDefaults.ICON),
            backgroundColor = Values.long(raw("backgroundColor"), QueueDefaults.BACKGROUND_COLOR),
            foregroundColor = Values.long(raw("foregroundColor"), QueueDefaults.FOREGROUND_COLOR),
            videoUrl = Values.string(raw("videoURL")) ?: QueueDefaults.VIDEO_URL,
            videoVolume = Values.doubleOrNull(raw("videoVolume")) ?: QueueDefaults.VIDEO_VOLUME,
            soundVolume = Values.doubleOrNull(raw("soundVolume")) ?: QueueDefaults.SOUND_VOLUME,
            velocity = Values.long(raw("velocity"), QueueDefaults.VELOCITY),
        )
    }
}
