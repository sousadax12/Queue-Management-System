package pro.joaosousa.queuedisplay.tv.data.mapping

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Typed, forgiving reads from a Firestore data map (`DocumentSnapshot.getData()` or a contract
 * fixture). A missing or null field returns null. A field of the wrong type also returns null
 * and records a [ContractIssue], so the caller applies its default and one bad field never
 * breaks the whole doc.
 *
 * Every number goes through [Number] (TV invariant 11): Firestore may hand back a `Long` where
 * the portal meant a double (`1.0` is stored as `1`) and vice versa.
 */
internal class Fields(
    private val data: Map<String, Any?>,
    private val path: String,
    private val issues: MutableList<ContractIssue>,
) {
    fun pathOf(key: String): String = "$path.$key"

    fun issue(key: String, problem: String) {
        issues += ContractIssue(pathOf(key), problem)
    }

    /** The raw value, for fields that need custom handling. */
    fun raw(key: String): Any? = data[key]

    private fun absent(key: String, required: Boolean): Nothing? {
        if (required) issue(key, "missing")
        return null
    }

    private fun wrongType(key: String, expected: String, value: Any): Nothing? {
        issue(key, "expected $expected, got ${describe(value)}")
        return null
    }

    fun string(key: String, required: Boolean = false): String? = when (val v = data[key]) {
        null -> absent(key, required)
        is String -> v
        else -> wrongType(key, "a string", v)
    }

    fun boolean(key: String, required: Boolean = false): Boolean? = when (val v = data[key]) {
        null -> absent(key, required)
        is Boolean -> v
        else -> wrongType(key, "a boolean", v)
    }

    fun double(key: String, required: Boolean = false): Double? = when (val v = data[key]) {
        null -> absent(key, required)
        is Number -> v.toDouble().takeIf { it.isFinite() } ?: wrongType(key, "a finite number", v)
        else -> wrongType(key, "a number", v)
    }

    /** An integer. A double is accepted only when it has no fraction (`7.0`). */
    fun long(key: String, required: Boolean = false): Long? = when (val v = data[key]) {
        null -> absent(key, required)
        is Long, is Int, is Short, is Byte -> (v as Number).toLong()
        is Number -> {
            val d = v.toDouble()
            if (d.isFinite() && d == Math.rint(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                d.toLong()
            } else {
                wrongType(key, "an integer", v)
            }
        }
        else -> wrongType(key, "an integer", v)
    }

    fun int(key: String, required: Boolean = false): Int? {
        val l = long(key, required) ?: return null
        if (l < Int.MIN_VALUE || l > Int.MAX_VALUE) {
            issue(key, "integer $l out of range")
            return null
        }
        return l.toInt()
    }

    /** A Firestore `Timestamp` (or `Date`) as epoch milliseconds. */
    fun epochMillis(key: String, required: Boolean = false): Long? = when (val v = data[key]) {
        null -> absent(key, required)
        is Timestamp -> v.seconds * 1000 + v.nanoseconds / 1_000_000
        is Date -> v.time
        else -> wrongType(key, "a timestamp", v)
    }

    fun obj(key: String, required: Boolean = false): Fields? = when (val v = data[key]) {
        null -> absent(key, required)
        is Map<*, *> -> Fields(v.stringKeys(), pathOf(key), issues)
        else -> wrongType(key, "an object", v)
    }

    fun list(key: String, required: Boolean = false): List<Any?>? = when (val v = data[key]) {
        null -> absent(key, required)
        is List<*> -> v
        else -> wrongType(key, "a list", v)
    }

    /** A list of strings; other elements are dropped and reported. */
    fun stringList(key: String, required: Boolean = false): List<String>? {
        val items = list(key, required) ?: return null
        return items.mapIndexedNotNull { i, item ->
            item as? String ?: run {
                issue("$key[$i]", "expected a string, got ${describe(item)}; dropped")
                null
            }
        }
    }

    /** A 0..1 gain; out-of-range values are clamped and reported. */
    fun gain(key: String, required: Boolean = false): Double? {
        val d = double(key, required) ?: return null
        if (d < 0.0 || d > 1.0) {
            issue(key, "$d is outside 0..1; clamped")
            return d.coerceIn(0.0, 1.0)
        }
        return d
    }

    /** An ARGB colour: an unsigned 32-bit value. A signed 32-bit value keeps its bit pattern. */
    fun argb(key: String, required: Boolean = false): Long? {
        val l = long(key, required) ?: return null
        if (l < Int.MIN_VALUE || l > 0xffffffffL) {
            issue(key, "$l is not a 32-bit ARGB colour")
            return null
        }
        return l and 0xffffffffL
    }

    companion object {
        fun of(data: Map<String, Any?>, path: String, issues: MutableList<ContractIssue>) =
            Fields(data, path, issues)

        fun describe(value: Any?): String = when (value) {
            null -> "null"
            is String -> "string \"${value.take(40)}\""
            is Boolean -> "boolean $value"
            is Number -> "number $value"
            is List<*> -> "a list"
            is Map<*, *> -> "an object"
            else -> value::class.java.simpleName
        }
    }
}

internal fun Map<*, *>.stringKeys(): Map<String, Any?> =
    entries.filter { it.key is String }.associate { it.key as String to it.value }
