package jp.simplist.memo.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TemplateRepository private constructor(context: Context) {

    private val templateDao = MemoDatabase.get(context).templateDao()

    fun observeAll(): Flow<List<Template>> = templateDao.observeAll()
    suspend fun getAll(): List<Template> = templateDao.getAll()
    suspend fun getById(id: Long): Template? = templateDao.getById(id)
    suspend fun count(): Int = templateDao.count()
    suspend fun insert(template: Template): Long = templateDao.insert(template)
    suspend fun insertAll(templates: List<Template>) = templateDao.insertAll(templates)
    suspend fun update(template: Template) = templateDao.update(template)
    suspend fun delete(id: Long) = templateDao.deleteById(id)

    companion object {
        @Volatile private var INSTANCE: TemplateRepository? = null

        fun get(context: Context): TemplateRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: TemplateRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
