package de.lagebild.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lagebild.app.data.local.TranslationDao
import de.lagebild.app.data.repository.TransitRepository
import de.lagebild.app.data.settings.AppSettings
import de.lagebild.app.data.settings.SettingsRepository
import de.lagebild.app.data.settings.ThemeMode
import de.lagebild.app.data.settings.TranslationEngine
import de.lagebild.app.data.sources.NewsSource
import de.lagebild.app.data.sources.SourceRepository
import de.lagebild.app.data.translation.DeepLTranslator
import de.lagebild.app.domain.model.Category
import de.lagebild.app.domain.model.TransitStation
import de.lagebild.app.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val transit: TransitRepository,
    private val scheduler: WorkScheduler,
    private val translationDao: TranslationDao,
    deepL: DeepLTranslator,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val sources: StateFlow<List<NewsSource>> = sourceRepository.sources
    val deepLAvailable = deepL.isAvailable

    val stationResults = MutableStateFlow<List<TransitStation>>(emptyList())
    val stationSearchError = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { sourceRepository.getSources() }
    }

    fun setSourceEnabled(source: NewsSource, enabled: Boolean) = viewModelScope.launch { repo.setSourceEnabled(source, enabled) }
    fun setCategoryVisible(c: Category, visible: Boolean) = viewModelScope.launch { repo.setCategoryVisible(c, visible) }

    fun setInterval(minutes: Int) = viewModelScope.launch {
        repo.setSyncInterval(minutes)
        scheduler.scheduleSync(minutes, replace = true)
    }

    fun setNotifyBreaking(v: Boolean) = viewModelScope.launch { repo.setNotifyBreaking(v) }
    fun setNotifyMultiSource(v: Boolean) = viewModelScope.launch { repo.setNotifyMultiSource(v) }
    fun setNotifyWarnings(v: Boolean) = viewModelScope.launch { repo.setNotifyWarnings(v) }
    fun setNotifyMinorWarnings(v: Boolean) = viewModelScope.launch { repo.setNotifyMinorWarnings(v) }
    fun setNotifyTraffic(v: Boolean) = viewModelScope.launch { repo.setNotifyTraffic(v) }
    fun setNotifyUpdates(v: Boolean) = viewModelScope.launch { repo.setNotifyUpdates(v) }
    fun setQuietEnabled(v: Boolean) = viewModelScope.launch { repo.setQuietHoursEnabled(v) }
    fun setQuietHours(start: Int, end: Int) = viewModelScope.launch { repo.setQuietHours(start, end) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { repo.setThemeMode(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { repo.setDynamicColor(v) }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { repo.setWifiOnlyModelDownload(v) }

    fun setEngine(v: TranslationEngine) = viewModelScope.launch { repo.setTranslationEngine(v) }
    fun clearTranslations() = viewModelScope.launch { translationDao.clear() }

    fun searchStations(query: String) = viewModelScope.launch {
        if (query.trim().length < 3) {
            stationResults.value = emptyList(); return@launch
        }
        runCatching { transit.searchStations(query.trim()) }
            .onSuccess { stationResults.value = it; stationSearchError.value = null }
            .onFailure { stationSearchError.value = "Suche nicht möglich – keine Verbindung zu transport.rest" }
    }

    fun addStation(s: TransitStation) = viewModelScope.launch {
        repo.addFavoriteStation(s)
        stationResults.value = emptyList()
    }

    fun removeStation(s: TransitStation) = viewModelScope.launch { repo.removeFavoriteStation(s) }
    fun setLines(text: String) = viewModelScope.launch {
        repo.setFavoriteLines(text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }
}
