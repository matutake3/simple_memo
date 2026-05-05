package jp.simplist.memo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    /** 更新日順 (デフォルト)。最後に編集したメモを上に。 */
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeByUpdated(): Flow<List<Memo>>

    /** 作成日順。新しく作ったメモを上に。 */
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByCreated(): Flow<List<Memo>>

    /**
     * 名前順 (タイトル昇順)。空タイトルは下段にまとめ、その中では updatedAt DESC で安定。
     * COLLATE NOCASE で大文字小文字を区別しない。
     */
    @Query(
        """
        SELECT * FROM memos
        WHERE deletedAt IS NULL
        ORDER BY
          CASE WHEN title IS NULL OR title = '' THEN 1 ELSE 0 END,
          title COLLATE NOCASE ASC,
          updatedAt DESC
        """
    )
    fun observeByTitle(): Flow<List<Memo>>

    /** 重要度順。★が多いものを上に、同点は updatedAt DESC。 */
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL ORDER BY priority DESC, updatedAt DESC")
    fun observeByPriority(): Flow<List<Memo>>

    /**
     * カスタム並び替え: ユーザーがドラッグして決めた sortOrder で並べる。
     * sortOrder 未設定のメモは下段に updatedAt DESC で。
     */
    @Query(
        """
        SELECT * FROM memos
        WHERE deletedAt IS NULL
        ORDER BY
          CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END,
          sortOrder ASC,
          updatedAt DESC
        """
    )
    fun observeByCustom(): Flow<List<Memo>>

    /** ゴミ箱用: deletedAt が設定済みのメモ。 */
    @Query("SELECT * FROM memos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Memo>>

    @Query("SELECT * FROM memos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Memo?

    @Query("SELECT * FROM memos WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Memo?>

    @Query("SELECT * FROM memos WHERE deletedAt IS NULL")
    suspend fun getAllActive(): List<Memo>

    @Query("SELECT * FROM memos WHERE tagId = :tagId AND deletedAt IS NULL")
    suspend fun getByTag(tagId: Long): List<Memo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: Memo): Long

    @Update
    suspend fun update(memo: Memo)

    @Query("UPDATE memos SET deletedAt = :now WHERE id = :id")
    suspend fun moveToTrash(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE memos SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM memos WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeExpired(threshold: Long)

    @Query("DELETE FROM memos WHERE deletedAt IS NOT NULL")
    suspend fun deleteAllTrash()

    @Query("UPDATE memos SET deletedAt = NULL WHERE deletedAt IS NOT NULL")
    suspend fun restoreAllTrash()

    @Query("UPDATE memos SET sortOrder = NULL")
    suspend fun clearSortOrders()

    @Transaction
    suspend fun applyManualOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("UPDATE memos SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE memos SET tagId = NULL WHERE tagId = :tagId")
    suspend fun clearTagFromMemos(tagId: Long)
}
