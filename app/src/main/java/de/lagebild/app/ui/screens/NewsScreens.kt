package de.lagebild.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.StoryGroup
import de.lagebild.app.ui.components.ArticleActions
import de.lagebild.app.ui.components.DisruptionRow
import de.lagebild.app.ui.components.EmptyState
import de.lagebild.app.ui.components.OfflineBanner
import de.lagebild.app.ui.components.SectionHeader
import de.lagebild.app.ui.components.StoryCard
import de.lagebild.app.ui.components.WarningCard
import de.lagebild.app.ui.util.formatDateTime
import de.lagebild.app.ui.util.openInCustomTab
import de.lagebild.app.ui.util.shareArticle

@Composable
private fun rememberArticleActions(vm: ArticleActionsViewModel): ArticleActions {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface
    return remember(vm, toolbar) {
        ArticleActions(
            open = { a -> vm.onOpened(a); openInCustomTab(context, a.url, toolbar) },
            bookmark = { a -> vm.onToggleBookmark(a) },
            share = { a -> shareArticle(context, a) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val actions = rememberArticleActions(vm)
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(refresh.message) {
        refresh.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.selected) { listState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Lagebild", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = state.selected == null, onClick = { vm.select(null) }, label = { Text("Top") }) }
                items(state.categories) { c ->
                    FilterChip(selected = state.selected == c, onClick = { vm.select(c) }, label = { Text(c.label) })
                }
            }
            OfflineBanner(refresh.offline, state.lastSync)
            PullToRefreshBox(
                isRefreshing = refresh.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.isEmpty && !refresh.refreshing) {
                    EmptyState(
                        "Noch keine Meldungen",
                        if (refresh.offline) "Keine Internetverbindung. Sobald du online bist, zum Aktualisieren nach unten ziehen."
                        else "Zum Aktualisieren nach unten ziehen.",
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        state.breaking?.let { b ->
                            item(key = "breaking") { BreakingBanner(b, onClick = { actions.open(b) }) }
                        }
                        if (state.selected == null) item { SectionHeader("Top-Meldungen") }
                        storyItems(state.groups, actions, firstLarge = true)
                        if (state.groups.isEmpty() && !refresh.refreshing) {
                            item { EmptyState("Keine Meldungen", "In dieser Kategorie gibt es gerade nichts Neues.") }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.storyItems(
    groups: List<StoryGroup>,
    actions: ArticleActions,
    firstLarge: Boolean,
) {
    items(groups.size, key = { groups[it].key }) { i ->
        StoryCard(groups[i], actions, large = firstLarge && i == 0)
    }
}

@Composable
private fun BreakingBanner(article: Article, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiary).clickable(onClick = onClick).padding(14.dp),
    ) {
        Text("EILMELDUNG · ${article.sourceName.uppercase()}", color = Color.White, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text(article.displayTitle, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BerlinScreen(onOpenTransitSettings: () -> Unit, vm: BerlinViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val actions = rememberArticleActions(vm)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(refresh.message) {
        refresh.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Berlin", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refresh.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { SectionHeader("Warnungen") }
                if (state.warnings.isEmpty()) {
                    item {
                        Text(
                            "Derzeit keine Warnungen für Berlin (NINA, DWD).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    items(state.warnings, key = { it.id }) { WarningCard(it) }
                }

                item { SectionHeader("Verkehr · BVG / S-Bahn / VBB") }
                when {
                    !state.hasFavoriteStations -> item {
                        Text(
                            "Lege unter „Optionen“ deine Haltestellen und Lieblingslinien fest, um Störungen zu sehen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onOpenTransitSettings).padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    state.transit.disruptions.isEmpty() -> item {
                        Text(
                            if (state.transit.error != null) state.transit.error!!
                            else if (state.transit.updatedAt == 0L) "Wird geladen …"
                            else "Keine Störungen auf deinen Linien (Stand ${formatDateTime(state.transit.updatedAt)}).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    else -> items(state.transit.disruptions, key = { it.id }) { DisruptionRow(it) }
                }

                item { SectionHeader("Nachrichten aus Berlin") }
                storyItems(state.groups, actions, firstLarge = false)
                item {
                    Text(
                        "Quellen: NINA/BBK, Deutscher Wetterdienst, VBB-Daten über transport.rest, rbb24, Tagesspiegel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val actions = rememberArticleActions(vm)
    Scaffold(topBar = { TopAppBar(title = { Text("Suche") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Stichwort, z. B. Bundestag") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Clear, "Leeren") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (query.length >= 2 && results.isEmpty()) {
                EmptyState("Nichts gefunden", "Gesucht wird in allen gespeicherten Meldungen der letzten Tage – auch offline.")
            } else {
                LazyColumn {
                    items(results, key = { it.id }) { StoryCard(StoryGroup(it, emptyList()), actions) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(vm: BookmarksViewModel = hiltViewModel()) {
    val list by vm.bookmarks.collectAsStateWithLifecycle()
    val actions = rememberArticleActions(vm)
    Scaffold(topBar = { TopAppBar(title = { Text("Lesezeichen") }) }) { padding ->
        if (list.isEmpty()) {
            EmptyState("Keine Lesezeichen", "Tippe bei einem Artikel auf das Lesezeichen-Symbol, um ihn hier zu speichern.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(list, key = { it.key }) { StoryCard(it, actions) }
            }
        }
    }
}
