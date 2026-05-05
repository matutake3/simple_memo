package jp.simplist.memo.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.R
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.data.ListSortMode
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.data.Tag
import jp.simplist.memo.data.TagRepository
import jp.simplist.memo.databinding.ActivityMainBinding
import jp.simplist.memo.privacy.PrivacyLockController
import jp.simplist.memo.search.SearchActivity
import jp.simplist.memo.ui.dialogs.TagPickerBottomSheet
import jp.simplist.memo.ui.dialogs.TemplatePickerBottomSheet
import jp.simplist.memo.ui.adapters.MemoListAdapter
import jp.simplist.memo.ui.adapters.TagFilterRowBinder
import jp.simplist.memo.ui.adapters.TagFilterSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Main screen — メモ一覧 + タグフィルタ + FAB ペア + 並び替えモード。
 * SPEC §4.1 / DESIGN_SPEC §7-A 参照。
 */
class MainActivity : ThemedActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var memoAdapter: MemoListAdapter
    private lateinit var memoRepo: MemoRepository
    private lateinit var tagRepo: TagRepository
    private lateinit var settings: AppSettings
    private lateinit var tagFilterBinder: TagFilterRowBinder
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var currentSelection: TagFilterSelection = TagFilterSelection.All
    private var savedSelectionBeforeSort: TagFilterSelection? = null
    /** ドラッグ並び替え UI が有効な状態 (旧 sortMode)。listSortMode (永続) とは別概念。 */
    private var isReordering: Boolean = false
    /** 複数選択モード (長押しから入る)。 */
    private var isMultiSelectMode: Boolean = false
    private val selectedMemoIds: MutableSet<Long> = mutableSetOf()
    private var allMemos: List<Memo> = emptyList()
    private var allTags: List<Tag> = emptyList()
    /** 並び替え基準を流す StateFlow。値変更 → flatMapLatest で観測対象 Flow を切替。 */
    private lateinit var sortModeFlow: MutableStateFlow<ListSortMode>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        memoRepo = MemoRepository.get(this)
        tagRepo = TagRepository.get(this)
        settings = AppSettings.get(this)
        sortModeFlow = MutableStateFlow(settings.listSortMode)

        // プライバシーロックが必要なら、最初のフレームから目隠しオーバーレイを出す。
        // setContentView 直後に判定して Visibility を確定させることで「コンテンツが一瞬見える」を防ぐ。
        if (settings.privacyLockEnabled && !PrivacyLockController.isUnlocked()) {
            binding.lockOverlay.visibility = View.VISIBLE
        }

        setupRecycler()
        setupToolbar()
        setupMultiSelectToolbar()
        setupFabs()
        setupBackHandler()

        observeData()

        // 初回起動時のオンボーディング
        if (!settings.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 認証必要なら目隠しオーバーレイを出してから guard を呼ぶ
        // (背後にメモ一覧が透けて見えないようにする)
        if (settings.privacyLockEnabled && !PrivacyLockController.isUnlocked()) {
            binding.lockOverlay.visibility = View.VISIBLE
        }
        PrivacyLockController.guard(this) {
            binding.lockOverlay.visibility = View.GONE
        }
        // タグ先頭文字オプションは設定画面から戻ったときに反映
        memoAdapter.setShowTagInitial(settings.showTagInitialOnCard)
    }

    private fun setupRecycler() {
        memoAdapter = MemoListAdapter(
            onMemoClick = ::handleMemoClick,
            onMemoLongClick = ::handleMemoLongClick,
            onDragStart = { vh -> itemTouchHelper.startDrag(vh) },
        )
        binding.memoList.layoutManager = LinearLayoutManager(this)
        binding.memoList.adapter = memoAdapter

        // ItemTouchHelper for drag reorder (isReordering 中のみ有効)
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (!isReordering) return false
                memoAdapter.moveItem(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                if (isReordering) commitManualOrder()
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.memoList)
    }

    private fun setupToolbar() {
        binding.menuButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.searchButton.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        // ⇅ アイコン: 並び替え基準ダイアログを表示
        binding.sortButton.setOnClickListener { showSortDialog() }
        // 「完了」: ドラッグ並び替えモードを抜ける
        binding.sortDoneButton.setOnClickListener { exitReorderMode() }
        binding.moreButton.setOnClickListener { showMoreMenu(it) }

        tagFilterBinder = TagFilterRowBinder(
            container = binding.tagFilterRow,
            onSelected = { sel ->
                if (sel is TagFilterSelection.Add) {
                    showCreateTagSheet()
                } else {
                    currentSelection = sel
                    // タグチップの選択状態を即時反映する (Flow 再 emit を待たずに再描画)
                    tagFilterBinder.bind(allTags, currentSelection)
                    refreshList()
                }
            },
        )
    }

    private fun setupFabs() {
        binding.fabMemo.setOnClickListener { startCreate(MemoType.TEXT) }
        binding.fabList.setOnClickListener { startCreate(MemoType.CHECKLIST) }
        binding.fabMemo.setOnLongClickListener { showTemplatePicker(MemoType.TEXT); true }
        binding.fabList.setOnLongClickListener { showTemplatePicker(MemoType.CHECKLIST); true }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isMultiSelectMode -> exitMultiSelectMode()
                    isReordering -> exitReorderMode()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun setupMultiSelectToolbar() {
        binding.msCloseButton.setOnClickListener { exitMultiSelectMode() }
        binding.msDeleteButton.setOnClickListener { bulkDelete() }
        binding.msTagButton.setOnClickListener { bulkChangeTag() }
        binding.msProtectButton.setOnClickListener { bulkToggleProtect() }
        binding.msDuplicateButton.setOnClickListener { bulkDuplicate() }
        binding.msSelectAllButton.setOnClickListener { selectAllVisible() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    sortModeFlow.flatMapLatest { mode -> memoRepo.observeActive(mode) },
                    tagRepo.observeAll(),
                ) { memos, tags -> memos to tags }
                    .collect { (memos, tags) ->
                        allMemos = memos
                        allTags = tags
                        tagFilterBinder.bind(tags, currentSelection)
                        refreshList()
                    }
            }
        }
    }

    private fun refreshList() {
        val filtered = when (val sel = currentSelection) {
            TagFilterSelection.All -> allMemos
            TagFilterSelection.Untagged -> allMemos.filter { it.tagId == null }
            is TagFilterSelection.Specific -> allMemos.filter { it.tagId == sel.tagId }
            TagFilterSelection.Add -> allMemos
        }
        binding.emptyState.visibility =
            if (filtered.isEmpty() && !isReordering && !isMultiSelectMode) View.VISIBLE else View.GONE
        memoAdapter.submit(filtered, allTags, isReordering)
        memoAdapter.updateMultiSelect(isMultiSelectMode, selectedMemoIds)
        // 削除済みなどで存在しない id を選択集合から自動除去
        if (isMultiSelectMode) {
            val visibleIds = filtered.map { it.id }.toSet()
            val pruned = selectedMemoIds.filter { it in visibleIds }
            if (pruned.size != selectedMemoIds.size) {
                selectedMemoIds.clear()
                selectedMemoIds.addAll(pruned)
                if (selectedMemoIds.isEmpty()) exitMultiSelectMode() else updateMultiSelectCount()
            }
        }
    }

    // === Sort & Reorder ===

    /** ⇅ アイコンで開く並び替え基準ダイアログ。 */
    private fun showSortDialog() {
        val labels = arrayOf(
            getString(R.string.sort_updated),
            getString(R.string.sort_created),
            getString(R.string.sort_title),
            getString(R.string.sort_priority),
        )
        val modes = arrayOf(
            ListSortMode.UPDATED,
            ListSortMode.CREATED,
            ListSortMode.TITLE,
            ListSortMode.PRIORITY,
        )
        // CUSTOM のときはどれもチェックされない (-1)
        val currentIdx = modes.indexOf(settings.listSortMode)
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_dialog_title)
            .setSingleChoiceItems(labels, currentIdx) { dialog, which ->
                applySortMode(modes[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun applySortMode(mode: ListSortMode) {
        settings.listSortMode = mode
        sortModeFlow.value = mode  // flatMapLatest が即座に観測対象を切替
    }

    /** ⋮「カスタム並び替え」: ドラッグ並び替え UI に入る。listSortMode = CUSTOM に切り替え。 */
    private fun enterReorderMode() {
        if (settings.listSortMode != ListSortMode.CUSTOM) {
            applySortMode(ListSortMode.CUSTOM)
        }
        isReordering = true
        savedSelectionBeforeSort = currentSelection
        currentSelection = TagFilterSelection.All
        binding.sortButton.visibility = View.GONE
        binding.searchButton.visibility = View.GONE
        binding.moreButton.visibility = View.GONE
        binding.sortDoneButton.visibility = View.VISIBLE
        binding.tagFilterScroll.visibility = View.GONE
        binding.fabContainer.visibility = View.GONE
        refreshList()
    }

    private fun exitReorderMode() {
        isReordering = false
        savedSelectionBeforeSort?.let { currentSelection = it }
        savedSelectionBeforeSort = null
        binding.sortButton.visibility = View.VISIBLE
        binding.searchButton.visibility = View.VISIBLE
        binding.moreButton.visibility = View.VISIBLE
        binding.sortDoneButton.visibility = View.GONE
        binding.tagFilterScroll.visibility = View.VISIBLE
        binding.fabContainer.visibility = View.VISIBLE
        tagFilterBinder.bind(allTags, currentSelection)
        refreshList()
    }

    private fun commitManualOrder() {
        val orderedIds = memoAdapter.currentOrderedIds()
        lifecycleScope.launch { memoRepo.applyManualOrder(orderedIds) }
    }

    // === Menus ===

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.main_more, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_custom_sort -> enterReorderMode()
                    R.id.action_show_trash -> startActivity(Intent(this@MainActivity, TrashActivity::class.java))
                }
                true
            }
        }.show()
    }

    // 旧長押しコンテキストメニュー (showMemoContextMenu / confirmDeleteMemo / duplicateMemo /
    //   showTagSheetForMemo / toggleProtect) は廃止。長押し → 複数選択モード経由のバルク操作に統合。

    private fun showCreateTagSheet() {
        TagEditDialog.showCreate(this) {
            currentSelection = TagFilterSelection.All
            tagFilterBinder.bind(allTags, currentSelection)
        }
    }

    private fun showTemplatePicker(type: MemoType) {
        TemplatePickerBottomSheet.show(supportFragmentManager, type) { templateId ->
            startCreate(type, templateId)
        }
    }

    // === Open / Create ===

    private fun openMemo(memo: Memo) {
        if (isReordering) return
        val intent = when (memo.type) {
            MemoType.TEXT -> Intent(this, EditMemoActivity::class.java)
            MemoType.CHECKLIST -> Intent(this, EditChecklistActivity::class.java)
        }.apply {
            putExtra(EditMemoActivity.EXTRA_MEMO_ID, memo.id)
        }
        startActivity(intent)
    }

    /** タップ時のディスパッチ: 複数選択中なら toggle、通常なら開く。 */
    private fun handleMemoClick(memo: Memo) {
        if (isReordering) return
        if (isMultiSelectMode) {
            toggleSelection(memo.id)
        } else {
            openMemo(memo)
        }
    }

    /** 長押し: 通常モードなら複数選択を開始してその id を選択状態に。 */
    private fun handleMemoLongClick(memo: Memo, anchor: View) {
        if (isReordering) return
        if (!isMultiSelectMode) {
            enterMultiSelectMode(memo.id)
        } else {
            toggleSelection(memo.id)
        }
    }

    // === Multi-select ===

    private fun enterMultiSelectMode(initialId: Long) {
        isMultiSelectMode = true
        selectedMemoIds.clear()
        selectedMemoIds.add(initialId)
        binding.toolbar.visibility = View.GONE
        binding.multiSelectToolbar.visibility = View.VISIBLE
        binding.multiSelectFooter.visibility = View.VISIBLE
        binding.tagFilterScroll.visibility = View.GONE
        binding.fabContainer.visibility = View.GONE
        updateMultiSelectCount()
        refreshProtectIcon()
        refreshList()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedMemoIds.clear()
        binding.toolbar.visibility = View.VISIBLE
        binding.multiSelectToolbar.visibility = View.GONE
        binding.multiSelectFooter.visibility = View.GONE
        binding.tagFilterScroll.visibility = View.VISIBLE
        binding.fabContainer.visibility = View.VISIBLE
        refreshList()
    }

    private fun toggleSelection(id: Long) {
        if (id in selectedMemoIds) selectedMemoIds.remove(id) else selectedMemoIds.add(id)
        if (selectedMemoIds.isEmpty()) {
            exitMultiSelectMode()
        } else {
            updateMultiSelectCount()
            refreshProtectIcon()
            memoAdapter.updateMultiSelect(true, selectedMemoIds)
        }
    }

    private fun updateMultiSelectCount() {
        binding.msCountText.text = getString(R.string.multi_count_format, selectedMemoIds.size)
    }

    /** 選択中メモが「すべて保護中」なら鍵を閉に、それ以外は開に。 */
    private fun refreshProtectIcon() {
        val selected = allMemos.filter { it.id in selectedMemoIds }
        val allProtected = selected.isNotEmpty() && selected.all { it.isProtected }
        binding.msProtectButton.setImageResource(
            if (allProtected) R.drawable.ic_lock else R.drawable.ic_lock_open,
        )
    }

    private fun selectAllVisible() {
        val ids = memoAdapter.currentVisibleIds()
        selectedMemoIds.clear()
        selectedMemoIds.addAll(ids)
        if (selectedMemoIds.isEmpty()) {
            exitMultiSelectMode()
        } else {
            updateMultiSelectCount()
            refreshProtectIcon()
            memoAdapter.updateMultiSelect(true, selectedMemoIds)
        }
    }

    private fun bulkDelete() {
        val selected = allMemos.filter { it.id in selectedMemoIds }
        if (selected.isEmpty()) return
        val protectedIds = selected.filter { it.isProtected }.map { it.id }.toSet()
        val deletableIds = selected.map { it.id }.filterNot { it in protectedIds }

        if (deletableIds.isEmpty()) {
            // 全件保護中 → ブロック
            AlertDialog.Builder(this)
                .setMessage(R.string.multi_delete_all_protected)
                .setPositiveButton(R.string.action_ok, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.multi_delete_confirm_format, deletableIds.size))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    for (id in deletableIds) memoRepo.moveToTrash(id)
                    if (protectedIds.isNotEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.multi_delete_skipped_format, deletableIds.size, protectedIds.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    exitMultiSelectMode()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun bulkChangeTag() {
        TagPickerBottomSheet.show(supportFragmentManager, currentTagId = null) { newTagId ->
            lifecycleScope.launch {
                val ids = selectedMemoIds.toList()
                for (id in ids) {
                    val m = memoRepo.getMemo(id) ?: continue
                    memoRepo.updateMemo(m.copy(tagId = newTagId))
                }
                exitMultiSelectMode()
            }
        }
    }

    private fun bulkToggleProtect() {
        val selected = allMemos.filter { it.id in selectedMemoIds }
        if (selected.isEmpty()) return
        val allProtected = selected.all { it.isProtected }
        val newProtected = !allProtected
        lifecycleScope.launch {
            for (m in selected) {
                if (m.isProtected != newProtected) {
                    memoRepo.updateMemo(m.copy(isProtected = newProtected))
                }
            }
            // モードは抜けず、アイコン状態だけ反転
            refreshProtectIcon()
        }
    }

    private fun bulkDuplicate() {
        val selected = allMemos.filter { it.id in selectedMemoIds }
        lifecycleScope.launch {
            for (memo in selected) {
                val newId = memoRepo.insertMemo(
                    memo.copy(
                        id = 0L,
                        sortOrder = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                if (memo.type == MemoType.CHECKLIST) {
                    val items = memoRepo.getChecklistItems(memo.id)
                    memoRepo.replaceChecklistItems(newId, items.map { it.copy(id = 0L, memoId = newId) })
                }
            }
            exitMultiSelectMode()
        }
    }

    private fun startCreate(type: MemoType, templateId: Long? = null) {
        val intent = when (type) {
            MemoType.TEXT -> Intent(this, EditMemoActivity::class.java)
            MemoType.CHECKLIST -> Intent(this, EditChecklistActivity::class.java)
        }
        if (templateId != null) intent.putExtra(EditMemoActivity.EXTRA_TEMPLATE_ID, templateId)
        // フィルタ中のタグがあればそのタグを初期値に
        (currentSelection as? TagFilterSelection.Specific)?.tagId?.let {
            intent.putExtra(EditMemoActivity.EXTRA_INITIAL_TAG_ID, it)
        }
        startActivity(intent)
    }
}
