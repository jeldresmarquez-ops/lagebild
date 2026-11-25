package de.lagebild.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.lagebild.app.domain.model.Article
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Öffnet den Originalartikel in einem Custom Tab (Browser innerhalb der App). */
fun openInCustomTab(context: Context, url: String, toolbarColor: Color) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor.toArgb()).build())
        .build()
    try {
        intent.launchUrl(context, Uri.parse(url))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

fun shareArticle(context: Context, article: Article) {
    val text = "${article.displayTitle}\n\n${article.url}\n\nQuelle: ${article.sourceName}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.displayTitle)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Artikel teilen"))
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM., HH:mm", Locale.GERMANY)

/** "vor 12 Min." für Aktuelles, sonst Uhrzeit bzw. Datum. */
fun formatArticleTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - epochMs
    val zoned = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return when {
        diff < DateUtils.MINUTE_IN_MILLIS -> "gerade eben"
        diff < DateUtils.HOUR_IN_MILLIS -> "vor ${diff / DateUtils.MINUTE_IN_MILLIS} Min."
        DateUtils.isToday(epochMs) -> "${timeFormat.format(zoned)} Uhr"
        else -> "${dateTimeFormat.format(zoned)} Uhr"
    }
}

fun formatDateTime(epochMs: Long): String =
    "${dateTimeFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))} Uhr"
