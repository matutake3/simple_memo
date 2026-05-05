package jp.simplist.memo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.Presets
import jp.simplist.memo.trial.TrialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Application entry. Initialises the trial state and creates the notification
 * channels used by the app:
 *   - "checklist_lockscreen_v1": ロック画面に常時表示するチェックリスト通知 (LOW)
 *   - "trial_expired_v1":         無料期間終了の一回だけの通知 (DEFAULT)
 *
 * 初回起動時にプリセット (タグ 3 + テンプレ 5) を投入。
 * 起動毎に 7 日経過したゴミ箱メモを完全削除 (idempotent)。
 */
class App : Application() {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TrialManager.initialize(this)
        createChecklistChannel()
        createTrialExpiredChannel()
        Presets.seedIfNeeded(this, bgScope)
        purgeExpiredTrash()
    }

    private fun purgeExpiredTrash() {
        bgScope.launch {
            val retention = TimeUnit.DAYS.toMillis(TRASH_RETENTION_DAYS)
            MemoRepository.get(this@App).purgeExpired(retention)
        }
    }

    private fun createChecklistChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_CHECKLIST,
            getString(R.string.notif_channel_checklist),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_checklist_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    private fun createTrialExpiredChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_TRIAL_EXPIRED,
            getString(R.string.notif_channel_trial_expired),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notif_channel_trial_expired_desc)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_CHECKLIST = "checklist_lockscreen_v1"
        const val CHANNEL_TRIAL_EXPIRED = "trial_expired_v1"
        const val TRASH_RETENTION_DAYS = 7L
    }
}
