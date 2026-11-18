package pro.joaosousa.queuedisplay.tv.data.rss

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import pro.joaosousa.queuedisplay.tv.domain.model.Feed
import pro.joaosousa.queuedisplay.tv.domain.model.FeedItem
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Thrown when the bytes are XML but not a feed this parser understands. */
class FeedFormatException(message: String) : Exception(message)

/**
 * RSS 2.0 and Atom -> [Feed], using the JDK's DOM parser (design D6).
 *
 * `webfeed` has no Android equivalent and pulling a feed library in for two dialects is not worth
 * the dependency, so this is a deliberately small reader: the ticker only needs a title, a link and
 * one line of text per entry. Titles and descriptions are stripped of markup and CDATA because the
 * ticker draws text, not HTML, and a raw `<p>` would show as literal angle brackets.
 *
 * Parsing is strict about XML and forgiving about feeds: anything that is not RSS/Atom, or is
 * malformed, throws so the caller can report an error state instead of showing an empty ticker.
 */
object FeedParser {

    fun parse(xml: String): Feed {
        val root = document(xml).documentElement
            ?: throw FeedFormatException("empty document")
        return when (root.local()) {
            "rss" -> rss(root)
            "feed" -> atom(root)
            else -> throw FeedFormatException("not an RSS or Atom document: <${root.tagName}>")
        }
    }

    private fun rss(root: Element): Feed {
        val channel = root.child("channel")
            ?: throw FeedFormatException("RSS document has no <channel>")
        val items = channel.children("item").mapNotNull { item ->
            entry(
                title = text(item.child("title")),
                link = text(item.child("link")),
                description = text(item.child("description")) ?: text(item.child("encoded")),
            )
        }
        return Feed(title = text(channel.child("title")), items = items)
    }

    private fun atom(root: Element): Feed {
        val items = root.children("entry").mapNotNull { node ->
            entry(
                title = text(node.child("title")),
                link = atomLink(node),
                description = text(node.child("summary")) ?: text(node.child("content")),
            )
        }
        return Feed(title = text(root.child("title")), items = items)
    }

    /** An entry with no title has nothing to ticker; dropping it beats a blank line. */
    private fun entry(title: String?, link: String?, description: String?): FeedItem? =
        title?.let { FeedItem(title = it, link = link, description = description) }

    /** Atom puts the target in `href`: prefer `rel="alternate"`, else the first link there is. */
    private fun atomLink(entry: Element): String? {
        val links = entry.children("link").filter { it.hasAttribute("href") }
        val alternate = links.firstOrNull { it.getAttribute("rel") in setOf("", "alternate") }
        return (alternate ?: links.firstOrNull())?.getAttribute("href")?.takeIf { it.isNotBlank() }
    }

    /** Element text with tags, CDATA wrappers and HTML entities reduced to a single ticker line. */
    private fun text(element: Element?): String? {
        if (element == null) return null
        // textContent already resolves CDATA sections; what is left is markup and entities.
        return clean(element.textContent.orEmpty()).takeIf { it.isNotBlank() }
    }

    private fun document(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            isXIncludeAware = false
            // A feed must not be able to make the box fetch anything: no DTDs, no external refs.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }

    private fun clean(raw: String): String = raw
        .replace(TAG, " ")
        .let(::unescape)
        .replace('\u00a0', ' ')
        .replace(WHITESPACE, " ")
        .trim()

    /** `&amp;` last: unescaping it first would turn `&amp;lt;` into a tag. */
    private fun unescape(text: String): String = ENTITIES.entries.fold(text) { acc, (entity, char) ->
        acc.replace(entity, char)
    }

    private fun Element.local(): String = tagName.substringAfter(':')

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun Element.children(name: String): List<Element> {
        val nodes = childNodes
        return buildList {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                if (node is Element && node.local() == name) add(node)
            }
        }
    }

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val ENTITIES = linkedMapOf(
        "&nbsp;" to " ",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#39;" to "'",
        "&lt;" to "<",
        "&gt;" to ">",
        "&amp;" to "&",
    )
}