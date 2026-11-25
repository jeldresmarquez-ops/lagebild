package de.lagebild.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lagebild.app.BuildConfig
import de.lagebild.app.data.settings.ThemeMode
import de.lagebild.app.data.settings.TranslationEngine
import de.lagebild.app.domain.model.Category
import de.lagebild.app.notifications.NotificationHelper
import de.lagebild.app.notifications.QuietHours
import de.lagebild.app.ui.components.LineBadge
import de.lagebild.app.ui.components.SectionHeader
import de.lagebild.app.ui.update.UpdateViewModel
import de.lagebild.app.ui.util.openInCustomTab
import androidx.compose.material3.MaterialTheme.colorScheme as colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    updateVm: UpdateViewModel,
    onOpenSources: () -> Unit,
    onOpenTransit: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionTick by remember { mutableStateOf(0) }
    val hasPermission = remember(permissionTick) { NotificationHelper(context).hasPermission() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }

    Scaffold(topBar = { TopAppBar(title = { Text("Einstellungen") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader("Inhalte") }
            item { NavRow("Quellen", "Nachrichtenquellen einzeln an- und abschalten", onOpenSources) }
            item { SectionHeader("Kategorien auf der Startseite") }
            items(Category.entries) { c ->
                SwitchRow(c.label, null, c !in s.hiddenCategories) { vm.setCategoryVisible(c, it) }
            }

            item { SectionHeader("Benachrichtigungen") }
            if (!hasPermission && Build.VERSION.SDK_INT >= 33) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Benachrichtigungen sind für Lagebild ausgeschaltet.", color = colors.tertiary)
                        Row {
                            TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Erlauben") }
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }) { Text("Android-Einstellungen") }
                        }
                    }
                }
            }
            item { SwitchRow("Eilmeldungen", "z. B. tagesschau-Eilmeldungen", s.notifyBreaking, vm::setNotifyBreaking) }
            item {
                SwitchRow(
                    "Wichtige Themen", "Wenn mindestens drei Quellen gleichzeitig über dasselbe Thema berichten",
                    s.notifyMultiSource, vm::setNotifyMultiSource,
                )
            }
            item {
                SwitchRow(
                    "Gefahrenmeldungen Berlin", "Unwetter und Gefahren der hohen Stufen werden immer gemeldet",
                    s.notifyWarnings, vm::setNotifyWarnings,
                )
            }
            item { SwitchRow("Auch einfache Wetterhinweise", "Gelbe Warnstufe (z. B. Frost, Glätte)", s.notifyMinorWarnings, vm::setNotifyMinorWarnings) }
            item { SwitchRow("Verkehrsstörungen", "Nur für deine Lieblingslinien", s.notifyTraffic, vm::setNotifyTraffic) }
            item { SwitchRow("App-Updates", null, s.notifyUpdates, vm::setNotifyUpdates) }
            item { SwitchRow("Ruhezeiten", "Keine Nachrichten-Töne in dieser Zeit (Gefahren hoher Stufe ausgenommen)", s.quietHoursEnabled, vm::setQuietEnabled) }
            if (s.quietHoursEnabled) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Von")
                        TimeDropdown(s.quietStartMinutes) { vm.setQuietHours(it, s.quietEndMinutes) }
                        Text("bis")
                        TimeDropdown(s.quietEndMinutes) { vm.setQuietHours(s.quietStartMinutes, it) }
                    }
                }
            }
            item {
                ChoiceRow(
                    "Abfrage-Intervall", listOf(15, 30, 60, 120, 240), s.syncIntervalMinutes,
                    { if (it < 60) "$it Minuten" else "${it / 60} Std." }, vm::setInterval,
                )
            }

            item { SectionHeader("Berlin · Verkehr") }
            item {
                NavRow(
                    "Haltestellen & Lieblingslinien",
                    if (s.favoriteStations.isEmpty()) "Noch nichts festgelegt"
                    else s.favoriteStations.joinToString { it.name } + if (s.favoriteLines.isNotEmpty()) " · Linien: " + s.favoriteLines.joinToString() else "",
                    onOpenTransit,
                )
            }

            item { SectionHeader("Übersetzung") }
            items(TranslationEngine.entries) { e ->
                val enabled = e != TranslationEngine.DEEPL || vm.deepLAvailable
                Row(
                    Modifier.fillMaxWidth().selectable(selected = s.translationEngine == e, enabled = enabled) { vm.setEngine(e) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.translationEngine == e, onClick = null, enabled = enabled)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(e.label, color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
                        if (!enabled) Text("Kein DeepL-API-Key eingetragen (siehe README)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SwitchRow("Sprachmodelle nur im WLAN laden", "ML Kit lädt einmalig ca. 30 MB pro Sprache", s.wifiOnlyModelDownload, vm::setWifiOnly) }
            item {
                TextButton(onClick = vm::clearTranslations, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Übersetzungs-Cache leeren")
                }
            }

            item { SectionHeader("Darstellung") }
            item { ChoiceRow("Design", ThemeMode.entries, s.themeMode, { it.label }, vm::setTheme) }
            if (Build.VERSION.SDK_INT >= 31) {
                item { SwitchRow("Dynamische Farben", "Farben aus deinem Hintergrundbild (Android 12+)", s.dynamicColor, vm::setDynamicColor) }
            }

            item { SectionHeader("Updates") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Installierte Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Text("Repository: ${BuildConfig.GITHUB_REPO}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = updateVm::checkManually, modifier = Modifier.padding(top = 8.dp)) { Text("Nach Updates suchen") }
                }
            }

            item { SectionHeader("Über") }
            item {
                Text(
                    "Lagebild zeigt nur Überschriften und Teaser. Der vollständige Artikel öffnet sich immer beim Original-Anbieter. " +
                        "Warnungen: BBK (NINA), Deutscher Wetterdienst. Verkehr: VBB-Daten über transport.rest. " +
                        "Übersetzungen: Google ML Kit bzw. DeepL – maschinell, ohne Gewähr.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                val toolbar = colors.surface
                TextButton(
                    onClick = { openInCustomTab(context, "https://github.com/${BuildConfig.GITHUB_REPO}", toolbar) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                ) { Text("Projekt auf GitHub") }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(label(selected)) },
        modifier = Modifier.clickable { open = true },
        trailingContent = {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(label(o)) }, onClick = { onSelect(o); open = false })
                }
            }
        },
    )
}

@Composable
private fun TimeDropdown(minutes: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(QuietHours.format(minutes)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        (0 until 48).map { it * 30 }.forEach { m ->
            DropdownMenuItem(text = { Text(QuietHours.format(m)) }, onClick = { onSelect(m); open = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quellen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            sources.groupBy { it.group }.forEach { (group, list) ->
                item { SectionHeader(group) }
                items(list, key = { it.id }) { src ->
                    val info = buildList {
                        if (src.needsTranslation) add("wird übersetzt")
                        if (src.paywall) add("teilweise Bezahlschranke")
                        if (src.type == "guardian" && BuildConfig.GUARDIAN_API_KEY.isBlank()) add("API-Key fehlt")
                    }.joinToString(" · ").ifEmpty { null }
                    SwitchRow(src.name, info, s.isSourceEnabled(src)) { vm.setSourceEnabled(src, it) }
                }
            }
            item {
                Text(
                    "Die Quellenliste wird beim Start aus config/sources.json in deinem GitHub-Repository geladen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val results by vm.stationResults.collectAsStateWithLifecycle()
    val error by vm.stationSearchError.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var lines by rememberSaveable(s.favoriteLines) { mutableStateOf(s.favoriteLines.sorted().joinToString(", ")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verkehr Berlin") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SectionHeader("Lieblingslinien") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = lines,
                        onValueChange = { lines = it },
                        label = { Text("z. B. U2, S7, M10, 100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.setLines(lines) }, modifier = Modifier.padding(vertical = 8.dp)) { Text("Speichern") }
                        Spacer(Modifier.width(12.dp))
                        s.favoriteLines.sorted().take(8).forEach { LineBadge(it); Spacer(Modifier.width(4.dp)) }
                    }
                    Text(
                        "Leer lassen = alle Linien an deinen Haltestellen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            item { SectionHeader("Meine Haltestellen") }
            if (s.favoriteStations.isEmpty()) {
                item { Text("Noch keine Haltestelle gewählt.", Modifier.padding(horizontal = 16.dp), color = colors.onSurfaceVariant) }
            }
            items(s.favoriteStations, key = { it.id }) { st ->
                ListItem(
                    headlineContent = { Text(st.name) },
                    trailingContent = { IconButton(onClick = { vm.removeStation(st) }) { Icon(Icons.Outlined.Close, "Entfernen") } },
                )
            }
            item { SectionHeader("Haltestelle hinzufügen") }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.searchStations(it) },
                    label = { Text("Haltestelle suchen, z. B. Alexanderplatz") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                error?.let { Text(it, color = colors.tertiary, modifier = Modifier.padding(16.dp)) }
            }
            items(results, key = { "r" + it.id }) { st ->
                ListItem(headlineContent = { Text(st.name) }, modifier = Modifier.clickable { vm.addStation(st); query = "" })
                HorizontalDivider()
            }
            item {
                Text(
                    "Lagebild fragt die Abfahrten dieser Haltestellen ab und meldet Störungshinweise, Ausfälle und " +
                        "Verspätungen ab 10 Minuten. Datenquelle: VBB über transport.rest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
