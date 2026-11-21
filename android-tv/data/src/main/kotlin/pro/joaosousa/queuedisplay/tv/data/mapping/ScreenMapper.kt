package pro.joaosousa.queuedisplay.tv.data.mapping

import pro.joaosousa.queuedisplay.tv.domain.model.ClockZone
import pro.joaosousa.queuedisplay.tv.domain.model.MediaZone
import pro.joaosousa.queuedisplay.tv.domain.model.QueuesZone
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenDefaults
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenZones
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateFallback
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId
import pro.joaosousa.queuedisplay.tv.domain.model.TickerZone

/** `sites/{siteId}/screens/{screenId}` → [Screen]. */
object ScreenMapper {
    fun map(siteId: String, screenId: String, data: Map<String, Any?>): Mapped<Screen> {
        val issues = mutableListOf<ContractIssue>()
        val f = Fields.of(data, Collections.screen(siteId, screenId), issues)

        val (template, fallback) = template(f)
        val zones = f.obj("zones", required = true)

        val screen = Screen(
            id = screenId,
            siteId = siteId,
            name = f.string("name", required = true) ?: ScreenDefaults.NAME,
            template = template,
            templateFallback = fallback,
            zones = ScreenZones(
                queues = zones?.obj("queues")?.let(::queuesZone),
                media = zones?.obj("media")?.let(::mediaZone),
                ticker = zones?.obj("ticker")?.let {
                    TickerZone(enabled = it.boolean("enabled", required = true) ?: ScreenDefaults.TICKER_ENABLED)
                },
                clock = zones?.obj("clock")?.let(::clockZone),
            ),
            maxVideoHeight = f.positiveInt("maxVideoHeight") ?: ScreenDefaults.MAX_VIDEO_HEIGHT,
            safeAreaPercent = f.int("safeAreaPercent", required = true)?.let { p ->
                if (p in 0..ScreenDefaults.MAX_SAFE_AREA_PERCENT) {
                    p
                } else {
                    f.issue("safeAreaPercent", "$p is outside 0..${ScreenDefaults.MAX_SAFE_AREA_PERCENT}; clamped")
                    p.coerceIn(0, ScreenDefaults.MAX_SAFE_AREA_PERCENT)
                }
            } ?: ScreenDefaults.SAFE_AREA_PERCENT,
        )
        return Mapped(screen, issues)
    }

    /** A missing or unknown template falls back and is reported, never silently (TV invariant 5). */
    private fun template(f: Fields): Pair<TemplateId, TemplateFallback?> {
        val fallback = TemplateId.FALLBACK.wire
        return when (val raw = f.raw("template")) {
            is String -> TemplateId.fromWire(raw)?.let { it to null } ?: run {
                f.issue("template", "unknown template \"$raw\"; showing $fallback")
                TemplateId.FALLBACK to TemplateFallback(raw)
            }
            null -> {
                f.issue("template", "missing; showing $fallback")
                TemplateId.FALLBACK to TemplateFallback(null)
            }
            else -> {
                f.issue("template", "expected a string, got ${Fields.describe(raw)}; showing $fallback")
                TemplateId.FALLBACK to TemplateFallback(null)
            }
        }
    }

    /**
     * `queueIds` that is missing, null or not a list means every queue of the site (TV invariant 7).
     * It never becomes an empty list, which would show nothing.
     */
    private fun queuesZone(z: Fields): QueuesZone {
        val queueIds = when (z.raw("queueIds")) {
            null -> null
            is List<*> -> z.stringList("queueIds")
            else -> {
                z.list("queueIds") // records the wrong type
                null
            }
        }
        return QueuesZone(queueIds = queueIds, chime = z.boolean("chime", required = true) ?: ScreenDefaults.QUEUES_CHIME)
    }

    private fun mediaZone(z: Fields) = MediaZone(
        playlistId = z.string("playlistId")?.takeIf { it.isNotEmpty() },
        volume = z.gain("volume", required = true) ?: ScreenDefaults.MEDIA_VOLUME,
    )

    private fun clockZone(z: Fields) = ClockZone(
        pattern = z.string("pattern", required = true)?.takeIf { it.isNotBlank() } ?: ScreenDefaults.CLOCK_PATTERN,
        showDate = z.boolean("showDate", required = true) ?: ScreenDefaults.CLOCK_SHOW_DATE,
    )
}
