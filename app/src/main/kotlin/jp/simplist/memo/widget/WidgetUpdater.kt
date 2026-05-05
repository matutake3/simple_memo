package jp.simplist.memo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import jp.simplist.memo.R

/**
 * アプリ側からウィジェットの再描画をトリガーするヘルパー。
 *
 * - メモ内容変更時に MemoRepository から呼ばれる
 * - 該当 widgetId のみ更新するため、関係ないウィジェットの再描画を避ける
 */
object WidgetUpdater {

    /**
     * 指定 memoId を表示しているウィジェット (テキスト・チェックリスト両方) を更新。
     */
    fun notifyMemoChanged(context: Context, memoId: Long) {
        val settings = WidgetSettings.get(context)
        val mgr = AppWidgetManager.getInstance(context)

        // テキストメモウィジェット
        val textIds = settings.allTextMemoMappings()
            .filterValues { it == memoId }.keys.toIntArray()
        if (textIds.isNotEmpty()) {
            TextMemoWidgetProvider.updateAll(context, mgr, textIds)
        }

        // チェックリストウィジェット
        val checklistIds = settings.allChecklistMappings()
            .filterValues { it == memoId }.keys.toIntArray()
        if (checklistIds.isNotEmpty()) {
            ChecklistWidgetProvider.updateAll(context, mgr, checklistIds)
            // 項目リストの内容も再ロードさせる
            for (id in checklistIds) {
                mgr.notifyAppWidgetViewDataChanged(id, R.id.checklistItems)
            }
        }
    }

    /**
     * 指定 memoId が削除されたとき (ゴミ箱移動含む) のフック。
     * 表示していたウィジェットを「メモが削除されました」状態にする。
     */
    fun notifyMemoDeleted(context: Context, memoId: Long) {
        // 削除も通常更新と同じ経路で処理 (Provider 側で memo == null / deletedAt != null を見て表示切替)
        notifyMemoChanged(context, memoId)
    }

    /**
     * カラー ID (0..18) → ウィジェット背景 drawable resource を返す。
     * RemoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", N) で使う。
     * 主にメモ削除時のフラット背景用 (ヘッダー/ボディ分離前のフォールバック)。
     */
    @DrawableRes
    fun cardBackgroundDrawable(colorId: Int): Int = when (colorId) {
        1 -> R.drawable.bg_widget_card_1
        2 -> R.drawable.bg_widget_card_2
        3 -> R.drawable.bg_widget_card_3
        4 -> R.drawable.bg_widget_card_4
        5 -> R.drawable.bg_widget_card_5
        6 -> R.drawable.bg_widget_card_6
        7 -> R.drawable.bg_widget_card_7
        8 -> R.drawable.bg_widget_card_8
        9 -> R.drawable.bg_widget_card_9
        10 -> R.drawable.bg_widget_card_10
        11 -> R.drawable.bg_widget_card_11
        12 -> R.drawable.bg_widget_card_12
        13 -> R.drawable.bg_widget_card_13
        14 -> R.drawable.bg_widget_card_14
        15 -> R.drawable.bg_widget_card_15
        16 -> R.drawable.bg_widget_card_16
        17 -> R.drawable.bg_widget_card_17
        18 -> R.drawable.bg_widget_card_18
        else -> R.drawable.bg_widget_card_default
    }

    /**
     * カラー ID → ウィジェットヘッダー (上のみ角丸 + フルカラー) drawable。
     */
    @DrawableRes
    fun headerBackgroundDrawable(colorId: Int): Int = when (colorId) {
        1 -> R.drawable.bg_widget_header_1
        2 -> R.drawable.bg_widget_header_2
        3 -> R.drawable.bg_widget_header_3
        4 -> R.drawable.bg_widget_header_4
        5 -> R.drawable.bg_widget_header_5
        6 -> R.drawable.bg_widget_header_6
        7 -> R.drawable.bg_widget_header_7
        8 -> R.drawable.bg_widget_header_8
        9 -> R.drawable.bg_widget_header_9
        10 -> R.drawable.bg_widget_header_10
        11 -> R.drawable.bg_widget_header_11
        12 -> R.drawable.bg_widget_header_12
        13 -> R.drawable.bg_widget_header_13
        14 -> R.drawable.bg_widget_header_14
        15 -> R.drawable.bg_widget_header_15
        16 -> R.drawable.bg_widget_header_16
        17 -> R.drawable.bg_widget_header_17
        18 -> R.drawable.bg_widget_header_18
        else -> R.drawable.bg_widget_header_default
    }

    /**
     * カラー ID → ウィジェットボディ (下のみ角丸 + 同系淡色) drawable。
     */
    @DrawableRes
    fun bodyBackgroundDrawable(colorId: Int): Int = when (colorId) {
        1 -> R.drawable.bg_widget_body_1
        2 -> R.drawable.bg_widget_body_2
        3 -> R.drawable.bg_widget_body_3
        4 -> R.drawable.bg_widget_body_4
        5 -> R.drawable.bg_widget_body_5
        6 -> R.drawable.bg_widget_body_6
        7 -> R.drawable.bg_widget_body_7
        8 -> R.drawable.bg_widget_body_8
        9 -> R.drawable.bg_widget_body_9
        10 -> R.drawable.bg_widget_body_10
        11 -> R.drawable.bg_widget_body_11
        12 -> R.drawable.bg_widget_body_12
        13 -> R.drawable.bg_widget_body_13
        14 -> R.drawable.bg_widget_body_14
        15 -> R.drawable.bg_widget_body_15
        16 -> R.drawable.bg_widget_body_16
        17 -> R.drawable.bg_widget_body_17
        18 -> R.drawable.bg_widget_body_18
        else -> R.drawable.bg_widget_body_default
    }

    /** 設定 Activity を再起動する PendingIntent (削除済みメモタップ時など)。 */
    fun configActivityPendingIntent(
        context: Context,
        widgetId: Int,
        configClass: Class<*>,
    ): PendingIntent {
        val intent = Intent(context, configClass).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // FLAG_ACTIVITY_NEW_TASK は不要 (ウィジェット config は通常起動扱い)
            // ただし taskAffinity を考慮して
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
