package de.lagebild.app.data.remote.feed

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Liest RSS 2.0, RSS 1.0 (RDF) und Atom.
 * SAX läuft sowohl auf Android als auch in normalen JVM-Unit-Tests.
 * Externe Entities sind abgeschaltet (Schutz vor XXE-Angriffen).
 */
object RssParser {

    fun parse(input: InputStream, maxItems: Int = 60): List<FeedItem> {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val handler = Handler(maxItems)
        try {
            factory.newSAXParser().parse(InputSource(input), handler)
        } catch (_: StopParsing) {
            // genug Einträge gelesen
        }
        return handler.items
    }

    fun parse(xml: String, maxItems: Int = 60): List<FeedItem> =
        parse(xml.byteInputStream(Charsets.UTF_8), maxItems)

    private class StopParsing : RuntimeException()

    private class Handler(private val maxItems: Int) : DefaultHandler() {
        val items = ArrayList<FeedItem>()

        private var inItem = false
        private val text = StringBuilder()

        private var title: String? = null
        private var link: String? = null
        private var guid: String? = null
        private var description: String? = null
        private var content: String? = null
        private var date: String? = null
        private var image: String? = null

        private fun reset() {
            title = null; link = null; guid = null; description = null
            content = null; date = null; image = null
        }

        override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {
            val name = localName.ifEmpty { qName.substringAfter(':') }
            text.setLength(0)
            when (name) {
                "item", "entry" -> { inItem = true; reset() }
            }
            if (!inItem) return
            when (name) {
                // Atom: <link rel="alternate" href="..."/>
                "link" -> {
                    val href = atts.getValue("href")
                    val rel = atts.getValue("rel")
                    if (href != null && (rel == null || rel == "alternate") && link == null) link = href
                }
                "enclosure" -> {
                    val type = atts.getValue("type") ?: ""
                    val url = atts.getValue("url")
                    if (url != null && (type.startsWith("image") || type.isEmpty()) && image == null) image = url
                }
                "thumbnail", "content" -> {
                    // media:thumbnail / media:content (nur Bilder)
                    val url = atts.getValue("url")
                    val medium = atts.getValue("medium") ?: atts.getValue("type") ?: "image"
                    if (url != null && medium.startsWith("image") && image == null) image = url
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val name = localName.ifEmpty { qName.substringAfter(':') }
            if (!inItem) return
            val value = text.toString().trim()
            when (name) {
                "title" -> if (title == null) title = value
                "link" -> if (link == null && value.isNotEmpty()) link = value
                "guid", "id" -> if (guid == null) guid = value
                "description", "summary" -> if (description == null) description = value
                "encoded" -> if (content == null) content = value // content:encoded
                "content" -> if (content == null && value.isNotEmpty()) content = value
                "pubDate", "published", "updated", "date", "issued" -> if (date == null) date = value
                "item", "entry" -> {
                    inItem = false
                    finishItem()
                    if (items.size >= maxItems) throw StopParsing()
                }
            }
            text.setLength(0)
        }

        private fun finishItem() {
            val t = title?.let { TextUtils.decodeEntities(TextUtils.cleanHtml(it, 300) ?: it) }
            val l = link?.trim()
            if (t.isNullOrBlank() || l.isNullOrBlank()) return
            val rawSummary = description ?: content
            items += FeedItem(
                guid = guid?.takeIf { it.isNotBlank() },
                title = t,
                link = l,
                summary = TextUtils.cleanHtml(rawSummary),
                publishedAt = TextUtils.parseDate(date),
                imageUrl = image ?: TextUtils.firstImage(rawSummary) ?: TextUtils.firstImage(content),
            )
        }
    }
}
