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
    /** タグ先頭文字ミニチップを表示するか (AppSettings.showTagInitialOnCard より MainActivity が反映)。 */
    private var showTagInitial: Boolean = false

    /** 並び替えモード中の挙動: 一時的にローカル list を入れ替えてレンダ。 */
    private var workingList: MutableList<Memo>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMemoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onDragStart)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val memo = currentDisplayedAt(position)
        val isSelected = multiSelectMode && memo.id in selectedIds
        val tag = memo.tagId?.let { tagsById[it] }
        holder.bind(memo, sortMode, multiSelectMode, isSelected, showTagInitial, tag, onMemoClick, onMemoLongClick)
    }

    fun setShowTagInitial(enabled: Boolean) {
        if (showTagInitial != enabled) {
            showTagInitial = enabled
            notifyDataSetChanged()
        }
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

    /**
     * 複数選択モードの状態を更新。選択 id が変わったら再描画。
     *
     * 重要: 呼び出し側 (MainActivity) は MutableSet をそのまま渡してくる可能性がある。
     * 同じインスタンスを mutate されると `selectedIds != this.selectedIds` が常に false に
     * なり、2 件目以降の選択が画面に反映されないため、ここで toSet() で防御的コピーを取る。
     */
    fun updateMultiSelect(active: Boolean, selectedIds: Set<Long>) {
        val snapshot = selectedIds.toSet()
        val changed = active != multiSelectMode || snapshot != this.selectedIds
        multiSelectMode = active
        this.selectedIds = snapshot
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
            showTagInitial: Boolean,
            tag: Tag?,
            onClick: (Memo) -> Unit,
            onLongClick: (Memo, View) -> Unit,
        ) {
            val ctx = binding.root.context
            val cardColor = MemoColorUtils.resolve(ctx, memo.color)
            val derived = MemoColorUtils.darkenForOnSurface(cardColor)

            // 複数選択モードの視覚表現:
            //   選択中 → 通常色のまま (alpha 1.0)
            //   未選択 → カード全体を alpha 0.4 で薄くフェード
            // 「選んだものをさらに濃くする」とラベル表示のように見えるためやめた。
            binding.root.backgroundTintList = ColorStateList.valueOf(cardColor)
            binding.root.alpha = if (multiSelectMode && !isSelected) 0.4f else 1f
            binding.selectionBadge.visibility = View.GONE
            // type アイコンは常に種別を反映 (チェック付き正方形 or テキストメモ)
            binding.typeIcon.setImageResource(
                if (memo.type == MemoType.TEXT) R.drawable.ic_note else R.drawable.ic_check_square,
            )
            // 色なし (color=0) は HSL 計算が破綻するので、テーマで指定した固定色 (リスト FAB と同色) を使う。
            // 色付きメモは従来通りカード色から派生した濃色。
            val iconTint = if (memo.color == 0) {
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(R.attr.iconTintDefault, tv, true)
                if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
            } else {
                derived
            }
            binding.typeIcon.imageTintList = ColorStateList.valueOf(iconTint)
            binding.mainText.text = primaryDisplayText(memo)
            // textColorPrimary はテーマ追従 (標準=ink、スタイリッシュ=ink_stylish)
            val textColor = run {
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
                if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
            }
            binding.mainText.setTextColor(textColor)
            // タグ先頭文字ミニチップ
            if (showTagInitial && tag != null) {
                val tagFirstChar = tag.name.firstOrNull()?.toString().orEmpty()
                if (tagFirstChar.isNotEmpty()) {
                    binding.tagInitial.visibility = View.VISIBLE
                    binding.tagInitial.text = tagFirstChar
                    val tagColorInt = MemoColorUtils.resolve(ctx, tag.color)
                    // paper は theme attr 経由で取得 (スタイリッシュなら白、標準ならクリーム)
                    val paperColor = run {
                        val tv = android.util.TypedValue()
                        ctx.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
                        tv.data
                    }
                    binding.tagInitial.backgroundTintList = ColorStateList.valueOf(
                        MemoColorUtils.blendForChipBackground(tagColorInt, paperColor),
                    )
                    binding.tagInitial.setTextColor(MemoColorUtils.darkenForChipText(tagColorInt))
                } else {
                    binding.tagInitial.visibility = View.GONE
                }
            } else {
                binding.tagInitial.visibility = View.GONE
            }
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
