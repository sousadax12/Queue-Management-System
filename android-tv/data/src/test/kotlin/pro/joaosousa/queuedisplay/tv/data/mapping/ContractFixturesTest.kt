package pro.joaosousa.queuedisplay.tv.data.mapping

import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Kotlin loader follows the fixture encoding in `fixtures/README.md`. */
class ContractFixturesTest {

    private fun doc(json: String) = ContractFixtures.parseDocs("""{"x/1": $json}""").getValue("x/1")

    @Test
    fun `integer literals are Long and other numbers Double`() {
        val d = doc("""{"a": 1, "b": 0.6, "c": 4282339765, "d": 2.55e1, "e": -3}""")
        assertEquals(1L, d["a"])
        assertEquals(0.6, d["b"])
        assertEquals(4282339765L, d["c"])
        assertEquals(25.5, d["d"])
        assertEquals(-3L, d["e"])
    }

    @Test
    fun `integer-valued doubles are rejected`() {
        listOf("1.0", "2e3", "-0.0").forEach { n ->
            assertThrows(IllegalArgumentException::class.java) { doc("""{"a": $n}""") }
        }
    }

    @Test
    fun `timestamps and sentinels decode`() {
        val d = doc("""{"t": {"__ts": "2026-11-15T09:58:12.123456Z"}, "s": {"__sentinel": "serverTimestamp"}}""")
        assertEquals(Timestamp(Instant.parse("2026-11-15T09:58:12Z").epochSecond, 123_456_000), d["t"])
        assertEquals(ContractFixtures.now, d["s"])
    }

    @Test
    fun `reserved keys in any other shape are rejected`() {
        listOf(
            """{"__tz": "2026-11-15T10:00:00Z"}""",
            """{"__ts": "2026-11-15T10:00:00Z", "extra": 1}""",
            """{"__ts": "2026-11-15T10:00:00+01:00"}""",
            """{"__ts": "2026-11-15T10:00:00.1234567Z"}""",
            """{"__sentinel": "increment"}""",
        ).forEach { v ->
            assertThrows(IllegalStateException::class.java) {
                try {
                    doc("""{"a": $v}""")
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException(e)
                }
            }
        }
    }

    @Test
    fun `every fixture file the TV reads exists and has docs`() {
        ContractFixtures.TV_FILES.forEach { file ->
            assertTrue("$file is empty", ContractFixtures.load(file).isNotEmpty())
        }
    }
}
