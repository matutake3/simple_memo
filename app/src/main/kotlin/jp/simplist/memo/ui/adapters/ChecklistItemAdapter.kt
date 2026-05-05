package jp.simplist.memo.ui.adapters

import android.content.Context
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.data.ChecklistItem
import jp.simplist.memo.databinding.ItemChecklistRowBinding

/**
 * チェックリスト項目アダプタ。
 *
 * IME / フォーカス保持の鍵 (DESIGN_SPEC §7-C):
 *  - 文字入力中の Flow 再 emit で notifyDataSetChanged を呼ぶと EditText が再描画されて
 *    フォーカスと IME コンポーズ状態が失われる (= 1 文字ごとにタイトルへ飛んでひらがなしか
 *    打てない不具合)。本アダプタは「構造的に同じ」場合は notify を全くしない。
 *  - 構造変更 (追加 / 削除 / チェック切替 / 並び替え) の時だけ DiffUtil で差分通知。
 *
 * Enter / Backspace 仕様 (ユーザー要望):
 *  - Enter (項目): 直下に新規項目を挿入し、フォーカス移動 → onEnterPressed
 *  - 空項目で Backspace: その項目を削除し、前の項目 (なければ title) にフォーカス
 *    → onBackspaceOnEmpty
 *
 * フォーカス制御:
 *  - requestFocusForItem(id) を呼ぶと、その id を持つ VH に EditText フォーカス + IME 表示。
 *    対象 VH がまだレイアウトされていない場合は pendingFocusId として保存し、
 *    onBindViewHolder の中で適用する。
 */
class ChecklistItemAdapter(
    private val onChecked: (ChecklistItem, Boolean) -> Unit,
    private val onTextChanged: (ChecklistItem, String) -> Unit,
    private val onEnterPressed: (ChecklistItem) -> Unit,
    private val onBackspaceOnEmpty: (ChecklistItem) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit,
    private val canEdit: Boolean,
) : RecyclerView.Adapter<ChecklistItemAdapter.VH>() {

    private val workingList = mutableListOf<ChecklistItem>()
    private var attachedRecyclerView: RecyclerView? = null
    private var pendingFocusId: Long? = null
    /** メモ色に追従させるためのカード色。setMemoColor() で更新。 */
    private var memoCardColor: Int = 0xFFF4EEE3.toInt() // paper のデフォルト

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        attachedRecyclerView = rv
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        attachedRecyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChecklistRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, this)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = workingList[position]
        holder.bind(item, canEdit, memoCardColor, onChecked, onTextChanged, onEnterPressed, onBackspaceOnEmpty, onDragStart)
        if (pendingFocusId == item.id) {
            pendingFocusId = null
            holder.requestFocusOnEdit()
        }
    }

    /** メモのカラーが変わった時に呼ぶ。チェックボックスの tint がメモ色に追従する。 */
    fun setMemoCardColor(@androidx.annotation.ColorInt color: Int) {
        if (memoCardColor == color) return
        memoCardColor = color
        notifyItemRangeChanged(0, itemCount)
    }

    override fun getItemCount(): Int = workingList.size

    fun submit(items: List<ChecklistItem>) {
        // チェック済みは下段に並べる
        val sorted = items.sortedWith(compareBy({ it.checked }, { it.sortOrder }))
        val old = workingList.toList()

        // 構造的に同じ (id・checked が同位置で一致 / 件数も同じ) なら、テキスト編集だけが
        // 起きたケース → notify せずに workingList のみ入れ替え。
        val structurallyEqual = old.size == sorted.size &&
            old.indices.all { i ->
                old[i].id == sorted[i].id && old[i].checked == sorted[i].checked
            }

        workingList.clear()
        workingList.addAll(sorted)

        if (structurallyEqual) {
            tryApplyPendingFocus()
            return
        }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = sorted.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                old[oldPos].id == sorted[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = old[oldPos]; val b = sorted[newPos]
                return a.id == b.id && a.checked == b.checked && a.sortOrder == b.sortOrder
            }
        })
        diff.dispatchUpdatesTo(this)
        // dispatchUpdatesTo の onBindViewHolder が走るので pendingFocus はそこで適用される。
        // 適用されなかった場合 (描画タイミングのズレ) のフォールバック:
        tryApplyPendingFocus()
    }

    fun move(from: Int, to: Int) {
        val item = workingList.removeAt(from)
        workingList.add(to, item)
        notifyItemMoved(from, to)
    }

    fun currentIds(): List<Long> = workingList.map { it.id }

    fun findPosition(id: Long): Int = workingList.indexOfFirst { it.id == id }

    /**
     * 指定 id の項目に EditText フォーカスを移す。VH がまだ描画されていない場合は
     * pendingFocusId として保存し、次回 onBindViewHolder で適用する。
     */
    fun requestFocusForItem(id: Long) {
        pendingFocusId = id
        tryApplyPendingFocus()
    }

    private fun tryApplyPendingFocus() {
        val id = pendingFocusId ?: return
        val rv = attachedRecyclerView ?: return
        val pos = findPosition(id)
        if (pos < 0) return  // まだ workingList に居ない (Flow emit 待ち)
        rv.post {
            val cur = pendingFocusId ?: return@post
            val curPos = findPosition(cur)
            if (curPos < 0) return@post

            // 対象が完全に画面内かチェック。画面外 / 部分的に隠れているなら自動スクロール。
            val lm = rv.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            if (lm != null) {
                val firstFully = lm.findFirstCompletelyVisibleItemPosition()
                val lastFully = lm.findLastCompletelyVisibleItemPosition()
                val fullyVisible = firstFully != RecyclerView.NO_POSITION &&
                    curPos in firstFully..lastFully
                if (!fullyVisible) {
                    rv.smoothScrollToPosition(curPos)
                    // スクロール完了 + 再レイアウト後に再試行
                    rv.postDelayed({ tryApplyPendingFocus() }, 200)
                    return@post
                }
            }

            val vh = rv.findViewHolderForAdapterPosition(curPos) as? VH
            if (vh != null) {
                vh.requestFocusOnEdit()
                pendingFocusId = null
            } else {
                rv.postDelayed({ tryApplyPendingFocus() }, 50)
            }
        }
    }

    class VH(
        private val binding: ItemChecklistRowBinding,
        private val parent: ChecklistItemAdapter,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var current: ChecklistItem? = null
        private var watcher: TextWatcher? = null

        @Suppress("ClickableViewAccessibility")
        fun bind(
            item: ChecklistItem,
            canEdit: Boolean,
            memoCardColor: Int,
            onChecked: (ChecklistItem, Boolean) -> Unit,
            onTextChanged: (ChecklistItem, String) -> Unit,
            onEnterPressed: (ChecklistItem) -> Unit,
            onBackspaceOnEmpty: (ChecklistItem) -> Unit,
            onDragStart: (RecyclerView.ViewHolder) -> Unit,
        ) {
            current = item

            // 既存 watcher を外してから text を更新 (誤発火防止)
            watcher?.let { binding.itemEdit.removeTextChangedListener(it) }
            watcher = null

            // text セットは「現在の表示値と異なる場合のみ」。ユーザー入力中の IME / フォーカスを守る。
            val currentText = binding.itemEdit.text?.toString().orEmpty()
            if (currentText != item.text) {
                binding.itemEdit.setText(item.text)
                if (binding.itemEdit.hasFocus()) {
                    binding.itemEdit.setSelection(binding.itemEdit.text?.length ?: 0)
                }
            }

            // チェックボックスの縁取り / チェック色をメモのカードカラーに追従させる。
            // 派生色 (HSL 明度 0.30) で十分なコントラストを確保。
            val derived = jp.simplist.memo.util.MemoColorUtils.darkenForOnSurface(memoCardColor)
            val checkboxTint = android.content.res.ColorStateList.valueOf(derived)
            binding.checkbox.buttonTintList = checkboxTint

            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = item.checked
            binding.checkbox.setOnCheckedChangeListener { _, c ->
                val cur = current ?: return@setOnCheckedChangeListener
                onChecked(cur, c)
            }
            applyCheckedStyle(item.checked)

            val w = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val cur = current ?: return
                    val text = s?.toString().orEmpty()
                    if (text == cur.text) return
                    onTextChanged(cur, text)
                }
            }
            binding.itemEdit.addTextChangedListener(w)
            watcher = w

            // Enter で次の項目を追加
            binding.itemEdit.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    val cur = current ?: return@setOnEditorActionListener false
                    if (!canEdit) return@setOnEditorActionListener false
                    onEnterPressed(cur)
                    true
                } else false
            }

            // 空項目で Backspace → 削除 + 前へフォーカス
            binding.itemEdit.onBackspaceWhenEmpty = {
                val cur = current
                if (cur != null && canEdit) {
                    onBackspaceOnEmpty(cur)
                    true
                } else false
            }

            // ドラッグハンドル
            binding.dragHandle.setOnTouchListener { _, ev ->
                if (canEdit && ev.action == MotionEvent.ACTION_DOWN) {
                    onDragStart(this); true
                } else false
            }

            binding.itemEdit.isEnabled = canEdit
            binding.checkbox.isEnabled = canEdit
            binding.dragHandle.visibility = if (canEdit) View.VISIBLE else View.GONE
        }

        fun requestFocusOnEdit() {
            binding.itemEdit.requestFocus()
            binding.itemEdit.setSelection(binding.itemEdit.text?.length ?: 0)
            val imm = binding.root.context
                .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.itemEdit, InputMethodManager.SHOW_IMPLICIT)
            // 親 RecyclerView に対して「自分の矩形を画面内に表示してくれ」と要求。
            // IME による縮小で部分的に見切れているケース等の最終保険。
            binding.itemEdit.post {
                val r = android.graphics.Rect(0, 0, binding.itemEdit.width, binding.itemEdit.height)
                binding.itemEdit.requestRectangleOnScreen(r)
            }
        }

        private fun applyCheckedStyle(checked: Boolean) {
            if (checked) {
                binding.itemEdit.paintFlags = binding.itemEdit.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.itemEdit.alpha = 0.6f
            } else {
                binding.itemEdit.paintFlags = binding.itemEdit.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.itemEdit.alpha = 1.0f
            }
        }
    }
}
