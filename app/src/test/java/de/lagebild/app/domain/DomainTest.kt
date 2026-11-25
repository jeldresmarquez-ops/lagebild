package de.lagebild.app.domain

import com.google.common.truth.Truth.assertThat
import de.lagebild.app.data.remote.VbbDeparture
import de.lagebild.app.data.remote.VbbLine
import de.lagebild.app.data.remote.VbbRemark
import de.lagebild.app.data.repository.TransitRepository
import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.Category
import de.lagebild.app.domain.model.DisruptionKind
import de.lagebild.app.domain.model.TransitStation
import de.lagebild.app.notifications.QuietHours
import de.lagebild.app.update.UpdateManager
import org.junit.Test

class DomainTest {

    private fun article(id: String, source: String, title: String, time: Long = 1_000_000L, titleDe: String? = null) = Article(
        id = id, sourceId = source, sourceName = source, sourceColor = null, url = "https://$source/$id",
        title = title, teaser = null, imageUrl = null, publishedAt = time, category = null,
        isTop = true, isBreaking = false, language = if (titleDe != null) "en" else "de", titleDe = titleDe,
    )

    @Test
    fun `gruppiert gleiche Themen verschiedener Quellen`() {
        val list = listOf(
            article("1", "tagesschau", "Bundestag beschließt Haushalt für 2027"),
            article("2", "spiegel", "Haushalt 2027: Bundestag beschließt Etat nach langer Debatte"),
            article("3", "bbc", "German parliament passes budget", titleDe = "Bundestag beschließt Haushalt"),
            article("4", "zeit", "Sturmwarnung für Norddeutschland"),
        )
        val groups = StoryGrouper.group(list)
        assertThat(groups).hasSize(2)
        assertThat(groups[0].lead.id).isEqualTo("1")
        assertThat(groups[0].related.map { it.id }).containsExactly("2", "3")
        assertThat(groups[0].sourceCount).isEqualTo(3)
    }

    @Test
    fun `gruppiert nicht zwei Artikel derselben Quelle`() {
        val list = listOf(
            article("1", "spiegel", "Bundestag beschließt Haushalt"),
            article("2", "spiegel", "Bundestag beschließt Haushalt – die Reaktionen"),
        )
        assertThat(StoryGrouper.group(list)).hasSize(2)
    }

    @Test
    fun `gruppiert nicht über große Zeitabstände`() {
        val day = 24L * 60 * 60 * 1000
        val list = listOf(
            article("1", "a", "Bundestag beschließt Haushalt", time = 10 * day),
            article("2", "b", "Bundestag beschließt Haushalt", time = 8 * day),
        )
        assertThat(StoryGrouper.group(list)).hasSize(2)
    }

    @Test
    fun `Ruhezeiten auch über Mitternacht`() {
        assertThat(QuietHours.isQuiet(23 * 60, 22 * 60, 7 * 60)).isTrue()
        assertThat(QuietHours.isQuiet(3 * 60, 22 * 60, 7 * 60)).isTrue()
        assertThat(QuietHours.isQuiet(12 * 60, 22 * 60, 7 * 60)).isFalse()
        assertThat(QuietHours.isQuiet(13 * 60, 12 * 60, 14 * 60)).isTrue()
        assertThat(QuietHours.isQuiet(14 * 60, 12 * 60, 14 * 60)).isFalse()
        assertThat(QuietHours.isQuiet(5, 0, 0)).isFalse()
        assertThat(QuietHours.format(7 * 60 + 30)).isEqualTo("07:30")
    }

    @Test
    fun `Update nur bei höherem versionCode`() {
        assertThat(UpdateManager.isNewer(5, 4)).isTrue()
        assertThat(UpdateManager.isNewer(4, 4)).isFalse()
        assertThat(UpdateManager.isNewer(3, 4)).isFalse()
    }

    @Test
    fun `Verkehr - Störungen nur für Lieblingslinien`() {
        val station = TransitStation("900100003", "S+U Alexanderplatz")
        val deps = listOf(
            VbbDeparture(line = VbbLine("U2"), remarks = listOf(VbbRemark("him1", "warning", "Bauarbeiten U2", "Zwischen A und B Ersatzverkehr"))),
            VbbDeparture(line = VbbLine("U2"), remarks = listOf(VbbRemark("him1", "warning", "Bauarbeiten U2", "Zwischen A und B Ersatzverkehr"))),
            VbbDeparture(line = VbbLine("U2"), cancelled = true, direction = "Pankow"),
            VbbDeparture(line = VbbLine("S7"), delay = 900),
            VbbDeparture(line = VbbLine("100"), remarks = listOf(VbbRemark("him2", "warning", "Umleitung 100"))),
            VbbDeparture(line = VbbLine("S5"), remarks = listOf(VbbRemark(null, "hint", "Fahrradmitnahme möglich"))),
        )
        val result = TransitRepository.extractDisruptions(station, deps, setOf("u2", "S7"))
        assertThat(result.map { it.kind }).containsExactly(DisruptionKind.WARNING, DisruptionKind.CANCELLATION, DisruptionKind.DELAY)
        assertThat(result.first().lines).containsExactly("U2")
        assertThat(result[2].title).isEqualTo("Verspätungen bis 15 Minuten")

        val all = TransitRepository.extractDisruptions(station, deps, emptySet())
        assertThat(all.count { it.kind == DisruptionKind.WARNING }).isEqualTo(2)
    }

    @Test
    fun `Ueberblick nimmt je Ressort die zwei meistberichteten Themen`() {
        val now = 100L * 60 * 60 * 1000
        fun a(id: String, src: String, title: String, cat: Category, ageH: Long = 1, top: Boolean = false) =
            article(id, src, title, time = now - ageH * 60 * 60 * 1000).copy(category = cat, isTop = top)
        val list = listOf(
            // Politik: Thema „Haushalt“ bei drei Quellen, „Rente“ bei zwei, „Wahlrecht“ bei einer
            a("p1", "tagesschau", "Bundestag beschließt Haushalt für 2027", Category.POLITIK),
            a("p2", "dlf", "Haushalt 2027: Bundestag beschließt Etat", Category.POLITIK),
            a("p3", "taz", "Bundestag beschließt umstrittenen Haushalt 2027", Category.POLITIK),
            a("p4", "ntv", "Rentenpaket: Koalition einigt sich auf Rentenniveau", Category.POLITIK),
            a("p5", "zdf", "Koalition einigt sich beim Rentenpaket auf Rentenniveau", Category.POLITIK),
            a("p6", "dw", "Neues Wahlrecht vor Gericht", Category.POLITIK, top = true),
            // Sport: nur eine Meldung, plus eine zu alte
            a("s1", "ntv", "Bayern gewinnt Spitzenspiel", Category.SPORT),
            a("s2", "taz", "Alte Meldung vom Vortag", Category.SPORT, ageH = 30),
            // anderes Ressort zählt nicht
            a("t1", "golem", "Neues Smartphone vorgestellt", Category.TECHNOLOGIE),
        )
        val result = FeedSelector.briefing(list, now = now)
        assertThat(result.map { it.first }).containsExactly(Category.POLITIK, Category.SPORT).inOrder()
        val politik = result[0].second
        assertThat(politik).hasSize(2)
        assertThat(politik[0].sourceCount).isEqualTo(3)
        assertThat(politik[1].sourceCount).isEqualTo(2)
        assertThat(result[1].second.map { it.lead.id }).containsExactly("s1")
    }

    @Test
    fun `Ueberblick bevorzugt bei Gleichstand Aufmacher`() {
        val now = 100L * 60 * 60 * 1000
        val list = listOf(
            article("w1", "ntv", "Dax schließt kaum verändert", time = now - 1000).copy(category = Category.WIRTSCHAFT, isTop = false),
            article("w2", "dlf", "Inflation sinkt im November deutlich", time = now - 5000).copy(category = Category.WIRTSCHAFT, isTop = true),
        )
        val result = FeedSelector.briefing(list, perCategory = 1, now = now)
        assertThat(result.single().second.single().lead.id).isEqualTo("w2")
    }

    @Test
    fun `Ueberblick zaehlt Quellen auch aus anderen Ressorts mit`() {
        val now = 100L * 60 * 60 * 1000
        val t = now - 60_000
        val list = listOf(
            // Dasselbe Thema: taz führt es unter Politik, tagesschau unter Inland, DLF nur im Top-Feed
            article("a", "taz", "Bundestag streitet über Wehrpflicht", time = t).copy(category = Category.POLITIK),
            article("b", "tagesschau", "Wehrpflicht: Bundestag streitet über Gesetz", time = t).copy(category = Category.NATIONAL),
            article("c", "dlf", "Streit im Bundestag über Wehrpflicht", time = t).copy(category = null),
            article("d", "ntv", "Kanzler reist nach Paris", time = t).copy(category = Category.POLITIK),
        )
        val politik = FeedSelector.briefing(list, now = now).single { it.first == Category.POLITIK }.second
        assertThat(politik.first().sourceCount).isEqualTo(3)
        assertThat(politik.map { it.sourceCount }).containsExactly(3, 1).inOrder()
    }
}
