package de.lagebild.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import de.lagebild.app.BuildConfig
import de.lagebild.app.data.remote.GitHubApi
import de.lagebild.app.data.remote.HttpFetcher
import de.lagebild.app.data.remote.UpdateManifest
import de.lagebild.app.domain.model.AppUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class ReadyToInstall(val update: AppUpdate, val file: File) : UpdateState
    data class Error(val message: String, val update: AppUpdate? = null) : UpdateState
}

/**
 * In-App-Updates ohne Play Store:
 * 1. Neuestes GitHub-Release abfragen, `update.json` lesen und versionCode vergleichen.
 * 2. APK mit Fortschritt herunterladen und die SHA-256-Prüfsumme prüfen.
 * 3. Android-Installationsdialog öffnen. Android prüft dabei zusätzlich, dass die
 *    neue APK mit demselben Schlüssel signiert ist wie die installierte App.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHub: GitHubApi,
    private val fetcher: HttpFetcher,
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val isConfigured: Boolean
        get() = BuildConfig.GITHUB_REPO.contains('/') && !BuildConfig.GITHUB_REPO.startsWith("DEIN-")

    /** Prüft auf ein neueres Release. Liefert das Update oder null. */
    suspend fun check(): AppUpdate? {
        if (!isConfigured) {
            _state.value = UpdateState.Error("GITHUB_REPO ist in gradle.properties noch nicht eingetragen.")
            return null
        }
        _state.value = UpdateState.Checking
        return try {
            val (owner, repo) = BuildConfig.GITHUB_REPO.split('/', limit = 2)
            val release = gitHub.latestRelease(owner, repo)
            val manifestAsset = release.assets.firstOrNull { it.name == "update.json" }
                ?: throw IOException("Release ${release.tagName} enthält keine update.json")
            val manifest = json.decodeFromString(UpdateManifest.serializer(), fetcher.getString(manifestAsset.downloadUrl))
            val apkAsset = release.assets.firstOrNull { it.name == manifest.apk }
                ?: throw IOException("APK ${manifest.apk} fehlt im Release")
            val update = AppUpdate(
                versionCode = manifest.versionCode,
                versionName = manifest.versionName,
                changelog = release.body?.trim().orEmpty().ifEmpty { "Keine Änderungsnotizen." },
                apkUrl = apkAsset.downloadUrl,
                apkSize = apkAsset.size,
                sha256 = manifest.sha256.lowercase(),
            )
            if (isNewer(update.versionCode, BuildConfig.VERSION_CODE)) {
                _state.value = UpdateState.Available(update)
                update
            } else {
                _state.value = UpdateState.UpToDate
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            _state.value = UpdateState.Error(
                if (e.code() == 404) "Noch kein Release im Repository gefunden." else "GitHub-Fehler (HTTP ${e.code()})",
            )
            null
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Update-Prüfung fehlgeschlagen: ${e.message ?: "Netzwerkfehler"}")
            null
        }
    }

    suspend fun download(update: AppUpdate): File? {
        _state.value = UpdateState.Downloading(update, 0f)
        return try {
            val file = withContext(Dispatchers.IO) { downloadAndVerify(update) }
            _state.value = UpdateState.ReadyToInstall(update, file)
            file
        } catch (e: CancellationException) {
            _state.value = UpdateState.Available(update)
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Error(e.message ?: "Download fehlgeschlagen", update)
            null
        }
    }

    private suspend fun downloadAndVerify(update: AppUpdate): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // alte Downloads entfernen
        val target = File(dir, "lagebild-${update.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")

        client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download fehlgeschlagen (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Leere Antwort")
            val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0 && done - lastEmit > 128 * 1024) {
                            lastEmit = done
                            _state.value = UpdateState.Downloading(update, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(update.sha256, ignoreCase = true)) {
            target.delete()
            throw IOException("Prüfsumme stimmt nicht – die Datei wurde verworfen. Bitte erneut versuchen.")
        }
        return target
    }

    /** Darf die App APKs installieren? (Einstellung „Unbekannte Apps installieren“) */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Öffnet den Android-Installationsdialog für die heruntergeladene APK. */
    fun install(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Error("Kein Installationsprogramm gefunden")
            false
        }
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }

    companion object {
        fun isNewer(remoteVersionCode: Int, installedVersionCode: Int) = remoteVersionCode > installedVersionCode
    }
}
