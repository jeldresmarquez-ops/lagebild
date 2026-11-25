package de.lagebild.app.data.sources

import kotlinx.serialization.Serializable

/** Struktur von config/sources.json im GitHub-Repository. */
@Serializable
data class SourceList(
    val schemaVersion: Int = 1,
    val sources: List<NewsSource> = emptyList(),
)

@Serializable
data class NewsSource(
    val id: String,
    val name: String,
    /** "rss", "tagesschau" oder "guardian" */
    val type: String = "rss",
    /** ISO-Sprachcode, z. B. "de" oder "en". Nicht-deutsche Quellen werden übersetzt. */
    val language: String = "de",
    val group: String = "Weitere",
    val color: String? = null,
    val homepage: String? = null,
    val paywall: Boolean = false,
    val enabledByDefault: Boolean = true,
    val feeds: List<FeedConfig> = emptyList(),
) {
    val needsTranslation: Boolean get() = !language.equals("de", ignoreCase = true)
}

@Serializable
data class FeedConfig(
    val url: String,
    /** Category-Name, z. B. "POLITIK". Leer bei reinen Top-Feeds. */
    val category: String? = null,
    /** Feed enthält die wichtigsten Meldungen der Quelle (Startseite). */
    val top: Boolean = false,
)
