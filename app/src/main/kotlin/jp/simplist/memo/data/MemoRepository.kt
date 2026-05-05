package jp.simplist.memo.data

import android.content.Context
import jp.simplist.memo.notification.ChecklistNotificationsManager
import jp.simplist.memo.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow

/**
 * Memo / ChecklistItem の薄い Repository。DAO へのアクセスを singleton でまとめる。
 * Room DAO は Context 渡しで取得し、Application スコープで保持。
 *
 * 副作用:
 *   - 書き込み系メソッドは内容変更後に WidgetUpdater.notifyMemoChanged および
 *     ChecklistNotificationsManager.notifyMemoChanged を呼ぶ。
 *     ホーム画面ウィジェットとロック画面通知がアプリ内編集にリアルタイム追従する。
 */
class MemoRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = MemoDatabase.get(context)
    private val memoDao = db.memoDao()
    private val checklistDao = db.checklistDao()

    /** memo 内容変更を widget / 通知に伝える共通ヘルパ。 */
    private fun broadcastChange(memoId: Long) {
        WidgetUpdater.notifyMemoChanged(appContext, memoId)
        ChecklistNotificationsManager.notifyMemoChanged(appContext, memoId)
    }
    private fun broadcastDelete(memoId: Long) {
        WidgetUpdater.notifyMemoDeleted(appContext, memoId)
        ChecklistNotificationsManager.notifyMemoDeleted(appContext, memoId)
    }

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
        broadcastChange(memo.id)
    }
    suspend fun moveToTrash(id: Long) {
        memoDao.moveToTrash(id)
        broadcastDelete(id)
    }
    suspend fun restoreFromTrash(id: Long) {
        memoDao.restore(id)
        broadcastChange(id)
    }
    suspend fun deletePermanently(id: Long) {
        memoDao.deletePermanently(id)
        broadcastDelete(id)
    }
    suspend fun purgeExpired(retentionMs: Long) {
        val threshold = System.currentTimeMillis() - retentionMs
        memoDao.purgeExpired(threshold)
        // purge した個別 id は分からないが、影響を受けたウィジェット / 通知は
        // 次の onUpdate (またはユーザー操作) で空状態を表示する。
    }
    suspend fun deleteAllTrash() = memoDao.deleteAllTrash()
    suspend fun restoreAllTrash() = memoDao.restoreAllTrash()
    suspend fun applyManualOrder(orderedIds: List<Long>) = memoDao.applyManualOrder(orderedIds)
    suspend fun clearManualOrders() = memoDao.clearSortOrders()
    suspend fun clearTagFromMemos(tagId: Long) = memoDao.clearTagFromMemos(tagId)

    suspend fun insertChecklistItem(item: ChecklistItem): Long {
        val id = checklistDao.insert(item)
        broadcastChange(item.memoId)
        return id
    }
    suspend fun insertChecklistItems(items: List<ChecklistItem>): List<Long> {
        val ids = checklistDao.insertAll(items)
        items.firstOrNull()?.let { broadcastChange(it.memoId) }
        return ids
    }
    suspend fun updateChecklistItem(item: ChecklistItem) {
        checklistDao.update(item)
        broadcastChange(item.memoId)
    }
    suspend fun deleteChecklistItem(id: Long) {
        val memoId = checklistDao.getMemoIdFor(id)
        checklistDao.deleteById(id)
        memoId?.let { broadcastChange(it) }
    }
    suspend fun deleteCheckedItems(memoId: Long) {
        checklistDao.deleteCheckedFor(memoId)
        broadcastChange(memoId)
    }
    suspend fun setItemChecked(id: Long, checked: Boolean) {
        checklistDao.setChecked(id, checked)
        // memoId はこの API のシグネチャに無いので逆引き。widget / 通知の即時反映に必要。
        checklistDao.getMemoIdFor(id)?.let { broadcastChange(it) }
    }
    suspend fun replaceChecklistItems(memoId: Long, items: List<ChecklistItem>) {
        checklistDao.replaceForMemo(memoId, items)
        broadcastChange(memoId)
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
