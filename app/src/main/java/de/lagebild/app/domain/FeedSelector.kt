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

    /** Ressorts im „Überblick“ – in dieser Reihenfolge. */
    val BRIEFING_CATEGORIES = listOf(Category.POLITIK, Category.WIRTSCHAFT, Category.WISSENSCHAFT, Category.SPORT)

    /**
     * Überblick: je Ressort die wichtigsten Themen der letzten 24 Stunden.
     * „Wichtig“ = über das Thema berichten die meisten Quellen gleichzeitig; bei Gleichstand
     * gewinnt eine Aufmacher-Meldung (Top-Feed) und danach die neuere.
     * Ein Ressort ohne aktuelle Meldungen wird weggelassen.
     *
     * Die Themen werden über *alle* Feeds gebildet und erst danach einem Ressort zugeordnet
     * (nach der häufigsten Ressort-Angabe ihrer Artikel). So zählt z. B. eine tagesschau-Meldung
     * aus „Inland“ mit, wenn andere Quellen dasselbe Thema unter „Politik“ führen.
     */
    fun briefing(
        articles: List<Article>,
        categories: List<Category> = BRIEFING_CATEGORIES,
        perCategory: Int = 2,
        now: Long = System.currentTimeMillis(),
    ): List<Pair<Category, List<StoryGroup>>> {
        val recent = articles.filter { it.publishedAt > now - 24 * HOUR }.sortedByDescending { it.publishedAt }
        val bySection = StoryGrouper.group(recent).mapNotNull { g ->
            val counts = (listOf(g.lead) + g.related).mapNotNull { it.category }.filter { it in categories }
                .groupingBy { it }.eachCount()
            // Häufigstes Ressort; bei Gleichstand das, das in [categories] zuerst kommt.
            val section = categories.filter { it in counts }.maxByOrNull { counts.getValue(it) }
            section?.let { it to g }
        }.groupBy({ it.first }, { it.second })
        return categories.mapNotNull { cat ->
            val best = bySection[cat].orEmpty()
                .sortedWith(
                    compareByDescending<StoryGroup> { it.sourceCount }
                        .thenByDescending { g -> (listOf(g.lead) + g.related).any { it.isTop } }
                        .thenByDescending { it.lead.publishedAt },
                )
                .take(perCategory)
            if (best.isEmpty()) null else cat to best
        }
    }

    fun breaking(articles: List<Article>, now: Long = System.currentTimeMillis()): Article? =
        articles.filter { it.isBreaking && it.publishedAt > now - 3 * HOUR }.maxByOrNull { it.publishedAt }
}
