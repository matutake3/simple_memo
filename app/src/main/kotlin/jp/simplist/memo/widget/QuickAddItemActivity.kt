package jp.simplist.memo.widget

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.simplist.memo.R
import jp.simplist.memo.data.ChecklistItem
import jp.simplist.memo.data.MemoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * チェックリストウィジェットの「項目を追加」行から呼ばれる軽量 Activity。
 *
 * 起動経路: ChecklistWidgetProvider が `quickAddRow` の OnClick に紐付ける PendingIntent。
 * フロー:
 *   1. 渡された memoId から既存項目を取得 (タイトル表示 + sortOrder 算出のみに使用)
 *   2. 入力欄に文字を入れて「追加」or IME Done で保存
 *   3. ChecklistItem を insert → WidgetUpdater 経由でウィジェット再描画 → finish
 *
 * Theme.SimpleMemo.QuickAdd (windowIsFloating=true) で開くため、外側タップで finish。
 */
class QuickAddItemActivity : AppCompatActivity() {

    private var memoId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        memoId = intent?.getLongExtra(EXTRA_MEMO_ID, -1L) ?: -1L
        if (memoId <= 0L) {
            finish(); return
        }

        setContentView(R.layout.activity_quick_add_item)

        val titleView = findViewById<TextView>(R.id.quickAddTitle)
        val input = findViewById<EditText>(R.id.quickAddInput)
        val cancelBtn = findViewById<Button>(R.id.quickAddCancel)
        val submitBtn = findViewById<Button>(R.id.quickAddSubmit)

        // タイトル表示 (メモ名がなければ「リスト」フォールバック)
        lifecycleScope.launch {
            val memo = withContext(Dispatchers.IO) {
                MemoRepository.get(this@QuickAddItemActivity).getMemo(memoId)
            }
            val displayTitle = memo?.title?.takeIf { it.isNotBlank() } ?: "リスト"
            titleView.text = "${displayTitle}に追加"
        }

        cancelBtn.setOnClickListener { finish() }
        submitBtn.setOnClickListener { commitAndClose(input.text.toString()) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitAndClose(input.text.toString()); true
            } else false
        }

        // フォーカス + キーボード自動表示
        input.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        input.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun commitAndClose(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) { finish(); return }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val repo = MemoRepository.get(this@QuickAddItemActivity)
                val existing = repo.getChecklistItems(memoId)
                val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
                repo.insertChecklistItem(
                    ChecklistItem(
                        memoId = memoId,
                        text = text,
                        checked = false,
                        sortOrder = nextOrder,
                    ),
                )
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_MEMO_ID = "extra_memo_id"
    }
}
