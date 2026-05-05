package jp.simplist.memo.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Memo / ChecklistItem の薄い Repository。DAO へのアクセスを singleton でまとめる。
 * Room DAO は Context 渡しで取得し、Application スコープで保持。
 */
class MemoRepository private constructor(context: Context) {

    private val db = MemoDatabase.get(context)
    private val memoDao = db.memoDao()
    private val checklistDao = db.checklistDao()

    /** 並び替え基準ごとに対応する Flow を返す。 */
    fun observeActive(mode: ListSortMode): Flow<List<Memo>> = when (mode) {
        ListSortMode.UPDATED -> memoDao.observeByUpdated()
        ListSortMode.CREATED -> memoDao.observeByCreated()
        ListSortMode.TITLE -> memoDao.observeByTitle()
        ListSortMode.PRIORITY -> memoDao.observeByPriority()
        ListSortMode.CUSTOM -> memoDao.observeByCustom()
    }

    fun observeTrash(): Flow<List<Memo>> = memoDao.observeTrash()
    fun observeMemo(id: Long): Flow<Memo?> = memoDao.observeById(id)
    fun observeChecklistItems(memoId: Long): Flow<List<ChecklistItem>> =
        checklistDao.observeForMemo(memoId)

    suspend fun getMemo(id: Long): Memo? = memoDao.getById(id)
    suspend fun getAllActive(): List<Memo> = memoDao.getAllActive()
    suspend fun getChecklistItems(memoId: Long): List<ChecklistItem> =
        checklistDao.getForMemo(memoId)

    suspend fun insertMemo(memo: Memo): Long = memoDao.insert(memo)
    suspend fun updateMemo(memo: Memo) = memoDao.update(memo.copy(updatedAt = System.currentTimeMillis()))
    suspend fun moveToTrash(id: Long) = memoDao.moveToTrash(id)
    suspend fun restoreFromTrash(id: Long) = memoDao.restore(id)
    suspend fun deletePermanently(id: Long) = memoDao.deletePermanently(id)
    suspend fun purgeExpired(retentionMs: Long) {
        val threshold = System.currentTimeMillis() - retentionMs
        memoDao.purgeExpired(threshold)
    }
    suspend fun deleteAllTrash() = memoDao.deleteAllTrash()
    suspend fun restoreAllTrash() = memoDao.restoreAllTrash()
    suspend fun applyManualOrder(orderedIds: List<Long>) = memoDao.applyManualOrder(orderedIds)
    suspend fun clearManualOrders() = memoDao.clearSortOrders()
    suspend fun clearTagFromMemos(tagId: Long) = memoDao.clearTagFromMemos(tagId)

    suspend fun insertChecklistItem(item: ChecklistItem): Long = checklistDao.insert(item)
    suspend fun insertChecklistItems(items: List<ChecklistItem>): List<Long> =
        checklistDao.insertAll(items)
    suspend fun updateChecklistItem(item: ChecklistItem) = checklistDao.update(item)
    suspend fun deleteChecklistItem(id: Long) = checklistDao.deleteById(id)
    suspend fun deleteCheckedItems(memoId: Long) = checklistDao.deleteCheckedFor(memoId)
    suspend fun setItemChecked(id: Long, checked: Boolean) = checklistDao.setChecked(id, checked)
    suspend fun replaceChecklistItems(memoId: Long, items: List<ChecklistItem>) =
        checklistDao.replaceForMemo(memoId, items)
    suspend fun applyChecklistOrder(orderedIds: List<Long>) =
        checklistDao.applyOrder(orderedIds)

    companion object {
        @Volatile private var INSTANCE: MemoRepository? = null

        fun get(context: Context): MemoRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: MemoRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
