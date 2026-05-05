package jp.simplist.memo.privacy

import androidx.fragment.app.FragmentActivity
import jp.simplist.memo.data.AppSettings

/**
 * プライバシーロックの guard。
 * - 設定 ON のとき、アプリ起動時 / バックグラウンド復帰時に呼び出す。
 * - 認証成功でアプリ終了まで再認証なし (App scope の lastUnlockedAt 管理)。
 *
 * V1: アプリ全体ロック (個別メモロックは v2)。
 */
object PrivacyLockController {

    /** SESSION_TTL_MS を超えてロック中なら認証要求。簡易: 一度成功したら今回起動中は再要求しない。 */
    private const val SESSION_TTL_MS = 24L * 60L * 60L * 1000L

    /**
     * Activity の onResume 等で呼ぶ。
     * 設定 OFF か、過去 SESSION_TTL_MS 以内に成功していればコールバック即発火。
     * それ以外は BiometricPrompt を出して、成功時にコールバック。
     */
    fun guard(activity: FragmentActivity, onUnlocked: () -> Unit) {
        val settings = AppSettings.get(activity)
        if (!settings.privacyLockEnabled) { onUnlocked(); return }
        val now = System.currentTimeMillis()
        if (now - settings.lastUnlockedAt < SESSION_TTL_MS) { onUnlocked(); return }
        if (!BiometricHelper.canAuthenticate(activity)) {
            // 設定で ON にしてあるが端末側で利用不可になった場合 → fallthrough。
            onUnlocked()
            return
        }
        BiometricHelper.authenticate(activity,
            onSuccess = {
                settings.lastUnlockedAt = System.currentTimeMillis()
                onUnlocked()
            },
            onFailure = {
                // 認証失敗 → アプリを閉じる
                activity.finishAffinity()
            },
        )
    }
}
