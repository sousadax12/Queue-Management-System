package pro.joaosousa.queuedisplay.tv.data.rss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** RSS 2.0 and Atom, including the markup the real feeds put inside their titles. */
class FeedParserTest {

    @Test
    fun `parses an RSS 2_0 channel with its items in document order`() {
        val feed = FeedParser.parse(RSS)

        assertEquals("RTP Notícias", feed.title)
        assertEquals(2, feed.items.size)
        assertEquals("Greve nos transportes", feed.items[0].title)
        assertEquals("https://example.test/a", feed.items[0].link)
        assertEquals("Olá mundo", feed.items[0].description)
        assertEquals("Segunda notícia", feed.items[1].title)
        assertEquals("Uma descrição sem markup", feed.items[1].description)
    }

    @Test
    fun `parses an Atom feed and prefers the alternate link`() {
        val feed = FeedParser.parse(ATOM)

        assertEquals("RTP Notícias", feed.title)
        assertEquals(1, feed.items.size)
        assertEquals("Greve nos transportes", feed.items[0].title)   // entities decoded, then tags
        assertEquals("https://example.test/a", feed.items[0].link)   // not rel="self"
        assertEquals("Resumo & detalhe", feed.items[0].description)
    }

    @Test
    fun `titles lose CDATA wrappers, tags and runs of whitespace`() {
        val feed = FeedParser.parse(RSS)

        assertTrue(feed.items.none { it.title.contains("<") || it.title.contains("CDATA") })
        assertEquals("Segunda notícia", feed.items[1].title)   // five spaces collapsed to one
        assertTrue(feed.items.all { it.title == it.title.trim() })
    }

    @Test
    fun `an item with no title is dropped instead of ticking a blank line`() {
        val feed = FeedParser.parse(
            """
            <rss version="2.0"><channel><title>Só isto</title>
              <item><link>https://example.test/sem-titulo</link></item>
            </channel></rss>
            """.trimIndent(),
        )

        assertTrue(feed.items.isEmpty())
        assertEquals("Só isto", feed.title)
    }

    @Test
    fun `a channel without a title is a null title, not an empty string`() {
        val feed = FeedParser.parse("<rss version=\"2.0\"><channel></channel></rss>")

        assertNull(feed.title)
        assertTrue(feed.items.isEmpty())
    }

    @Test
    fun `malformed XML throws so the caller can show an error state`() {
        assertThrows<Exception> { FeedParser.parse("<rss><channel><item></rss>") }
        assertThrows<Exception> { FeedParser.parse("not xml at all") }
        assertThrows<Exception> { FeedParser.parse("") }
    }

    @Test
    fun `well-formed XML that is not a feed is rejected by name`() {
        val failure = assertThrows<FeedFormatException> { FeedParser.parse("<html><body/></html>") }

        assertTrue(failure.message.orEmpty().contains("html"))
    }

    @Test
    fun `an RSS document without a channel is a format error`() {
        assertThrows<FeedFormatException> { FeedParser.parse("<rss version=\"2.0\"></rss>") }
    }

    private companion object {
        val RSS = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0">
              <channel>
                <title><![CDATA[RTP <b>Notícias</b>]]></title>
                <item>
                  <title><![CDATA[<b>Greve</b> nos transportes]]></title>
                  <link>https://example.test/a</link>
                  <description><![CDATA[<p>Olá <b>mundo</b></p>]]></description>
                </item>
                <item>
                  <title>Segunda     notícia</title>
                  <link>https://example.test/b</link>
                  <description>Uma descrição sem markup</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        /** Same fixture with an item whose title is wrapped over two lines. */
        val ATOM = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>RTP Notícias</title>
              <entry>
                <title type="html">&lt;b&gt;Greve&lt;/b&gt; nos transportes</title>
                <link rel="self" href="https://example.test/self"/>
                <link rel="alternate" href="https://example.test/a"/>
                <summary>Resumo &amp; detalhe</summary>
              </entry>
            </feed>
        """.trimIndent()
    }
}