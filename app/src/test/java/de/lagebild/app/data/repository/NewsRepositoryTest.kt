package de.lagebild.app.data.repository

import com.google.common.truth.Truth.assertThat
import de.lagebild.app.data.local.ArticleDao
import de.lagebild.app.data.local.ArticleEntity
import de.lagebild.app.data.local.ArticleWithState
import de.lagebild.app.data.local.BookmarkEntity
import de.lagebild.app.data.local.ReadEntity
import de.lagebild.app.data.local.TranslationDao
import de.lagebild.app.data.local.TranslationEntity
import de.lagebild.app.data.local.UserStateDao
import de.lagebild.app.data.remote.HttpException
import de.lagebild.app.data.remote.HttpFetcher
import de.lagebild.app.data.settings.AppSettings
import de.lagebild.app.data.settings.SettingsStore
import de.lagebild.app.data.settings.TranslationEngine
import de.lagebild.app.data.sources.FeedConfig
import de.lagebild.app.data.sources.NewsSource
import de.lagebild.app.data.translation.TextTranslator
import de.lagebild.app.data.translation.TranslationService
import de.lagebild.app.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NewsRepositoryTest {

    // ---------------- Fakes ----------------

    private class FakeArticleDao : ArticleDao {
        val rows = MutableStateFlow<Map<String, ArticleEntity>>(emptyMap())
        override fun observeAll(limit: Int): Flow<List<ArticleWithState>> =
            rows.map { m -> m.values.sortedByDescending { it.publishedAt }.map { ArticleWithState(it, false, false) } }
        override fun search(q: String) = rows.map { m ->
            m.values.filter { it.title.contains(q, true) || it.titleDe?.contains(q, true) == true }.map { ArticleWithState(it, false, false) }
        }
        override suspend fun getByIds(ids: List<String>) = ids.mapNotNull { rows.value[it] }
        override suspend fun getById(id: String) = rows.value[id]
        override suspend fun upsertAll(items: List<ArticleEntity>) { rows.value = rows.value + items.associateBy { it.id } }
        override suspend fun deleteFetchedBefore(before: Long) { rows.value = rows.value.filterValues { it.fetchedAt >= before } }
        override suspend fun recent(since: Long) = rows.value.values.filter { it.publishedAt > since }
        override suspend fun count() = rows.value.size
    }

    private class FakeUserStateDao : UserStateDao {
        val bookmarks = MutableStateFlow<Map<String, BookmarkEntity>>(emptyMap())
        val read = mutableMapOf<String, ReadEntity>()
        override suspend fun markRead(item: ReadEntity) { read[item.id] = item }
        override suspend fun deleteReadBefore(before: Long) { read.values.removeAll { it.readAt < before } }
        override fun observeBookmarks() = bookmarks.map { it.values.toList() }
        override suspend fun addBookmark(item: BookmarkEntity) { bookmarks.value = bookmarks.value + (item.id to item) }
        override suspend fun removeBookmark(id: String) { bookmarks.value = bookmarks.value - id }
        override suspend fun isBookmarked(id: String) = id in bookmarks.value
    }

    private class FakeTranslationDao : TranslationDao {
        val map = mutableMapOf<String, TranslationEntity>()
        override suspend fun getAll(keys: List<String>) = keys.mapNotNull { map[it] }
        override suspend fun insertAll(items: List<TranslationEntity>) { items.forEach { map[it.key] = it } }
        override suspend fun deleteBefore(before: Long) {}
        override suspend fun clear() = map.clear()
    }

    private class FakeTranslator : TextTranslator {
        var calls = 0
        override val engineId = "fake"
        override val isAvailable = true
        override suspend fun translate(texts: List<String>, sourceLang: String, wifiOnlyDownload: Boolean): List<String> {
            calls += texts.size
            return texts.map { "DE: $it" }
        }
    }

    private class FakeSettings(var value: AppSettings = AppSettings()) : SettingsStore {
        override val settings: Flow<AppSettings> get() = MutableStateFlow(value)
        override suspend fun current() = value
        override suspend fun setLastSyncAt(t: Long) { value = value.copy(lastSyncAt = t) }
    }

    private class FakeFetcher : HttpFetcher {
        val responses = mutableMapOf<String, String>()
        val failures = mutableMapOf<String, IOException>()
        override suspend fun getBytes(url: String): ByteArray {
            failures[url]?.let { throw it }
            return (responses[url] ?: throw HttpException(404, "nicht gefunden: $url")).toByteArray()
        }
    }

    private class FakeSources(val list: List<NewsSource>) : SourceProvider {
        override suspend fun getSources(forceRemote: Boolean) = list
        override val current: List<NewsSource> get() = list
    }

    // ---------------- Testdaten ----------------

    private fun rss(vararg items: Pair<String, String>) = buildString {
        append("<rss><channel>")
        items.forEach { (title, link) ->
            append("<item><title>$title</title><link>$link</link><pubDate>Wed, 25 Nov 2026 09:00:00 GMT</pubDate></item>")
        }
        append("</channel></rss>")
    }

    private val spiegel = NewsSource(
        id = "spiegel", name = "Der Spiegel", language = "de",
        feeds = listOf(
            FeedConfig("https://spiegel/top", top = true),
            FeedConfig("https://spiegel/politik", category = "POLITIK"),
        ),
    )
    private val bbc = NewsSource(
        id = "bbc", name = "BBC News", language = "en",
        feeds = listOf(FeedConfig("https://bbc/world", category = "INTERNATIONAL")),
    )
    private val ft = NewsSource(
        id = "ft", name = "FT", language = "en", enabledByDefault = false,
        feeds = listOf(FeedConfig("https://ft/world", category = "INTERNATIONAL")),
    )

    private lateinit var articleDao: FakeArticleDao
    private lateinit var fetcher: FakeFetcher
    private lateinit var translator: FakeTranslator
    private lateinit var settings: FakeSettings
    private lateinit var repo: NewsRepository
    private val now = 1_795_600_000_000L // 25.11.2026

    @Before
    fun setUp() {
        articleDao = FakeArticleDao()
        fetcher = FakeFetcher()
        translator = FakeTranslator()
        settings = FakeSettings()
        val translation = TranslationService(FakeTranslationDao(), translator, translator)
        repo = NewsRepository(
            articleDao, FakeUserStateDao(), FakeSources(listOf(spiegel, bbc, ft)), settings, fetcher, translation,
        )
        repo.clock = { now }

        fetcher.responses["https://spiegel/top"] = rss("Haushalt beschlossen" to "https://www.spiegel.de/politik/a?utm_source=rss")
        fetcher.responses["https://spiegel/politik"] = rss(
            "Haushalt beschlossen" to "https://spiegel.de/politik/a",
            "Koalition streitet" to "https://spiegel.de/politik/b",
        )
        fetcher.responses["https://bbc/world"] = rss("Budget passed in Berlin" to "https://bbc.co.uk/news/1")
        fetcher.responses["https://ft/world"] = rss("Paywalled story" to "https://ft.com/1")
    }

    @Test
    fun `refresh speichert Artikel, führt Feeds zusammen und übersetzt nur fremdsprachige`() = runTest {
        val result = repo.refresh()

        assertThat(result.isFirstSync).isTrue()
        assertThat(result.errors).isEmpty()
        val stored = articleDao.rows.value.values
        // Spiegel-Artikel "a" steht in zwei Feeds (mit/ohne Tracking-Parameter) → nur einmal gespeichert
        assertThat(stored.map { it.title }).containsExactly("Haushalt beschlossen", "Koalition streitet", "Budget passed in Berlin")
        val a = stored.first { it.title == "Haushalt beschlossen" }
        assertThat(a.isTop).isTrue()
        assertThat(a.category).isEqualTo(Category.POLITIK.name)
        assertThat(a.titleDe).isNull() // deutsche Quelle: keine Übersetzung

        val b = stored.first { it.sourceId == "bbc" }
        assertThat(b.titleDe).isEqualTo("DE: Budget passed in Berlin")
        // FT ist standardmäßig aus
        assertThat(stored.none { it.sourceId == "ft" }).isTrue()
    }

    @Test
    fun `zweiter Abruf erkennt nur neue Artikel und nutzt den Übersetzungs-Cache`() = runTest {
        repo.refresh()
        val callsAfterFirst = translator.calls
        fetcher.responses["https://bbc/world"] = rss(
            "Budget passed in Berlin" to "https://bbc.co.uk/news/1",
            "Storm hits coast" to "https://bbc.co.uk/news/2",
        )
        val second = repo.refresh()

        assertThat(second.isFirstSync).isFalse()
        assertThat(second.newArticles.map { it.title }).containsExactly("Storm hits coast")
        assertThat(translator.calls - callsAfterFirst).isEqualTo(1) // nur der neue Titel
    }

    @Test
    fun `fehlerhafte Quelle wird gemeldet, Cache bleibt erhalten`() = runTest {
        repo.refresh()
        fetcher.failures["https://bbc/world"] = IOException("timeout")
        val result = repo.refresh()

        assertThat(result.errors.map { it.sourceName }).containsExactly("BBC News")
        assertThat(result.offline).isFalse()
        assertThat(articleDao.rows.value.values.any { it.sourceId == "bbc" }).isTrue()
    }

    @Test
    fun `ohne Internet wird offline gemeldet`() = runTest {
        listOf("https://spiegel/top", "https://spiegel/politik", "https://bbc/world").forEach {
            fetcher.failures[it] = IOException("Unable to resolve host")
        }
        val result = repo.refresh()
        assertThat(result.offline).isTrue()
        assertThat(articleDao.rows.value).isEmpty()
    }

    @Test
    fun `abgeschaltete Quellen erscheinen nicht in der Liste`() = runTest {
        repo.refresh()
        settings.value = settings.value.copy(disabledSourceOverrides = setOf("bbc"))
        val visible = repo.observeArticles().first()
        assertThat(visible.map { it.sourceId }.toSet()).containsExactly("spiegel")
    }

    @Test
    fun `Übersetzung abgeschaltet lässt Originale stehen`() = runTest {
        settings.value = settings.value.copy(translationEngine = TranslationEngine.OFF)
        repo.refresh()
        assertThat(articleDao.rows.value.values.first { it.sourceId == "bbc" }.titleDe).isNull()
        assertThat(translator.calls).isEqualTo(0)
    }

    @Test
    fun `URL-Normalisierung entfernt Tracking-Parameter`() {
        assertThat(NewsRepository.normalizeUrl("https://www.Spiegel.de/a/?utm_source=x&id=2#top"))
            .isEqualTo("spiegel.de/a?id=2")
        assertThat(NewsRepository.articleId("s", "https://spiegel.de/a?utm_medium=rss"))
            .isEqualTo(NewsRepository.articleId("s", "https://www.spiegel.de/a/"))
    }
}
