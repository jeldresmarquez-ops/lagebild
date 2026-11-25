package de.lagebild.app.data.remote.feed

import com.google.common.truth.Truth.assertThat
import de.lagebild.app.data.remote.DwdParser
import de.lagebild.app.domain.model.WarnLevel
import org.junit.Test

class ApiParserTest {

    @Test
    fun `tagesschau - Eilmeldung, Bild und Link werden erkannt`() {
        val json = """
            {"news":[
              {"sophoraId":"eilmeldung-1","externalId":"ext-1","title":"Razzia bei Terrorgruppe",
               "topline":"Top-Thema","firstSentence":"Die Bundesanwaltschaft hat <strong>drei</strong> Verdächtige festnehmen lassen.",
               "date":"2026-11-25T10:17:51.870+01:00","detailsweb":"https://www.tagesschau.de/eilmeldung/eilmeldung-1.html",
               "breakingNews":true,"type":"story",
               "teaserImage":{"imageVariants":{"1x1-144":"https://img/1x1.jpg","16x9-640":"https://img/16x9.jpg"}},
               "tags":[{"tag":"Eilmeldung"}], "unbekanntesFeld": 42},
              {"title":"Wetter","topline":"Deutschland","detailsweb":"https://www.tagesschau.de/wetter.html","type":"story","breakingNews":false},
              {"title":"Video","detailsweb":"https://www.tagesschau.de/v.html","type":"video"}
            ]}
        """.trimIndent()
        val items = TagesschauParser.parse(json)
        assertThat(items).hasSize(2)
        assertThat(items[0].isBreaking).isTrue()
        assertThat(items[0].title).isEqualTo("Razzia bei Terrorgruppe")
        assertThat(items[0].imageUrl).isEqualTo("https://img/16x9.jpg")
        assertThat(items[0].summary).isEqualTo("Die Bundesanwaltschaft hat drei Verdächtige festnehmen lassen.")
        assertThat(items[0].guid).isEqualTo("ext-1")
        assertThat(items[1].title).isEqualTo("Deutschland: Wetter")
        assertThat(items[1].isBreaking).isFalse()
    }

    @Test
    fun `guardian - Felder und URL-Aufbau`() {
        val json = """
            {"response":{"status":"ok","results":[
              {"id":"world/2026/nov/25/x","webTitle":"Leaders meet in Brussels","webUrl":"https://www.theguardian.com/world/x",
               "webPublicationDate":"2026-11-25T08:00:00Z","fields":{"trailText":"<p>EU summit begins</p>","thumbnail":"https://i.guim.co.uk/t.jpg"}}
            ]}}
        """.trimIndent()
        val items = GuardianParser.parse(json)
        assertThat(items).hasSize(1)
        assertThat(items[0].summary).isEqualTo("EU summit begins")
        assertThat(items[0].imageUrl).isEqualTo("https://i.guim.co.uk/t.jpg")

        val url = GuardianParser.buildUrl("https://content.guardianapis.com/world", "KEY")
        assertThat(url).contains("api-key=KEY")
        assertThat(url).contains("show-fields=trailText%2Cthumbnail")
    }

    @Test
    fun `dwd - JSONP wird gelesen und auf Berlin gefiltert`() {
        val jsonp = """
            warnWetter.loadWarnings({"time":1795598407000,"warnings":{
              "103401000":[{"type":5,"level":2,"start":1795532400000,"end":1795604400000,"regionName":"Delmenhorst","event":"FROST","headline":"Amtliche WARNUNG vor FROST"}],
              "111000000":[{"type":1,"level":3,"start":1795532400000,"end":1895604400000,"regionName":"Berlin","event":"STURMBÖEN","headline":"Amtliche UNWETTERWARNUNG vor ORKANARTIGEN BÖEN","description":"Es treten Böen bis 110 km/h auf.","instruction":""}]
            },"vorabInformation":{},"copyright":"DWD"});
        """.trimIndent()
        val warnings = DwdParser.parse(jsonp)
        assertThat(warnings).hasSize(1)
        val w = warnings[0]
        assertThat(w.title).contains("UNWETTERWARNUNG")
        assertThat(w.level).isEqualTo(WarnLevel.SEVERE)
        assertThat(w.instruction).isNull()
        assertThat(w.level.isHighPriority).isTrue()
    }

    @Test
    fun `dwd - Warnstufen`() {
        assertThat(DwdParser.levelOf(1)).isEqualTo(WarnLevel.MINOR)
        assertThat(DwdParser.levelOf(2)).isEqualTo(WarnLevel.MODERATE)
        assertThat(DwdParser.levelOf(3)).isEqualTo(WarnLevel.SEVERE)
        assertThat(DwdParser.levelOf(4)).isEqualTo(WarnLevel.EXTREME)
    }
}
