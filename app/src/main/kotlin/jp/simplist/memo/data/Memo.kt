package jp.simplist.memo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * メモ / リスト共通エンティティ。
 * SPEC §3.1 参照。
 *
 * - type=TEXT: body にテキスト本文を持つ。ChecklistItem は持たない。
 * - type=CHECKLIST: body は使わない。ChecklistItem テーブルに別途項目を保存。
 * - color: 0=デフォルト (色なし)、1〜18 がパレット (DESIGN_SPEC §3.4)
 * - priority: 0=なし、1〜3=★の数
 * - tagId: 1メモ1タグ。null=未分類。
 * - protected: 誤削除防止フラグ (削除時に確認ダイアログを出す)
 * - sortOrder: 手動並び替え時の順序。null なら updatedAt 順 (DESC) で表示。
 * - deletedAt: ゴミ箱用。null=未削除、Long=削除日時 (epoch ms)。7 日経過で完全削除。
 */
@Entity(
    tableName = "memos",
    indices = [
        Index("deletedAt"),
        Index("tagId"),
        Index("updatedAt"),
        Index("sortOrder"),
    ],
)
data class Memo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: MemoType,
    val title: String? = null,
    val body: String? = null,
    val color: Int = 0,
    val priority: Int = 0,
    val tagId: Long? = null,
    @androidx.room.ColumnInfo(name = "protected")
    val isProtected: Boolean = false,
    val sortOrder: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

enum class MemoType { TEXT, CHECKLIST }
