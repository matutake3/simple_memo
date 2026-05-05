package jp.simplist.memo.widget

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.R
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.databinding.ItemWidgetMemoPickerBinding
import jp.simplist.memo.util.MemoColorUtils

/** ウィジェット設定 Activity でメモを選ぶための RecyclerView アダプタ。 */
class WidgetMemoPickerAdapter(
    private val onClick: (Memo) -> Unit,
) : ListAdapter<Memo, WidgetMemoPickerAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemWidgetMemoPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemWidgetMemoPickerBinding,
        private val onClick: (Memo) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(memo: Memo) {
            val ctx = b.root.context
            val cardColor = MemoColorUtils.resolve(ctx, memo.color)
            val derived = MemoColorUtils.darkenForOnSurface(cardColor)
            b.root.backgroundTintList = ColorStateList.valueOf(cardColor)
            b.typeIcon.setImageResource(
                if (memo.type == MemoType.TEXT) R.drawable.ic_note else R.drawable.ic_check_square,
            )
            b.typeIcon.imageTintList = ColorStateList.valueOf(derived)
            b.title.text = memo.title?.takeIf { it.isNotBlank() } ?: when (memo.type) {
                MemoType.TEXT -> memo.body?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: "(無題のメモ)"
                MemoType.CHECKLIST -> "(無題のリスト)"
            }
            b.root.setOnClickListener { onClick(memo) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Memo>() {
            override fun areItemsTheSame(a: Memo, b: Memo): Boolean = a.id == b.id
            override fun areContentsTheSame(a: Memo, b: Memo): Boolean = a == b
        }
    }
}
