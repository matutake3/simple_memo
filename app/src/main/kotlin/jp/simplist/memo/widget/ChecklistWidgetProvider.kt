package jp.simplist.memo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import jp.simplist.memo.R
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.ui.EditChecklistActivity
import jp.simplist.memo.ui.EditMemoActivity
import kotlinx.coroutines.runBlocking

/**
 * チェックリストウィジェットの AppWidgetProvider。
 *
 * 構造:
 *  - ListView の各項目 → ChecklistRemoteViewsService が提供
 *  - 項目タップ → ChecklistToggleReceiver にイベントが渡る (PendingIntentTemplate + fillIn)
 *  - タイトル領域タップ → アプリ (EditChecklistActivity) を開く
 *  - メモ削除時は空状態 → タップで設定 Activity 再起動
 */
class ChecklistWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAll(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val settings = WidgetSettings.get(context)
        // 削除前に対象 widget が指していた memoId を記録 → 削除 → 残った widget に同じ memoId が
        // 紐付いていなければ通知も消す。
        val affectedMemoIds = mutableSetOf<Long>()
        for (id in appWidgetIds) {
            settings.getChecklistMemoId(id)?.let { affectedMemoIds.add(it) }
            settings.removeWidget(id)
        }
        if (affectedMemoIds.isEmpty()) return
        val remaining = settings.allChecklistMappings().values.toSet()
        for (memoId in affectedMemoIds) {
            if (memoId !in remaining) {
                jp.simplist.memo.notification.ChecklistNotificationsManager
                    .notifyMemoDeleted(context, memoId)
            }
        }
    }

    companion object {
        fun updateAll(context: Context, mgr: AppWidgetManager, widgetIds: IntArray) {
            if (widgetIds.isEmpty()) return
            val settings = WidgetSettings.get(context)
            val repo = MemoRepository.get(context)
            for (widgetId in widgetIds) {
                val memoId = settings.getChecklistMemoId(widgetId)
                val memo = if (memoId != null) {
                    runBlocking { repo.getMemo(memoId) }?.takeIf {
                        it.deletedAt == null && it.type == MemoType.CHECKLIST
                    }
                } else null
                val views = buildViews(context, widgetId, memo)
                mgr.updateAppWidget(widgetId, views)
            }
        }

        private fun buildViews(context: Context, widgetId: Int, memo: Memo?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_checklist)
            if (memo == null) {
                views.setViewVisibility(R.id.widgetContent, View.GONE)
                views.setViewVisibility(R.id.widgetEmpty, View.VISIBLE)
                views.setInt(
                    R.id.widgetRoot, "setBackgroundResource",
                    WidgetUpdater.cardBackgroundDrawable(context, 0),
                )
                val pi = WidgetUpdater.configActivityPendingIntent(
                    context, widgetId, ChecklistConfigActivity::class.java,
                )
                views.setOnClickPendingIntent(R.id.widgetRoot, pi)
                return views
            }
            views.setViewVisibility(R.id.widgetContent, View.VISIBLE)
            views.setViewVisibility(R.id.widgetEmpty, View.GONE)
            // widgetRoot 本体の outline も切替 (Android 12+ ウィジェットホストは widgetRoot の
            // outline で clip するため、ここを更新しないとスタイリッシュでも角丸が残る)。
            views.setInt(
                R.id.widgetRoot, "setBackgroundResource",
                WidgetUpdater.cardBackgroundDrawable(context, memo.color),
            )
            // 2 段背景: ヘッダー (フルカラー) + ボディ (淡色)
            views.setInt(
                R.id.widgetHeader, "setBackgroundResource",
                WidgetUpdater.headerBackgroundDrawable(context, memo.color),
            )
            views.setInt(
                R.id.widgetBody, "setBackgroundResource",
                WidgetUpdater.bodyBackgroundDrawable(context, memo.color),
            )
            // タイトル
            val title = memo.title?.takeIf { it.isNotBlank() } ?: "(無題のリスト)"
            views.setTextViewText(R.id.widgetTitle, title)
            // ListView に RemoteViews アダプタを接続
            val serviceIntent = Intent(context, ChecklistRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(ChecklistRemoteViewsService.EXTRA_MEMO_ID, memo.id)
                // 同じ Service を異なる widgetId で複数立ち上げるため、URI で識別
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.checklistItems, serviceIntent)
            views.setEmptyView(R.id.checklistItems, R.id.checklistEmpty)
            // タイトルタップ → アプリで該当リストを開く
            val openIntent = Intent(context, EditChecklistActivity::class.java).apply {
                putExtra(EditMemoActivity.EXTRA_MEMO_ID, memo.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                context,
                widgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, openPi)
            views.setOnClickPendingIntent(R.id.checklistEmpty, openPi)
            // クイック追加行 → QuickAddItemActivity (透過テーマで Dialog 風に開く)
            val quickAddIntent = Intent(context, QuickAddItemActivity::class.java).apply {
                putExtra(QuickAddItemActivity.EXTRA_MEMO_ID, memo.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            // requestCode は widgetId と被らないように 100000 オフセット
            val quickAddPi = PendingIntent.getActivity(
                context,
                widgetId + QUICK_ADD_REQUEST_OFFSET,
                quickAddIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.quickAddRow, quickAddPi)
            // 各項目クリック用のテンプレート (fillInIntent で memoId/itemId を埋める)
            val toggleIntent = Intent(context, ChecklistToggleReceiver::class.java).apply {
                action = ChecklistToggleReceiver.ACTION_TOGGLE
            }
            val togglePi = PendingIntent.getBroadcast(
                context,
                widgetId,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.checklistItems, togglePi)
            return views
        }

        private const val QUICK_ADD_REQUEST_OFFSET = 100_000
    }
}
