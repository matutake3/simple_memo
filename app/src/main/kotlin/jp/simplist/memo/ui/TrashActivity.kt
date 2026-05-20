package jp.simplist.memo.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.App
import jp.simplist.memo.R
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.databinding.ActivitySimpleListBinding
import jp.simplist.memo.databinding.ItemTrashCardBinding
import jp.simplist.memo.util.MemoColorUtils
import jp.simplist.memo.util.TimeFormat
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TrashActivity : ThemedActivity() {

    private lateinit var binding: ActivitySimpleListBinding
    private lateinit var adapter: TrashAdapter
    private lateinit var repo: MemoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsInsets()

        repo = MemoRepository.get(this)
        binding.toolbarTitle.setText(R.string.title_trash)
        binding.backButton.setOnClickListener { finish() }
        binding.headerNote.visibility = View.VISIBLE
        binding.headerNote.setText(R.string.trash_desc)
        binding.addRow.visibility = View.GONE
        binding.moreButton.visibility = View.VISIBLE
        binding.moreButton.setOnClickListener { showMoreMenu(it) }

        adapter = TrashAdapter(
            onClick = { memo -> showCardMenu(memo) },
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observeTrash().collect { items ->
                    adapter.submitList(items)
                    binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyText.text = "ゴミ箱は空です"
                }
            }
        }
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.trash_more, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_restore_all -> lifecycleScope.launch { repo.restoreAllTrash() }
                    R.id.action_delete_all -> AlertDialog.Builder(this@TrashActivity)
                        .setMessage(R.string.trash_delete_all_confirm)
                        .setPositiveButton(R.string.action_delete) { _, _ ->
                            lifecycleScope.launch { repo.deleteAllTrash() }
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                }
                true
            }
        }.show()
    }

    private fun showCardMenu(memo: Memo) {
        AlertDialog.Builder(this)
            .setItems(arrayOf(getString(R.string.action_restore), getString(R.string.action_delete_permanently))) { _, which ->
                lifecycleScope.launch {
                    when (which) {
                        0 -> repo.restoreFromTrash(memo.id)
                        1 -> repo.deletePermanently(memo.id)
                    }
                }
            }
            .show()
    }
}

class TrashAdapter(
    private val onClick: (Memo) -> Unit,
) : ListAdapter<Memo, TrashAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTrashCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemTrashCardBinding,
        private val onClick: (Memo) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {
        fun bind(memo: Memo) {
            val ctx = b.root.context
            val cardColor = MemoColorUtils.resolve(ctx, memo.color)
            val derived = MemoColorUtils.darkenForOnSurface(cardColor)
            b.root.backgroundTintList = ColorStateList.valueOf(cardColor)
            b.typeIcon.setImageResource(
                if (memo.type == MemoType.TEXT) R.drawable.ic_note else R.drawable.ic_check_box,
            )
            b.typeIcon.imageTintList = ColorStateList.valueOf(derived)
            b.title.text = memo.title?.takeIf { it.isNotBlank() } ?: when (memo.type) {
                MemoType.TEXT -> memo.body?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: "(無題のメモ)"
                MemoType.CHECKLIST -> "(無題のリスト)"
            }
            val deletedAt = memo.deletedAt ?: 0L
            val retention = TimeUnit.DAYS.toMillis(App.TRASH_RETENTION_DAYS)
            val daysLeft = TimeFormat.daysRemainingFromTrash(deletedAt, retention)
            b.remainingText.text = ctx.getString(R.string.trash_remaining, daysLeft)
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
