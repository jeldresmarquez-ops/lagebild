package de.lagebild.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lagebild.app.data.repository.NewsRepository
import de.lagebild.app.data.repository.TransitRepository
import de.lagebild.app.data.repository.TransitState
import de.lagebild.app.data.repository.WarningRepository
import de.lagebild.app.data.settings.SettingsRepository
import de.lagebild.app.domain.FeedSelector
import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.Category
import de.lagebild.app.domain.model.StoryGroup
import de.lagebild.app.domain.model.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Gemeinsame Artikel-Aktionen für alle Listen. */
abstract class ArticleActionsViewModel(private val news: NewsRepository) : ViewModel() {
    fun onOpened(article: Article) = viewModelScope.launch { news.markRead(article) }
    fun onToggleBookmark(article: Article) = viewModelScope.launch { news.toggleBookmark(article) }
}

/** Was oben in der Chip-Leiste gewählt ist. */
sealed interface HomeTab {
    data object Top : HomeTab
    data object Briefing : HomeTab
    data class Section(val category: Category) : HomeTab
}

data class HomeUiState(
    val categories: List<Category> = Category.entries,
    val tab: HomeTab = HomeTab.Top,
    /** Überblick: je Ressort die wichtigsten Themen (nur gefüllt, wenn der Überblick gewählt ist). */
    val briefing: List<Pair<Category, List<StoryGroup>>> = emptyList(),
    val groups: List<StoryGroup> = emptyList(),
    val breaking: Article? = null,
    val isEmpty: Boolean = false,
    val lastSync: Long = 0,
)

data class RefreshUi(
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class HomeViewModel @javax.inject.Inject constructor(
    private val news: NewsRepository,
    settings: SettingsRepository,
) : ArticleActionsViewModel(news) {

    private val selected = MutableStateFlow<HomeTab>(HomeTab.Top)
    private val _refresh = MutableStateFlow(RefreshUi())
    val refresh: StateFlow<RefreshUi> = _refresh

    private val articles = news.observeArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<HomeUiState> = combine(articles, selected, settings.settings) { list, tab, s ->
        val visibleCats = Category.entries.filter { it !in s.hiddenCategories }
        val all = list.orEmpty()
        // Ein ausgeblendetes Ressort kann nicht gewählt bleiben.
        val effective = if (tab is HomeTab.Section && tab.category !in visibleCats) HomeTab.Top else tab
        HomeUiState(
            categories = visibleCats,
            tab = effective,
            briefing = if (effective == HomeTab.Briefing) {
                FeedSelector.briefing(all, FeedSelector.BRIEFING_CATEGORIES.filter { it in visibleCats })
            } else emptyList(),
            groups = when (effective) {
                HomeTab.Top -> FeedSelector.topStories(all)
                HomeTab.Briefing -> emptyList()
                is HomeTab.Section -> FeedSelector.category(all, effective.category)
            },
            breaking = if (effective == HomeTab.Top) FeedSelector.breaking(all) else null,
            isEmpty = list != null && all.isEmpty(),
            lastSync = s.lastSyncAt,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh(reloadSources = true)
    }

    fun select(tab: HomeTab) {
        selected.value = tab
    }

    fun refresh(reloadSources: Boolean = false) {
        if (_refresh.value.refreshing) return
        viewModelScope.launch {
            _refresh.value = _refresh.value.copy(refreshing = true, message = null)
            val result = runCatching { news.refresh(reloadSources) }
            _refresh.value = result.fold(
                onSuccess = { r ->
                    RefreshUi(
                        offline = r.offline,
                        message = when {
                            r.offline -> null
                            r.errors.isNotEmpty() -> "Nicht erreichbar: " + r.errors.joinToString { it.sourceName }
                            else -> null
                        },
                    )
                },
                onFailure = { RefreshUi(offline = true, message = "Aktualisierung fehlgeschlagen") },
            )
        }
    }

    /** Beim Öffnen der App: neu laden, wenn die letzte erfolgreiche Abfrage älter als 15 Minuten ist. */
    fun refreshIfStale() {
        val last = state.value.lastSync
        if (last > 0 && System.currentTimeMillis() - last > 15 * 60_000L) refresh()
    }

    fun consumeMessage() {
        _refresh.value = _refresh.value.copy(message = null)
    }
}

data class BerlinUiState(
    val warnings: List<Warning> = emptyList(),
    val transit: TransitState = TransitState(),
    val hasFavoriteStations: Boolean = false,
    val groups: List<StoryGroup> = emptyList(),
)

@HiltViewModel
class BerlinViewModel @javax.inject.Inject constructor(
    private val news: NewsRepository,
    private val warnings: WarningRepository,
    private val transit: TransitRepository,
    private val settings: SettingsRepository,
) : ArticleActionsViewModel(news) {

    private val _refresh = MutableStateFlow(RefreshUi())
    val refresh: StateFlow<RefreshUi> = _refresh

    val state: StateFlow<BerlinUiState> = combine(
        news.observeArticles(), warnings.observe(), transit.state, settings.settings,
    ) { articles, w, t, s ->
        BerlinUiState(
            warnings = w,
            transit = t,
            hasFavoriteStations = s.favoriteStations.isNotEmpty(),
            groups = FeedSelector.category(articles, Category.BERLIN).take(60),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BerlinUiState())

    init {
        refresh(includeNews = false)
    }

    fun refresh(includeNews: Boolean = true) {
        if (_refresh.value.refreshing) return
        viewModelScope.launch {
            _refresh.value = RefreshUi(refreshing = true)
            val s = settings.current()
            val w = runCatching { warnings.refresh() }.getOrNull()
            val t = runCatching { transit.refresh(s.favoriteStations, s.favoriteLines) }.getOrNull()
            val n = if (includeNews) runCatching { news.refresh() }.getOrNull() else null
            val problems = listOfNotNull(
                w?.error ?: if (w == null) "Warnungen nicht erreichbar" else null,
                t?.error,
            )
            _refresh.value = RefreshUi(offline = n?.offline == true, message = problems.joinToString().ifEmpty { null })
        }
    }

    fun consumeMessage() {
        _refresh.value = _refresh.value.copy(message = null)
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @javax.inject.Inject constructor(
    private val news: NewsRepository,
) : ArticleActionsViewModel(news) {
    val query = MutableStateFlow("")

    val results: StateFlow<List<Article>> = query
        .debounce(250)
        .flatMapLatest { q -> if (q.trim().length < 2) flowOf(emptyList()) else news.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class BookmarksViewModel @javax.inject.Inject constructor(
    private val news: NewsRepository,
) : ArticleActionsViewModel(news) {
    val bookmarks: StateFlow<List<StoryGroup>> = news.observeBookmarks()
        .map { list -> list.map { StoryGroup(it, emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
