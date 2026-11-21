package pro.joaosousa.queuedisplay.tv.data.mapping

import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults

/**
 * `queues/{queueId}` → [Queue]. Stays compatible with the Flutter mapper
 * (`lib/mappers/queue_doc_mapper.dart`) on every doc that mapper accepts, and is more
 * forgiving: Flutter-era and hand-made docs may miss any field (contract `queueReadSchema`),
 * which then takes its [QueueDefaults] value without being reported. Colours are unsigned
 * 32-bit values, so they arrive as `Long`. `orgId` and the audit fields are ignored.
 */
object QueueMapper {
    fun map(queueId: String, data: Map<String, Any?>): Mapped<Queue> {
        val issues = mutableListOf<ContractIssue>()
        val f = Fields.of(data, Collections.queue(queueId), issues)
        val queue = Queue(
            id = queueId,
            siteId = f.string("siteId")?.takeIf { it.isNotEmpty() },
            name = f.string("name") ?: QueueDefaults.NAME,
            number = f.int("number") ?: QueueDefaults.NUMBER,
            icon = f.int("icon") ?: QueueDefaults.ICON,
            backgroundColor = f.argb("backgroundColor") ?: QueueDefaults.BACKGROUND_COLOR,
            foregroundColor = f.argb("foregroundColor") ?: QueueDefaults.FOREGROUND_COLOR,
            videoUrl = f.string("videoURL") ?: QueueDefaults.VIDEO_URL,
            videoVolume = f.gain("videoVolume") ?: QueueDefaults.VIDEO_VOLUME,
            soundVolume = f.gain("soundVolume") ?: QueueDefaults.SOUND_VOLUME,
            velocity = f.double("velocity")?.let { v ->
                v.takeIf { it >= 0 } ?: run { f.issue("velocity", "$v is negative"); null }
            } ?: QueueDefaults.VELOCITY,
        )
        return Mapped(queue, issues)
    }
}
