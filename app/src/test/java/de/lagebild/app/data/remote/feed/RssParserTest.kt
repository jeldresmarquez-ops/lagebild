package de.lagebild.app.data.remote.feed

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.OffsetDateTime

class RssParserTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <title>Testfeed</title>
            <item>
              <title><![CDATA[Bundestag beschließt Haushalt &amp; Schuldenbremse]]></title>
              <link>https://example.de/politik/haushalt-123.html</link>
              <guid isPermaLink="false">abc-123</guid>
              <pubDate>Wed, 25 Nov 2026 09:15:15 GMT</pubDate>
              <description><![CDATA[<img src="https://img.example.de/a.jpg" /><p>Der Bundestag hat nach langer Debatte den Etat verabschiedet.</p>]]></description>
            </item>
            <item>
              <title>Zweite Meldung</title>
              <link>https://example.de/zwei</link>
              <pubDate>Wed, 25 Nov 2026 10:00:00 +0100</pubDate>
              <media:thumbnail url="https://img.example.de/thumb.jpg" width="240" height="135"/>
              <content:encoded><![CDATA[<p>Voller Text, der nicht übernommen werden soll …</p>]]></content:encoded>
            </item>
            <item>
              <title></title>
              <link>https://example.de/ohne-titel</link>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `parst RSS 2_0 mit CDATA, HTML-Teaser und Bild aus der Beschreibung`() {
        val items = RssParser.parse(rss)
        assertThat(items).hasSize(2) // Eintrag ohne Titel wird verworfen
        val first = items[0]
        assertThat(first.title).isEqualTo("Bundestag beschließt Haushalt & Schuldenbremse")
        assertThat(first.link).isEqualTo("https://example.de/politik/haushalt-123.html")
        assertThat(first.guid).isEqualTo("abc-123")
        assertThat(first.summary).isEqualTo("Der Bundestag hat nach langer Debatte den Etat verabschiedet.")
        assertThat(first.imageUrl).isEqualTo("https://img.example.de/a.jpg")
        assertThat(first.publishedAt).isEqualTo(OffsetDateTime.parse("2026-11-25T09:15:15Z").toInstant().toEpochMilli())
    }

    @Test
    fun `nutzt media-thumbnail und Zeitzonen-Offset`() {
        val second = RssParser.parse(rss)[1]
        assertThat(second.imageUrl).isEqualTo("https://img.example.de/thumb.jpg")
        assertThat(second.publishedAt).isEqualTo(OffsetDateTime.parse("2026-11-25T09:00:00Z").toInstant().toEpochMilli())
        assertThat(second.summary).startsWith("Voller Text")
    }

    @Test
    fun `parst Atom-Feeds`() {
        val atom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom</title>
              <entry>
                <title type="html">Neue Studie zu &lt;b&gt;Klimawandel&lt;/b&gt;</title>
                <link rel="alternate" href="https://science.example.org/news/1"/>
                <link rel="enclosure" href="https://science.example.org/audio.mp3"/>
                <id>tag:science.example.org,2026:1</id>
                <updated>2026-11-24T18:30:00+01:00</updated>
                <summary>Forschende haben neue Daten vorgelegt.</summary>
              </entry>
            </feed>
        """.trimIndent()
        val items = RssParser.parse(atom)
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Neue Studie zu Klimawandel")
        assertThat(items[0].link).isEqualTo("https://science.example.org/news/1")
        assertThat(items[0].guid).isEqualTo("tag:science.example.org,2026:1")
        assertThat(items[0].publishedAt).isEqualTo(OffsetDateTime.parse("2026-11-24T17:30:00Z").toInstant().toEpochMilli())
    }

    @Test
    fun `begrenzt die Anzahl der Einträge`() {
        val many = buildString {
            append("<rss><channel>")
            repeat(100) { append("<item><title>T$it</title><link>https://x.de/$it</link></item>") }
            append("</channel></rss>")
        }
        assertThat(RssParser.parse(many, maxItems = 10)).hasSize(10)
    }

    @Test
    fun `ignoriert externe Entities (XXE-Schutz)`() {
        val evil = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><title>Hallo &xxe;</title><link>https://x.de/1</link></item></channel></rss>
        """.trimIndent()
        val items = runCatching { RssParser.parse(evil) }.getOrDefault(emptyList())
        items.forEach { assertThat(it.title).doesNotContain("root:") }
    }

    @Test
    fun `versteht gängige Datumsformate`() {
        assertThat(TextUtils.parseDate("Wed, 25 Nov 2026 09:15:15 GMT")).isNotNull()
        assertThat(TextUtils.parseDate("Wed, 25 Nov 2026 09:15:15 +0100")).isNotNull()
        assertThat(TextUtils.parseDate("2026-11-25T10:17:51.870+01:00")).isNotNull()
        assertThat(TextUtils.parseDate("2026-11-25T09:00:00Z")).isNotNull()
        assertThat(TextUtils.parseDate("2026-11-24")).isNotNull()
        assertThat(TextUtils.parseDate("kein Datum")).isNull()
        assertThat(TextUtils.parseDate(null)).isNull()
    }

    @Test
    fun `cleanHtml entfernt Tags und dekodiert Entities`() {
        assertThat(TextUtils.cleanHtml("<p>A&nbsp;&amp;&#228;&#x00FC;</p>")).isEqualTo("A &äü")
        assertThat(TextUtils.cleanHtml("   ")).isNull()
        val long = "Wort ".repeat(200)
        assertThat(TextUtils.cleanHtml(long, 50)!!.length).isAtMost(52)
    }
}
