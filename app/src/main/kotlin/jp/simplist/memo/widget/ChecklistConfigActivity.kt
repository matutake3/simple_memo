package jp.simplist.memo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.ui.ThemedActivity
import jp.simplist.memo.ui.applySystemBarsInsets
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.databinding.ActivityWidgetConfigBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * チェックリストウィジェット配置時の設定 Activity。
 * 既存メモから type=CHECKLIST のものを選び、widgetId と紐付ける。
 */
class ChecklistConfigActivity : ThemedActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsInsets()

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        binding.configTitle.text = "リストを選択"
        binding.cancelButton.setOnClickListener { finish() }

        val adapter = WidgetMemoPickerAdapter { memo -> onMemoSelected(memo.id) }
        binding.memoList.layoutManager = LinearLayoutManager(this)
        binding.memoList.adapter = adapter

        lifecycleScope.launch {
            val memos = withContext(Dispatchers.IO) {
                MemoRepository.get(this@ChecklistConfigActivity).getAllActive()
                    .filter { it.type == MemoType.CHECKLIST }
                    .sortedByDescending { it.updatedAt }
            }
            adapter.submitList(memos)
            binding.emptyText.visibility = if (memos.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyText.text = "チェックリストがありません。\nまずリストを作成してから配置してください。"
        }
    }

    private fun onMemoSelected(memoId: Long) {
        WidgetSettings.get(this).setChecklistMemoId(widgetId, memoId)
        val mgr = AppWidgetManager.getInstance(this)
        ChecklistWidgetProvider.updateAll(this, mgr, intArrayOf(widgetId))
        // RemoteViewsService の項目もリロードさせる
        mgr.notifyAppWidgetViewDataChanged(widgetId, jp.simplist.memo.R.id.checklistItems)
        // ウィジェット通知が ON ならこの memo の通知も即時 post (OFF なら内部で no-op)
        jp.simplist.memo.notification.ChecklistNotificationsManager
            .notifyMemoChanged(this, memoId)
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}
