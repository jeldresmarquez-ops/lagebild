package de.lagebild.app.data.remote.feed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offizielle tagesschau-API (https://tagesschau.api.bund.dev).
 * Nutzt nur Titel, Teaser, Bild und Link – der Artikel selbst öffnet sich auf tagesschau.de.
 */
object TagesschauParser {

    @Serializable
    data class Response(val news: List<News> = emptyList())

    @Serializable
    data class News(
        val sophoraId: String? = null,
        val externalId: String? = null,
        val title: String? = null,
        val topline: String? = null,
        val firstSentence: String? = null,
        val date: String? = null,
        val detailsweb: String? = null,
        val shareURL: String? = null,
        val breakingNews: Boolean = false,
        val type: String? = null,
        val teaserImage: TeaserImage? = null,
        val tags: List<Tag> = emptyList(),
    )

    @Serializable
    data class TeaserImage(val imageVariants: Map<String, String> = emptyMap())

    @Serializable
    data class Tag(val tag: String? = null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(body: String): List<FeedItem> {
        val response = json.decodeFromString(Response.serializer(), body)
        return response.news
            .filter { it.type == null || it.type == "story" || it.type == "webview" }
            .mapNotNull { n ->
                val title = n.title?.trim().orEmpty()
                val link = n.detailsweb ?: n.shareURL
                if (title.isEmpty() || link.isNullOrBlank()) return@mapNotNull null
                val image = n.teaserImage?.imageVariants?.let { v ->
                    v["16x9-640"] ?: v["16x9-512"] ?: v["16x9-960"] ?: v.values.firstOrNull()
                }
                val breaking = n.breakingNews || n.tags.any { it.tag.equals("Eilmeldung", ignoreCase = true) }
                FeedItem(
                    guid = n.externalId ?: n.sophoraId,
                    title = if (!n.topline.isNullOrBlank() && n.topline != "Top-Thema") "${n.topline}: $title" else title,
                    link = link,
                    summary = TextUtils.cleanHtml(n.firstSentence),
                    publishedAt = TextUtils.parseDate(n.date),
                    imageUrl = image,
                    isBreaking = breaking,
                )
            }
    }
}
