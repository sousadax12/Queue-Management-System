package pro.joaosousa.queuedisplay.tv.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RssParserTest {

    private fun parse(xml: String) = RssParser.titles(xml.byteInputStream())

    private fun resource(name: String) =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing test resource $name" }

    @Test
    fun readsTheItemTitlesOfASavedRtpFeed() {
        // https://www.rtp.pt/noticias/rss on 2026-11-21, cut to its first five items.
        val titles = resource("rss/rtp-2026-11-21.xml").use(RssParser::titles)

        assertEquals(
            listOf(
                "10h Foi identificado o autor da ameaça de bomba na Convenção do Chega",
                "Évora, o lugar onde (de)Vagar se faz cultura",
                "OE2027. \"Solidário\" com Montenegro, Passos Coelho diz que \"não aceitaria\" que fosse Parlamento a governar",
                "09h Há três distritos com aviso vermelho do IPMA",
                "Constituído arguido. PSP identificou autor da ameaça de bomba na convenção do Chega",
            ),
            titles,
        )
    }

    @Test
    fun ignoresTheChannelAndImageTitles() {
        val titles = parse(
            """
            <rss version="2.0"><channel>
              <title>Channel</title>
              <image><title>Logo</title></image>
              <item><title>One</title></item>
            </channel></rss>
            """.trimIndent(),
        )

        assertEquals(listOf("One"), titles)
    }

    @Test
    fun trimsCdataTitlesAndDropsBlankOnes() {
        val titles = parse(
            """
            <rss version="2.0"><channel>
              <item><title><![CDATA[  Spaced & quoted  ]]></title></item>
              <item><title>   </title></item>
              <item><description>no title</description></item>
            </channel></rss>
            """.trimIndent(),
        )

        assertEquals(listOf("Spaced & quoted"), titles)
    }

    @Test
    fun readsTheEncodingFromTheXmlDeclaration() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><rss><channel><item><title>Évora</title></item></channel></rss>"""

        assertEquals(listOf("Évora"), RssParser.titles(xml.toByteArray(Charsets.ISO_8859_1).inputStream()))
    }

    @Test
    fun anEmptyChannelHasNoTitles() {
        assertEquals(emptyList<String>(), parse("""<rss version="2.0"><channel><title>Empty</title></channel></rss>"""))
    }

    @Test
    fun anEmptyBodyIsAnError() {
        assertThrows(RssParseException::class.java) { parse("") }
    }

    @Test
    fun malformedXmlIsAnError() {
        assertThrows(RssParseException::class.java) {
            parse("""<rss version="2.0"><channel><item><title>Cut off""")
        }
    }

    @Test
    fun anHtmlPageIsAnError() {
        assertThrows(RssParseException::class.java) {
            parse("""<html><body><item><title>Not a feed</title></item></body></html>""")
        }
    }

    @Test
    fun neverFetchesExternalEntities() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY ext SYSTEM "http://127.0.0.1:9/secret">]>
            <rss><channel><item><title>A&ext;B</title></item></channel></rss>
        """.trimIndent()

        assertEquals(listOf("AB"), parse(xml))
    }
}
