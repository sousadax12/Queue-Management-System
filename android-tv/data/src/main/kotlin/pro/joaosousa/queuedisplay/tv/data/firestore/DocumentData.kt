package pro.joaosousa.queuedisplay.tv.data.firestore

import com.google.firebase.firestore.DocumentSnapshot

/**
 * A read-only view of one Firestore document.
 *
 * Mappers are written against this instead of [DocumentSnapshot] so the fixture-based tests need
 * no emulator; [SnapshotData] is the single place in the module that names the SDK, and there is
 * exactly one of them per repository read. Mapping stays manual — no `toObject()` — so one bad
 * field degrades a zone instead of crashing the screen (design "Project layout").
 */
interface DocumentData {
    val id: String

    /** The raw value, whatever Firestore gave us: String, Boolean, Long, Double, Map, List. */
    fun raw(field: String): Any?
}

class SnapshotData(private val snapshot: DocumentSnapshot) : DocumentData {
    override val id: String get() = snapshot.id
    override fun raw(field: String): Any? = snapshot.get(field)
}

/**
 * Invariant 11: the portal writes from JavaScript, so any numeric field may arrive as a Long or
 * a Double — `1.0` and `1` are the same number there. Read through this, never `as Double`.
 */
object Values {

    fun string(value: Any?): String? = value as? String

    fun bool(value: Any?, default: Boolean): Boolean = value as? Boolean ?: default

    fun long(value: Any?, default: Long): Long = (value as? Number)?.toLong() ?: default

    fun longOrNull(value: Any?): Long? = (value as? Number)?.toLong()

    fun doubleOrNull(value: Any?): Double? = (value as? Number)?.toDouble()

    /** True when the key is present at all, which is not the same as holding a usable value. */
    fun has(map: Map<String, Any?>?, field: String): Boolean = map?.containsKey(field) == true

    @Suppress("UNCHECKED_CAST")
    fun map(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun list(value: Any?): List<Any?>? = value as? List<Any?>

    /** Strings from a list, dropping anything that is not one. */
    fun stringList(value: Any?): List<String> = list(value).orEmpty().mapNotNull { it as? String }
}
