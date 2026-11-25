package de.lagebild.app.data.repository

import android.util.Log
import de.lagebild.app.BuildConfig
import de.lagebild.app.data.local.ArticleDao
import de.lagebild.app.data.local.BookmarkEntity
import de.lagebild.app.data.local.ReadEntity
import de.lagebild.app.data.local.UserStateDao
import de.lagebild.app.data.local.toDomain
import de.lagebild.app.data.local.toEntity
import de.lagebild.app.data.remote.HttpFetcher
import de.lagebild.app.data.remote.feed.FeedItem
import de.lagebild.app.data.remote.feed.GuardianParser
import de.lagebild.app.data.remote.feed.RssParser
import de.lagebild.app.data.remote.feed.TagesschauParser
import de.lagebild.app.data.settings.SettingsStore
import de.lagebild.app.data.sources.FeedConfig
import de.lagebild.app.data.sources.NewsSource
import de.lagebild.app.data.sources.SourceRepository
import de.lagebild.app.data.translation.TranslationService
import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.Category
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class SourceError(val sourceName: String, val message: String)

data class RefreshResult(
    /** Artikel, die bei dieser Abfrage zum ersten Mal aufgetaucht sind. */
    val newArticles: List<Article>,
    val errors: List<SourceError>,
    /** true, wenn der Cache vorher leer war (dann keine Benachrichtigungsflut). */
    val isFirstSync: Boolean,
    /** true, wenn keine einzige Quelle erreichbar war (vermutlich kein Internet). */
    val offline: Boolean,
)

/** Schnittstelle für die Quellenliste – erleichtert Tests. */
interface SourceProvider {
    suspend fun getSources(forceRemote: Boolean = false): List<NewsSource>
    val current: List<NewsSource>
}

class DefaultSourceProvider @Inject constructor(private val repo: SourceRepository) : SourceProvider {
    override suspend fun getSources(forceRemote: Boolean) = repo.getSources(forceRemote)
    override val current: List<NewsSource> get() = repo.sources.value
}

@Singleton
class NewsRepository @Inject constructor(
    private val articleDao: ArticleDao,
    private val userStateDao: UserStateDao,
    private val sources: SourceProvider,
    private val settingsRepository: SettingsStore,
    private val fetcher: HttpFetcher,
    private val translationService: TranslationService,
) {
    /** Für Tests überschreibbar. */
    internal var clock: () -> Long = System::currentTimeMillis
    internal var guardianApiKey: String = BuildConfig.GUARDIAN_API_KEY

    fun observeArticles(): Flow<List<Article>> =
        combine(articleDao.observeAll(), settingsRepository.settings) { rows, settings ->
            val enabled = sources.current.filter { settings.isSourceEnabled(it) }.map { it.id }.toSet()
            rows.map { it.toDomain() }.filter { enabled.isEmpty() || it.sourceId in enabled }
        }

    fun search(query: String): Flow<List<Article>> =
        articleDao.search(query.trim()).map { rows -> rows.map { it.toDomain() } }

    fun observeBookmarks(): Flow<List<Article>> =
        userStateDao.observeBookmarks().map { list -> list.map { it.article.toDomain(isBookmarked = true) } }

    suspend fun markRead(article: Article) = userStateDao.markRead(ReadEntity(article.id, clock()))

    suspend fun toggleBookmark(article: Article) {
        if (userStateDao.isBookmarked(article.id)) {
            userStateDao.removeBookmark(article.id)
        } else {
            val entity = articleDao.getById(article.id) ?: article.toEntity(clock())
            userStateDao.addBookmark(BookmarkEntity(article.id, entity, clock()))
        }
    }

    private val refreshMutex = Mutex()
    private var lastResult: RefreshResult? = null
    private var finishedRefreshes = 0

    /**
     * Ruft alle aktivierten Quellen parallel ab und aktualisiert den Offline-Cache.
     * Laufen zwei Aktualisierungen gleichzeitig an (z. B. Startseite und Hintergrund-Sync),
     * wartet die zweite und übernimmt das Ergebnis der ersten, statt alles doppelt zu laden.
     */
    suspend fun refresh(reloadSourceList: Boolean = false): RefreshResult {
        val seenBefore = finishedRefreshes
        return refreshMutex.withLock {
            lastResult?.takeIf { finishedRefreshes > seenBefore }
                ?: doRefresh(reloadSourceList).also { lastResult = it; finishedRefreshes++ }
        }
    }

    private suspend fun doRefresh(reloadSourceList: Boolean): RefreshResult = coroutineScope {
        val settings = settingsRepository.current()
        val enabledSources = sources.getSources(reloadSourceList).filter { settings.isSourceEnabled(it) }
        val now = clock()
        val semaphore = Semaphore(6)

        val results = enabledSources.flatMap { source ->
            source.feeds.map { feed ->
                async { semaphore.withPermit { fetchFeed(source, feed) } }
            }
        }.awaitAll()

        val errors = results.filter { it.error != null }
            .groupBy { it.source.id }
            // Eine Quelle gilt als fehlerhaft, wenn alle ihre Feeds fehlschlagen.
            .filter { (id, list) -> list.size == results.count { it.source.id == id } }
            .map { (_, list) -> SourceError(list.first().source.name, list.first().error!!) }
        val offline = !fetcher.isOnline() ||
            (results.isNotEmpty() && results.all { it.error != null && it.isNetworkError })

        val merged = mergeFeedResults(results, now)
        val wasEmpty = articleDao.count() == 0
        val existing = merged.keys.chunked(500).flatMap { articleDao.getByIds(it) }.associateBy { it.id }

        // Bereits vorhandene Übersetzungen übernehmen, wenn sich der Originaltitel nicht geändert hat.
        val withOldTranslations = merged.values.map { a ->
            val old = existing[a.id]
            if (old != null && old.title == a.title && old.titleDe != null) a.copy(titleDe = old.titleDe, teaserDe = old.teaserDe) else a
        }
        fun entitiesOf(list: List<Article>) = list.map { a ->
            val e = a.toEntity(now)
            existing[a.id]?.let { e.copy(firstSeenAt = it.firstSeenAt) } ?: e
        }

        // Erst speichern (Artikel erscheinen sofort), dann übersetzen. Beim ersten Start
        // lädt ML Kit sonst minutenlang Sprachmodelle, bevor überhaupt etwas angezeigt wird.
        if (withOldTranslations.isNotEmpty()) articleDao.upsertAll(entitiesOf(withOldTranslations))
        // "Stand" = letzte erfolgreiche Abfrage – schon jetzt setzen, Übersetzungen können dauern.
        if (!offline) settingsRepository.setLastSyncAt(now)
        val translated = translationService.translateArticles(
            withOldTranslations, settings.translationEngine, settings.wifiOnlyModelDownload,
        )
        val changed = translated.filterIndexed { i, a -> a !== withOldTranslations[i] }
        if (changed.isNotEmpty()) articleDao.upsertAll(entitiesOf(changed))
        articleDao.deleteFetchedBefore(now - CACHE_RETENTION_MS)
        userStateDao.deleteReadBefore(now - 30L * DAY_MS)

        RefreshResult(
            newArticles = translated.filter { it.id !in existing },
            errors = errors,
            isFirstSync = wasEmpty,
            offline = offline,
        )
    }

    private class MissingKeyException : Exception()

    internal data class FeedResult(
        val source: NewsSource,
        val feed: FeedConfig,
        val items: List<FeedItem>,
        val error: String? = null,
        val isNetworkError: Boolean = false,
    )

    private suspend fun fetchFeed(source: NewsSource, feed: FeedConfig): FeedResult = try {
        val items: List<FeedItem> = when (source.type.lowercase()) {
            "tagesschau" -> TagesschauParser.parse(fetcher.getString(feed.url))
            "guardian" -> {
                if (guardianApiKey.isBlank()) throw MissingKeyException()
                GuardianParser.parse(fetcher.getString(GuardianParser.buildUrl(feed.url, guardianApiKey)))
            }
            else -> RssParser.parse(fetcher.getBytes(feed.url).inputStream(), MAX_ITEMS_PER_FEED)
        }
        FeedResult(source, feed, items.take(MAX_ITEMS_PER_FEED))
    } catch (e: MissingKeyException) {
        FeedResult(source, feed, emptyList(), "Kein Guardian-API-Key hinterlegt (siehe README)")
    } catch (e: IOException) {
        Log.w(TAG, "Feed ${feed.url} nicht erreichbar: ${e.message}")
        FeedResult(source, feed, emptyList(), e.message ?: "Netzwerkfehler", isNetworkError = e !is de.lagebild.app.data.remote.HttpException)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w(TAG, "Feed ${feed.url} fehlerhaft", e)
        FeedResult(source, feed, emptyList(), "Feed konnte nicht gelesen werden")
    }

    /** Fasst Einträge, die in mehreren Feeds derselben Quelle vorkommen, zu einem Artikel zusammen. */
    internal fun mergeFeedResults(results: List<FeedResult>, now: Long): Map<String, Article> {
        val map = LinkedHashMap<String, Article>()
        for (r in results) {
            val feedCategory = Category.fromKey(r.feed.category)
            for (item in r.items) {
                val id = articleId(r.source.id, item.link)
                val prev = map[id]
                val article = Article(
                    id = id,
                    sourceId = r.source.id,
                    sourceName = r.source.name,
                    sourceColor = r.source.color,
                    url = item.link,
                    title = item.title,
                    teaser = item.summary?.takeIf { it != item.title },
                    imageUrl = item.imageUrl,
                    publishedAt = (item.publishedAt ?: now).coerceAtMost(now + 60_000),
                    category = feedCategory,
                    isTop = r.feed.top,
                    isBreaking = item.isBreaking,
                    language = r.source.language,
                    paywall = r.source.paywall,
                )
                map[id] = if (prev == null) article else prev.copy(
                    category = prev.category ?: article.category,
                    isTop = prev.isTop || article.isTop,
                    isBreaking = prev.isBreaking || article.isBreaking,
                    imageUrl = prev.imageUrl ?: article.imageUrl,
                    teaser = prev.teaser ?: article.teaser,
                )
            }
        }
        return map
    }

    companion object {
        private const val TAG = "NewsRepository"
        private const val MAX_ITEMS_PER_FEED = 40
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val CACHE_RETENTION_MS = 4 * DAY_MS

        /** Stabile ID aus Quelle + normalisierter URL (ohne Tracking-Parameter). */
        fun articleId(sourceId: String, url: String): String {
            val normalized = normalizeUrl(url)
            val digest = MessageDigest.getInstance("SHA-1").digest("$sourceId|$normalized".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }

        fun normalizeUrl(url: String): String = runCatching {
            val uri = URI(url.trim())
            val query = uri.rawQuery?.split('&')
                ?.filterNot { p -> p.startsWith("utm_") || p.startsWith("at_") || p.startsWith("syn-") || p.startsWith("ref=") }
                ?.sorted()
                ?.joinToString("&")
                ?.takeIf { it.isNotEmpty() }
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: ""
            val path = (uri.rawPath ?: "").trimEnd('/')
            buildString {
                append(host).append(path)
                if (query != null) append('?').append(query)
            }
        }.getOrDefault(url.trim())
    }
}
