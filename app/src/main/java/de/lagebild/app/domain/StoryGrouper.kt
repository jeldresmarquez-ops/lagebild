package de.lagebild.app.domain

import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.StoryGroup
import java.text.Normalizer

/**
 * Fasst Meldungen verschiedener Quellen zum selben Thema zusammen.
 *
 * Verfahren: Aus jeder (deutschen bzw. übersetzten) Überschrift werden die
 * aussagekräftigen Wörter genommen (ohne Füllwörter). Zwei Artikel gelten als
 * gleiches Thema, wenn sie zeitlich nah beieinander liegen und sich genug
 * Wörter teilen (Jaccard-Ähnlichkeit). Einfach, schnell und ohne Server.
 */
object StoryGrouper {

    private const val MIN_SIMILARITY = 0.34
    private const val MIN_SHARED_TOKENS = 2
    private const val MAX_TIME_DIFF_MS = 18L * 60 * 60 * 1000

    private val stopWords = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "und", "oder",
        "aber", "in", "im", "ins", "an", "am", "auf", "aus", "bei", "mit", "nach", "von", "vom", "zu", "zum",
        "zur", "für", "über", "unter", "vor", "gegen", "um", "ist", "sind", "war", "wird", "werden", "hat",
        "haben", "nicht", "kein", "keine", "sich", "auch", "noch", "nur", "so", "wie", "was", "wer", "als",
        "es", "er", "sie", "wir", "ihr", "man", "neue", "neuer", "neues", "mehr", "jetzt", "soll", "will",
        "kann", "live", "liveblog", "news", "ticker", "eilmeldung", "+++", "video", "podcast", "kommentar",
        "the", "a", "an", "of", "to", "and", "or", "for", "on", "at", "by", "with", "from", "is", "are", "be",
    )

    fun tokens(text: String): Set<String> {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC)
        return normalized
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence()
            .filter { it.length >= 3 && it !in stopWords }
            .map { stem(it) }
            .toSet()
    }

    /** Sehr einfache Wortstamm-Kürzung, damit "Wahlen"/"Wahl" zusammenpassen. */
    private fun stem(word: String): String {
        val suffixes = listOf("ungen", "ern", "en", "es", "er", "e", "n", "s")
        for (s in suffixes) {
            if (word.length - s.length >= 4 && word.endsWith(s)) return word.dropLast(s.length)
        }
        return word
    }

    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        // Ohne Zwischen-Mengen zählen – wird bei ~1500 Artikeln über eine Million Mal aufgerufen.
        val (small, large) = if (a.size <= b.size) a to b else b to a
        var shared = 0
        for (t in small) if (t in large) shared++
        if (shared < MIN_SHARED_TOKENS) return 0.0
        return shared.toDouble() / (a.size + b.size - shared)
    }

    /**
     * @param articles bereits nach Relevanz/Zeit sortierte Liste.
     * Die Reihenfolge der Gruppen folgt dem jeweils ersten (Leit-)Artikel.
     */
    fun group(articles: List<Article>): List<StoryGroup> {
        val tokenCache = articles.associate { it.id to tokens(it.displayTitle) }
        val used = HashSet<String>()
        val result = ArrayList<StoryGroup>()
        for ((i, lead) in articles.withIndex()) {
            if (lead.id in used) continue
            used += lead.id
            val leadTokens = tokenCache.getValue(lead.id)
            val related = ArrayList<Article>()
            val seenSources = hashSetOf(lead.sourceId)
            for (j in i + 1 until articles.size) {
                val other = articles[j]
                if (other.id in used) continue
                if (other.sourceId in seenSources) continue // pro Quelle nur ein Artikel je Gruppe
                if (kotlin.math.abs(other.publishedAt - lead.publishedAt) > MAX_TIME_DIFF_MS) continue
                if (similarity(leadTokens, tokenCache.getValue(other.id)) >= MIN_SIMILARITY) {
                    related += other
                    used += other.id
                    seenSources += other.sourceId
                }
            }
            result += StoryGroup(lead, related)
        }
        return result
    }
}
