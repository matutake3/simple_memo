package jp.simplist.memo.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
class EditChecklistActivity : ThemedActivity() {

    private lateinit var binding: ActivityEditChecklistBinding
    private lateinit var repo: MemoRepository
    private lateinit var adapter: ChecklistItemAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var memoId: Long = 0L
    private var memo: Memo? = null
    private var saveJob: Job? = null
    private var canEdit: Boolean = true
    /** 当 Activity セッションで新規作成された場合 true。空のまま戻ったら DB から消す判定に使う。 */
    private var isNewMemo: Boolean = false
    /** 最新の checklist items キャッシュ (Flow から更新)。discardIfEmpty で参照。 */
    private var currentItems: List<ChecklistItem> = emptyList()
    /** 入力候補テキスト (頻度順)。Activity 起動時に 1 回ロード、以降キャッシュ。 */
    private var allSuggestions: List<String> = emptyList()
    private var suggestionsLoaded: Boolean = false
    /** 保存処理 (insert / update) を直列化して二重 insert を防ぐ。 */
    private val saveMutex = Mutex()
    /** observe を多重起動させないためのジョブ参照。 */
    private var memoObserveJob: Job? = null
    private var itemsObserveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditChecklistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsInsets()

        repo = MemoRepository.get(this)
        canEdit = TrialManager.get().canEditMemos()

        memoId = intent.getLongExtra(EditMemoActivity.EXTRA_MEMO_ID, 0L)
        isNewMemo = memoId == 0L
        val templateId = intent.getLongExtra(EditMemoActivity.EXTRA_TEMPLATE_ID, 0L)
        val initialTagId = if (intent.hasExtra(EditMemoActivity.EXTRA_INITIAL_TAG_ID))
            intent.getLongExtra(EditMemoActivity.EXTRA_INITIAL_TAG_ID, -1L) else null

        wireToolbar()
        wireBottom()
        wireWatchers()
        setupRecycler()

        lifecycleScope.launch {
            if (isNewMemo) {
                // 新規リストはタイトル / 項目が入るまで DB に書かない。
                val (initTitle, items) = if (templateId > 0L) {
                    val tpl = TemplateRepository.get(this@EditChecklistActivity).getById(templateId)
                    val title = tpl?.title?.takeIf { it.isNotBlank() } ?: tpl?.name
                    title to (tpl?.items() ?: emptyList())
                } else (null to emptyList())
                memo = Memo(
                    type = MemoType.CHECKLIST,
                    title = initTitle,
                    color = 0,
                    priority = 0,
                    tagId = initialTagId?.takeIf { it > 0L },
                    isProtected = false,
                )
                renderMemo(memo!!)
                // 項目0件なのでタップ要素を見せる (まだ observe を始めていないので手動で)
                binding.emptyTapZone.visibility = android.view.View.VISIBLE
                // テンプレからの初期内容ありなら即 insert + 項目投入
                if (!initTitle.isNullOrBlank() || items.isNotEmpty()) {
                    ensureMemoInserted()
                    if (memoId != 0L && items.isNotEmpty()) {
                        val seeded = items.mapIndexed { i, t ->
                            ChecklistItem(memoId = memoId, text = t, sortOrder = i)
                        }
                        repo.insertChecklistItems(seeded)
                    }
                }
            } else {
                startObservingMemo()
                startObservingItems()
            }
        }

        binding.emptyTapZone.setOnClickListener {
            if (!canEdit) return@setOnClickListener
            lifecycleScope.launch {
                ensureMemoInserted()
                if (memoId == 0L) return@launch
                val newId = repo.insertChecklistItem(
                    ChecklistItem(memoId = memoId, text = "", checked = false, sortOrder = 0),
                )
                adapter.requestFocusForItem(newId)
            }
        }

        if (!canEdit) {
            binding.titleEdit.isEnabled = false
        } else if (isNewMemo) {
            // 新規リストはタイトルにフォーカス + IME 自動表示
            binding.titleEdit.requestFocus()
            binding.titleEdit.post {
                val imm = getSystemService(InputMethodManager::class.java)
                imm?.showSoftInput(binding.titleEdit, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        // 入力候補をロード (memoId の有無に関わらず横断履歴から取得)。
        // 多めにキャッシュしておくのは、blacklist や current items で除外しても
        // 上位 10 件を確保するための headroom。
        lifecycleScope.launch {
            allSuggestions = repo.getFrequentChecklistTexts(50)
            suggestionsLoaded = true
            bindSuggestions()
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
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
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

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val item = adapter.itemAt(pos) ?: return
                lifecycleScope.launch { repo.deleteChecklistItem(item.id) }
                // 誤スワイプ救済: 5 秒間「元に戻す」で復元可能
                Snackbar.make(binding.root, "項目を削除しました", Snackbar.LENGTH_LONG)
                    .setAction("元に戻す") {
                        lifecycleScope.launch {
                            // 同一 sortOrder で再 insert (id は新規採番)
                            repo.insertChecklistItem(item.copy(id = 0L))
                        }
                    }
                    .show()
            }

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
        binding.protectButton.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            memo?.let { saveImmediate(it.copy(isProtected = !it.isProtected)) }
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
            ensureMemoInserted()
            if (memoId == 0L) return@launch
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

    private fun startObservingMemo() {
        if (memoObserveJob?.isActive == true) return
        memoObserveJob = lifecycleScope.launch {
            repo.observeMemo(memoId).collect {
                if (it == null) { finish(); return@collect }
                memo = it; renderMemo(it)
            }
        }
    }

    private fun startObservingItems() {
        if (itemsObserveJob?.isActive == true) return
        itemsObserveJob = lifecycleScope.launch {
            repo.observeChecklistItems(memoId).collect { items ->
                currentItems = items
                adapter.submit(items)
                binding.emptyTapZone.visibility =
                    if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                bindSuggestions()
            }
        }
    }

    /**
     * 入力候補チップを描画。allSuggestions から、現在のリストに既に存在するテキスト + 除外リストを
     * 弾いてチップ化する。候補なしなら container を非表示。
     */
    private fun bindSuggestions() {
        val settings = jp.simplist.memo.data.AppSettings.get(this)
        if (!suggestionsLoaded || !canEdit || !settings.suggestionEnabled) {
            binding.suggestionContainer.visibility = android.view.View.GONE
            return
        }
        val existing = currentItems.map { it.text }.toSet()
        val blacklist = settings.suggestionBlacklist
        val limit = settings.suggestionMaxCount
        val candidates = allSuggestions.filter {
            it.isNotBlank() && it !in existing && it !in blacklist
        }.take(limit)
        if (candidates.isEmpty()) {
            binding.suggestionContainer.visibility = android.view.View.GONE
            return
        }
        binding.suggestionContainer.visibility = android.view.View.VISIBLE
        binding.suggestionRow.removeAllViews()

        val ctx = this
        val density = resources.displayMetrics.density
        val padH = (12f * density).toInt()
        val padV = (4f * density).toInt()
        val height = (32f * density).toInt()

        // 背景 (チップ) は theme attr 経由 (標準=ピル / スタイリッシュ=直角)
        val tv = android.util.TypedValue()
        theme.resolveAttribute(R.attr.bgChipFilterUnselected, tv, true)
        val bgRes = tv.resourceId

        for (text in candidates) {
            val chip = android.widget.TextView(ctx).apply {
                this.text = text
                setTextColor(getColor(R.color.ink_secondary))
                textSize = 13f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.inter_regular)
                gravity = android.view.Gravity.CENTER
                setPadding(padH, padV, padH, padV)
                if (bgRes != 0) setBackgroundResource(bgRes)
                isClickable = true
                isFocusable = true
                setOnClickListener { onSuggestionTapped(text) }
                setOnLongClickListener { onSuggestionLongPressed(text); true }
            }
            // ChipGroup が chipSpacingHorizontal/Vertical で勝手に間隔を取るので margin 不要。
            val params = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                height,
            )
            binding.suggestionRow.addView(chip, params)
        }
    }

    /** 候補チップ長押し: 確認ダイアログ → blacklist に追加 + 即時非表示。 */
    private fun onSuggestionLongPressed(text: String) {
        AlertDialog.Builder(this)
            .setMessage("「$text」を入力候補から除外しますか？")
            .setPositiveButton(R.string.action_ok) { _, _ ->
                jp.simplist.memo.data.AppSettings.get(this).addToSuggestionBlacklist(text)
                allSuggestions = allSuggestions - text
                bindSuggestions()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** 候補チップタップ時: 末尾に新規項目として追加してフォーカス。 */
    private fun onSuggestionTapped(text: String) {
        if (!ensureCanEdit()) return
        lifecycleScope.launch {
            ensureMemoInserted()
            if (memoId == 0L) return@launch
            val nextOrder = (currentItems.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val newId = repo.insertChecklistItem(
                ChecklistItem(memoId = memoId, text = text, checked = false, sortOrder = nextOrder),
            )
            adapter.requestFocusForItem(newId)
        }
    }

    /**
     * memoId が 0 なら memo を実 DB へ insert し、observe を起動する。
     * 既に insert 済みなら何もしない。saveMutex で直列化済み。
     */
    private suspend fun ensureMemoInserted() {
        saveMutex.withLock {
            if (memoId == 0L) {
                val state = memo ?: return@withLock
                memoId = repo.insertMemo(state)
                startObservingMemo()
                startObservingItems()
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
        // 保護ボタン: ロック開閉アイコンで状態表示。
        binding.protectButton.setImageResource(
            if (m.isProtected) R.drawable.ic_lock else R.drawable.ic_lock_open,
        )
        binding.protectButton.imageTintList =
            ColorStateList.valueOf(getColor(R.color.icon_protect))
        binding.protectButton.contentDescription = getString(
            if (m.isProtected) R.string.action_unprotect else R.string.action_protect,
        )
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
        // まだ insert されていない時点では Flow からの再描画が来ないので手動で render。
        if (memoId == 0L) renderMemo(updated)
        lifecycleScope.launch {
            saveMutex.withLock {
                val state = memo ?: return@withLock
                if (memoId == 0L) {
                    // タイトルが空のままなら DB に書かない (項目追加時は ensureMemoInserted 経由で別途 insert)。
                    if (state.title.isNullOrBlank()) return@withLock
                    memoId = repo.insertMemo(state)
                    startObservingMemo()
                    startObservingItems()
                } else {
                    repo.updateMemo(state)
                }
            }
        }
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
        if (isFinishing && discardIfEmpty()) return
        commitTitleNow()
    }

    /**
     * 新規作成された空のリストを破棄する。タイトル空 + 全項目テキスト空が条件。
     * CASCADE で checklist_items も同時に消える。
     */
    private fun discardIfEmpty(): Boolean {
        if (!isNewMemo) return false
        val titleBlank = binding.titleEdit.text?.toString()?.isBlank() ?: true
        if (!titleBlank) return false
        if (currentItems.any { it.text.isNotBlank() }) return false
        saveJob?.cancel()
        val id = memoId
        if (id != 0L) {
            lifecycleScope.launch { repo.deletePermanently(id) }
        }
        return true
    }
}
