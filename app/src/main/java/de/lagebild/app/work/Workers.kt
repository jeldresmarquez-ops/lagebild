package de.lagebild.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.lagebild.app.data.settings.SettingsRepository
import de.lagebild.app.notifications.NotificationHelper
import de.lagebild.app.update.UpdateManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: SyncCoordinator,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (coordinator.runFullSync()) Result.success() else Result.retry()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateManager: UpdateManager,
    private val notifications: NotificationHelper,
    private val settings: SettingsRepository,
    private val notifiedDao: de.lagebild.app.data.local.NotifiedDao,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!updateManager.isConfigured || !settings.current().notifyUpdates) return Result.success()
        val update = updateManager.check() ?: return Result.success()
        val key = "update:${update.versionCode}"
        if (notifiedDao.existing(listOf(key)).isEmpty()) {
            notifiedDao.insertAll(listOf(de.lagebild.app.data.local.NotifiedEntity(key, System.currentTimeMillis())))
            notifications.showUpdateAvailable(update)
        }
        return Result.success()
    }
}

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Plant den regelmäßigen Abgleich. Android erlaubt minimal 15 Minuten. */
    fun scheduleSync(intervalMinutes: Int, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleUpdateCheck() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UPDATE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val SYNC_WORK = "news_sync"
        const val UPDATE_WORK = "update_check"
    }
}
