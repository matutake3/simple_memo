package jp.simplist.memo.privacy

import androidx.fragment.app.FragmentActivity
import jp.simplist.memo.data.AppSettings

/**
 * プライバシーロックの guard。
 *
 * セッション仕様 (2026-05 更新版・最終):
 *  - 認証状態は **インメモリのみ** (SharedPreferences には書かない)
 *  - したがって OS によるプロセス kill / アプリ強制終了で session が自動失効
 *    → 「アプリを完全に終了して再起動した時のみ」再認証要求
 *  - 一時的な背景化 (ホームボタン → 別アプリ → 戻る等) では再認証しない
 *    (端末ロック画面で十分な防御になっており、頻繁な再認証は UX を損なうため)
 *
 * v1 ではアプリ全体に対するロック (個別メモロックは v2)。
 */
object PrivacyLockController {

    /** 認証成功フラグ。プロセス kill で当然リセットされる (= 再起動時に再認証)。 */
    @Volatile private var unlocked: Boolean = false

    /** 現在の認証状態。Activity 側で「認証完了までコンテンツを隠す」判定に使う。 */
    fun isUnlocked(): Boolean = unlocked

    /**
     * Activity の onResume 等で呼ぶ。
     * - 設定 OFF または既に認証済みなら即 onUnlocked
     * - それ以外は BiometricPrompt を出して、成功時にコールバック
     * - 認証不可端末では fallthrough (UI を死なせないため)
     */
    fun guard(activity: FragmentActivity, onUnlocked: () -> Unit) {
        val settings = AppSettings.get(activity)
        if (!settings.privacyLockEnabled) { onUnlocked(); return }
        if (unlocked) { onUnlocked(); return }
        if (!BiometricHelper.canAuthenticate(activity)) { onUnlocked(); return }

        BiometricHelper.authenticate(activity,
            onSuccess = {
                unlocked = true
                onUnlocked()
            },
            onFailure = {
                activity.finishAffinity()
            },
        )
    }
}
