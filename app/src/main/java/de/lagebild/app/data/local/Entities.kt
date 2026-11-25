package de.lagebild.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [Index("publishedAt"), Index("sourceId"), Index("category")],
)
data class ArticleEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val sourceName: String,
    val sourceColor: String?,
    val url: String,
    val title: String,
    val teaser: String?,
    val imageUrl: String?,
    val publishedAt: Long,
    val category: String?,
    val isTop: Boolean,
    val isBreaking: Boolean,
    val language: String,
    val paywall: Boolean,
    val titleDe: String?,
    val teaserDe: String?,
    /** Zeitpunkt, an dem die App den Artikel zum ersten Mal gesehen hat (für Benachrichtigungen). */
    val firstSeenAt: Long,
    val fetchedAt: Long,
)

/** Artikel inklusive Gelesen-/Lesezeichen-Status (per JOIN). */
data class ArticleWithState(
    @Embedded val article: ArticleEntity,
    @ColumnInfo(name = "isRead") val isRead: Boolean,
    @ColumnInfo(name = "isBookmarked") val isBookmarked: Boolean,
)

@Entity(tableName = "read_articles")
data class ReadEntity(
    @PrimaryKey val id: String,
    val readAt: Long,
)

/** Lesezeichen speichern eine Kopie des Artikels, damit sie den Cache-Aufräumer überleben. */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    @Embedded(prefix = "a_") val article: ArticleEntity,
    val savedAt: Long,
)

/** Übersetzungs-Cache: gleicher Ausgangstext wird nie zweimal übersetzt. */
@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val key: String,
    val sourceLang: String,
    val translated: String,
    val engine: String,
    val createdAt: Long,
)

@Entity(tableName = "warnings")
data class WarningEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val title: String,
    val description: String?,
    val instruction: String?,
    val level: String,
    val start: Long?,
    val end: Long?,
    val sentAt: Long,
)

/** Merkt sich, wofür schon eine Benachrichtigung verschickt wurde (keine Duplikate). */
@Entity(tableName = "notified")
data class NotifiedEntity(
    @PrimaryKey val key: String,
    val notifiedAt: Long,
)
