package de.lagebild.app.data.remote.feed

/** Ein Eintrag aus einem beliebigen Feed (RSS, Atom, tagesschau, Guardian), noch ohne Quellen-Zuordnung. */
data class FeedItem(
    val guid: String?,
    val title: String,
    val link: String,
    val summary: String?,
    val publishedAt: Long?,
    val imageUrl: String?,
    val isBreaking: Boolean = false,
)
