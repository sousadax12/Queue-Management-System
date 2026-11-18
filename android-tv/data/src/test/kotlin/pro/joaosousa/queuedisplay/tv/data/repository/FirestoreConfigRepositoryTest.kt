package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.data.firestore.DocumentSpec
import pro.joaosousa.queuedisplay.tv.data.fixtures.ContractFixtures
import pro.joaosousa.queuedisplay.tv.data.fixtures.FakeFirestoreGateway
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId
import java.io.IOException

/** Site and screen configuration: read-only, mapped through the existing mappers. */
class FirestoreConfigRepositoryTest {

    @Test
    fun `sites are ordered by name so the picker does not reshuffle`() = runBlocking {
        val gateway = FakeFirestoreGateway()

        FirestoreConfigRepository(gateway).sites().first()

        assertEquals(DocumentSpec.Collection("sites", orderBy = "name"), gateway.requested.single())
    }

    @Test
    fun `sites maps every document, keeping the site's ticker`() = runBlocking {
        val docs = ContractFixtures.docs("sites.json").values.toList()
        val gateway = FakeFirestoreGateway(
            mapOf(DocumentSpec.Collection("sites", orderBy = "name") to docs),
        )

        val sites = FirestoreConfigRepository(gateway).sites().first()

        assertEquals(setOf("Loja Porto Centro", "Clínica Lisboa"), sites.map { it.name }.toSet())
        val porto = sites.single { it.id == "site_porto_3fKq" }
        assertEquals("Europe/Lisbon", porto.timeZone)
        assertEquals(listOf("https://www.rtp.pt/noticias/rss"), porto.ticker?.feedUrls)
        assertEquals(15L, porto.ticker?.refreshMinutes)
        assertEquals(1.0, porto.chime?.volume)
    }

    @Test
    fun `a site that is gone reads as null`() = runBlocking {
        assertNull(FirestoreConfigRepository(FakeFirestoreGateway()).site("site_x").first())
    }

    @Test
    fun `a failing listener reads as an empty site list`() = runBlocking {
        val gateway = FakeFirestoreGateway(snapshotFailure = IOException("offline"))

        assertTrue(FirestoreConfigRepository(gateway).sites().first().isEmpty())
    }

    @Test
    fun `a screen is read from its subcollection and its template is reported as known`() = runBlocking {
        val path = "sites/site_porto_3fKq/screens/scr_lobby_right_Vb8n"
        val gateway = FakeFirestoreGateway(
            mapOf(DocumentSpec.Document(path) to listOf(ContractFixtures.doc("screens.json", path))),
        )

        val result = FirestoreConfigRepository(gateway).screen("site_porto_3fKq", "scr_lobby_right_Vb8n").first()

        assertEquals(path, (gateway.requested.single() as DocumentSpec.Document).path)
        assertEquals(TemplateId.FULLSCREEN_MEDIA, result?.screen?.template)
        assertNull(result?.unknownTemplate)
    }

    @Test
    fun `an unknown template falls back to the default and is reported (invariant 5)`() = runBlocking {
        val doc = ContractFixtures.MapDocument(
            id = "scr_future",
            data = mapOf("name" to "Lobby futura", "template" to "future_template"),
        )
        val gateway = FakeFirestoreGateway(
            mapOf(DocumentSpec.Document("sites/site_x/screens/scr_future") to listOf(doc)),
        )

        val result = FirestoreConfigRepository(gateway).screen("site_x", "scr_future").first()

        assertEquals(TemplateId.DEFAULT, result?.screen?.template)
        assertEquals("future_template", result?.unknownTemplate)
    }

    @Test
    fun `a missing screen is null`() = runBlocking {
        assertNull(FirestoreConfigRepository(FakeFirestoreGateway()).screen("site_x", "scr_none").first())
    }
}