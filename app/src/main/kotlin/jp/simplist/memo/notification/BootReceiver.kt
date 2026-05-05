package jp.simplist.memo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.simplist.memo.data.AppSettings

/**
 * デバイス起動完了で常駐通知を再 post する。
 * チェックリスト通知は ongoing なので、再起動でも自動復帰してほしい。
 *
 * 必要権限: RECEIVE_BOOT_COMPLETED (Manifest 既設)。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        if (!AppSettings.get(context).widgetNotificationEnabled) return
        ChecklistNotificationsManager.postAll(context)
    }
}
