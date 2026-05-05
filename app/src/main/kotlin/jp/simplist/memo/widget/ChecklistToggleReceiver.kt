package jp.simplist.memo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.simplist.memo.data.MemoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * チェックリストウィジェットの項目タップで起動される BroadcastReceiver。
 *
 * fillInIntent で memoId / itemId を受け取り、DB を反転 → ウィジェット更新を依頼。
 * 重要点 (SPEC §15.4):
 *   - トライアル切れでもチェック ON/OFF 操作は無料継続。買い物中に困らないように。
 *   - したがって TrialManager.canEditMemos() はチェックしない。
 */
class ChecklistToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val memoId = intent.getLongExtra(EXTRA_MEMO_ID, 0L)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (memoId <= 0L || itemId <= 0L) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repo = MemoRepository.get(context)
                val items = repo.getChecklistItems(memoId)
                val target = items.find { it.id == itemId } ?: return@launch
                repo.setItemChecked(itemId, !target.checked)
                // ウィジェット即時反映 (DB の Flow 経路は MainActivity 用なので、
                // ウィジェット用に明示的に notifyAppWidgetViewDataChanged を投げる)
                WidgetUpdater.notifyMemoChanged(context, memoId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "jp.simplist.memo.widget.action.TOGGLE_CHECK"
        const val EXTRA_MEMO_ID = "memoId"
        const val EXTRA_ITEM_ID = "itemId"
    }
}
