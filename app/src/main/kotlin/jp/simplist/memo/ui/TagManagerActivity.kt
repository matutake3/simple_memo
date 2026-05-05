package jp.simplist.memo.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.R
import jp.simplist.memo.data.Tag
import jp.simplist.memo.data.TagRepository
import jp.simplist.memo.databinding.ActivitySimpleListBinding
import jp.simplist.memo.databinding.ItemTagRowBinding
import jp.simplist.memo.util.MemoColorUtils
import kotlinx.coroutines.launch

class TagManagerActivity : ThemedActivity() {

    private lateinit var binding: ActivitySimpleListBinding
    private lateinit var adapter: TagAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarTitle.setText(R.string.title_tag_manager)
        binding.backButton.setOnClickListener { finish() }
        binding.addRowText.text = getString(R.string.tag_create_new)

        adapter = TagAdapter { tag ->
            TagEditDialog.showEdit(this, tag) { /* repository observes update */ }
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.addRow.setOnClickListener {
            TagEditDialog.showCreate(this) { /* observed via flow */ }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TagRepository.get(this@TagManagerActivity).observeAll().collect { tags ->
                    adapter.submitList(tags)
                    binding.emptyText.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyText.text = "タグがありません"
                }
            }
        }
    }
}

class TagAdapter(
    private val onClick: (Tag) -> Unit,
) : ListAdapter<Tag, TagAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTagRowBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
        return VH(b, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemTagRowBinding,
        private val onClick: (Tag) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {
        fun bind(tag: Tag) {
            val ctx = b.root.context
            b.colorDot.backgroundTintList = ColorStateList.valueOf(MemoColorUtils.resolve(ctx, tag.color))
            b.tagName.text = tag.name
            b.root.setOnClickListener { onClick(tag) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Tag>() {
            override fun areItemsTheSame(a: Tag, b: Tag): Boolean = a.id == b.id
            override fun areContentsTheSame(a: Tag, b: Tag): Boolean = a == b
        }
    }
}
