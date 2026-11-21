package pro.joaosousa.queuedisplay.tv.data.rss

import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** The body is not an RSS 2.0 document. */
class RssParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads the item titles of an RSS 2.0 feed (`rss/channel/item/title`), as Flutter's `webfeed`
 * did for the ticker. Titles are trimmed and blank ones dropped. SAX, so it runs on Android and
 * on the JVM alike; the XML declaration decides the encoding. External entities are never
 * fetched.
 */
object RssParser {

    /** @throws RssParseException if the body is not well-formed XML or its root is not `<rss>`. */
    fun titles(body: InputStream): List<String> {
        val handler = TitleHandler()
        try {
            val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser()
            val reader = parser.xmlReader
            reader.entityResolver = NoExternalEntities
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.parse(InputSource(body))
        } catch (e: SAXException) {
            throw RssParseException("Not well-formed XML: ${e.message}", e)
        } catch (e: IllegalStateException) {
            throw RssParseException(e.message ?: "Not RSS", e)
        }
        if (!handler.sawRss) throw RssParseException("Empty document")
        return handler.titles
    }

    private object NoExternalEntities : org.xml.sax.EntityResolver {
        override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(StringReader(""))
    }

    private class TitleHandler : DefaultHandler() {
        val titles = mutableListOf<String>()
        var sawRss = false
        private val path = ArrayDeque<String>()
        private var text: StringBuilder? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            if (path.isEmpty()) {
                check(qName == "rss") { "Not RSS: the root element is <$qName>" }
                sawRss = true
            }
            path.addLast(qName)
            if (path == ITEM_TITLE) text = StringBuilder()
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text?.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            if (path == ITEM_TITLE) {
                text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(titles::add)
                text = null
            }
            path.removeLast()
        }

        override fun fatalError(e: org.xml.sax.SAXParseException) = throw e

        companion object {
            private val ITEM_TITLE = listOf("rss", "channel", "item", "title")
        }
    }
}
