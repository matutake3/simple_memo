package jp.simplist.memo.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 軽量な app 設定。SharedPreferences ベース (DataStore ではない)。
 * 設定画面のスイッチ類を保存する。
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** メモ一覧の並び替え基準。新仕様 (2026-05): 旧 prioritySortEnabled を統合。 */
    var listSortMode: ListSortMode
        get() = runCatching {
            ListSortMode.valueOf(
                prefs.getString(KEY_LIST_SORT_MODE, ListSortMode.UPDATED.name) ?: ListSortMode.UPDATED.name,
            )
        }.getOrDefault(ListSortMode.UPDATED)
        set(value) { prefs.edit().putString(KEY_LIST_SORT_MODE, value.name).apply() }

    var widgetNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_NOTIF, false)
        set(value) { prefs.edit().putBoolean(KEY_WIDGET_NOTIF, value).apply() }

    /** "TEXT" or "CHECKLIST". */
    var defaultMemoType: MemoType
        get() = runCatching {
            MemoType.valueOf(prefs.getString(KEY_DEFAULT_TYPE, MemoType.TEXT.name) ?: MemoType.TEXT.name)
        }.getOrDefault(MemoType.TEXT)
        set(value) { prefs.edit().putString(KEY_DEFAULT_TYPE, value.name).apply() }

    var privacyLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_LOCK, false)
        set(value) { prefs.edit().putBoolean(KEY_PRIVACY_LOCK, value).apply() }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply() }

    /** 初回起動時にプリセットタグ・テンプレを挿入したかどうか。 */
    var presetsSeeded: Boolean
        get() = prefs.getBoolean(KEY_PRESETS_SEEDED, false)
        set(value) { prefs.edit().putBoolean(KEY_PRESETS_SEEDED, value).apply() }

    // 旧 lastUnlockedAt (SharedPreferences 版) は廃止。
    // 認証セッションは PrivacyLockController でインメモリ管理 (プロセス kill で自動失効)。

    /** バックアップ先フォルダの SAF Tree URI (永続化済み)。null = 未設定。 */
    var backupFolderUri: String?
        get() = prefs.getString(KEY_BACKUP_FOLDER, null)
        set(value) { prefs.edit().putString(KEY_BACKUP_FOLDER, value).apply() }

    /** 自動バックアップ ON/OFF。 */
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_AUTO, false)
        set(value) { prefs.edit().putBoolean(KEY_BACKUP_AUTO, value).apply() }

    /** 最後に成功したバックアップ時刻 (epoch ms)。 */
    var lastBackupAt: Long
        get() = prefs.getLong(KEY_BACKUP_LAST, 0L)
        set(value) { prefs.edit().putLong(KEY_BACKUP_LAST, value).apply() }

    /** メモカード上にタグの先頭 1 文字をミニチップで表示するか。 */
    var showTagInitialOnCard: Boolean
        get() = prefs.getBoolean(KEY_TAG_INITIAL, false)
        set(value) { prefs.edit().putBoolean(KEY_TAG_INITIAL, value).apply() }

    companion object {
        private const val NAME = "app_settings"
        private const val KEY_LIST_SORT_MODE = "list_sort_mode"
        private const val KEY_WIDGET_NOTIF = "widget_notification_enabled"
        private const val KEY_DEFAULT_TYPE = "default_memo_type"
        private const val KEY_PRIVACY_LOCK = "privacy_lock_enabled"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_PRESETS_SEEDED = "presets_seeded"
        private const val KEY_BACKUP_FOLDER = "backup_folder_uri"
        private const val KEY_BACKUP_AUTO = "backup_auto_enabled"
        private const val KEY_BACKUP_LAST = "backup_last_at"
        private const val KEY_TAG_INITIAL = "tag_initial_on_card"

        @Volatile private var INSTANCE: AppSettings? = null

        fun get(context: Context): AppSettings = INSTANCE ?: synchronized(this) {
            INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
        }
    }
}
