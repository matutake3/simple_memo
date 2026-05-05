package jp.simplist.memo.data

import android.content.Context
import jp.simplist.memo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 初回起動時のプリセット投入。AppSettings.presetsSeeded で 1 回限り。
 *
 * - タグ 3 つ: 仕事 (sage 14) / プライベート (sky 11) / 買い物 (coral 4)
 * - テンプレ 5 つ: やること / 買い物リスト / 運動リスト / 見たい映画 / 読みたい本
 *   (すべて CHECKLIST 種別、空 items で生成)
 */
object Presets {

    fun seedIfNeeded(context: Context, scope: CoroutineScope) {
        val settings = AppSettings.get(context)
        if (settings.presetsSeeded) return
        scope.launch(Dispatchers.IO) {
            val tagRepo = TagRepository.get(context)
            val templateRepo = TemplateRepository.get(context)
            // タグ件数が 0 のときだけ投入 (バックアップから復帰したケースを尊重)
            if (tagRepo.count() == 0) {
                tagRepo.insert(Tag(name = context.getString(R.string.preset_tag_work), color = 14, sortOrder = 0))
                tagRepo.insert(Tag(name = context.getString(R.string.preset_tag_private), color = 11, sortOrder = 1))
                tagRepo.insert(Tag(name = context.getString(R.string.preset_tag_shopping), color = 4, sortOrder = 2))
            }
            if (templateRepo.count() == 0) {
                val items = listOf(
                    Triple(R.string.preset_template_todo, MemoType.CHECKLIST, listOf<String>()),
                    Triple(R.string.preset_template_shopping, MemoType.CHECKLIST, listOf<String>()),
                    Triple(R.string.preset_template_workout, MemoType.CHECKLIST, listOf<String>()),
                    Triple(R.string.preset_template_movies, MemoType.CHECKLIST, listOf<String>()),
                    Triple(R.string.preset_template_books, MemoType.CHECKLIST, listOf<String>()),
                )
                items.forEachIndexed { index, (resId, type, list) ->
                    templateRepo.insert(
                        Template(
                            type = type,
                            name = context.getString(resId),
                            itemsCsv = if (list.isEmpty()) null else Template.joinItems(list),
                            isPreset = true,
                            sortOrder = index,
                        ),
                    )
                }
            }
            settings.presetsSeeded = true
        }
    }
}
