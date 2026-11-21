package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * Screen templates the app can render. [wire] is the value of `screens/{id}.template`
 * (contract `TEMPLATE_IDS`) and the `id` in `android-tv/contract/templates.json` (C6).
 */
enum class TemplateId(val wire: String) {
    QUEUES_MEDIA("queues_media"),
    FULLSCREEN_MEDIA("fullscreen_media"),
    MEDIA_TICKER_CLOCK("media_ticker_clock"),
    QUEUES_ONLY("queues_only"),
    ;

    companion object {
        /** What a screen shows when its `template` is missing or unknown (TV invariant 5). */
        val FALLBACK = QUEUES_MEDIA

        /** The template for [wire], or null when the value is unknown to this app version. */
        fun fromWire(wire: String): TemplateId? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Why a screen is shown with [TemplateId.FALLBACK] instead of its configured template.
 * Never silent: the screen carries it so diagnostics can show it, and the mapper reports it.
 */
data class TemplateFallback(
    /** The raw `template` value, or null when the field was missing or not a string. */
    val raw: String?,
)
