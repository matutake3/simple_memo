package jp.simplist.memo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * テンプレートエンティティ。
 * - itemsCsv: CHECKLIST 用に項目を改行区切りで CSV 文字列化して保存。
 *             TEXT のときは null。
 * - isPreset: プリセット (初回起動時に挿入) か、ユーザー追加か。両方とも編集・削除可。
 * SPEC §3.1 / §8 参照。
 */
@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: MemoType,
    val name: String,
    val title: String? = null,
    val body: String? = null,
    val itemsCsv: String? = null,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0,
) {
    fun items(): List<String> =
        itemsCsv?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    companion object {
        fun joinItems(items: List<String>): String =
            items.filter { it.isNotEmpty() }.joinToString("\n")
    }
}
