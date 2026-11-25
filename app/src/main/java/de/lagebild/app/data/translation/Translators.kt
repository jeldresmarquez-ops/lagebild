package de.lagebild.app.data.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import de.lagebild.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Höchstens so lange auf den Download eines Sprachmodells warten (ca. 30 MB). */
private const val MODEL_DOWNLOAD_TIMEOUT_MS = 3 * 60_000L

/** Übersetzt Texte ins Deutsche. */
interface TextTranslator {
    val engineId: String
    val isAvailable: Boolean
    suspend fun translate(texts: List<String>, sourceLang: String, wifiOnlyDownload: Boolean): List<String>
}

/**
 * Google ML Kit: läuft komplett auf dem Gerät. Beim ersten Mal wird pro Sprache
 * ein Sprachmodell (~30 MB) heruntergeladen, danach funktioniert alles offline.
 */
@Singleton
class MlKitTranslator @Inject constructor() : TextTranslator {
    override val engineId = "mlkit"
    override val isAvailable = true

    private val clients = HashMap<String, Translator>()
    private val mutex = Mutex()

    private suspend fun client(lang: String, wifiOnly: Boolean): Translator = mutex.withLock {
        clients[lang] ?: run {
            val source = TranslateLanguage.fromLanguageTag(lang) ?: TranslateLanguage.ENGLISH
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.GERMAN)
                .build()
            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
            // Ohne (WLAN-)Verbindung wartet ML Kit sonst endlos – dann lieber erst beim nächsten Mal übersetzen.
            try {
                withTimeout(MODEL_DOWNLOAD_TIMEOUT_MS) { translator.downloadModelIfNeeded(conditions).await() }
            } catch (e: Exception) {
                translator.close()
                throw IOException("Sprachmodell $lang nicht verfügbar", e)
            }
            clients[lang] = translator
            translator
        }
    }

    override suspend fun translate(texts: List<String>, sourceLang: String, wifiOnlyDownload: Boolean): List<String> {
        val translator = client(sourceLang, wifiOnlyDownload)
        return texts.map { translator.translate(it).await() }
    }
}

/** DeepL API (Free oder Pro). Schlüssel kommt aus local.properties bzw. GitHub-Secret. */
@Singleton
class DeepLTranslator @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : TextTranslator {
    override val engineId = "deepl"
    private val apiKey = BuildConfig.DEEPL_API_KEY
    override val isAvailable: Boolean get() = apiKey.isNotBlank()

    @Serializable
    private data class Req(val text: List<String>, val target_lang: String, val source_lang: String? = null)

    @Serializable
    private data class Resp(val translations: List<Item>)

    @Serializable
    private data class Item(val text: String)

    override suspend fun translate(texts: List<String>, sourceLang: String, wifiOnlyDownload: Boolean): List<String> {
        if (!isAvailable) throw IOException("Kein DeepL-API-Key hinterlegt")
        // Kostenlose Schlüssel enden auf ":fx" und nutzen einen eigenen Server.
        val host = if (apiKey.endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com"
        val body = json.encodeToString(Req.serializer(), Req(texts, "DE", sourceLang.uppercase()))
        val request = Request.Builder()
            .url("$host/v2/translate")
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runInterruptible(Dispatchers.IO) {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("DeepL-Fehler HTTP ${r.code}")
                json.decodeFromString(Resp.serializer(), r.body!!.string()).translations.map { it.text }
            }
        }
    }
}
