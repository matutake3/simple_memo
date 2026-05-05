package jp.simplist.memo.search

import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.R
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.databinding.ItemSearchResultBinding
import jp.simplist.memo.util.MemoColorUtils
import jp.simplist.memo.util.TimeFormat

data class SearchHit(
    val memo: Memo,
    val combinedText: String,
    val matchIndex: Int,
    val matchLength: Int,
)

class SearchResultAdapter(
    private val onClick: (SearchHit) -> Unit,
) : ListAdapter<SearchHit, SearchResultAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val binding: ItemSearchResultBinding,
        private val onClick: (SearchHit) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hit: SearchHit) {
            val ctx = binding.root.context
            val memo = hit.memo
            val cardColor = MemoColorUtils.resolve(ctx, memo.color)
            val derived = MemoColorUtils.darkenForOnSurface(cardColor)
            binding.root.backgroundTintList = ColorStateList.valueOf(cardColor)
            binding.typeIcon.setImageResource(
                if (memo.type == MemoType.TEXT) R.drawable.ic_note else R.drawable.ic_check_box,
            )
            binding.typeIcon.imageTintList = ColorStateList.valueOf(derived)
            binding.title.text = memo.title?.takeIf { it.isNotBlank() } ?: when (memo.type) {
                MemoType.TEXT -> "(無題のメモ)"
                MemoType.CHECKLIST -> "(無題のリスト)"
            }
            binding.snippet.text = buildSnippet(hit, derived)
            binding.timestamp.text = TimeFormat.relative(memo.updatedAt)
            binding.root.setOnClickListener { onClick(hit) }
        }

        private fun buildSnippet(hit: SearchHit, highlightColor: Int): CharSequence {
            val combined = hit.combinedText
            val from = (hit.matchIndex - 30).coerceAtLeast(0)
            val to = (hit.matchIndex + hit.matchLength + 30).coerceAtMost(combined.length)
            val raw = combined.substring(from, to).replace('\n', ' ')
            val prefix = if (from > 0) "..." else ""
            val suffix = if (to < combined.length) "..." else ""
            val text = "$prefix$raw$suffix"
            val span = SpannableString(text)
            val matchInSnippet = text.indexOf(combined.substring(hit.matchIndex, hit.matchIndex + hit.matchLength), ignoreCase = true)
            if (matchInSnippet >= 0) {
                span.setSpan(
                    StyleSpan(android.graphics.Typeface.BOLD),
                    matchInSnippet,
                    matchInSnippet + hit.matchLength,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    ForegroundColorSpan(highlightColor),
                    matchInSnippet,
                    matchInSnippet + hit.matchLength,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            return span
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchHit>() {
            override fun areItemsTheSame(a: SearchHit, b: SearchHit): Boolean = a.memo.id == b.memo.id
            override fun areContentsTheSame(a: SearchHit, b: SearchHit): Boolean = a == b
        }
    }
}
