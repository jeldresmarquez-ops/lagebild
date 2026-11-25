package de.lagebild.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.lagebild.app.MainActivity
import de.lagebild.app.R
import de.lagebild.app.domain.model.AppUpdate
import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.StoryGroup
import de.lagebild.app.domain.model.TransitDisruption
import de.lagebild.app.domain.model.WarnLevel
import de.lagebild.app.domain.model.Warning
import de.lagebild.app.ui.theme.warnColorArgb
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_BREAKING = "breaking"
        const val CHANNEL_WARNINGS = "warnings"
        const val CHANNEL_WARNINGS_HIGH = "warnings_high"
        const val CHANNEL_TRAFFIC = "traffic"
        const val CHANNEL_UPDATES = "updates"

        const val EXTRA_OPEN_URL = "open_url"
        const val EXTRA_DESTINATION = "destination"
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CHANNEL_BREAKING, context.getString(R.string.channel_breaking), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_breaking_desc) },
            NotificationChannel(CHANNEL_WARNINGS_HIGH, "Gefahrenmeldungen (hohe Stufe)", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Unwetter- und Gefahrenwarnungen der Stufen „schwer“ und „extrem“" },
            NotificationChannel(CHANNEL_WARNINGS, context.getString(R.string.channel_warnings), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_warnings_desc) },
            NotificationChannel(CHANNEL_TRAFFIC, context.getString(R.string.channel_traffic), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_traffic_desc) },
            NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_updates_desc) },
        )
        nm.createNotificationChannels(channels)
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(requestCode: Int, url: String? = null, destination: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            url?.let { putExtra(EXTRA_OPEN_URL, it) }
            destination?.let { putExtra(EXTRA_DESTINATION, it) }
            data = Uri.parse("lagebild://notification/$requestCode")
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        if (!hasPermission()) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Berechtigung wurde zwischenzeitlich entzogen
        }
    }

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setAutoCancel(true)

    fun showBreaking(article: Article, silent: Boolean) {
        val id = article.id.hashCode()
        notify(
            id,
            base(CHANNEL_BREAKING)
                .setContentTitle("${if (article.isBreaking) "Eilmeldung" else "Top-Meldung"} · ${article.sourceName}")
                .setContentText(article.displayTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(article.displayTitle, article.displayTeaser).joinToString("\n\n")))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(silent)
                .setContentIntent(contentIntent(id, url = article.url)),
        )
    }

    fun showMultiSource(group: StoryGroup, silent: Boolean) {
        val id = ("multi" + group.key).hashCode()
        val sources = (listOf(group.lead) + group.related).map { it.sourceName }.distinct()
        notify(
            id,
            base(CHANNEL_BREAKING)
                .setContentTitle("Wichtiges Thema · ${sources.size} Quellen")
                .setContentText(group.lead.displayTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${group.lead.displayTitle}\n\nBerichtet von: ${sources.joinToString()}"))
                .setSilent(silent)
                .setContentIntent(contentIntent(id, url = group.lead.url)),
        )
    }

    /** Hohe Warnstufen ignorieren die Ruhezeiten bewusst. */
    fun showWarning(warning: Warning, silent: Boolean) {
        val id = warning.id.hashCode()
        val high = warning.level.isHighPriority
        notify(
            id,
            base(if (high) CHANNEL_WARNINGS_HIGH else CHANNEL_WARNINGS)
                .setContentTitle("${warnLabel(warning.level)} · ${warning.provider}")
                .setContentText(warning.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(warning.title, warning.description).joinToString("\n\n")))
                .setColor(warnColorArgb(warning.level))
                .setColorized(high)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(if (high) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(silent && !high)
                .setContentIntent(contentIntent(id, destination = "berlin")),
        )
    }

    fun showTraffic(disruption: TransitDisruption, silent: Boolean) {
        val id = disruption.id.hashCode()
        notify(
            id,
            base(CHANNEL_TRAFFIC)
                .setContentTitle("${disruption.lines.joinToString()} · ${disruption.title}")
                .setContentText(disruption.text ?: disruption.stationName)
                .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(disruption.text, "Haltestelle: ${disruption.stationName}").joinToString("\n\n")))
                .setSilent(silent)
                .setContentIntent(contentIntent(id, destination = "berlin")),
        )
    }

    fun showUpdateAvailable(update: AppUpdate) {
        notify(
            4711,
            base(CHANNEL_UPDATES)
                .setContentTitle("Update verfügbar: Version ${update.versionName}")
                .setContentText("Tippe, um das Update zu installieren.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(update.changelog.take(500)))
                .setContentIntent(contentIntent(4711, destination = "update")),
        )
    }

    private fun warnLabel(level: WarnLevel) = when (level) {
        WarnLevel.EXTREME -> "Extreme Gefahr"
        WarnLevel.SEVERE -> "Unwetter/Gefahr"
        WarnLevel.MODERATE -> "Warnung"
        WarnLevel.MINOR -> "Hinweis"
    }
}
