package jp.simplist.memo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_items WHERE memoId = :memoId ORDER BY sortOrder ASC")
    fun observeForMemo(memoId: Long): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE memoId = :memoId ORDER BY sortOrder ASC")
    suspend fun getForMemo(memoId: Long): List<ChecklistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistItem>): List<Long>

    @Update
    suspend fun update(item: ChecklistItem)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM checklist_items WHERE memoId = :memoId AND checked = 1")
    suspend fun deleteCheckedFor(memoId: Long)

    @Query("DELETE FROM checklist_items WHERE memoId = :memoId")
    suspend fun deleteAllFor(memoId: Long)

    @Query("UPDATE checklist_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    /** 任意の項目 id から所属する memoId を引く (widget / 通知の refresh 用)。 */
    @Query("SELECT memoId FROM checklist_items WHERE id = :id")
    suspend fun getMemoIdFor(id: Long): Long?

    /**
     * 全リスト横断で「よく使う項目テキスト」を頻度順 + 直近性タイブレークで返す。
     * 入力候補チップ行で使う。空文字は除外。
     */
    @Query("""
        SELECT text FROM checklist_items
        WHERE text != ''
        GROUP BY text
        ORDER BY COUNT(*) DESC, MAX(id) DESC
        LIMIT :limit
    """)
    suspend fun getFrequentTexts(limit: Int): List<String>

    @Transaction
    suspend fun replaceForMemo(memoId: Long, items: List<ChecklistItem>) {
        deleteAllFor(memoId)
        insertAll(items.map { it.copy(memoId = memoId) })
    }

    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("UPDATE checklist_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}
