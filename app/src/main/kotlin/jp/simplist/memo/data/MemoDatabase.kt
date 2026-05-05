package jp.simplist.memo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Memo::class, ChecklistItem::class, Tag::class, Template::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MemoDatabase : RoomDatabase() {

    abstract fun memoDao(): MemoDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun tagDao(): TagDao
    abstract fun templateDao(): TemplateDao

    companion object {
        private const val DB_NAME = "memos.db"

        @Volatile private var INSTANCE: MemoDatabase? = null

        fun get(context: Context): MemoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                MemoDatabase::class.java,
                DB_NAME,
            )
                // 破壊的マイグレーション禁止 (シリーズ規約)。スキーマ変更は MIGRATION_n_n+1 で。
                .build()
                .also { INSTANCE = it }
        }
    }
}
