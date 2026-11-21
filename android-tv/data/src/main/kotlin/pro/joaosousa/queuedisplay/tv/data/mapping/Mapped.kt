package pro.joaosousa.queuedisplay.tv.data.mapping

/** A field that broke the contract and was defaulted, or an item that was dropped. */
data class ContractIssue(
    /** Document path plus field, e.g. `queues/q_balcao_a.number` or `playlists/p.items[2]`. */
    val path: String,
    val problem: String,
) {
    override fun toString(): String = "$path: $problem"
}

/**
 * A mapper's output: the domain value (null only when the doc is unusable as a whole) and
 * every contract issue met on the way. Missing optional fields are not issues.
 */
data class Mapped<out T>(val value: T?, val issues: List<ContractIssue>)
