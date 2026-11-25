package de.lagebild.app

import de.lagebild.app.data.remote.feed.RssParser
import de.lagebild.app.data.remote.feed.TagesschauParser
import de.lagebild.app.data.sources.SourceList
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Prüft alle Feeds aus config/sources.json gegen das echte Internet.
 * Läuft nur, wenn die Umgebungsvariable LIVE_FEEDS gesetzt ist (siehe README):
 *   LIVE_FEEDS=1 ./gradlew testDebugUnitTest --tests "*LiveFeedsCheck*"
 * Ergebnis: app/build/live-feeds.txt
 */
class LiveFeedsCheck {
    @Test
    fun allFeeds() {
        Assume.assumeTrue(System.getenv("LIVE_FEEDS") != null)
        val json = Json { ignoreUnknownKeys = true }
        val list = json.decodeFromString(SourceList.serializer(), File("../config/sources.json").readText())
        val client = OkHttpClient()
        val out = StringBuilder()
        for (s in list.sources) for (f in s.feeds) {
            if (s.type == "guardian") continue
            val line = runCatching {
                val body = client.newCall(Request.Builder().url(f.url).header("User-Agent", "Lagebild-Test").build()).execute().use { it.body!!.bytes() }
                val items = if (s.type == "tagesschau") TagesschauParser.parse(body.toString(Charsets.UTF_8)) else RssParser.parse(body.inputStream())
                val noDate = items.count { it.publishedAt == null }
                val img = items.count { it.imageUrl != null }
                "OK  ${items.size} items, noDate=$noDate, img=$img  | ${items.firstOrNull()?.title?.take(60)} | ${f.url}"
            }.getOrElse { "ERR ${it.javaClass.simpleName}: ${it.message?.take(80)} | ${f.url}" }
            out.appendLine(line)
        }
        File("build/live-feeds.txt").writeText(out.toString())
    }

    /** Warn- und Verkehrs-APIs mit den echten DTOs der App dekodieren. */
    @Test
    fun warningAndTransitApis() {
        Assume.assumeTrue(System.getenv("LIVE_FEEDS") != null)
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }
        val client = OkHttpClient()
        fun get(url: String) = client.newCall(Request.Builder().url(url).header("User-Agent", "Lagebild-Test").build())
            .execute().use { it.body!!.string() }
        val out = StringBuilder()
        val dwd = de.lagebild.app.data.remote.DwdParser.parse(get(de.lagebild.app.data.remote.DwdParser.URL))
        out.appendLine("DWD Berlin: ${dwd.size} Warnungen ${dwd.map { it.title }}")
        // Saarlouis als Beispiel-Kreis mit aktiver Meldung, Berlin als Ziel
        for (ars in listOf("110000000000", "100440000000")) {
            val items = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(de.lagebild.app.data.remote.NinaDashboardItem.serializer()),
                get("https://warnung.bund.de/api31/dashboard/$ars.json"),
            )
            out.appendLine("NINA $ars: ${items.size} ${items.map { it.payload?.data?.provider + ":" + it.i18nTitle["de"]?.take(40) }}")
            items.firstOrNull()?.let {
                val d = json.decodeFromString(de.lagebild.app.data.remote.NinaDetails.serializer(), get("https://warnung.bund.de/api31/warnings/${it.id}.json"))
                out.appendLine("  Details: ${d.info.firstOrNull()?.severity} ${d.info.firstOrNull()?.description?.take(60)}")
            }
        }
        val deps = json.decodeFromString(
            de.lagebild.app.data.remote.VbbDepartures.serializer(),
            get("https://v6.vbb.transport.rest/stops/900100003/departures?duration=60&results=150&remarks=true"),
        )
        out.appendLine("VBB Alexanderplatz: ${deps.departures.size} Abfahrten, Linien ${deps.departures.mapNotNull { it.line?.name }.distinct().take(15)}")
        val disruptions = de.lagebild.app.data.repository.TransitRepository.extractDisruptions(
            de.lagebild.app.domain.model.TransitStation("900100003", "Alexanderplatz"), deps.departures, emptySet(),
        )
        out.appendLine("  Störungen: ${disruptions.map { it.kind.name + " " + it.lines + " " + it.title }}")
        File("build/live-apis.txt").writeText(out.toString())
    }
}
