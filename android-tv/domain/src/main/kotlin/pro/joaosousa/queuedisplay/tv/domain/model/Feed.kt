package pro.joaosousa.queuedisplay.tv.domain.model

/** One entry of an RSS/Atom feed, already stripped of markup for the ticker. */
data class FeedItem(
    val title: String,
    val link: String?,
    val description: String?,
)

/** A parsed feed. `items` is in document order and never null. */
data class Feed(
    val title: String?,
    val items: List<FeedItem>,
)
