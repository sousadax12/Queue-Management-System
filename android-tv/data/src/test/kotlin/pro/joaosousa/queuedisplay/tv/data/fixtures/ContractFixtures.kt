package pro.joaosousa.queuedisplay.tv.data.fixtures

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import pro.joaosousa.queuedisplay.tv.data.firestore.DocumentData
import java.io.File

/**
 * Reads the **shared** contract fixtures in `admin-portal/packages/contract/fixtures`.
 *
 * They are the machine-readable half of the portal/TV contract (design C8): the portal's zod
 * schemas validate them and its seed script writes them, so if the TV mappers drift from these,
 * CI on either side fails. This loader mirrors `admin-portal/packages/contract/src/encoding.ts`:
 * a JSON number with no `.` or exponent is a `Long`, anything else is a `Double`, `{"__ts": …}`
 * is a timestamp and `{"__sentinel": "serverTimestamp"}` is the write placeholder — both of
 * which the TV ignores, exactly as it ignores `orgId`, `updatedAt` and `updatedBy`.
 */
object ContractFixtures {

    /** Marker for `serverTimestamp()`: a write placeholder the TV never reads. */
    object ServerSentinel {
        override fun toString(): String = "serverTimestamp"
    }

    private val cache = mutableMapOf<String, Map<String, DocumentData>>()

    fun docs(file: String): Map<String, DocumentData> = cache.getOrPut(file) {
        val root = fixturesDir()
        val text = File(root, file).readText()
        val parsed = JsonParser.parseString(text) as JsonElement
        parsed.asJsonObject.entrySet().associate { (path, element) ->
            val id = path.substringAfterLast('/')
            path to MapDocument(id, toMap(element.asJsonObject))
        }
    }

    /** The document at `path`, e.g. `queues/q_balcao_a_P4tc`. */
    fun doc(file: String, path: String): DocumentData =
        docs(file)[path] ?: error("no $path in $file")

    /** Walks up from the working directory, so the test task's cwd does not matter. */
    private fun fixturesDir(): File {
        val relative = "admin-portal/packages/contract/fixtures"
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.isDirectory }
        return found ?: error(
            "contract fixtures not found; expected $relative above ${File("").absolutePath}"
        )
    }

    private fun toMap(obj: com.google.gson.JsonObject): Map<String, Any?> =
        obj.entrySet().associate { (key, value) -> key to toValue(value) }

    private fun toValue(element: JsonElement): Any? = when {
        element.isJsonNull -> null
        element.isJsonArray -> element.asJsonArray.map { toValue(it) }
        element.isJsonPrimitive -> toPrimitive(element.asJsonPrimitive)
        element.isJsonObject -> {
            val obj = element.asJsonObject
            when {
                // {"__ts": "2026-11-15T09:30:00Z"} — a Firestore Timestamp on read.
                obj.size() == 1 && obj.has("__ts") -> obj.get("__ts").asString
                // {"__sentinel": "serverTimestamp"} — the write placeholder.
                obj.size() == 1 && obj.has("__sentinel") -> ServerSentinel
                else -> toMap(obj)
            }
        }
        else -> error("unsupported fixture element: $element")
    }

    private fun toPrimitive(primitive: com.google.gson.JsonPrimitive): Any? = when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isString -> primitive.asString
        else -> {
            // The raw text, not the parsed double: this is the only way to tell 1 from 1.0,
            // which is the distinction the contract's encoding rests on.
            val raw = primitive.asString
            if (raw.contains('.') || raw.contains('e', true)) raw.toDouble() else raw.toLong()
        }
    }

    /** The read view the mappers take, backed by a fixture document. */
    class MapDocument(override val id: String, private val data: Map<String, Any?>) : DocumentData {
        override fun raw(field: String): Any? = data[field]
    }
}
