package de.lagebild.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.lagebild.app.data.sources.NewsSource
import de.lagebild.app.domain.model.Category
import de.lagebild.app.domain.model.TransitStation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class TranslationEngine(val label: String) {
    MLKIT("Google ML Kit (offline, kostenlos)"),
    DEEPL("DeepL (online, bessere Qualität)"),
    OFF("Nicht übersetzen"),
}

enum class ThemeMode(val label: String) { SYSTEM("Wie System"), LIGHT("Hell"), DARK("Dunkel") }

data class AppSettings(
    val enabledSourceOverrides: Set<String> = emptySet(),
    val disabledSourceOverrides: Set<String> = emptySet(),
    val hiddenCategories: Set<Category> = emptySet(),
    val syncIntervalMinutes: Int = 30,
    val notifyBreaking: Boolean = true,
    val notifyMultiSource: Boolean = true,
    val notifyWarnings: Boolean = true,
    val notifyMinorWarnings: Boolean = false,
    val notifyTraffic: Boolean = true,
    val notifyUpdates: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val translationEngine: TranslationEngine = TranslationEngine.MLKIT,
    val wifiOnlyModelDownload: Boolean = true,
    val favoriteStations: List<TransitStation> = emptyList(),
    val favoriteLines: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val installHelpShown: Boolean = false,
    val lastSyncAt: Long = 0,
) {
    fun isSourceEnabled(source: NewsSource): Boolean = when (source.id) {
        in disabledSourceOverrides -> false
        in enabledSourceOverrides -> true
        else -> source.enabledByDefault
    }
}

/** Minimale Schnittstelle, die Repositories brauchen (in Tests durch Fake ersetzbar). */
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setLastSyncAt(t: Long)
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStore {
    private object Keys {
        val enabledSources = stringSetPreferencesKey("enabled_sources")
        val disabledSources = stringSetPreferencesKey("disabled_sources")
        val hiddenCategories = stringSetPreferencesKey("hidden_categories")
        val syncInterval = intPreferencesKey("sync_interval")
        val notifyBreaking = booleanPreferencesKey("notify_breaking")
        val notifyMultiSource = booleanPreferencesKey("notify_multi_source")
        val notifyWarnings = booleanPreferencesKey("notify_warnings")
        val notifyMinorWarnings = booleanPreferencesKey("notify_minor_warnings")
        val notifyTraffic = booleanPreferencesKey("notify_traffic")
        val notifyUpdates = booleanPreferencesKey("notify_updates")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val translationEngine = stringPreferencesKey("translation_engine")
        val wifiOnlyModel = booleanPreferencesKey("wifi_only_model")
        val favoriteStations = stringSetPreferencesKey("favorite_stations")
        val favoriteLines = stringSetPreferencesKey("favorite_lines")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val installHelpShown = booleanPreferencesKey("install_help_shown")
        val lastSyncAt = longPreferencesKey("last_sync_at")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            enabledSourceOverrides = p[Keys.enabledSources] ?: emptySet(),
            disabledSourceOverrides = p[Keys.disabledSources] ?: emptySet(),
            hiddenCategories = (p[Keys.hiddenCategories] ?: emptySet()).mapNotNull { Category.fromKey(it) }.toSet(),
            syncIntervalMinutes = p[Keys.syncInterval] ?: d.syncIntervalMinutes,
            notifyBreaking = p[Keys.notifyBreaking] ?: d.notifyBreaking,
            notifyMultiSource = p[Keys.notifyMultiSource] ?: d.notifyMultiSource,
            notifyWarnings = p[Keys.notifyWarnings] ?: d.notifyWarnings,
            notifyMinorWarnings = p[Keys.notifyMinorWarnings] ?: d.notifyMinorWarnings,
            notifyTraffic = p[Keys.notifyTraffic] ?: d.notifyTraffic,
            notifyUpdates = p[Keys.notifyUpdates] ?: d.notifyUpdates,
            quietHoursEnabled = p[Keys.quietEnabled] ?: d.quietHoursEnabled,
            quietStartMinutes = p[Keys.quietStart] ?: d.quietStartMinutes,
            quietEndMinutes = p[Keys.quietEnd] ?: d.quietEndMinutes,
            translationEngine = p[Keys.translationEngine]
                ?.let { runCatching { TranslationEngine.valueOf(it) }.getOrNull() } ?: d.translationEngine,
            wifiOnlyModelDownload = p[Keys.wifiOnlyModel] ?: d.wifiOnlyModelDownload,
            favoriteStations = (p[Keys.favoriteStations] ?: emptySet()).mapNotNull { entry ->
                val parts = entry.split('|', limit = 2)
                if (parts.size == 2) TransitStation(parts[0], parts[1]) else null
            }.sortedBy { it.name },
            favoriteLines = p[Keys.favoriteLines] ?: emptySet(),
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            installHelpShown = p[Keys.installHelpShown] ?: false,
            lastSyncAt = p[Keys.lastSyncAt] ?: 0L,
        )
    }

    override suspend fun current(): AppSettings = settings.first()

    suspend fun setSourceEnabled(source: NewsSource, enabled: Boolean) = context.dataStore.edit { p ->
        val on = (p[Keys.enabledSources] ?: emptySet()).toMutableSet()
        val off = (p[Keys.disabledSources] ?: emptySet()).toMutableSet()
        on.remove(source.id); off.remove(source.id)
        if (enabled != source.enabledByDefault) {
            if (enabled) on += source.id else off += source.id
        }
        p[Keys.enabledSources] = on
        p[Keys.disabledSources] = off
    }

    suspend fun setCategoryVisible(category: Category, visible: Boolean) = context.dataStore.edit { p ->
        val set = (p[Keys.hiddenCategories] ?: emptySet()).toMutableSet()
        if (visible) set.remove(category.name) else set.add(category.name)
        p[Keys.hiddenCategories] = set
    }

    suspend fun setSyncInterval(minutes: Int) = context.dataStore.edit { it[Keys.syncInterval] = minutes.coerceAtLeast(15) }
    suspend fun setNotifyBreaking(v: Boolean) = context.dataStore.edit { it[Keys.notifyBreaking] = v }
    suspend fun setNotifyMultiSource(v: Boolean) = context.dataStore.edit { it[Keys.notifyMultiSource] = v }
    suspend fun setNotifyWarnings(v: Boolean) = context.dataStore.edit { it[Keys.notifyWarnings] = v }
    suspend fun setNotifyMinorWarnings(v: Boolean) = context.dataStore.edit { it[Keys.notifyMinorWarnings] = v }
    suspend fun setNotifyTraffic(v: Boolean) = context.dataStore.edit { it[Keys.notifyTraffic] = v }
    suspend fun setNotifyUpdates(v: Boolean) = context.dataStore.edit { it[Keys.notifyUpdates] = v }
    suspend fun setQuietHoursEnabled(v: Boolean) = context.dataStore.edit { it[Keys.quietEnabled] = v }
    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) = context.dataStore.edit {
        it[Keys.quietStart] = startMinutes
        it[Keys.quietEnd] = endMinutes
    }
    suspend fun setTranslationEngine(v: TranslationEngine) = context.dataStore.edit { it[Keys.translationEngine] = v.name }
    suspend fun setWifiOnlyModelDownload(v: Boolean) = context.dataStore.edit { it[Keys.wifiOnlyModel] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = v }
    suspend fun setInstallHelpShown() = context.dataStore.edit { it[Keys.installHelpShown] = true }
    override suspend fun setLastSyncAt(t: Long) {
        context.dataStore.edit { it[Keys.lastSyncAt] = t }
    }

    suspend fun addFavoriteStation(station: TransitStation) = context.dataStore.edit { p ->
        p[Keys.favoriteStations] = (p[Keys.favoriteStations] ?: emptySet()) + "${station.id}|${station.name}"
    }

    suspend fun removeFavoriteStation(station: TransitStation) = context.dataStore.edit { p ->
        p[Keys.favoriteStations] = (p[Keys.favoriteStations] ?: emptySet()).filterNot { it.startsWith("${station.id}|") }.toSet()
    }

    suspend fun setFavoriteLines(lines: Set<String>) = context.dataStore.edit { p ->
        p[Keys.favoriteLines] = lines.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    }
}
