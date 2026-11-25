package de.lagebild.app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lagebild.app.BuildConfig
import de.lagebild.app.update.UpdateState

/** Alle Update-Dialoge: Hinweis, Fortschritt, Fehler und die Erklärung zur Installationserlaubnis. */
@Composable
fun UpdateDialogs(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val visible by vm.dialogVisible.collectAsStateWithLifecycle()
    val help by vm.showInstallHelp.collectAsStateWithLifecycle()

    // Zurück aus den Android-Einstellungen → Installation fortsetzen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (help && vm.canInstall()) vm.continueInstall()
    }

    if (help) {
        InstallHelpDialog(
            canInstall = vm.canInstall(),
            onOpenSettings = vm::openInstallSettings,
            onContinue = vm::continueInstall,
            onDismiss = vm::dismiss,
        )
        return
    }
    if (!visible) return

    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Suche nach Updates …") },
            text = { CircularProgressIndicator() },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("Schließen") } },
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Alles aktuell") },
            text = { Text("Du nutzt die neueste Version (${BuildConfig.VERSION_NAME}).") },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("OK") } },
        )
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Update auf Version ${s.update.versionName}") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Installiert: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    if (s.update.apkSize > 0) {
                        Text("Größe: %.1f MB".format(s.update.apkSize / 1_048_576.0), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Was ist neu?", fontWeight = FontWeight.Bold)
                    Text(s.update.changelog, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { vm.download(s.update) }) { Text("Herunterladen & installieren") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Später") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Lade Version ${s.update.versionName} …") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()} %")
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelDownload) { Text("Abbrechen") } },
        )
        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Bereit zur Installation") },
            text = { Text("Version ${s.update.versionName} wurde heruntergeladen und die Prüfsumme (SHA-256) ist korrekt.") },
            confirmButton = { TextButton(onClick = { vm.install(s.file) }) { Text("Installieren") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Später") } },
        )
        is UpdateState.Error -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Update nicht möglich") },
            text = { Text(s.message) },
            confirmButton = {
                if (s.update != null) TextButton(onClick = { vm.download(s.update) }) { Text("Erneut versuchen") }
                else TextButton(onClick = vm::dismiss) { Text("OK") }
            },
            dismissButton = { if (s.update != null) TextButton(onClick = vm::dismiss) { Text("Schließen") } },
        )
    }
}

@Composable
private fun InstallHelpDialog(
    canInstall: Boolean,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Updates installieren erlauben") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Lagebild kommt nicht aus dem Play Store. Damit die App ihre eigenen Updates installieren darf, " +
                        "musst du das einmalig erlauben:",
                )
                Spacer(Modifier.height(8.dp))
                Text("1. Tippe auf „Einstellungen öffnen“.")
                Text("2. Aktiviere „Dieser Quelle vertrauen“ bzw. „Unbekannte Apps installieren“.")
                Text("3. Gehe mit der Zurück-Taste in die App – die Installation startet dann automatisch.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sicherheit: Android installiert das Update nur, wenn es mit demselben Schlüssel signiert ist wie die " +
                        "installierte App. Zusätzlich prüft Lagebild die SHA-256-Prüfsumme.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            if (canInstall) TextButton(onClick = onContinue) { Text("Jetzt installieren") }
            else TextButton(onClick = onOpenSettings) { Text("Einstellungen öffnen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
