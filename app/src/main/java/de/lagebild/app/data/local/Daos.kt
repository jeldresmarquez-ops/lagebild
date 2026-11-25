package de.lagebild.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ARTICLE_STATE_SELECT = """
    SELECT a.*,
        (r.id IS NOT NULL) AS isRead,
        (b.id IS NOT NULL) AS isBookmarked
    FROM articles a
    LEFT JOIN read_articles r ON r.id = a.id
    LEFT JOIN bookmarks b ON b.id = a.id
"""

@Dao
interface ArticleDao {

    @Query("$ARTICLE_STATE_SELECT ORDER BY a.publishedAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 2000): Flow<List<ArticleWithState>>

    @Query(
        """$ARTICLE_STATE_SELECT
        WHERE a.title LIKE '%' || :q || '%' OR a.teaser LIKE '%' || :q || '%'
           OR a.titleDe LIKE '%' || :q || '%' OR a.teaserDe LIKE '%' || :q || '%'
        ORDER BY a.publishedAt DESC LIMIT 200"""
    )
    fun search(q: String): Flow<List<ArticleWithState>>

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: String): ArticleEntity?

    @Upsert
    suspend fun upsertAll(items: List<ArticleEntity>)

    @Query("DELETE FROM articles WHERE fetchedAt < :before")
    suspend fun deleteFetchedBefore(before: Long)

    @Query("SELECT * FROM articles WHERE publishedAt > :since ORDER BY isTop DESC, publishedAt DESC")
    suspend fun recent(since: Long): List<ArticleEntity>

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun count(): Int
}

@Dao
interface UserStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markRead(item: ReadEntity)

    @Query("DELETE FROM read_articles WHERE readAt < :before")
    suspend fun deleteReadBefore(before: Long)

    @Query("SELECT * FROM bookmarks ORDER BY savedAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun removeBookmark(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE id = :id)")
    suspend fun isBookmarked(id: String): Boolean
}

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE `key` IN (:keys)")
    suspend fun getAll(keys: List<String>): List<TranslationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TranslationEntity>)

    @Query("DELETE FROM translations WHERE createdAt < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM translations")
    suspend fun clear()
}

@Dao
abstract class WarningDao {
    @Query("SELECT * FROM warnings ORDER BY sentAt DESC")
    abstract fun observeAll(): Flow<List<WarningEntity>>

    @Query("SELECT * FROM warnings")
    abstract suspend fun getAll(): List<WarningEntity>

    @Transaction
    open suspend fun replaceAll(items: List<WarningEntity>) {
        clear()
        insertAll(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<WarningEntity>)

    @Query("DELETE FROM warnings")
    abstract suspend fun clear()
}

@Dao
interface NotifiedDao {
    @Query("SELECT `key` FROM notified WHERE `key` IN (:keys)")
    suspend fun existing(keys: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<NotifiedEntity>)

    @Query("DELETE FROM notified WHERE notifiedAt < :before")
    suspend fun deleteBefore(before: Long)
}
