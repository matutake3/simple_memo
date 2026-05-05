package jp.simplist.memo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * タグエンティティ。最大 10 個まで (UI 側でガード)。
 * color はメモと同じ ID 空間 (1〜18)、0 はタグでは未使用。
 * SPEC §3.1 / §7 参照。
 */
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val color: Int = 14, // default: sage
    val sortOrder: Int = 0,
)
