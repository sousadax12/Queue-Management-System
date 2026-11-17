package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * Screen templates the box can render. Values come from the app, and the portal only offers
 * templates a box supports, using `android-tv/contract/templates.json` (design C6, invariant 5).
 *
 * [fromRaw] returns null for anything unrecognised so the caller can fall back to [DEFAULT]
 * *and report it* rather than silently rendering the wrong layout. The registry is pinned
 * against `contract/templates.json` by a `:ui` test.
 */
enum class TemplateId(val raw: String) {
    QUEUES_MEDIA("queues_media"),
    FULLSCREEN_MEDIA("fullscreen_media"),
    MEDIA_TICKER_CLOCK("media_ticker_clock"),
    QUEUES_ONLY("queues_only");

    companion object {
        /** Invariant 5: an unknown template falls back to this one. */
        val DEFAULT = QUEUES_MEDIA

        fun fromRaw(raw: String?): TemplateId? = entries.firstOrNull { it.raw == raw }
    }
}
