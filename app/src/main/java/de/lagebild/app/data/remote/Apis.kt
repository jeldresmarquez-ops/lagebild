package de.lagebild.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ---------------------------------------------------------------------------
// NINA – Warn-App des Bundesamts für Bevölkerungsschutz (https://nina.api.bund.dev)
// ---------------------------------------------------------------------------
interface NinaApi {
    /** Alle aktuellen Warnungen für einen Amtlichen Regionalschlüssel (Berlin = 110000000000). */
    @GET("dashboard/{ars}.json")
    suspend fun dashboard(@Path("ars") ars: String): List<NinaDashboardItem>

    @GET("warnings/{id}.json")
    suspend fun details(@Path("id") id: String): NinaDetails
}

@Serializable
data class NinaDashboardItem(
    val id: String,
    val payload: NinaPayload? = null,
    val i18nTitle: Map<String, String> = emptyMap(),
    val sent: String? = null,
)

@Serializable
data class NinaPayload(val data: NinaPayloadData? = null)

@Serializable
data class NinaPayloadData(
    val headline: String? = null,
    val provider: String? = null,
    val severity: String? = null,
    val msgType: String? = null,
)

@Serializable
data class NinaDetails(val sent: String? = null, val info: List<NinaInfo> = emptyList())

@Serializable
data class NinaInfo(
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val severity: String? = null,
    val onset: String? = null,
    val expires: String? = null,
)

// ---------------------------------------------------------------------------
// VBB/BVG über transport.rest (https://v6.vbb.transport.rest) – offene, freie HAFAS-Schnittstelle
// ---------------------------------------------------------------------------
interface VbbApi {
    @GET("locations")
    suspend fun locations(
        @Query("query") query: String,
        @Query("results") results: Int = 8,
        @Query("poi") poi: Boolean = false,
        @Query("addresses") addresses: Boolean = false,
    ): List<VbbLocation>

    @GET("stops/{id}/departures")
    suspend fun departures(
        @Path("id") stopId: String,
        @Query("duration") durationMinutes: Int = 60,
        @Query("results") results: Int = 150,
        @Query("remarks") remarks: Boolean = true,
    ): VbbDepartures
}

@Serializable
data class VbbLocation(val type: String? = null, val id: String? = null, val name: String? = null)

@Serializable
data class VbbDepartures(val departures: List<VbbDeparture> = emptyList())

@Serializable
data class VbbDeparture(
    val tripId: String? = null,
    val direction: String? = null,
    val delay: Int? = null,
    val cancelled: Boolean = false,
    val line: VbbLine? = null,
    val remarks: List<VbbRemark> = emptyList(),
)

@Serializable
data class VbbLine(val name: String? = null, val product: String? = null)

@Serializable
data class VbbRemark(
    val id: String? = null,
    val type: String? = null,
    val summary: String? = null,
    val text: String? = null,
)

// ---------------------------------------------------------------------------
// GitHub Releases (für In-App-Updates)
// ---------------------------------------------------------------------------
interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(@Path("owner") owner: String, @Path("repo") repo: String): GitHubRelease
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)

/** Inhalt von update.json, das die GitHub Action an jedes Release hängt. */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
)
