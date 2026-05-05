package jp.simplist.memo.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.simplist.memo.R
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.data.Template
import jp.simplist.memo.data.TemplateRepository
import jp.simplist.memo.databinding.ActivitySimpleListBinding
import jp.simplist.memo.databinding.ItemTemplateRowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TemplateManagerActivity : ThemedActivity() {

    private lateinit var binding: ActivitySimpleListBinding
    private lateinit var adapter: TemplateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarTitle.setText(R.string.title_template_manager)
        binding.backButton.setOnClickListener { finish() }
        binding.addRowText.text = "新しいテンプレートを追加"

        adapter = TemplateAdapter { tpl -> showEditDialog(tpl) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.addRow.setOnClickListener { showCreateDialog() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TemplateRepository.get(this@TemplateManagerActivity).observeAll().collect { items ->
                    adapter.submitList(items)
                    binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyText.text = "テンプレートがありません"
                }
            }
        }
    }

    private fun showCreateDialog() {
        showTemplateDialog(this, null)
    }

    private fun showEditDialog(tpl: Template) {
        showTemplateDialog(this, tpl)
    }

    private fun showTemplateDialog(ctx: Context, existing: Template?) {
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_template_edit, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val titleInput = view.findViewById<EditText>(R.id.titleInput)
        val bodyInput = view.findViewById<EditText>(R.id.bodyInput)
        val typeRadioText = view.findViewById<android.widget.RadioButton>(R.id.typeText)
        val typeRadioChecklist = view.findViewById<android.widget.RadioButton>(R.id.typeChecklist)

        nameInput.setText(existing?.name ?: "")
        titleInput.setText(existing?.title ?: "")
        // body は TEXT のときは body、CHECKLIST のときは items を改行で詰める
        val bodyText = when (existing?.type) {
            MemoType.CHECKLIST -> existing?.items()?.joinToString("\n").orEmpty()
            MemoType.TEXT -> existing?.body.orEmpty()
            null -> ""
        }
        bodyInput.setText(bodyText)
        if (existing?.type == MemoType.CHECKLIST) typeRadioChecklist.isChecked = true
        else typeRadioText.isChecked = true

        nameInput.inputType = InputType.TYPE_CLASS_TEXT
        titleInput.inputType = InputType.TYPE_CLASS_TEXT
        bodyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        val builder = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "テンプレートを追加" else "テンプレートを編集")
            .setView(view)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
        if (existing != null) builder.setNeutralButton(R.string.action_delete, null)
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            val type = if (typeRadioChecklist.isChecked) MemoType.CHECKLIST else MemoType.TEXT
            val titleVal = titleInput.text?.toString()?.takeIf { it.isNotEmpty() }
            val bodyVal = bodyInput.text?.toString().orEmpty()
            val itemsCsv = if (type == MemoType.CHECKLIST && bodyVal.isNotEmpty())
                Template.joinItems(bodyVal.lines())
            else null
            val finalBody = if (type == MemoType.TEXT) bodyVal.takeIf { it.isNotEmpty() } else null
            lifecycleScope.launch(Dispatchers.IO) {
                val repo = TemplateRepository.get(ctx)
                if (existing == null) {
                    repo.insert(Template(
                        type = type,
                        name = name,
                        title = titleVal,
                        body = finalBody,
                        itemsCsv = itemsCsv,
                        isPreset = false,
                        sortOrder = repo.count(),
                    ))
                } else {
                    repo.update(existing.copy(
                        type = type,
                        name = name,
                        title = titleVal,
                        body = finalBody,
                        itemsCsv = itemsCsv,
                    ))
                }
                withContext(Dispatchers.Main) { dialog.dismiss() }
            }
        }
        if (existing != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(ctx)
                    .setMessage("「${existing.name}」を削除しますか？")
                    .setPositiveButton(R.string.action_delete) { _: DialogInterface, _: Int ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            TemplateRepository.get(ctx).delete(existing.id)
                            withContext(Dispatchers.Main) { dialog.dismiss() }
                        }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        }
    }
}

class TemplateAdapter(
    private val onClick: (Template) -> Unit,
) : ListAdapter<Template, TemplateAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTemplateRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemTemplateRowBinding,
        private val onClick: (Template) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {
        fun bind(tpl: Template) {
            b.typeIcon.setImageResource(
                if (tpl.type == MemoType.CHECKLIST) R.drawable.ic_check_box else R.drawable.ic_note,
            )
            b.templateName.text = tpl.name
            b.root.setOnClickListener { onClick(tpl) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Template>() {
            override fun areItemsTheSame(a: Template, b: Template): Boolean = a.id == b.id
            override fun areContentsTheSame(a: Template, b: Template): Boolean = a == b
        }
    }
}
