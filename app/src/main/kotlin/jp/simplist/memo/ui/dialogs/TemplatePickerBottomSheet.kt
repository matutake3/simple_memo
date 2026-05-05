package jp.simplist.memo.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import jp.simplist.memo.R
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.data.TemplateRepository
import kotlinx.coroutines.runBlocking

/**
 * テンプレート選択ダイアログ。FAB 長押しで起動 (SPEC §8.2)。
 * 選択後、コールバックでテンプレ ID (0 = テンプレなし) を返す。
 */
class TemplatePickerBottomSheet : DialogFragment() {

    private var typeFilter: MemoType = MemoType.TEXT
    private var onPick: ((Long?) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val all = runBlocking { TemplateRepository.get(ctx).getAll() }
        val templates = all.filter { it.type == typeFilter }
        val labels = mutableListOf<String>().apply {
            add(getString(R.string.template_no_template))
            templates.forEach { add(it.name) }
        }
        val ids = mutableListOf<Long?>().apply {
            add(null)
            templates.forEach { add(it.id) }
        }
        var picked = 0
        return AlertDialog.Builder(ctx)
            .setTitle(R.string.template_picker_title)
            .setSingleChoiceItems(labels.toTypedArray(), 0) { _, which -> picked = which }
            .setPositiveButton(R.string.action_ok) { _, _ -> onPick?.invoke(ids[picked]) }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    companion object {
        fun show(fm: FragmentManager, type: MemoType, onPick: (Long?) -> Unit) {
            TemplatePickerBottomSheet().apply {
                this.typeFilter = type
                this.onPick = onPick
            }.show(fm, "template")
        }
    }
}
