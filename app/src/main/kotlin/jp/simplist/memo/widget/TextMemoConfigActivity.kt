package jp.simplist.memo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.databinding.ActivityWidgetConfigBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * テキストメモウィジェット配置時の設定 Activity。
 * 既存メモから 1 つ選んで widgetId と紐付け、ウィジェットを描画する。
 */
class TextMemoConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 配置がキャンセルされる場合に備えて先に RESULT_CANCELED を設定
        setResult(Activity.RESULT_CANCELED)

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        binding.configTitle.text = "テキストメモを選択"
        binding.cancelButton.setOnClickListener { finish() }

        val adapter = WidgetMemoPickerAdapter { memo -> onMemoSelected(memo.id) }
        binding.memoList.layoutManager = LinearLayoutManager(this)
        binding.memoList.adapter = adapter

        lifecycleScope.launch {
            val memos = withContext(Dispatchers.IO) {
                MemoRepository.get(this@TextMemoConfigActivity).getAllActive()
                    .filter { it.type == MemoType.TEXT }
                    .sortedByDescending { it.updatedAt }
            }
            adapter.submitList(memos)
            binding.emptyText.visibility = if (memos.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyText.text = "テキストメモがありません。\nまずメモを作成してから配置してください。"
        }
    }

    private fun onMemoSelected(memoId: Long) {
        WidgetSettings.get(this).setTextMemoMemoId(widgetId, memoId)
        // ウィジェット即時更新
        val mgr = AppWidgetManager.getInstance(this)
        TextMemoWidgetProvider.updateAll(this, mgr, intArrayOf(widgetId))
        // OS に成功を通知してウィジェットを Home に追加
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}
