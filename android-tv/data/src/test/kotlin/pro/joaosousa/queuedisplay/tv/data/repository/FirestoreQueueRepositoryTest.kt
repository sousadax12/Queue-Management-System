package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.data.firestore.DocumentSpec
import pro.joaosousa.queuedisplay.tv.data.firestore.Filter
import pro.joaosousa.queuedisplay.tv.data.fixtures.ContractFixtures
import pro.joaosousa.queuedisplay.tv.data.fixtures.FakeFirestoreGateway
import java.io.IOException

/** The reads the operator depends on, and the one write it makes. */
class FirestoreQueueRepositoryTest {

    private val siteId = "site_porto_3fKq"

    private val siteQuery = DocumentSpec.Collection(
        name = "queues",
        filter = Filter("siteId", siteId),
        orderBy = "name",
    )

    @Test
    fun `queues listens to its site only, ordered by name`() = runBlocking {
        val gateway = FakeFirestoreGateway(
            mapOf(
                siteQuery to listOf(
                    ContractFixtures.doc("queues.json", "queues/q_balcao_a_P4tc"),
                    ContractFixtures.doc("queues.json", "queues/q_consultas_Ma9v"),
                ),
            ),
        )

        val queues = FirestoreQueueRepository(gateway).queues(siteId).first()

        assertEquals(siteQuery, gateway.requested.single())
        assertEquals(listOf("Balcão A", "Consultas"), queues.map { it.name })
        assertEquals(listOf(42L, 0L), queues.map { it.number })
    }

    @Test
    fun `an empty site is an empty list, not an error`() = runBlocking {
        val queues = FirestoreQueueRepository(FakeFirestoreGateway()).queues(siteId).first()

        assertTrue(queues.isEmpty())
    }

    @Test
    fun `a listener error degrades to no queues instead of killing the screen`() = runBlocking {
        val gateway = FakeFirestoreGateway(snapshotFailure = IOException("offline"))

        assertTrue(FirestoreQueueRepository(gateway).queues(siteId).first().isEmpty())
    }

    @Test
    fun `one queue is read by path and mapped with QueueMapper`() = runBlocking {
        val gateway = FakeFirestoreGateway(
            mapOf(
                DocumentSpec.Document("queues/q_balcao_a_P4tc") to
                    listOf(ContractFixtures.doc("queues.json", "queues/q_balcao_a_P4tc")),
            ),
        )

        val queue = FirestoreQueueRepository(gateway).queue("q_balcao_a_P4tc").first()

        assertEquals(42L, queue?.number)
        assertEquals("Balcão A", queue?.name)
        assertEquals(4282339765L, queue?.backgroundColor)
    }

    @Test
    fun `a deleted or unreachable queue is null`() = runBlocking {
        val missing = FirestoreQueueRepository(FakeFirestoreGateway()).queue("gone").first()
        val failing = FirestoreQueueRepository(FakeFirestoreGateway(snapshotFailure = IOException("offline")))
            .queue("gone")
            .first()

        assertNull(missing)
        assertNull(failing)
    }

    @Test
    fun `setNumber writes the number field and nothing else`() = runBlocking {
        // The Flutter app does update({'number': n}); anything wider would wipe the portal's
        // audit fields (updatedAt/updatedBy) on the next operator tap.
        val gateway = FakeFirestoreGateway()

        val result = FirestoreQueueRepository(gateway).setNumber("q_balcao_a_P4tc", 43)

        assertTrue(result.isSuccess)
        val update = gateway.updates.single()
        assertEquals("queues/q_balcao_a_P4tc", update.path)
        assertEquals(mapOf("number" to 43L), update.fields)
        assertEquals(setOf("number"), update.fields.keys)
    }

    @Test
    fun `a failed write is a failure Result, never an exception`() = runBlocking {
        val gateway = FakeFirestoreGateway().apply { updateFailure = IOException("permission denied") }

        val result = FirestoreQueueRepository(gateway).setNumber("q_balcao_a_P4tc", 43)

        assertTrue(result.isFailure)
        assertEquals("permission denied", result.exceptionOrNull()?.message)
        assertTrue(gateway.updates.isEmpty())
    }
}