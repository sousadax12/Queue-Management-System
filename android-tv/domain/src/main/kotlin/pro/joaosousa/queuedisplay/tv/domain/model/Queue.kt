package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * `queues/{queueId}` — the number is advanced by the Flutter operator, everything else is
 * written by the portal.
 *
 * Colours are ARGB unsigned 32-bit values, so [backgroundColor] and [foregroundColor] are
 * [Long]: the portal writes values above [Int.MAX_VALUE] (e.g. `4282339765`), and a `Long`
 * survives that bit pattern. The UI narrows with `Color(value.toInt())`.
 *
 * Volumes are [Double] because the portal writes from JavaScript and Firestore may store
 * `1` or `1.0` for the same gain (invariant 11), and because legacy docs carry `0.5`.
 */
data class Queue(
    val id: String,
    val orgId: String?,
    val siteId: String?,
    val number: Long,
    val name: String,
    val icon: Long,
    val backgroundColor: Long,
    val foregroundColor: Long,
    val videoUrl: String,
    val videoVolume: Double,
    val soundVolume: Double,
    val velocity: Long,
)

/**
 * Neutral values for every field a queue may be missing (invariant 6, `QUEUE_DEFAULTS` in
 * `admin-portal/packages/contract/src/schemas/queues.ts`). A reader defaults rather than
 * throwing, so one bad document degrades a zone instead of crashing the screen.
 */
object QueueDefaults {
    const val NUMBER = 0L
    /** Font Awesome `fa-user`, the portal's default icon. */
    const val ICON = 0xf007L
    const val BACKGROUND_COLOR = 0xff2d5b9fL
    const val FOREGROUND_COLOR = 0xffffffffL
    const val VIDEO_URL = ""
    const val VIDEO_VOLUME = 0.0
    const val SOUND_VOLUME = 1.0
    const val VELOCITY = 60L
    /** Matches the portal schema's `z.string().min(1)`. */
    const val NAME = ""
}
