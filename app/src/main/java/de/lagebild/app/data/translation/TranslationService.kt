package de.lagebild.app.data.translation

import android.util.Log
import de.lagebild.app.data.local.TranslationDao
import de.lagebild.app.data.local.TranslationEntity
import de.lagebild.app.data.settings.TranslationEngine
import de.lagebild.app.domain.model.Article
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Übersetzt Überschriften und Teaser fremdsprachiger Artikel und cached das Ergebnis in Room.
 * Deutsche Artikel werden nicht angefasst.
 */
@Singleton
class TranslationService @Inject constructor(
    private val dao: TranslationDao,
    @Named("mlkit") private val mlKit: TextTranslator,
    @Named("deepl") private val deepL: TextTranslator,
) {
    suspend fun translateArticles(
        articles: List<Article>,
        engine: TranslationEngine,
        wifiOnlyDownload: Boolean,
    ): List<Article> {
        if (engine == TranslationEngine.OFF) return articles
        val foreign = articles.filter { !it.language.equals("de", true) && it.titleDe == null }
        if (foreign.isEmpty()) return articles

        val translated = HashMap<String, Article>()
        foreign.groupBy { it.language.lowercase() }.forEach { (lang, list) ->
            val texts = list.flatMap { listOfNotNull(it.title, it.teaser) }.distinct()
            val map = runCatching { translateTexts(texts, lang, engine, wifiOnlyDownload) }
                .onFailure { Log.w(TAG, "Übersetzung fehlgeschlagen ($lang)", it) }
                .getOrDefault(emptyMap())
            list.forEach { a ->
                val t = map[a.title] ?: return@forEach
                translated[a.id] = a.copy(titleDe = t, teaserDe = a.teaser?.let { map[it] })
            }
        }
        return articles.map { translated[it.id] ?: it }
    }

    /** Liefert eine Zuordnung Originaltext → deutscher Text (aus Cache oder neu übersetzt). */
    internal suspend fun translateTexts(
        texts: List<String>,
        lang: String,
        engine: TranslationEngine,
        wifiOnlyDownload: Boolean,
    ): Map<String, String> {
        val translator = if (engine == TranslationEngine.DEEPL && deepL.isAvailable) deepL else mlKit
        val keyed = texts.associateBy { cacheKey(translator.engineId, lang, it) }
        val cached = dao.getAll(keyed.keys.toList()).associate { it.key to it.translated }
        val result = HashMap<String, String>()
        keyed.forEach { (k, text) -> cached[k]?.let { result[text] = it } }

        val missing = keyed.filterKeys { it !in cached }
        if (missing.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val newEntities = ArrayList<TranslationEntity>()
            missing.entries.chunked(25).forEach { chunk ->
                val out = translator.translate(chunk.map { it.value }, lang, wifiOnlyDownload)
                chunk.zip(out).forEach { (entry, translatedText) ->
                    result[entry.value] = translatedText
                    newEntities += TranslationEntity(entry.key, lang, translatedText, translator.engineId, now)
                }
            }
            dao.insertAll(newEntities)
        }
        return result
    }

    private fun cacheKey(engine: String, lang: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$engine|$lang|$text".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "TranslationService"
    }
}
