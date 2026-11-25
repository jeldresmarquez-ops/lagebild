package de.lagebild.app.data.repository

import android.util.Log
import de.lagebild.app.data.local.WarningDao
import de.lagebild.app.data.local.toDomain
import de.lagebild.app.data.local.toEntity
import de.lagebild.app.data.remote.DwdParser
import de.lagebild.app.data.remote.HttpFetcher
import de.lagebild.app.data.remote.NinaApi
import de.lagebild.app.data.remote.feed.TextUtils
import de.lagebild.app.domain.model.WarnLevel
import de.lagebild.app.domain.model.Warning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class WarningRefresh(val warnings: List<Warning>, val error: String?)

/** Gefahrenmeldungen für Berlin aus NINA (BBK) und vom Deutschen Wetterdienst. */
@Singleton
class WarningRepository @Inject constructor(
    private val nina: NinaApi,
    private val fetcher: HttpFetcher,
    private val dao: WarningDao,
) {
    fun observe(): Flow<List<Warning>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }.sortedWith(compareByDescending<Warning> { it.level }.thenByDescending { it.sentAt })
    }

    suspend fun refresh(): WarningRefresh = coroutineScope {
        val ninaJob = async { runCatching { loadNina() } }
        val dwdJob = async { runCatching { DwdParser.parse(fetcher.getString(DwdParser.URL)) } }
        val ninaResult = ninaJob.await()
        val dwdResult = dwdJob.await()
        listOf(ninaResult, dwdResult).forEach { r ->
            r.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        }

        val errors = listOfNotNull(
            ninaResult.exceptionOrNull()?.let { "NINA nicht erreichbar" },
            dwdResult.exceptionOrNull()?.let { "DWD nicht erreichbar" },
        )
        val now = System.currentTimeMillis()
        val all = (ninaResult.getOrDefault(emptyList()) + dwdResult.getOrDefault(emptyList()))
            .filter { it.end == null || it.end > now }

        // Nur ersetzen, wenn mindestens eine Quelle geantwortet hat – sonst Cache behalten.
        if (ninaResult.isSuccess || dwdResult.isSuccess) {
            val keep = if (ninaResult.isSuccess && dwdResult.isSuccess) all else {
                val cached = dao.getAll().map { it.toDomain() }
                val failedProvider = if (ninaResult.isFailure) "NINA" else "DWD"
                all + cached.filter { providerKey(it.provider) == failedProvider }
            }
            dao.replaceAll(keep.map { it.toEntity() })
        }
        WarningRefresh(all, errors.joinToString().ifEmpty { null })
    }

    private fun providerKey(provider: String) = if (provider.contains("Wetterdienst")) "DWD" else "NINA"

    private suspend fun loadNina(): List<Warning> = coroutineScope {
        val items = nina.dashboard(BERLIN_ARS)
            // DWD-Warnungen holen wir direkt beim DWD (ausführlicher, ohne Doppelungen).
            .filter { it.payload?.data?.provider?.equals("DWD", ignoreCase = true) != true }
            .filter { it.payload?.data?.msgType?.equals("Cancel", ignoreCase = true) != true }
        items.map { item ->
            async {
                val details = runCatching { nina.details(item.id) }
                    .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "NINA-Details ${item.id}", it) }
                    .getOrNull()
                val info = details?.info?.firstOrNull()
                val data = item.payload?.data
                Warning(
                    id = "nina-${item.id}",
                    provider = providerLabel(data?.provider),
                    title = item.i18nTitle["de"] ?: info?.headline ?: data?.headline ?: "Warnmeldung",
                    description = TextUtils.cleanHtml(info?.description?.replace("<br/>", "\n"), 2000),
                    instruction = TextUtils.cleanHtml(info?.instruction?.replace("<br/>", "\n"), 2000),
                    level = ninaLevel(info?.severity ?: data?.severity),
                    start = TextUtils.parseDate(info?.onset),
                    end = TextUtils.parseDate(info?.expires),
                    sentAt = TextUtils.parseDate(item.sent ?: details?.sent) ?: System.currentTimeMillis(),
                )
            }
        }.map { it.await() }
    }

    companion object {
        private const val TAG = "WarningRepository"
        const val BERLIN_ARS = "110000000000"

        fun ninaLevel(severity: String?): WarnLevel = when (severity?.lowercase()) {
            "extreme" -> WarnLevel.EXTREME
            "severe" -> WarnLevel.SEVERE
            "moderate" -> WarnLevel.MODERATE
            else -> WarnLevel.MINOR
        }

        fun providerLabel(provider: String?): String = when (provider?.uppercase()) {
            "MOWAS" -> "Bevölkerungsschutz (MoWaS)"
            "KATWARN" -> "KATWARN"
            "BIWAPP" -> "BIWAPP"
            "POLICE" -> "Polizei"
            "LHP" -> "Hochwasserzentrale"
            else -> provider ?: "NINA"
        }
    }
}
