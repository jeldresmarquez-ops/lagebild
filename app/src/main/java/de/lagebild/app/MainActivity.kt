package de.lagebild.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.lagebild.app.data.settings.AppSettings
import de.lagebild.app.data.settings.SettingsRepository
import de.lagebild.app.notifications.NotificationHelper
import de.lagebild.app.ui.screens.BerlinScreen
import de.lagebild.app.ui.screens.BookmarksScreen
import de.lagebild.app.ui.screens.HomeScreen
import de.lagebild.app.ui.screens.SearchScreen
import de.lagebild.app.ui.settings.SettingsScreen
import de.lagebild.app.ui.settings.SourcesScreen
import de.lagebild.app.ui.settings.TransitSettingsScreen
import de.lagebild.app.ui.theme.LagebildTheme
import de.lagebild.app.ui.update.UpdateDialogs
import de.lagebild.app.ui.update.UpdateViewModel
import de.lagebild.app.ui.util.openInCustomTab
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val updateVm: UpdateViewModel by viewModels()

    /** Ziel aus einer angetippten Benachrichtigung. */
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingIntent.value = intent
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            LagebildTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AppRoot(updateVm, pendingIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Start", Icons.Outlined.Newspaper),
    Tab("berlin", "Berlin", Icons.Outlined.LocationCity),
    Tab("search", "Suche", Icons.Outlined.Search),
    Tab("bookmarks", "Gemerkt", Icons.Outlined.Bookmarks),
    Tab("settings", "Optionen", Icons.Outlined.Settings),
)

@Composable
private fun AppRoot(updateVm: UpdateViewModel, pendingIntent: MutableStateFlow<Intent?>) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface
    val intent by pendingIntent.collectAsStateWithLifecycle()

    // Einmalig nach der Benachrichtigungs-Erlaubnis fragen (Android 13+)
    var asked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            asked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        updateVm.checkOnStartup()
    }

    // Benachrichtigung angetippt → Artikel öffnen bzw. passenden Bereich zeigen
    LaunchedEffect(intent) {
        val i = intent ?: return@LaunchedEffect
        i.getStringExtra(NotificationHelper.EXTRA_OPEN_URL)?.let { openInCustomTab(context, it, toolbarColor) }
        when (i.getStringExtra(NotificationHelper.EXTRA_DESTINATION)) {
            "berlin" -> nav.navigateTab("berlin")
            "update" -> { nav.navigateTab("settings"); updateVm.checkManually() }
        }
        pendingIntent.value = null
    }

    Scaffold(
        bottomBar = {
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route || (tab.route == "settings" && route?.startsWith("settings/") == true),
                        onClick = { nav.navigateTab(tab.route) },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding()))
                .fillMaxSize(),
        ) {
            composable("home") { HomeScreen() }
            composable("berlin") { BerlinScreen(onOpenTransitSettings = { nav.navigate("settings/transit") }) }
            composable("search") { SearchScreen() }
            composable("bookmarks") { BookmarksScreen() }
            composable("settings") {
                SettingsScreen(
                    updateVm = updateVm,
                    onOpenSources = { nav.navigate("settings/sources") },
                    onOpenTransit = { nav.navigate("settings/transit") },
                )
            }
            composable("settings/sources") { SourcesScreen(onBack = { nav.popBackStack() }) }
            composable("settings/transit") { TransitSettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }

    UpdateDialogs(updateVm)
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
