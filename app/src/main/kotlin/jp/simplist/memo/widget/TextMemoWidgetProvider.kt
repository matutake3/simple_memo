package jp.simplist.memo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import jp.simplist.memo.R
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.ui.EditMemoActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * テキストメモウィジェットの AppWidgetProvider。
 *
 * - 設定 Activity で memoId が永続化される
 * - onUpdate / onAppWidgetOptionsChanged で widget RemoteViews を更新
 * - メモ削除時は WidgetUpdater 経由で onUpdate が呼ばれて空状態を表示
 */
class TextMemoWidgetProvider : AppWidgetProvider() {

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
        for (id in appWidgetIds) settings.removeWidget(id)
    }

    companion object {
        /**
         * 指定 widgetId 群を更新。各 widget の memoId をロードし、Memo を取得して RemoteViews を構築。
         */
        fun updateAll(context: Context, mgr: AppWidgetManager, widgetIds: IntArray) {
            if (widgetIds.isEmpty()) return
            val settings = WidgetSettings.get(context)
            val repo = MemoRepository.get(context)
            for (widgetId in widgetIds) {
                val memoId = settings.getTextMemoMemoId(widgetId)
                val memo = if (memoId != null) {
                    runBlocking { repo.getMemo(memoId) }?.takeIf {
                        it.deletedAt == null && it.type == MemoType.TEXT
                    }
                } else null
                val views = buildViews(context, widgetId, memo)
                mgr.updateAppWidget(widgetId, views)
            }
        }

        private fun buildViews(context: Context, widgetId: Int, memo: Memo?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_text_memo)
            if (memo == null) {
                // 空状態: タップで設定 Activity 再起動。背景はフラットなカード色 (0)。
                views.setViewVisibility(R.id.widgetContent, View.GONE)
                views.setViewVisibility(R.id.widgetEmpty, View.VISIBLE)
                views.setInt(
                    R.id.widgetRoot, "setBackgroundResource",
                    WidgetUpdater.cardBackgroundDrawable(0),
                )
                val pi = WidgetUpdater.configActivityPendingIntent(
                    context, widgetId, TextMemoConfigActivity::class.java,
                )
                views.setOnClickPendingIntent(R.id.widgetRoot, pi)
                return views
            }
            views.setViewVisibility(R.id.widgetContent, View.VISIBLE)
            views.setViewVisibility(R.id.widgetEmpty, View.GONE)
            // 2 段背景: ヘッダー (フルカラー) + ボディ (淡色)
            views.setInt(
                R.id.widgetHeader, "setBackgroundResource",
                WidgetUpdater.headerBackgroundDrawable(memo.color),
            )
            views.setInt(
                R.id.widgetBody, "setBackgroundResource",
                WidgetUpdater.bodyBackgroundDrawable(memo.color),
            )
            // タイトル / 本文
            val title = memo.title?.takeIf { it.isNotBlank() } ?: "(無題のメモ)"
            views.setTextViewText(R.id.widgetTitle, title)
            views.setTextViewText(R.id.widgetBodyText, memo.body.orEmpty())
            views.setViewVisibility(
                R.id.widgetBodyText,
                if (memo.body.isNullOrBlank()) View.GONE else View.VISIBLE,
            )
            // タップ → メモ編集画面へ
            val openIntent = Intent(context, EditMemoActivity::class.java).apply {
                putExtra(EditMemoActivity.EXTRA_MEMO_ID, memo.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                context,
                widgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openPi)
            return views
        }
    }
}
