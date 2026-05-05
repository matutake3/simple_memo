package jp.simplist.memo.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun memoTypeToString(type: MemoType): String = type.name

    @TypeConverter
    fun stringToMemoType(name: String): MemoType =
        runCatching { MemoType.valueOf(name) }.getOrDefault(MemoType.TEXT)
}
