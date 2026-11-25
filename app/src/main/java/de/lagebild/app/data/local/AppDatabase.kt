package de.lagebild.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Wichtig für spätere Updates: Wenn sich Tabellen ändern, [version] erhöhen und
 * eine Migration hinzufügen – sonst gehen Lesezeichen beim Update verloren.
 */
@Database(
    entities = [
        ArticleEntity::class,
        ReadEntity::class,
        BookmarkEntity::class,
        TranslationEntity::class,
        WarningEntity::class,
        NotifiedEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun userStateDao(): UserStateDao
    abstract fun translationDao(): TranslationDao
    abstract fun warningDao(): WarningDao
    abstract fun notifiedDao(): NotifiedDao
}
