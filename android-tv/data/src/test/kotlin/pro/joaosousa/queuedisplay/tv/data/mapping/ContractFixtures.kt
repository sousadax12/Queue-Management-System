package pro.joaosousa.queuedisplay.tv.data.mapping

import com.google.firebase.Timestamp
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Loads the shared contract fixtures (`admin-portal/packages/contract/fixtures/`) into the same
 * value types `DocumentSnapshot.getData()` returns. Mirrors `src/fixtures.ts` and the encoding in
 * `fixtures/README.md`:
 *
 * - a number literal without `.` or exponent is a `Long`, anything else a `Double`; an
 *   integer-valued double such as `1.0` is rejected;
 * - `{"__ts": "<ISO UTC>"}` is a [Timestamp] (0-6 fraction digits, `Z` suffix);
 * - `{"__sentinel": "serverTimestamp"}` resolves to one fixed [now] for the whole load, as a read
 *   would see it;
 * - any other `__` key fails the load.
 */
object ContractFixtures {
    /** Fixture files of the collections the TV reads. */
    val TV_FILES = listOf(
        "devices.json",
        "sites.json",
        "screens.json",
        "playlists.json",
        "queues.json",
        "queues.legacy.json",
    )

    val now: Timestamp = Timestamp(Instant.parse("2026-11-15T10:00:00Z").epochSecond, 0)

    private val ISO_UTC = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?Z$""")
    private val INT_LITERAL = Regex("""^-?\d+$""")

    val contractDir: File by lazy {
        val dir = System.getProperty("contract.dir") ?: error("contract.dir is not set (see data/build.gradle.kts)")
        File(dir).also { require(it.isDirectory) { "No contract package at $it" } }
    }

    val tvContractDir: File by lazy {
        File(System.getProperty("tv.contract.dir") ?: error("tv.contract.dir is not set"))
    }

    fun fixtureFile(name: String): File = File(contractDir, "fixtures/$name")

    /** One fixture file: document path → data. */
    fun load(file: String): Map<String, Map<String, Any?>> = parseDocs(fixtureFile(file).readText(), file)

    fun parseDocs(text: String, label: String = "JSON"): Map<String, Map<String, Any?>> {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: error("$label: expected an object keyed by doc path")
        return root.mapValues { (path, doc) ->
            @Suppress("UNCHECKED_CAST")
            decode(doc, "$label:$path") as? Map<String, Any?> ?: error("$label: $path is not an object")
        }
    }

    /** Decodes one encoded JSON value. */
    fun decode(element: JsonElement, at: String = "$"): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> primitive(element, at)
        is JsonArray -> element.mapIndexed { i, e -> decode(e, "$at[$i]") }
        is JsonObject -> obj(element, at)
    }

    private fun primitive(p: JsonPrimitive, at: String): Any = when {
        p.isString -> p.content
        p.content == "true" -> true
        p.content == "false" -> false
        INT_LITERAL.matches(p.content) -> p.content.toLong()
        else -> {
            val d = p.content.toDouble()
            require(d != Math.rint(d) || d.isInfinite()) {
                "$at: integer-valued double ${p.content} is ambiguous across JS and Kotlin; write it as an integer"
            }
            d
        }
    }

    private fun obj(o: JsonObject, at: String): Any {
        val reserved = o.keys.filter { it.startsWith("__") }
        if (reserved.isEmpty()) return o.mapValues { (k, v) -> decode(v, "$at.$k") }
        val ts = o["__ts"]
        if (o.size == 1 && ts is JsonPrimitive && ts.isString) {
            require(ISO_UTC.matches(ts.content)) { "$at: bad timestamp ${ts.content}" }
            val instant = Instant.parse(ts.content)
            return Timestamp(instant.epochSecond, instant.nano)
        }
        val sentinel = o["__sentinel"]
        if (o.size == 1 && sentinel is JsonPrimitive && sentinel.isString && sentinel.content == "serverTimestamp") {
            return now
        }
        error("$at: reserved \"__\" key in $o")
    }

    /** Maps one fixture doc with the mapper of its collection. */
    fun map(path: String, data: Map<String, Any?>): Mapped<Any> {
        val segments = path.split('/')
        return when {
            segments.size == 2 && segments[0] == Collections.DEVICES -> DeviceMapper.map(segments[1], data)
            segments.size == 2 && segments[0] == Collections.SITES -> SiteMapper.map(segments[1], data)
            segments.size == 4 && segments[0] == Collections.SITES && segments[2] == Collections.SCREENS ->
                ScreenMapper.map(segments[1], segments[3], data)
            segments.size == 2 && segments[0] == Collections.PLAYLISTS -> PlaylistMapper.map(segments[1], data)
            segments.size == 2 && segments[0] == Collections.QUEUES -> QueueMapper.map(segments[1], data)
            else -> error("The TV reads no doc at $path")
        }
    }
}
