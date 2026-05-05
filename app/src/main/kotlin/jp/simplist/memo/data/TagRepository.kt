package jp.simplist.memo.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TagRepository private constructor(context: Context) {

    private val tagDao = MemoDatabase.get(context).tagDao()
    private val memoDao = MemoDatabase.get(context).memoDao()

    fun observeAll(): Flow<List<Tag>> = tagDao.observeAll()
    suspend fun getAll(): List<Tag> = tagDao.getAll()
    suspend fun getById(id: Long): Tag? = tagDao.getById(id)
    suspend fun count(): Int = tagDao.count()
    suspend fun insert(tag: Tag): Long = tagDao.insert(tag)
    suspend fun update(tag: Tag) = tagDao.update(tag)
    /** タグ削除時はそのタグを使っていたメモから tagId を null にする (CASCADE 不可なので手動)。 */
    suspend fun delete(id: Long) {
        memoDao.clearTagFromMemos(id)
        tagDao.deleteById(id)
    }

    companion object {
        const val MAX_TAGS = 10

        @Volatile private var INSTANCE: TagRepository? = null

        fun get(context: Context): TagRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: TagRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
