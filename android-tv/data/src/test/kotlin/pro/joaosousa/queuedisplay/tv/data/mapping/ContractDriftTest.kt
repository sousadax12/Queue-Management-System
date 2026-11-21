package pro.joaosousa.queuedisplay.tv.data.mapping

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import pro.joaosousa.queuedisplay.tv.domain.model.QueueDefaults
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId

/**
 * The Kotlin copies of contract constants match the TypeScript sources. CI runs this when
 * a file under `admin-portal/packages/contract/src/schemas/` changes.
 */
class ContractDriftTest {

    private fun schema(name: String) = File(ContractFixtures.contractDir, "src/schemas/$name").readText()

    @Test
    fun `QueueDefaults mirrors QUEUE_DEFAULTS`() {
        val block = Regex("""QUEUE_DEFAULTS = \{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
            .find(schema("queues.ts"))!!.groupValues[1]
        val entries = Regex("""(\w+):\s*([^,\n]+),""").findAll(block)
            .associate { it.groupValues[1] to it.groupValues[2].trim() }

        fun num(key: String): Double {
            val v = entries.getValue(key)
            return if (v.startsWith("0x")) v.removePrefix("0x").toLong(16).toDouble() else v.toDouble()
        }

        assertEquals(
            setOf("number", "icon", "backgroundColor", "foregroundColor", "videoURL", "videoVolume", "soundVolume", "velocity"),
            entries.keys,
        )
        assertEquals(num("number"), QueueDefaults.NUMBER.toDouble(), 0.0)
        assertEquals(num("icon"), QueueDefaults.ICON.toDouble(), 0.0)
        assertEquals(num("backgroundColor"), QueueDefaults.BACKGROUND_COLOR.toDouble(), 0.0)
        assertEquals(num("foregroundColor"), QueueDefaults.FOREGROUND_COLOR.toDouble(), 0.0)
        assertEquals("''", entries.getValue("videoURL"))
        assertEquals(QueueDefaults.VIDEO_URL, "")
        assertEquals(num("videoVolume"), QueueDefaults.VIDEO_VOLUME, 0.0)
        assertEquals(num("soundVolume"), QueueDefaults.SOUND_VOLUME, 0.0)
        assertEquals(num("velocity"), QueueDefaults.VELOCITY, 0.0)
    }

    @Test
    fun `TemplateId matches the contract's TEMPLATE_IDS and templates json`() {
        val block = Regex("""TEMPLATE_IDS = \[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(schema("common.ts"))!!.groupValues[1]
        val contractIds = Regex("""'([a-z_]+)'""").findAll(block).map { it.groupValues[1] }.toList()
        val appIds = TemplateId.entries.map { it.wire }
        assertEquals(contractIds, appIds)

        val published = Json.parseToJsonElement(File(ContractFixtures.tvContractDir, "templates.json").readText()) as JsonArray
        assertEquals(appIds.toSet(), published.map { (it as JsonObject).getValue("id").jsonPrimitive.content }.toSet())
    }
}
