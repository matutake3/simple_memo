package jp.simplist.memo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import jp.simplist.memo.R
import jp.simplist.memo.data.ChecklistItem
import jp.simplist.memo.data.MemoRepository
import kotlinx.coroutines.runBlocking

/**
 * チェックリストウィジェットの ListView 用 RemoteViewsService。
 *
 * Intent 経由で memoId を受け取り、その memo の項目を順次 RemoteViews として返す。
 * 各項目のタップ用 fillInIntent には memoId / itemId を入れて、
 * Provider 側でセットした PendingIntentTemplate (ChecklistToggleReceiver) と組み合わさる。
 */
class ChecklistRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val memoId = intent.getLongExtra(EXTRA_MEMO_ID, 0L)
        return Factory(applicationContext, memoId)
    }

    private class Factory(
        private val context: Context,
        private val memoId: Long,
    ) : RemoteViewsFactory {

        private var items: List<ChecklistItem> = emptyList()

        override fun onCreate() { reload() }

        override fun onDataSetChanged() { reload() }

        private fun reload() {
            if (memoId <= 0L) {
                items = emptyList(); return
            }
            val repo = MemoRepository.get(context)
            // チェック済みは下段に並べる仕様 (アプリ内 EditChecklist と同じソート)
            items = runBlocking { repo.getChecklistItems(memoId) }
                .sortedWith(compareBy({ it.checked }, { it.sortOrder }))
        }

        override fun onDestroy() { items = emptyList() }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val item = items.getOrNull(position) ?: return getLoadingView()!!
            val views = RemoteViews(context.packageName, R.layout.widget_checklist_item)
            views.setTextViewText(R.id.itemText, item.text)
            views.setImageViewResource(
                R.id.itemCheck,
                if (item.checked) R.drawable.ic_check_box_filled else R.drawable.ic_check_box,
            )
            views.setTextColor(
                R.id.itemText,
                context.getColor(if (item.checked) R.color.graphite_dim else R.color.ink),
            )
            // タップ時に Provider のテンプレートと合体する fillInIntent
            val fillIn = Intent().apply {
                putExtra(ChecklistToggleReceiver.EXTRA_MEMO_ID, item.memoId)
                putExtra(ChecklistToggleReceiver.EXTRA_ITEM_ID, item.id)
            }
            views.setOnClickFillInIntent(R.id.itemRoot, fillIn)
            return views
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds(): Boolean = true
    }

    companion object {
        const val EXTRA_MEMO_ID = "widget_memo_id"
    }
}
