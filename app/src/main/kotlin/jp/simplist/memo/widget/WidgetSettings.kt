package jp.simplist.memo.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * ウィジェット ID と表示するメモ ID の対応を保存する SharedPreferences ラッパー。
 *
 * - テキストメモウィジェットとチェックリストウィジェットで別 prefix のキーを使う
 *   (ID 名前空間は共有だが、種類別に列挙したいため)
 * - ウィジェット削除時は AppWidgetProvider.onDeleted から remove を呼ぶ
 */
class WidgetSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun setTextMemoMemoId(widgetId: Int, memoId: Long) {
        prefs.edit().putLong(keyText(widgetId), memoId).apply()
    }

    fun getTextMemoMemoId(widgetId: Int): Long? {
        if (!prefs.contains(keyText(widgetId))) return null
        return prefs.getLong(keyText(widgetId), -1L).takeIf { it > 0L }
    }

    fun setChecklistMemoId(widgetId: Int, memoId: Long) {
        prefs.edit().putLong(keyChecklist(widgetId), memoId).apply()
    }

    fun getChecklistMemoId(widgetId: Int): Long? {
        if (!prefs.contains(keyChecklist(widgetId))) return null
        return prefs.getLong(keyChecklist(widgetId), -1L).takeIf { it > 0L }
    }

    fun removeWidget(widgetId: Int) {
        prefs.edit()
            .remove(keyText(widgetId))
            .remove(keyChecklist(widgetId))
            .apply()
    }

    /** すべてのテキストメモウィジェット (widgetId → memoId)。 */
    fun allTextMemoMappings(): Map<Int, Long> = prefs.all
        .filterKeys { it.startsWith(PREFIX_TEXT) }
        .mapNotNull { (k, v) ->
            val widgetId = k.removePrefix(PREFIX_TEXT).toIntOrNull() ?: return@mapNotNull null
            val memoId = (v as? Long)?.takeIf { it > 0L } ?: return@mapNotNull null
            widgetId to memoId
        }
        .toMap()

    /** すべてのチェックリストウィジェット (widgetId → memoId)。 */
    fun allChecklistMappings(): Map<Int, Long> = prefs.all
        .filterKeys { it.startsWith(PREFIX_CHECKLIST) }
        .mapNotNull { (k, v) ->
            val widgetId = k.removePrefix(PREFIX_CHECKLIST).toIntOrNull() ?: return@mapNotNull null
            val memoId = (v as? Long)?.takeIf { it > 0L } ?: return@mapNotNull null
            widgetId to memoId
        }
        .toMap()

    private fun keyText(widgetId: Int) = "$PREFIX_TEXT$widgetId"
    private fun keyChecklist(widgetId: Int) = "$PREFIX_CHECKLIST$widgetId"

    companion object {
        private const val NAME = "widget_settings"
        private const val PREFIX_TEXT = "text_memo_"
        private const val PREFIX_CHECKLIST = "checklist_"

        @Volatile private var INSTANCE: WidgetSettings? = null

        fun get(context: Context): WidgetSettings = INSTANCE ?: synchronized(this) {
            INSTANCE ?: WidgetSettings(context.applicationContext).also { INSTANCE = it }
        }
    }
}
