package jp.simplist.memo.util

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import jp.simplist.memo.R

/**
 * メモのカラー周りのヘルパ。
 * - 18 色パレットの ID (1〜18) + 0=デフォルト (= paper 同色) を扱う。
 * - カード色から派生した「アイコン・★・強調文字色」を計算 (HSL: 彩度 0.55 / 明度 0.30)。
 * - タグチップ用に「カード色を paper と 50:50 でブレンド」した薄色を計算。
 *
 * DESIGN_SPEC §3.4 / §6.7 / §12.5 参照。
 */
object MemoColorUtils {

    /** 0..18 の color ID から、対応する @ColorInt を返す。 */
    @ColorInt
    fun resolve(context: Context, colorId: Int): Int {
        val resId = when (colorId) {
            0 -> R.color.color_default
            1 -> R.color.color_cream
            2 -> R.color.color_apricot
            3 -> R.color.color_peach
            4 -> R.color.color_coral
            5 -> R.color.color_rose
            6 -> R.color.color_pink
            7 -> R.color.color_mauve
            8 -> R.color.color_lavender
            9 -> R.color.color_lilac
            10 -> R.color.color_dusty_blue
            11 -> R.color.color_sky
            12 -> R.color.color_aqua
            13 -> R.color.color_mint
            14 -> R.color.color_sage_swatch
            15 -> R.color.color_olive
            16 -> R.color.color_beige
            17 -> R.color.color_greige
            18 -> R.color.color_dust_yellow
            else -> R.color.color_default
        }
        return ContextCompat.getColor(context, resId)
    }

    /** カード色から、その上に重ねるアイコン・★・強調文字の色を計算。 */
    @ColorInt
    fun darkenForOnSurface(@ColorInt cardColor: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(cardColor, hsl)
        hsl[1] = 0.55f
        hsl[2] = 0.30f
        return ColorUtils.HSLToColor(hsl)
    }

    /** カード色から、タグチップの淡い背景色を計算 (paper と 50:50 ブレンド)。 */
    @ColorInt
    fun blendForChipBackground(@ColorInt cardColor: Int, @ColorInt paper: Int): Int {
        return ColorUtils.blendARGB(cardColor, paper, 0.5f)
    }

    /**
     * カード色からタグチップ用テキスト色 (HSL 明度 0.30 に固定) を返す。
     * darkenForOnSurface とほぼ同じだが、彩度はやや控えめ。
     */
    @ColorInt
    fun darkenForChipText(@ColorInt cardColor: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(cardColor, hsl)
        hsl[1] = 0.45f
        hsl[2] = 0.30f
        return ColorUtils.HSLToColor(hsl)
    }

    /** ID 1〜18 のリスト (ID 0 はデフォルトとして別扱い)。 */
    val PALETTE_IDS: IntRange = 1..18
}
