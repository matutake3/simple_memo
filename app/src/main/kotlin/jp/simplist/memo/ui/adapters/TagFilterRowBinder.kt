package jp.simplist.memo.ui.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import jp.simplist.memo.R
import jp.simplist.memo.data.Tag
import jp.simplist.memo.util.MemoColorUtils

sealed class TagFilterSelection {
    data object All : TagFilterSelection()
    data object Untagged : TagFilterSelection()
    data class Specific(val tagId: Long) : TagFilterSelection()
    data object Add : TagFilterSelection()
}

/**
 * タグフィルタ行を LinearLayout 内に動的に構築するバインダー。
 *
 * - 「すべて」「未分類」「+ 追加」の固定 chip と、ユーザー定義タグ chip を並べる。
 * - 選択状態は currentSelection で管理。
 */
class TagFilterRowBinder(
    private val container: LinearLayout,
    private val onSelected: (TagFilterSelection) -> Unit,
) {
    fun bind(tags: List<Tag>, selected: TagFilterSelection) {
        val ctx = container.context
        container.removeAllViews()
        addChip(ctx, ctx.getString(R.string.filter_all), selected is TagFilterSelection.All, null) {
            onSelected(TagFilterSelection.All)
        }
        addChip(ctx, ctx.getString(R.string.filter_untagged), selected is TagFilterSelection.Untagged, null) {
            onSelected(TagFilterSelection.Untagged)
        }
        for (tag in tags) {
            val isSelected = selected is TagFilterSelection.Specific && selected.tagId == tag.id
            addChip(ctx, tag.name, isSelected, tag.color) {
                onSelected(TagFilterSelection.Specific(tag.id))
            }
        }
        addChip(ctx, ctx.getString(R.string.filter_add), false, null) {
            onSelected(TagFilterSelection.Add)
        }
    }

    private fun addChip(
        ctx: Context,
        text: String,
        selected: Boolean,
        tagColor: Int?,
        onClick: () -> Unit,
    ) {
        val tv = TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.inter_medium)
            includeFontPadding = false
            gravity = Gravity.CENTER
            val padH = (14f * ctx.resources.displayMetrics.density).toInt()
            setPadding(padH, 0, padH, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        if (tagColor != null && tagColor in MemoColorUtils.PALETTE_IDS) {
            // 色付きタグ: 未選択は paper と 50:50 ブレンドの薄パステル、選択中はフル飽和タグ色。
            // 旧仕様で選択中に bg_chip_filter_selected (= 白) を当ててしまい、色のアイデンティティが
            // 失われて「タグ背景が白になる」不具合の原因だった。
            val paper = ctx.getColor(R.color.paper)
            val cardColor = MemoColorUtils.resolve(ctx, tagColor)
            val unselectedBg = MemoColorUtils.blendForChipBackground(cardColor, paper)
            val txt = MemoColorUtils.darkenForChipText(cardColor)
            tv.background = ctx.getDrawable(R.drawable.bg_chip_tag)
            tv.backgroundTintList =
                ColorStateList.valueOf(if (selected) cardColor else unselectedBg)
            tv.setTextColor(txt)
        } else {
            // 色なしタグ (「すべて」「未分類」「+追加」): 白 (選択中) / 薄グレー (未選択)
            tv.background = ctx.getDrawable(
                if (selected) R.drawable.bg_chip_filter_selected else R.drawable.bg_chip_filter_unselected,
            )
            tv.backgroundTintList = null
            tv.setTextColor(ctx.getColor(if (selected) R.color.ink else R.color.ink_secondary))
        }

        // elevation は描画が粗くなる原因なので使わない。selected は純白背景でコントラストを取る。

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (32f * ctx.resources.displayMetrics.density).toInt(),
        ).apply {
            marginEnd = (8f * ctx.resources.displayMetrics.density).toInt()
            gravity = Gravity.CENTER_VERTICAL
        }
        container.addView(tv, params)
    }
}
