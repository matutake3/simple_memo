package jp.simplist.memo.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import jp.simplist.memo.App
import jp.simplist.memo.R
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.data.ChecklistItem
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.ui.EditChecklistActivity
import jp.simplist.memo.ui.EditMemoActivity
import jp.simplist.memo.widget.ChecklistToggleReceiver
import jp.simplist.memo.widget.WidgetSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ロック画面 / 通知シェードに常駐するチェックリスト通知の管理。
 *
 * 仕様:
 *  - 対象: 「ウィジェットを配置済みのチェックリスト」だけ。
 *  - 1 リスト = 1 通知。常駐 (ongoing)、setSilent + チャンネル無音で alert を抑制。
 *  - collapsed: contentText に未完了項目を「、」連結で表示 (1 行)。
 *  - expanded: 8 スロットの custom RemoteViews。各行タップでチェック反転。
 *  - タイトル / 通知本体タップ → EditChecklistActivity。
 *  - 設定 OFF または項目 0 件 / メモ削除 / ウィジェット解除 で通知を消す。
 */
object ChecklistNotificationsManager {

    private const val NOTIF_ID_BASE = 1_000_000
    private fun notifId(memoId: Long): Int = NOTIF_ID_BASE + (memoId and 0xFFFFFF).toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun postAll(context: Context) {
        if (!AppSettings.get(context).widgetNotificationEnabled) return
        val memoIds = checklistMemoIdsWithWidget(context)
        if (memoIds.isEmpty()) return
        scope.launch {
            val repo = MemoRepository.get(context)
            for (id in memoIds) postOne(context, repo, id)
        }
    }

    fun cancelAll(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        scope.launch {
            val repo = MemoRepository.get(context)
            val all = repo.getAllActive()
            for (m in all) nm.cancel(notifId(m.id))
        }
    }

    fun notifyMemoChanged(context: Context, memoId: Long) {
        if (!AppSettings.get(context).widgetNotificationEnabled) return
        if (memoId !in checklistMemoIdsWithWidget(context)) return
        scope.launch {
            postOne(context, MemoRepository.get(context), memoId)
        }
    }

    fun notifyMemoDeleted(context: Context, memoId: Long) {
        NotificationManagerCompat.from(context).cancel(notifId(memoId))
    }

    private fun checklistMemoIdsWithWidget(context: Context): Set<Long> {
        return WidgetSettings.get(context).allChecklistMappings().values.toSet()
    }

    private suspend fun postOne(context: Context, repo: MemoRepository, memoId: Long) {
        val memo = repo.getMemo(memoId)
            ?.takeIf { it.deletedAt == null && it.type == MemoType.CHECKLIST }
        if (memo == null) {
            notifyMemoDeleted(context, memoId)
            return
        }
        val items = repo.getChecklistItems(memoId)
        if (items.isEmpty()) {
            notifyMemoDeleted(context, memoId)
            return
        }
        val notification = buildNotification(context, memo, items)
        try {
            NotificationManagerCompat.from(context).notify(notifId(memoId), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 未許可 (Android 13+)
        }
    }

    private fun buildNotification(
        context: Context,
        memo: Memo,
        items: List<ChecklistItem>,
    ): Notification {
        val sorted = items.sortedWith(compareBy({ it.checked }, { it.sortOrder }))
        val totalCount = sorted.size
        val title = memo.title?.takeIf { it.isNotBlank() } ?: "(無題のリスト)"

        // collapsed (1 行) 用: 未完了の項目名を「、」連結。
        val unchecked = sorted.filter { !it.checked }
        val collapsedText = if (unchecked.isEmpty()) {
            "${totalCount}件すべて完了"
        } else {
            unchecked.joinToString("、") { it.text }
        }

        val expanded = buildBigContentView(context, memo.id, title, sorted)

        // タイトル / 本体タップ → アプリで該当リストを開く
        val openIntent = Intent(context, EditChecklistActivity::class.java).apply {
            putExtra(EditMemoActivity.EXTRA_MEMO_ID, memo.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context,
            notifId(memo.id),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, App.CHANNEL_CHECKLIST)
            .setSmallIcon(R.drawable.ic_check_square)
            .setContentTitle(title)
            .setContentText(collapsedText)
            .setContentIntent(openPi)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    /** expanded view (BigContentView) 用の RemoteViews。各行タップで toggle。 */
    private fun buildBigContentView(
        context: Context,
        memoId: Long,
        title: String,
        items: List<ChecklistItem>,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.notification_checklist)
        rv.setTextViewText(R.id.notifTitle, title)

        val rowIds = intArrayOf(
            R.id.notifRow0, R.id.notifRow1, R.id.notifRow2, R.id.notifRow3,
            R.id.notifRow4, R.id.notifRow5, R.id.notifRow6, R.id.notifRow7,
        )
        val checkIds = intArrayOf(
            R.id.notifRow0Check, R.id.notifRow1Check, R.id.notifRow2Check, R.id.notifRow3Check,
            R.id.notifRow4Check, R.id.notifRow5Check, R.id.notifRow6Check, R.id.notifRow7Check,
        )
        val textIds = intArrayOf(
            R.id.notifRow0Text, R.id.notifRow1Text, R.id.notifRow2Text, R.id.notifRow3Text,
            R.id.notifRow4Text, R.id.notifRow5Text, R.id.notifRow6Text, R.id.notifRow7Text,
        )

        val maxSlots = rowIds.size
        for (i in 0 until maxSlots) {
            val item = items.getOrNull(i)
            if (item != null) {
                rv.setViewVisibility(rowIds[i], View.VISIBLE)
                rv.setImageViewResource(
                    checkIds[i],
                    if (item.checked) R.drawable.ic_check_box_filled else R.drawable.ic_check_box,
                )
                rv.setTextViewText(textIds[i], item.text)
                // タップで反転
                val toggleIntent = Intent(context, ChecklistToggleReceiver::class.java).apply {
                    action = ChecklistToggleReceiver.ACTION_TOGGLE
                    putExtra(ChecklistToggleReceiver.EXTRA_MEMO_ID, memoId)
                    putExtra(ChecklistToggleReceiver.EXTRA_ITEM_ID, item.id)
                    // widget の fillIn 用 PI と被らないよう URI で隔離
                    data = Uri.parse("memo-notif://$memoId/${item.id}")
                }
                val requestCode = ((memoId and 0xFFFF) * 1000 + (item.id and 0x3FF)).toInt()
                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                rv.setOnClickPendingIntent(rowIds[i], pi)
            } else {
                rv.setViewVisibility(rowIds[i], View.GONE)
            }
        }

        if (items.size > maxSlots) {
            rv.setViewVisibility(R.id.notifOverflow, View.VISIBLE)
            rv.setTextViewText(R.id.notifOverflow, "他 ${items.size - maxSlots} 件")
        } else {
            rv.setViewVisibility(R.id.notifOverflow, View.GONE)
        }

        return rv
    }
}
