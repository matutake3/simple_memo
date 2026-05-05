package jp.simplist.memo.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.R
import jp.simplist.memo.data.ChecklistItem
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.data.TemplateRepository
import jp.simplist.memo.databinding.ActivityEditChecklistBinding
import jp.simplist.memo.trial.TrialManager
import jp.simplist.memo.ui.adapters.ChecklistItemAdapter
import jp.simplist.memo.ui.dialogs.ColorPickerBottomSheet
import jp.simplist.memo.ui.dialogs.PriorityPickerBottomSheet
import jp.simplist.memo.ui.dialogs.TagPickerBottomSheet
import jp.simplist.memo.util.MemoColorUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * チェックリスト編集画面 (DESIGN_SPEC §7-C)。
 *
 * Enter / Backspace 仕様:
 *   - タイトルで Enter:
 *       - 項目が 0 件 → 新規項目を作成して focus
 *       - 項目が 1 件以上 → 先頭項目に focus 移動
 *   - 項目で Enter → 直下に新規項目を挿入して focus
 *   - 項目が空のまま Backspace → その項目を削除して 1 つ前 (なければ title) に focus
 *   - 「+ 項目を追加」ボタンは廃止 (Enter で十分なため)
 */
class EditChecklistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditChecklistBinding
    private lateinit var repo: MemoRepository
    private lateinit var adapter: ChecklistItemAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var memoId: Long = 0L
    private var memo: Memo? = null
    private var saveJob: Job? = null
    private var canEdit: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditChecklistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = MemoRepository.get(this)
        canEdit = TrialManager.get().canEditMemos()

        memoId = intent.getLongExtra(EditMemoActivity.EXTRA_MEMO_ID, 0L)
        val templateId = intent.getLongExtra(EditMemoActivity.EXTRA_TEMPLATE_ID, 0L)
        val initialTagId = if (intent.hasExtra(EditMemoActivity.EXTRA_INITIAL_TAG_ID))
            intent.getLongExtra(EditMemoActivity.EXTRA_INITIAL_TAG_ID, -1L) else null

        wireToolbar()
        wireBottom()
        wireWatchers()
        setupRecycler()

        lifecycleScope.launch {
            if (memoId == 0L) {
                val (initTitle, items) = if (templateId > 0L) {
                    val tpl = TemplateRepository.get(this@EditChecklistActivity).getById(templateId)
                    tpl?.title to (tpl?.items() ?: emptyList())
                } else (null to emptyList())
                val newMemo = Memo(
                    type = MemoType.CHECKLIST,
                    title = initTitle,
                    color = 0,
                    priority = 0,
                    tagId = initialTagId?.takeIf { it > 0L },
                    isProtected = false,
                )
                memoId = repo.insertMemo(newMemo)
                if (items.isNotEmpty()) {
                    val seeded = items.mapIndexed { i, t -> ChecklistItem(memoId = memoId, text = t, sortOrder = i) }
                    repo.insertChecklistItems(seeded)
                }
            }
            launch {
                repo.observeMemo(memoId).collect {
                    if (it == null) { finish(); return@collect }
                    memo = it; renderMemo(it)
                }
            }
            launch {
                repo.observeChecklistItems(memoId).collect { items ->
                    adapter.submit(items)
                    // リストが空のときだけタップ用のオーバーレイ表示
                    binding.emptyTapZone.visibility =
                        if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }

        binding.emptyTapZone.setOnClickListener {
            if (!canEdit) return@setOnClickListener
            lifecycleScope.launch {
                val newId = repo.insertChecklistItem(
                    ChecklistItem(memoId = memoId, text = "", checked = false, sortOrder = 0),
                )
                adapter.requestFocusForItem(newId)
            }
        }

        if (!canEdit) {
            binding.titleEdit.isEnabled = false
        }
    }

    private fun setupRecycler() {
        adapter = ChecklistItemAdapter(
            onChecked = { item, checked ->
                lifecycleScope.launch { repo.setItemChecked(item.id, checked) }
            },
            onTextChanged = { item, text ->
                if (!canEdit) return@ChecklistItemAdapter
                lifecycleScope.launch { repo.updateChecklistItem(item.copy(text = text)) }
            },
            onEnterPressed = { item -> handleItemEnter(item) },
            onBackspaceOnEmpty = { item -> handleItemBackspaceEmpty(item) },
            onDragStart = { vh -> itemTouchHelper.startDrag(vh) },
            canEdit = canEdit,
        )
        binding.itemList.layoutManager = LinearLayoutManager(this)
        binding.itemList.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                adapter.move(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(
                vh: RecyclerView.ViewHolder?,
                actionState: Int,
            ) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    // 透明背景だと他項目の文字が透けて重なって見えるので、ドラッグ中だけ
                    // 不透明な surface 色を敷いて、elevation で「持ち上がっている」感を出す。
                    vh.itemView.setBackgroundColor(getColor(R.color.surface))
                    vh.itemView.elevation = 8f * resources.displayMetrics.density
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                // ドラッグ終了で背景・影を元に戻す
                vh.itemView.background = null
                vh.itemView.elevation = 0f
                lifecycleScope.launch { repo.applyChecklistOrder(adapter.currentIds()) }
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.itemList)
    }

    private fun wireToolbar() {
        binding.backButton.setOnClickListener { commitTitleNow(); finish() }
        binding.colorButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            ColorPickerBottomSheet.show(supportFragmentManager, memo?.color ?: 0) { picked ->
                memo?.let { saveImmediate(it.copy(color = picked)) }
            }
        }
        binding.priorityButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            PriorityPickerBottomSheet.show(supportFragmentManager, memo?.priority ?: 0) { picked ->
                memo?.let { saveImmediate(it.copy(priority = picked)) }
            }
        }
        binding.tagButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            TagPickerBottomSheet.show(supportFragmentManager, memo?.tagId) { picked ->
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

    private fun wireBottom() {
        binding.deleteCheckedButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_completed_confirm)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    lifecycleScope.launch { repo.deleteCheckedItems(memoId) }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
        binding.shareButton.setOnClickListener {
            val m = memo ?: return@setOnClickListener
            lifecycleScope.launch {
                val items = repo.getChecklistItems(memoId)
                val text = buildString {
                    m.title?.takeIf { it.isNotBlank() }?.let { append(it).append("\n\n") }
                    for (it in items) {
                        append(if (it.checked) getString(R.string.share_checklist_checked) else getString(R.string.share_checklist_unchecked))
                        append(' ')
                        append(it.text)
                        append('\n')
                    }
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, m.title ?: "")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
            }
        }
    }

    private fun wireWatchers() {
        binding.titleEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveJob?.cancel()
                saveJob = lifecycleScope.launch {
                    delay(500)
                    commitTitleNow()
                }
            }
        })
        // タイトルで Enter → 先頭項目を作成 / フォーカス
        binding.titleEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                if (canEdit) {
                    handleTitleEnter()
                    true
                } else false
            } else false
        }
    }

    private fun handleTitleEnter() {
        // タイトルを即時保存 (debounce 待ちにしない)
        saveJob?.cancel()
        commitTitleNow()
        lifecycleScope.launch {
            val items = repo.getChecklistItems(memoId).sortedBy { it.sortOrder }
            if (items.isEmpty()) {
                val newId = repo.insertChecklistItem(
                    ChecklistItem(memoId = memoId, text = "", checked = false, sortOrder = 0),
                )
                adapter.requestFocusForItem(newId)
            } else {
                adapter.requestFocusForItem(items.first().id)
            }
        }
    }

    /** 項目で Enter → 直下に新規項目を挿入してフォーカス。 */
    private fun handleItemEnter(item: ChecklistItem) {
        lifecycleScope.launch {
            val all = repo.getChecklistItems(memoId).sortedBy { it.sortOrder }
            val targetSortOrder = item.sortOrder + 1
            // 現在 item.sortOrder + 1 以降にいる項目をすべて +1 ずらして空席を作る
            val toBump = all.filter { it.sortOrder >= targetSortOrder && it.id != item.id }
            for (b in toBump) {
                repo.updateChecklistItem(b.copy(sortOrder = b.sortOrder + 1))
            }
            val newId = repo.insertChecklistItem(
                ChecklistItem(
                    memoId = memoId,
                    text = "",
                    checked = false,
                    sortOrder = targetSortOrder,
                ),
            )
            adapter.requestFocusForItem(newId)
        }
    }

    /** 空項目で Backspace → 削除して直前の項目 (なければ title) にフォーカス。 */
    private fun handleItemBackspaceEmpty(item: ChecklistItem) {
        lifecycleScope.launch {
            val all = repo.getChecklistItems(memoId).sortedBy { it.sortOrder }
            val idx = all.indexOfFirst { it.id == item.id }
            val prevId = if (idx > 0) all[idx - 1].id else null
            repo.deleteChecklistItem(item.id)
            if (prevId != null) {
                adapter.requestFocusForItem(prevId)
            } else {
                // 直前項目なし → タイトルにフォーカスを戻す
                binding.itemList.post {
                    binding.titleEdit.requestFocus()
                    binding.titleEdit.setSelection(binding.titleEdit.text?.length ?: 0)
                }
            }
        }
    }

    private fun renderMemo(m: Memo) {
        if (binding.titleEdit.text.toString() != (m.title ?: "")) {
            binding.titleEdit.setText(m.title ?: "")
        }
        // タイトルブロック (画面端まで広がる FrameLayout) に色を載せる。
        // EditText 自身の背景は @null のまま (XML 定義どおり)。
        val cardColor = MemoColorUtils.resolve(this, m.color)
        binding.titleBlock.setBackgroundColor(cardColor)
        // 各チェックボックスの縁取り / チェック色も同色に追従させる
        adapter.setMemoCardColor(cardColor)
        binding.priorityButton.setImageResource(
            if (m.priority > 0) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
        )
        binding.priorityButton.imageTintList = ColorStateList.valueOf(getColor(R.color.icon_priority))
    }

    private fun commitTitleNow() {
        if (!canEdit) return
        val current = memo ?: return
        val title = binding.titleEdit.text?.toString()?.takeIf { it.isNotEmpty() }
        if (current.title == title) return
        saveImmediate(current.copy(title = title))
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
        commitTitleNow()
    }
}
