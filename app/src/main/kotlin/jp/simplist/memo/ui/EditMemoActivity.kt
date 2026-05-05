package jp.simplist.memo.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.simplist.memo.R
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.data.TemplateRepository
import jp.simplist.memo.databinding.ActivityEditMemoBinding
import jp.simplist.memo.trial.TrialManager
import jp.simplist.memo.ui.dialogs.ColorPickerBottomSheet
import jp.simplist.memo.ui.dialogs.PriorityPickerBottomSheet
import jp.simplist.memo.ui.dialogs.TagPickerBottomSheet
import jp.simplist.memo.util.MemoColorUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * テキストメモの編集画面 (DESIGN_SPEC §7-B)。
 *
 * - extras EXTRA_MEMO_ID で既存編集、未指定なら新規作成。
 * - extras EXTRA_TEMPLATE_ID でテンプレートからの新規作成。
 * - extras EXTRA_INITIAL_TAG_ID でタグフィルタからの新規作成 (初期タグ設定)。
 * - 入力は debounce 500ms で自動保存。戻る前にも明示保存。
 * - トライアル切れ後は編集を弾く (Toast → finish)。閲覧は許可だが入力欄を無効化。
 */
class EditMemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditMemoBinding
    private lateinit var repo: MemoRepository
    private var memoId: Long = 0L
    private var memo: Memo? = null
    private var saveJob: Job? = null
    private var canEdit: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = MemoRepository.get(this)
        canEdit = TrialManager.get().canEditMemos()

        memoId = intent.getLongExtra(EXTRA_MEMO_ID, 0L)
        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, 0L)
        val initialTagId = if (intent.hasExtra(EXTRA_INITIAL_TAG_ID))
            intent.getLongExtra(EXTRA_INITIAL_TAG_ID, -1L) else null

        wireToolbar()
        wireBottomBar()
        wireWatchers()

        lifecycleScope.launch {
            if (memoId == 0L) {
                // 新規作成
                val (initTitle, initBody) = if (templateId > 0L) {
                    val tpl = TemplateRepository.get(this@EditMemoActivity).getById(templateId)
                    (tpl?.title to tpl?.body)
                } else (null to null)
                val newMemo = Memo(
                    type = MemoType.TEXT,
                    title = initTitle,
                    body = initBody,
                    tagId = initialTagId?.takeIf { it > 0L },
                    color = 0,
                    priority = 0,
                    isProtected = false,
                )
                memoId = repo.insertMemo(newMemo)
            }
            // 観測
            repo.observeMemo(memoId).collect { current ->
                if (current == null) {
                    finish(); return@collect
                }
                memo = current
                renderMemo(current)
            }
        }

        if (!canEdit) {
            binding.titleEdit.isEnabled = false
            binding.bodyEdit.isEnabled = false
        }
    }

    private fun wireToolbar() {
        binding.backButton.setOnClickListener { commitNow(); finish() }
        binding.colorButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            val current = memo?.color ?: 0
            ColorPickerBottomSheet.show(supportFragmentManager, current) { picked ->
                memo?.let { saveImmediate(it.copy(color = picked)) }
            }
        }
        binding.priorityButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            val current = memo?.priority ?: 0
            PriorityPickerBottomSheet.show(supportFragmentManager, current) { picked ->
                memo?.let { saveImmediate(it.copy(priority = picked)) }
            }
        }
        binding.tagButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            val current = memo?.tagId
            TagPickerBottomSheet.show(supportFragmentManager, current) { picked ->
                memo?.let { saveImmediate(it.copy(tagId = picked)) }
            }
        }
        binding.deleteButton.setOnClickListener {
            val current = memo ?: return@setOnClickListener
            if (current.isProtected) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.delete_memo_protected_blocked)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_memo_confirm)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    lifecycleScope.launch { repo.moveToTrash(memoId); finish() }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun wireBottomBar() {
        binding.shareButton.setOnClickListener {
            val m = memo ?: return@setOnClickListener
            val text = buildString {
                m.title?.takeIf { it.isNotBlank() }?.let { append(it).append("\n\n") }
                m.body?.let { append(it) }
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, m.title ?: "")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
        }
        binding.protectButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            memo?.let { saveImmediate(it.copy(isProtected = !it.isProtected)) }
        }
    }

    private fun wireWatchers() {
        binding.titleEdit.addTextChangedListener(simpleWatcher { scheduleSave() })
        binding.bodyEdit.addTextChangedListener(simpleWatcher { scheduleSave() })
    }

    private fun renderMemo(m: Memo) {
        if (binding.titleEdit.text.toString() != (m.title ?: "")) {
            binding.titleEdit.setText(m.title ?: "")
        }
        if (binding.bodyEdit.text.toString() != (m.body ?: "")) {
            binding.bodyEdit.setText(m.body ?: "")
        }
        // タイトルブロック (画面端まで広がる FrameLayout) に色を載せる。
        // EditText 自身の背景は @null のまま (XML 定義どおり)。
        val cardColor = MemoColorUtils.resolve(this, m.color)
        binding.titleBlock.setBackgroundColor(cardColor)
        // protect ボタン状態
        binding.protectIcon.setImageResource(
            if (m.isProtected) R.drawable.ic_lock else R.drawable.ic_lock_open,
        )
        binding.protectIcon.imageTintList = ColorStateList.valueOf(getColor(R.color.accent_sage_dark))
        binding.protectLabel.text = getString(
            if (m.isProtected) R.string.action_unprotect else R.string.action_protect,
        )
        // 優先度アイコンは設定有無で切替
        binding.priorityButton.setImageResource(
            if (m.priority > 0) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
        )
    }

    private fun scheduleSave() {
        if (!canEdit) return
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(500)
            commitNow()
        }
    }

    private fun commitNow() {
        if (!canEdit) return
        val current = memo ?: return
        val title = binding.titleEdit.text?.toString()?.takeIf { it.isNotEmpty() }
        val body = binding.bodyEdit.text?.toString()?.takeIf { it.isNotEmpty() }
        if (current.title == title && current.body == body) return
        saveImmediate(current.copy(title = title, body = body))
    }

    private fun saveImmediate(updated: Memo) {
        memo = updated
        lifecycleScope.launch { repo.updateMemo(updated) }
    }

    private fun ensureCanEdit(): Boolean {
        if (canEdit) return true
        AlertDialog.Builder(this)
            .setMessage(R.string.trial_locked_message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
        return false
    }

    override fun onPause() {
        super.onPause()
        commitNow()
    }

    private fun simpleWatcher(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { action() }
    }

    companion object {
        const val EXTRA_MEMO_ID = "memoId"
        const val EXTRA_TEMPLATE_ID = "templateId"
        const val EXTRA_INITIAL_TAG_ID = "initialTagId"
    }
}
