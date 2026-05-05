package jp.simplist.memo.data

import android.content.Context
import jp.simplist.memo.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow

/**
 * Memo / ChecklistItem の薄い Repository。DAO へのアクセスを singleton でまとめる。
 * Room DAO は Context 渡しで取得し、Application スコープで保持。
 *
 * 副作用:
 *   - 書き込み系メソッドは内容変更後に WidgetUpdater.notifyMemoChanged を呼ぶ。
 *     ホーム画面のウィジェットがアプリ内編集にリアルタイム追従する。
 */
class MemoRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = MemoDatabase.get(context)
    private val memoDao = db.memoDao()
    private val checklistDao = db.checklistDao()

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
    suspend fun updateMemo(memo: Memo) {
        memoDao.update(memo.copy(updatedAt = System.currentTimeMillis()))
        WidgetUpdater.notifyMemoChanged(appContext, memo.id)
    }
    suspend fun moveToTrash(id: Long) {
        memoDao.moveToTrash(id)
        WidgetUpdater.notifyMemoDeleted(appContext, id)
    }
    suspend fun restoreFromTrash(id: Long) {
        memoDao.restore(id)
        WidgetUpdater.notifyMemoChanged(appContext, id)
    }
    suspend fun deletePermanently(id: Long) {
        memoDao.deletePermanently(id)
        WidgetUpdater.notifyMemoDeleted(appContext, id)
    }
    suspend fun purgeExpired(retentionMs: Long) {
        val threshold = System.currentTimeMillis() - retentionMs
        memoDao.purgeExpired(threshold)
        // purge した個別 id は分からないが、影響を受けたウィジェットは
        // 次の onUpdate (またはユーザー操作) で空状態を表示する。
    }
    suspend fun deleteAllTrash() = memoDao.deleteAllTrash()
    suspend fun restoreAllTrash() = memoDao.restoreAllTrash()
    suspend fun applyManualOrder(orderedIds: List<Long>) = memoDao.applyManualOrder(orderedIds)
    suspend fun clearManualOrders() = memoDao.clearSortOrders()
    suspend fun clearTagFromMemos(tagId: Long) = memoDao.clearTagFromMemos(tagId)

    suspend fun insertChecklistItem(item: ChecklistItem): Long {
        val id = checklistDao.insert(item)
        WidgetUpdater.notifyMemoChanged(appContext, item.memoId)
        return id
    }
    suspend fun insertChecklistItems(items: List<ChecklistItem>): List<Long> {
        val ids = checklistDao.insertAll(items)
        items.firstOrNull()?.let { WidgetUpdater.notifyMemoChanged(appContext, it.memoId) }
        return ids
    }
    suspend fun updateChecklistItem(item: ChecklistItem) {
        checklistDao.update(item)
        WidgetUpdater.notifyMemoChanged(appContext, item.memoId)
    }
    suspend fun deleteChecklistItem(id: Long) = checklistDao.deleteById(id)
    suspend fun deleteCheckedItems(memoId: Long) {
        checklistDao.deleteCheckedFor(memoId)
        WidgetUpdater.notifyMemoChanged(appContext, memoId)
    }
    suspend fun setItemChecked(id: Long, checked: Boolean) = checklistDao.setChecked(id, checked)
    suspend fun replaceChecklistItems(memoId: Long, items: List<ChecklistItem>) {
        checklistDao.replaceForMemo(memoId, items)
        WidgetUpdater.notifyMemoChanged(appContext, memoId)
    }
    suspend fun applyChecklistOrder(orderedIds: List<Long>) {
        checklistDao.applyOrder(orderedIds)
        // 並び替えは memoId が分からないので、ウィジェット側で onUpdate / 次の操作で同期される。
    }

    companion object {
        @Volatile private var INSTANCE: MemoRepository? = null

        fun get(context: Context): MemoRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: MemoRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
