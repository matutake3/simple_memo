package jp.simplist.memo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * チェックリスト項目。type=CHECKLIST のメモにのみ紐づく。
 * SPEC §3.1 参照。
 *
 * Foreign key で memoId を Memo.id に紐付けし、メモ削除時に CASCADE。
 */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = Memo::class,
            parentColumns = ["id"],
            childColumns = ["memoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memoId"), Index("sortOrder")],
)
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val memoId: Long,
    val text: String,
    val checked: Boolean = false,
    val sortOrder: Int = 0,
)
