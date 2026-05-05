package jp.simplist.memo.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.R
import jp.simplist.memo.data.ChecklistItem
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.data.Tag
import jp.simplist.memo.databinding.ItemMemoCardBinding
import jp.simplist.memo.util.MemoColorUtils

/**
 * メイン画面のメモ一覧アダプタ。
 *
 * - sortMode 中はドラッグハンドル表示 + 並び替え操作。
 * - 各カードの色 / アイコン色 / ★色は MemoColorUtils で派生計算。
 *
 * preview-only で本文の冒頭 1 行を表示するために、initialItemPreview map を任意で渡せる。
 * (Phase 2 で各メモの最初の項目を取得して埋める。Phase 1 では title 優先・本文 fallback。)
 */
class MemoListAdapter(
    private val onMemoClick: (Memo) -> Unit,
    private val onMemoLongClick: (Memo, View) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit,
) : ListAdapter<Memo, MemoListAdapter.VH>(DIFF) {

    private var sortMode: Boolean = false
    private var tagsById: Map<Long, Tag> = emptyMap()
    private val itemsCache: MutableList<Memo> = mutableListOf()
    /** 複数選択モード中かつ選択中の id 集合。MainActivity から submit 時に渡る。 */
    private var selectedIds: Set<Long> = emptySet()
    private var multiSelectMode: Boolean = false

    /** 並び替えモード中の挙動: 一時的にローカル list を入れ替えてレンダ。 */
    private var workingList: MutableList<Memo>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMemoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onDragStart)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val memo = currentDisplayedAt(position)
        val isSelected = multiSelectMode && memo.id in selectedIds
        holder.bind(memo, sortMode, multiSelectMode, isSelected, onMemoClick, onMemoLongClick)
    }

    override fun getItemCount(): Int = workingList?.size ?: super.getItemCount()

    fun submit(memos: List<Memo>, tags: List<Tag>, sortMode: Boolean) {
        this.sortMode = sortMode
        this.tagsById = tags.associateBy { it.id }
        itemsCache.clear()
        itemsCache.addAll(memos)
        if (sortMode) {
            workingList = itemsCache.toMutableList()
            notifyDataSetChanged()
        } else {
            workingList = null
            submitList(itemsCache.toList())
        }
    }

    /** 複数選択モードの状態を更新。選択 id が変わったら再描画。 */
    fun updateMultiSelect(active: Boolean, selectedIds: Set<Long>) {
        val changed = active != multiSelectMode || selectedIds != this.selectedIds
        multiSelectMode = active
        this.selectedIds = selectedIds
        if (changed) notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        val list = workingList ?: return
        val item = list.removeAt(from)
        list.add(to, item)
        notifyItemMoved(from, to)
    }

    fun currentOrderedIds(): List<Long> = (workingList ?: itemsCache).map { it.id }

    fun currentVisibleIds(): List<Long> = itemsCache.map { it.id }

    private fun currentDisplayedAt(position: Int): Memo =
        workingList?.get(position) ?: getItem(position)

    class VH(
        private val binding: ItemMemoCardBinding,
        private val onDragStart: (RecyclerView.ViewHolder) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        @Suppress("ClickableViewAccessibility")
        fun bind(
            memo: Memo,
            sortMode: Boolean,
            multiSelectMode: Boolean,
            isSelected: Boolean,
            onClick: (Memo) -> Unit,
            onLongClick: (Memo, View) -> Unit,
        ) {
            val ctx = binding.root.context
            val cardColor = MemoColorUtils.resolve(ctx, memo.color)
            val derived = MemoColorUtils.darkenForOnSurface(cardColor)

            binding.root.backgroundTintList = ColorStateList.valueOf(cardColor)
            // 複数選択中の見た目: 選択は ✓ アイコン、未選択はカード alpha を下げてフェード
            val typeIconRes = if (memo.type == MemoType.TEXT) R.drawable.ic_note else R.drawable.ic_check_square
            if (multiSelectMode && isSelected) {
                binding.typeIcon.setImageResource(R.drawable.ic_check)
                binding.root.alpha = 1f
            } else {
                binding.typeIcon.setImageResource(typeIconRes)
                binding.root.alpha = if (multiSelectMode) 0.55f else 1f
            }
            binding.typeIcon.imageTintList = ColorStateList.valueOf(derived)
            binding.mainText.text = primaryDisplayText(memo)
            binding.mainText.setTextColor(ctx.getColor(R.color.ink))
            // 保護中マーク
            if (memo.isProtected) {
                binding.lockIcon.visibility = View.VISIBLE
                binding.lockIcon.imageTintList = ColorStateList.valueOf(derived)
            } else {
                binding.lockIcon.visibility = View.GONE
            }

            // priority stars
            binding.priorityStars.removeAllViews()
            if (memo.priority > 0) {
                repeat(memo.priority) {
                    val star = ImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            (18f * ctx.resources.displayMetrics.density).toInt(),
                            (18f * ctx.resources.displayMetrics.density).toInt(),
                        )
                        setImageResource(R.drawable.ic_star_filled)
                        imageTintList = ColorStateList.valueOf(derived)
                        contentDescription = null
                    }
                    binding.priorityStars.addView(star)
                }
                binding.priorityStars.visibility = View.VISIBLE
            } else {
                binding.priorityStars.visibility = View.GONE
            }

            // drag handle
            if (sortMode) {
                binding.dragHandle.visibility = View.VISIBLE
                binding.dragHandle.imageTintList = ColorStateList.valueOf(derived)
                binding.dragHandle.setOnTouchListener { _, ev ->
                    if (ev.action == MotionEvent.ACTION_DOWN) {
                        onDragStart(this)
                        true
                    } else false
                }
                binding.root.setOnClickListener(null)
                binding.root.setOnLongClickListener(null)
            } else {
                binding.dragHandle.visibility = View.GONE
                binding.dragHandle.setOnTouchListener(null)
                binding.root.setOnClickListener { onClick(memo) }
                binding.root.setOnLongClickListener { onLongClick(memo, it); true }
            }
        }

        /**
         * カードに表示するメイン文字列。
         *  1. title が空でなければそれ
         *  2. TEXT のとき body の先頭行
         *  3. CHECKLIST のとき (Phase 1 では body 同様の placeholder。
         *     後段で MainActivity 側から最初の項目を流し込む拡張可)
         */
        private fun primaryDisplayText(memo: Memo): String {
            val title = memo.title?.trim().orEmpty()
            if (title.isNotEmpty()) return title
            return when (memo.type) {
                MemoType.TEXT -> memo.body?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
                    ?: "(無題のメモ)"
                MemoType.CHECKLIST -> "(無題のリスト)"
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Memo>() {
            override fun areItemsTheSame(a: Memo, b: Memo): Boolean = a.id == b.id
            override fun areContentsTheSame(a: Memo, b: Memo): Boolean = a == b
        }
    }
}
