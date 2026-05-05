package jp.simplist.memo.ui.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import jp.simplist.memo.R

/**
 * 優先度ピッカー (Material AlertDialog のラジオ実装)。
 * BottomSheet ではなく Dialog にしている (4 択固定で BottomSheet ほど大きくしないため)。
 */
class PriorityPickerBottomSheet : DialogFragment() {

    private var initial: Int = 0
    private var onPick: ((Int) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val labels = arrayOf(
            getString(R.string.priority_none),
            getString(R.string.priority_1),
            getString(R.string.priority_2),
            getString(R.string.priority_3),
        )
        var picked = initial.coerceIn(0, 3)
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.priority_picker_title)
            .setSingleChoiceItems(labels, picked) { _, which -> picked = which }
            .setPositiveButton(R.string.action_ok) { _, _ -> onPick?.invoke(picked) }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    companion object {
        fun show(fm: FragmentManager, current: Int, onPick: (Int) -> Unit) {
            PriorityPickerBottomSheet().apply {
                this.initial = current
                this.onPick = onPick
            }.show(fm, "priority")
        }
    }
}
