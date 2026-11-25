package de.lagebild.app.domain

import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.Category
import de.lagebild.app.domain.model.StoryGroup

/** Wählt und sortiert Artikel für Startseite und Kategorien. */
object FeedSelector {
    private const val HOUR = 60L * 60 * 1000

    /**
     * Top-Meldungen: Artikel aus den Top-Feeds der letzten 24 Stunden, zu Themen gruppiert.
     * Themen, über die mehrere Quellen berichten, rutschen nach oben (je Quelle +2 Std. „Bonus“).
     */
    fun topStories(articles: List<Article>, now: Long = System.currentTimeMillis()): List<StoryGroup> {
        val recent = articles.filter { it.publishedAt > now - 24 * HOUR }
        val tops = recent.filter { it.isTop || it.isBreaking }
        // Auch Nicht-Top-Artikel anderer Quellen dürfen sich einem Top-Thema anschließen.
        val groups = StoryGrouper.group(tops.sortedByDescending { it.publishedAt } + recent.filter { !it.isTop && !it.isBreaking })
            .filter { it.lead.isTop || it.lead.isBreaking }
        return groups.sortedByDescending { g ->
            val breakingBonus = if (g.lead.isBreaking && g.lead.publishedAt > now - 3 * HOUR) 24 * HOUR else 0
            g.lead.publishedAt + (g.sourceCount - 1) * 2 * HOUR + breakingBonus
        }
    }

    fun category(articles: List<Article>, category: Category): List<StoryGroup> =
        StoryGrouper.group(articles.filter { it.category == category }.sortedByDescending { it.publishedAt })

    fun breaking(articles: List<Article>, now: Long = System.currentTimeMillis()): Article? =
        articles.filter { it.isBreaking && it.publishedAt > now - 3 * HOUR }.maxByOrNull { it.publishedAt }
}
