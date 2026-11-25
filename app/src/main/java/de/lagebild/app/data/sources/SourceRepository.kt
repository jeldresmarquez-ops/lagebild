package de.lagebild.app.data.sources

import android.content.Context
import android.util.Log
import de.lagebild.app.BuildConfig
import de.lagebild.app.data.remote.HttpFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lädt die Quellenliste:
 * 1. aus dem GitHub-Repository (config/sources.json, Branch main) – so lassen sich
 *    Quellen ohne App-Update ändern,
 * 2. sonst aus der zuletzt erfolgreich geladenen Kopie,
 * 3. sonst aus der in die App eingebauten Kopie (assets/sources.json).
 */
@Singleton
class SourceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: HttpFetcher,
    private val json: Json,
) {
    private val cacheFile get() = File(context.filesDir, "sources.json")
    private val _sources = MutableStateFlow<List<NewsSource>>(emptyList())
    val sources: StateFlow<List<NewsSource>> = _sources.asStateFlow()

    private var remoteLoaded = false

    /** Liefert die aktuelle Liste; lädt beim ersten Aufruf (oder mit [forceRemote]) neu von GitHub. */
    suspend fun getSources(forceRemote: Boolean = false): List<NewsSource> = withContext(Dispatchers.IO) {
        if (_sources.value.isEmpty()) _sources.value = loadLocal()
        if (!remoteLoaded || forceRemote) {
            loadRemote()?.let { list ->
                _sources.value = list
                remoteLoaded = true
            }
        }
        _sources.value
    }

    private fun parse(text: String): List<NewsSource>? =
        runCatching { json.decodeFromString(SourceList.serializer(), text).sources }
            .onFailure { Log.w(TAG, "sources.json ungültig", it) }
            .getOrNull()
            ?.filter { it.feeds.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    private fun loadLocal(): List<NewsSource> {
        if (cacheFile.exists()) parse(cacheFile.readText())?.let { return it }
        val asset = context.assets.open("sources.json").bufferedReader().use { it.readText() }
        return parse(asset) ?: emptyList()
    }

    private suspend fun loadRemote(): List<NewsSource>? {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank() || repo.startsWith("DEIN-")) return null
        return runCatching {
            val text = fetcher.getString("https://raw.githubusercontent.com/$repo/main/config/sources.json")
            parse(text)?.also { cacheFile.writeText(text) }
        }.onFailure { Log.i(TAG, "Quellenliste von GitHub nicht erreichbar, nutze lokale Kopie: ${it.message}") }
            .getOrNull()
    }

    private companion object {
        const val TAG = "SourceRepository"
    }
}
