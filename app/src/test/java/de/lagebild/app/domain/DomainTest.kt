package de.lagebild.app.domain

import com.google.common.truth.Truth.assertThat
import de.lagebild.app.data.remote.VbbDeparture
import de.lagebild.app.data.remote.VbbLine
import de.lagebild.app.data.remote.VbbRemark
import de.lagebild.app.data.repository.TransitRepository
import de.lagebild.app.domain.model.Article
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
}
