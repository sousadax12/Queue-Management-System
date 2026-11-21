package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * `queues/{queueId}`. The number is written by the Flutter operator, everything else by the
 * portal. Legacy docs may miss any field, which then takes its [QueueDefaults] value.
 */
data class Queue(
    val id: String,
    /** Null only on Flutter-era docs not yet backfilled; the TV never sees those (rules). */
    val siteId: String?,
    val name: String,
    val number: Int,
    /** A Font Awesome Solid code point. */
    val icon: Int,
    /** ARGB as an unsigned 32-bit value, above `Int.MAX_VALUE` for opaque colours. */
    val backgroundColor: Long,
    val foregroundColor: Long,
    /** Legacy streamed video; empty = none. */
    val videoUrl: String,
    /** 0..1 gain. */
    val videoVolume: Double,
    /** 0..1 gain. */
    val soundVolume: Double,
    /** Legacy ticker speed in logical pixels per second. */
    val velocity: Double,
) {
    companion object {
        /** Display order when a zone shows every queue of the site (TV invariant 7). */
        val BY_NAME: Comparator<Queue> = compareBy<Queue> { it.name }.thenBy { it.id }
    }
}

/**
 * Mirrors `QUEUE_DEFAULTS` in `admin-portal/packages/contract/src/schemas/queues.ts`
 * (a `:data` test fails if they drift). [NAME] has no portal default: the portal always
 * writes a name, and only hand-made legacy docs can miss it.
 */
object QueueDefaults {
    const val NAME = ""
    const val NUMBER = 0
    const val ICON = 0xf007 // fa-user
    const val BACKGROUND_COLOR = 0xff2d5b9fL
    const val FOREGROUND_COLOR = 0xffffffffL
    const val VIDEO_URL = ""
    const val VIDEO_VOLUME = 0.0
    const val SOUND_VOLUME = 1.0
    const val VELOCITY = 60.0
}
