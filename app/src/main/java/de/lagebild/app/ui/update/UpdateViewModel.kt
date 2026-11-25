package de.lagebild.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lagebild.app.data.settings.SettingsRepository
import de.lagebild.app.domain.model.AppUpdate
import de.lagebild.app.update.UpdateManager
import de.lagebild.app.update.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = manager.state

    /** Dialog sichtbar? (Hintergrund-Prüfungen beim Start zeigen nur bei einem Update etwas an.) */
    val dialogVisible = MutableStateFlow(false)

    /** Erklärung „Unbekannte Apps installieren“ anzeigen. */
    val showInstallHelp = MutableStateFlow(false)

    private var job: Job? = null
    private var pendingFile: File? = null
    private var startupChecked = false

    fun checkOnStartup() {
        if (startupChecked || !manager.isConfigured) return
        startupChecked = true
        viewModelScope.launch {
            if (manager.check() != null) dialogVisible.value = true
            else if (manager.state.value !is UpdateState.Available) manager.reset()
        }
    }

    /** Button „Nach Updates suchen“. */
    fun checkManually() {
        dialogVisible.value = true
        viewModelScope.launch { manager.check() }
    }

    fun showDialog() {
        dialogVisible.value = true
    }

    fun download(update: AppUpdate) {
        job?.cancel()
        job = viewModelScope.launch {
            val file = manager.download(update) ?: return@launch
            install(file)
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    fun install(file: File) {
        viewModelScope.launch {
            val s = settings.current()
            if (!manager.canInstallPackages() || !s.installHelpShown) {
                pendingFile = file
                showInstallHelp.value = true
            } else {
                manager.install(file)
            }
        }
    }

    fun openInstallSettings() {
        viewModelScope.launch { settings.setInstallHelpShown() }
        manager.openInstallPermissionSettings()
    }

    /** Nach Rückkehr aus den Android-Einstellungen (oder wenn die Erlaubnis schon da ist). */
    fun continueInstall() {
        viewModelScope.launch { settings.setInstallHelpShown() }
        val file = pendingFile ?: (manager.state.value as? UpdateState.ReadyToInstall)?.file ?: return
        if (manager.canInstallPackages()) {
            showInstallHelp.value = false
            manager.install(file)
        }
    }

    fun canInstall() = manager.canInstallPackages()

    fun dismiss() {
        dialogVisible.value = false
        showInstallHelp.value = false
        if (manager.state.value is UpdateState.UpToDate || manager.state.value is UpdateState.Error) manager.reset()
    }
}
