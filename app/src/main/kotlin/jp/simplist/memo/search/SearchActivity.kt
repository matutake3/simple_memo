package jp.simplist.memo.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import jp.simplist.memo.R
import jp.simplist.memo.ui.ThemedActivity
import jp.simplist.memo.ui.applySystemBarsInsets
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.databinding.ActivitySearchBinding
import jp.simplist.memo.ui.EditChecklistActivity
import jp.simplist.memo.ui.EditMemoActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * インクリメンタルサーチ画面 (DESIGN_SPEC §7-D)。
 * - 入力 250ms debounce で検索発火 (IME コンポーズ対策、確定後の文字を主対象)。
 * - title / body / checklist item の text を部分一致 (大文字小文字区別なし)。
 */
class SearchActivity : ThemedActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter
    private lateinit var repo: MemoRepository

    private var debounceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsInsets()
        repo = MemoRepository.get(this)

        adapter = SearchResultAdapter { hit ->
            val intent = when (hit.memo.type) {
                MemoType.TEXT -> Intent(this, EditMemoActivity::class.java)
                MemoType.CHECKLIST -> Intent(this, EditChecklistActivity::class.java)
            }.apply { putExtra(EditMemoActivity.EXTRA_MEMO_ID, hit.memo.id) }
            startActivity(intent)
        }
        binding.resultList.layoutManager = LinearLayoutManager(this)
        binding.resultList.adapter = adapter

        binding.backButton.setOnClickListener { finish() }
        binding.closeButton.setOnClickListener { finish() }
        binding.clearButton.setOnClickListener {
            binding.searchEdit.setText("")
            binding.searchEdit.requestFocus()
        }

        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                binding.clearButton.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                debounceJob?.cancel()
                debounceJob = lifecycleScope.launch {
                    delay(250)
                    runSearch(q)
                }
            }
        })
        binding.searchEdit.requestFocus()
    }

    private fun runSearch(query: String) {
        lifecycleScope.launch {
            val q = query.trim()
            if (q.isEmpty()) {
                adapter.submitList(emptyList())
                binding.resultsCount.text = ""
                return@launch
            }
            val memos = repo.getAllActive()
            val hits = mutableListOf<SearchHit>()
            for (memo in memos) {
                val combined = buildString {
                    memo.title?.let { append(it).append('\n') }
                    memo.body?.let { append(it).append('\n') }
                    if (memo.type == MemoType.CHECKLIST) {
                        for (item in repo.getChecklistItems(memo.id)) {
                            append(item.text).append('\n')
                        }
                    }
                }
                val idx = combined.indexOf(q, ignoreCase = true)
                if (idx >= 0) {
                    hits.add(SearchHit(memo, combined, idx, q.length))
                }
            }
            adapter.submitList(hits)
            binding.resultsCount.text = getString(R.string.search_results_count, hits.size)
        }
    }
}
