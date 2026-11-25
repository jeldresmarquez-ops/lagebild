package de.lagebild.app.work

import de.lagebild.app.data.local.ArticleDao
import de.lagebild.app.data.local.NotifiedDao
import de.lagebild.app.data.local.NotifiedEntity
import de.lagebild.app.data.local.toDomain
import de.lagebild.app.data.repository.NewsRepository
import de.lagebild.app.data.repository.TransitRepository
import de.lagebild.app.data.repository.WarningRepository
import de.lagebild.app.data.settings.AppSettings
import de.lagebild.app.data.settings.SettingsRepository
import de.lagebild.app.domain.StoryGrouper
import de.lagebild.app.notifications.NotificationHelper
import de.lagebild.app.notifications.QuietHours
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein kompletter Hintergrund-Abgleich: Nachrichten, Warnungen, Verkehr –
 * und daraus die passenden Benachrichtigungen (ohne Duplikate, mit Ruhezeiten).
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val news: NewsRepository,
    private val warnings: WarningRepository,
    private val transit: TransitRepository,
    private val settingsRepository: SettingsRepository,
    private val articleDao: ArticleDao,
    private val notifiedDao: NotifiedDao,
    private val notifications: NotificationHelper,
) {
    /** @return true, wenn mindestens eine Datenquelle erreichbar war. */
    suspend fun runFullSync(): Boolean {
        val settings = settingsRepository.current()
        val quiet = settings.quietHoursEnabled && QuietHours.isQuiet(
            nowMinutes(), settings.quietStartMinutes, settings.quietEndMinutes,
        )
        val now = System.currentTimeMillis()

        val newsResult = news.refresh()
        if (!newsResult.isFirstSync && !quiet) notifyNews(newsResult.newArticles.map { it.id }.toSet(), settings, now)

        val warningResult = warnings.refresh()
        notifyWarnings(warningResult.warnings, settings, quiet)

        if (settings.favoriteStations.isNotEmpty()) {
            val t = transit.refresh(settings.favoriteStations, settings.favoriteLines)
            if (settings.notifyTraffic && !quiet && t.error == null) {
                val fresh = filterNew(t.disruptions.map { "traffic:${it.id}:${it.title}" to it })
                fresh.forEach { notifications.showTraffic(it, silent = false) }
            }
        }

        notifiedDao.deleteBefore(now - 7L * 24 * 60 * 60 * 1000)
        return !newsResult.offline || warningResult.error == null
    }

    private suspend fun notifyNews(newIds: Set<String>, settings: AppSettings, now: Long) {
        if (newIds.isEmpty()) return
        val recent = articleDao.recent(now - 6L * 60 * 60 * 1000).map { it.toDomain() }

        if (settings.notifyBreaking) {
            val breaking = recent.filter { it.isBreaking && it.id in newIds }
            filterNew(breaking.map { "art:${it.id}" to it }).take(3).forEach {
                notifications.showBreaking(it, silent = false)
            }
        }

        if (settings.notifyMultiSource) {
            // Thema, über das mindestens 3 verschiedene Quellen gerade berichten.
            val groups = StoryGrouper.group(recent).filter { g ->
                g.sourceCount >= MULTI_SOURCE_THRESHOLD && (listOf(g.lead) + g.related).any { it.id in newIds }
            }
            for (g in groups.take(2)) {
                val ids = (listOf(g.lead) + g.related).map { "multi:${it.id}" }
                if (notifiedDao.existing(ids).isNotEmpty()) continue
                notifiedDao.insertAll(ids.map { NotifiedEntity(it, now) })
                notifications.showMultiSource(g, silent = false)
            }
        }
    }

    private suspend fun notifyWarnings(list: List<de.lagebild.app.domain.model.Warning>, settings: AppSettings, quiet: Boolean) {
        val candidates = list.filter { w ->
            // Hohe Warnstufen werden IMMER gemeldet – unabhängig von Schalter und Ruhezeit.
            w.level.isHighPriority ||
                (settings.notifyWarnings && (settings.notifyMinorWarnings || w.level >= de.lagebild.app.domain.model.WarnLevel.MODERATE))
        }
        filterNew(candidates.map { "warn:${it.id}" to it }).forEach {
            notifications.showWarning(it, silent = quiet)
        }
    }

    /** Gibt nur Einträge zurück, für die noch keine Benachrichtigung verschickt wurde, und merkt sie sich. */
    private suspend fun <T> filterNew(items: List<Pair<String, T>>): List<T> {
        if (items.isEmpty()) return emptyList()
        val known = items.map { it.first }.chunked(500).flatMap { notifiedDao.existing(it) }.toSet()
        val fresh = items.filter { it.first !in known }
        val now = System.currentTimeMillis()
        notifiedDao.insertAll(fresh.map { NotifiedEntity(it.first, now) })
        return fresh.map { it.second }
    }

    private fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private companion object {
        const val MULTI_SOURCE_THRESHOLD = 3
    }
}
