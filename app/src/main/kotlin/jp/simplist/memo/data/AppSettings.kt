package jp.simplist.memo.data

import android.content.Context
import android.content.SharedPreferences

/**
 * UI スタイル切替。標準 (クリーム + 角丸 + マルチカラー) と
 * スタイリッシュ (白 + 直角 + モノトーン) の 2 種類。
 * テーマ適用と onResume での自動 recreate は jp.simplist.memo.ui.ThemedActivity が担当。
 */
enum class StyleMode { STANDARD, STYLISH }

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

    /** UI スタイル: 標準 (クリーム+角丸) / スタイリッシュ (白+直角). */
    var styleMode: StyleMode
        get() = runCatching {
            StyleMode.valueOf(
                prefs.getString(KEY_STYLE_MODE, StyleMode.STANDARD.name) ?: StyleMode.STANDARD.name,
            )
        }.getOrDefault(StyleMode.STANDARD)
        set(value) { prefs.edit().putString(KEY_STYLE_MODE, value.name).apply() }

    /** 入力候補チップ行を表示するか。 */
    var suggestionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTION_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_SUGGESTION_ENABLED, value).apply() }

    /** 入力候補に表示する最大件数 (5 / 8 / 10 / 12)。 */
    var suggestionMaxCount: Int
        get() = prefs.getInt(KEY_SUGGESTION_MAX, 8)
        set(value) { prefs.edit().putInt(KEY_SUGGESTION_MAX, value).apply() }

    /** 入力候補チップ行から除外する文字列セット (チップ長押しで追加)。 */
    var suggestionBlacklist: Set<String>
        get() = prefs.getStringSet(KEY_SUGGESTION_BLACKLIST, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_SUGGESTION_BLACKLIST, value).apply() }

    fun addToSuggestionBlacklist(text: String) {
        suggestionBlacklist = suggestionBlacklist.toMutableSet().apply { add(text) }
    }

    /**
     * 指定テキストが blacklist にあれば外す。
     * Repository の item insert / update から呼び、ユーザーがその単語を再入力した場合に
     * 自動で候補表示を復活させる (= 永久 block にしない)。
     */
    fun clearFromSuggestionBlacklist(text: String) {
        if (text.isBlank()) return
        val current = suggestionBlacklist
        if (text !in current) return
        suggestionBlacklist = current.toMutableSet().apply { remove(text) }
    }

    companion object {
        private const val NAME = "app_settings"
        private const val KEY_LIST_SORT_MODE = "list_sort_mode"
        private const val KEY_WIDGET_NOTIF = "widget_notification_enabled"
        private const val KEY_PRIVACY_LOCK = "privacy_lock_enabled"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_PRESETS_SEEDED = "presets_seeded"
        private const val KEY_BACKUP_FOLDER = "backup_folder_uri"
        private const val KEY_BACKUP_AUTO = "backup_auto_enabled"
        private const val KEY_BACKUP_LAST = "backup_last_at"
        private const val KEY_TAG_INITIAL = "tag_initial_on_card"
        private const val KEY_STYLE_MODE = "style_mode"
        private const val KEY_SUGGESTION_BLACKLIST = "suggestion_blacklist"
        private const val KEY_SUGGESTION_ENABLED = "suggestion_enabled"
        private const val KEY_SUGGESTION_MAX = "suggestion_max"

        @Volatile private var INSTANCE: AppSettings? = null

        fun get(context: Context): AppSettings = INSTANCE ?: synchronized(this) {
            INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
        }
    }
}
