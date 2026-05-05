package jp.simplist.memo.ui.dialogs

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.simplist.memo.R
import jp.simplist.memo.databinding.SheetColorPickerBinding
import jp.simplist.memo.util.MemoColorUtils

/**
 * 色選択シート (DESIGN_SPEC §7-B)。3 行 × 6 列のグリッドで 18 色 + 「色なし」。
 */
class ColorPickerBottomSheet : BottomSheetDialogFragment() {

    private var _b: SheetColorPickerBinding? = null
    private val b get() = _b!!

    private var initialColor: Int = 0
    private var onPick: ((Int) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetColorPickerBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        b.colorGrid.removeAllViews()
        for (id in MemoColorUtils.PALETTE_IDS) {
            val cell = LayoutInflater.from(ctx).inflate(R.layout.cell_color_swatch, b.colorGrid, false)
            val swatch = cell.findViewById<View>(R.id.swatch)
            val check = cell.findViewById<View>(R.id.selectedCheck)
            // 縁取りなしの単色 swatch (DESIGN: borderless で純粋な色面を見せる)
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(MemoColorUtils.resolve(ctx, id))
            }
            check.visibility = if (id == initialColor) View.VISIBLE else View.GONE
            cell.setOnClickListener {
                onPick?.invoke(id); dismiss()
            }
            b.colorGrid.addView(cell)
        }
        b.noneRow.setOnClickListener { onPick?.invoke(0); dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        fun show(fm: FragmentManager, currentColor: Int, onPick: (Int) -> Unit) {
            ColorPickerBottomSheet().apply {
                this.initialColor = currentColor
                this.onPick = onPick
            }.show(fm, "color")
        }
    }
}
