package jp.simplist.memo.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import jp.simplist.memo.R
import jp.simplist.memo.data.Tag
import jp.simplist.memo.data.TagRepository
import jp.simplist.memo.util.MemoColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * タグの新規作成 / 編集ダイアログ。
 * - showCreate: 新規作成 → コールバックに新しい tagId を返す。
 * - showEdit:   既存編集 / 削除。
 *
 * AlertDialog ベース (BottomSheet ではない)、色 swatch グリッド + 名前入力 + 削除ボタン。
 */
object TagEditDialog {

    fun showCreate(activity: Activity, onCreated: (Long) -> Unit) {
        showInternal(activity, null, onChanged = { id -> onCreated(id) })
    }

    fun showEdit(activity: Activity, tag: Tag, onChanged: (Long?) -> Unit) {
        showInternal(activity, tag) { id -> onChanged(id) }
    }

    private fun showInternal(activity: Activity, existing: Tag?, onChanged: (Long) -> Unit) {
        val ctx = activity
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_tag_edit, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val grid = view.findViewById<android.widget.GridLayout>(R.id.colorGrid)
        var pickedColor = existing?.color ?: 14
        nameInput.setText(existing?.name ?: "")
        nameInput.setSelection(nameInput.text?.length ?: 0)
        nameInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        // grid 描画 (縁取りなし、選択中は中央のチェックアイコンで示す)
        fun rebuildGrid() {
            grid.removeAllViews()
            for (id in MemoColorUtils.PALETTE_IDS) {
                val cell = LayoutInflater.from(ctx).inflate(R.layout.cell_color_swatch, grid, false)
                val swatch = cell.findViewById<android.view.View>(R.id.swatch)
                val check = cell.findViewById<android.view.View>(R.id.selectedCheck)
                swatch.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(MemoColorUtils.resolve(ctx, id))
                }
                check.visibility = if (id == pickedColor) android.view.View.VISIBLE else android.view.View.GONE
                cell.setOnClickListener {
                    pickedColor = id; rebuildGrid()
                }
                grid.addView(cell)
            }
        }
        rebuildGrid()

        val builder = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) R.string.tag_create_new else R.string.title_tag_manager)
            .setView(view)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.action_delete, null)
        }
        val dialog = builder.create()
        dialog.show()

        // OK の差し替え (バリデーション後のみ閉じる)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            val repo = TagRepository.get(ctx)
            MainScope().launch {
                if (existing == null) {
                    if (repo.count() >= TagRepository.MAX_TAGS) {
                        Toast.makeText(ctx, R.string.tag_max_reached, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val newId = withContext(Dispatchers.IO) {
                        repo.insert(Tag(name = name, color = pickedColor, sortOrder = repo.count()))
                    }
                    onChanged(newId)
                } else {
                    withContext(Dispatchers.IO) {
                        repo.update(existing.copy(name = name, color = pickedColor))
                    }
                    onChanged(existing.id)
                }
                dialog.dismiss()
            }
        }
        if (existing != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(ctx)
                    .setMessage("「${existing.name}」を削除しますか？")
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        MainScope().launch {
                            withContext(Dispatchers.IO) {
                                TagRepository.get(ctx).delete(existing.id)
                            }
                            dialog.dismiss()
                        }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        }
    }
}
