package de.lagebild.app.data.remote.feed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The Guardian Open Platform (Content API). Nutzungsbedingungen: nur Überschrift, Teaser und Link. */
object GuardianParser {

    @Serializable
    data class Envelope(val response: Body)

    @Serializable
    data class Body(val status: String? = null, val results: List<Result> = emptyList())

    @Serializable
    data class Result(
        val id: String,
        val webTitle: String,
        val webUrl: String,
        val webPublicationDate: String? = null,
        val fields: Fields? = null,
    )

    @Serializable
    data class Fields(val trailText: String? = null, val thumbnail: String? = null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Ergänzt eine Section-URL aus sources.json um API-Key und Parameter. */
    fun buildUrl(sectionUrl: String, apiKey: String): String =
        sectionUrl.toHttpUrl().newBuilder()
            .setQueryParameter("api-key", apiKey)
            .setQueryParameter("show-fields", "trailText,thumbnail")
            .setQueryParameter("page-size", "30")
            .setQueryParameter("order-by", "newest")
            .build()
            .toString()

    fun parse(body: String): List<FeedItem> =
        json.decodeFromString(Envelope.serializer(), body).response.results.map { r ->
            FeedItem(
                guid = r.id,
                title = r.webTitle,
                link = r.webUrl,
                summary = TextUtils.cleanHtml(r.fields?.trailText),
                publishedAt = TextUtils.parseDate(r.webPublicationDate),
                imageUrl = r.fields?.thumbnail,
            )
        }
}
