package jp.simplist.memo.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import jp.simplist.memo.R
import jp.simplist.memo.data.TagRepository
import jp.simplist.memo.ui.TagEditDialog
import kotlinx.coroutines.runBlocking

/**
 * タグピッカー: 既存タグ + 「タグなし」 + 「+ 新しいタグを作成」を AlertDialog で表示。
 */
class TagPickerBottomSheet : DialogFragment() {

    private var currentTagId: Long? = null
    private var onPick: ((Long?) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        // 簡易: 表示時点で同期取得 (タグは最大 10、軽量)
        val tags = runBlocking { TagRepository.get(ctx).getAll() }
        val labels = mutableListOf<String>().apply {
            add(getString(R.string.tag_none))
            tags.forEach { add(it.name) }
            add(getString(R.string.tag_create_new))
        }
        val ids = mutableListOf<Long?>().apply {
            add(null)
            tags.forEach { add(it.id) }
            add(-1L) // 新規作成のセンチネル
        }
        val initialIndex = ids.indexOfFirst { it == currentTagId }.coerceAtLeast(0)
        var picked = initialIndex
        return AlertDialog.Builder(ctx)
            .setTitle(R.string.tag_picker_title)
            .setSingleChoiceItems(labels.toTypedArray(), initialIndex) { _, which -> picked = which }
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val sel = ids[picked]
                if (sel == -1L) {
                    TagEditDialog.showCreate(requireActivity()) { newId ->
                        onPick?.invoke(newId)
                    }
                } else {
                    onPick?.invoke(sel)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    companion object {
        fun show(fm: FragmentManager, currentTagId: Long?, onPick: (Long?) -> Unit) {
            TagPickerBottomSheet().apply {
                this.currentTagId = currentTagId
                this.onPick = onPick
            }.show(fm, "tag")
        }
    }
}
