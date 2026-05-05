package jp.simplist.memo.trial

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import jp.simplist.memo.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Manages the 14-day free trial lifecycle.
 *
 * Ported from AdBlock / Lock; memo-specific differences:
 *   - TRIAL_DURATION_MS = 14 days, per SPEC §15.1
 *   - SALT scoped to the memo app (DeviceIdProvider)
 *
 * Anti-reset strategy is identical: backed-up trial_state file + SSAID hash
 * device check + PackageManager.firstInstallTime fallback.
 *
 * Trial-expired UX (SPEC §15.4):
 *   - canEditMemos() = false → 既存メモの編集・新規作成・タグ追加・テンプレ追加・
 *                              バックアップを全て無効化
 *   - canCreateMemos() = false → 同上 (UI 側で同じガードに使う)
 *   - 閲覧 / 共有 / 検索 / ウィジェットのチェック操作は無料継続
 *     (買い物中に突然使えなくなるとレビュー炎上要因)
 */
class TrialManager private constructor(context: Context) {

    enum class State { NOT_STARTED, ACTIVE, EXPIRED, PURCHASED }

    private val appCtx = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_TRIAL, Context.MODE_PRIVATE)
    private val purchasePrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_PURCHASE, Context.MODE_PRIVATE)
    private val deviceHash: String = DeviceIdProvider.deviceHash(context)

    private fun firstInstallTimeMs(): Long = try {
        appCtx.packageManager
            .getPackageInfo(appCtx.packageName, 0)
            .firstInstallTime
    } catch (_: PackageManager.NameNotFoundException) {
        System.currentTimeMillis()
    }

    fun ensureStarted() {
        if (isPurchased()) return
        val storedHash = prefs.getString(KEY_DEVICE_HASH, null)
        if (storedHash != null && storedHash != deviceHash) {
            resetForNewDevice()
        }
        if (prefs.getLong(KEY_TRIAL_START, 0L) == 0L) {
            prefs.edit()
                .putLong(KEY_TRIAL_START, firstInstallTimeMs())
                .putString(KEY_DEVICE_HASH, deviceHash)
                .apply()
        }
    }

    private fun effectiveTrialStartMs(): Long {
        val stored = prefs.getLong(KEY_TRIAL_START, 0L)
        val installed = firstInstallTimeMs()
        return when {
            stored == 0L -> installed
            else -> minOf(stored, installed)
        }
    }

    fun state(): State {
        if (isPurchased()) return State.PURCHASED
        val start = effectiveTrialStartMs()
        if (start == 0L) return State.NOT_STARTED
        val elapsed = System.currentTimeMillis() - start
        return if (elapsed >= TRIAL_DURATION_MS) State.EXPIRED else State.ACTIVE
    }

    fun remainingMillis(): Long {
        if (isPurchased()) return Long.MAX_VALUE
        val start = effectiveTrialStartMs()
        if (start == 0L) return TRIAL_DURATION_MS
        return (start + TRIAL_DURATION_MS - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun remainingDays(): Int = TimeUnit.MILLISECONDS.toDays(remainingMillis()).toInt()
    fun remainingHours(): Int = TimeUnit.MILLISECONDS.toHours(remainingMillis()).toInt()

    fun isPurchased(): Boolean = purchasePrefs.getBoolean(KEY_PURCHASE_ACTIVE, false)

    fun isTrialBypass(): Boolean = BuildConfig.TRIAL_BYPASS

    private fun isUnlocked(): Boolean {
        if (isTrialBypass()) return true
        return when (state()) {
            State.ACTIVE, State.PURCHASED -> true
            State.NOT_STARTED, State.EXPIRED -> false
        }
    }

    /** True when the user is allowed to add or edit block slots. */
    fun canEditMemos(): Boolean = isUnlocked()

    /** True when block enforcement is allowed (existing slots fire overlays). */
    fun canCreateMemos(): Boolean = isUnlocked()

    fun markPurchased(purchaseToken: String, orderId: String?) {
        purchasePrefs.edit()
            .putBoolean(KEY_PURCHASE_ACTIVE, true)
            .putString(KEY_PURCHASE_TOKEN, purchaseToken)
            .putString(KEY_PURCHASE_ORDER_ID, orderId)
            .putLong(KEY_PURCHASE_SINCE, System.currentTimeMillis())
            .apply()
    }

    fun clearPurchase() {
        purchasePrefs.edit()
            .putBoolean(KEY_PURCHASE_ACTIVE, false)
            .remove(KEY_PURCHASE_TOKEN)
            .remove(KEY_PURCHASE_ORDER_ID)
            .apply()
    }

    private fun resetForNewDevice() {
        prefs.edit()
            .remove(KEY_TRIAL_START)
            .putString(KEY_DEVICE_HASH, deviceHash)
            .apply()
    }

    companion object {
        private const val PREFS_TRIAL = "trial_state"
        private const val PREFS_PURCHASE = "purchase_state"
        private const val KEY_TRIAL_START = "trial_start_epoch_ms"
        private const val KEY_DEVICE_HASH = "device_hash"
        private const val KEY_PURCHASE_ACTIVE = "purchase_active"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
        private const val KEY_PURCHASE_ORDER_ID = "purchase_order_id"
        private const val KEY_PURCHASE_SINCE = "purchase_since_epoch_ms"

        // 3 days, per SPEC §4.1.
        private val TRIAL_DURATION_MS = TimeUnit.DAYS.toMillis(14)

        @Volatile private var INSTANCE: TrialManager? = null

        fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = TrialManager(context.applicationContext).also {
                            it.ensureStarted()
                        }
                    }
                }
            }
        }

        fun get(): TrialManager = INSTANCE
            ?: error("TrialManager not initialized. Call TrialManager.initialize() in Application.onCreate().")
    }
}
