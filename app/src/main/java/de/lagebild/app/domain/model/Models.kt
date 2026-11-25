package de.lagebild.app.domain.model

enum class Category(val label: String) {
    POLITIK("Politik"),
    INTERNATIONAL("International"),
    NATIONAL("Deutschland"),
    BERLIN("Berlin"),
    WISSENSCHAFT("Wissenschaft"),
    WIRTSCHAFT("Wirtschaft"),
    SPORT("Sport"),
    TECHNOLOGIE("Technologie");

    companion object {
        fun fromKey(key: String?): Category? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

data class Article(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val sourceColor: String?,
    val url: String,
    val title: String,
    val teaser: String?,
    val imageUrl: String?,
    val publishedAt: Long,
    val category: Category?,
    val isTop: Boolean,
    val isBreaking: Boolean,
    val language: String,
    val titleDe: String? = null,
    val teaserDe: String? = null,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val paywall: Boolean = false,
) {
    val isTranslated: Boolean get() = titleDe != null
    val displayTitle: String get() = titleDe ?: title
    val displayTeaser: String? get() = teaserDe ?: teaser
}

/** Mehrere Artikel verschiedener Quellen zum selben Thema. */
data class StoryGroup(
    val lead: Article,
    val related: List<Article>,
) {
    val key: String get() = lead.id
    val sourceCount: Int get() = (listOf(lead) + related).map { it.sourceId }.distinct().size
}

enum class WarnLevel(val label: String) {
    MINOR("Wetterwarnung / Hinweis"),
    MODERATE("Markante Warnung"),
    SEVERE("Unwetterwarnung / Gefahr"),
    EXTREME("Extreme Gefahr");

    val isHighPriority: Boolean get() = this >= SEVERE
}

data class Warning(
    val id: String,
    val provider: String,
    val title: String,
    val description: String?,
    val instruction: String?,
    val level: WarnLevel,
    val start: Long?,
    val end: Long?,
    val sentAt: Long,
)

enum class DisruptionKind { WARNING, CANCELLATION, DELAY }

data class TransitDisruption(
    val id: String,
    val kind: DisruptionKind,
    val lines: List<String>,
    val title: String,
    val text: String?,
    val stationName: String,
)

data class TransitStation(val id: String, val name: String)

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
)
