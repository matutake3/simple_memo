package jp.simplist.memo.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.data.ChecklistItem
import jp.simplist.memo.data.Memo
import jp.simplist.memo.data.MemoDatabase
import jp.simplist.memo.data.MemoRepository
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.data.Tag
import jp.simplist.memo.data.TagRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 手動バックアップ・復元の中核 (SPEC §9)。
 * - JSON: メモ + チェック項目 + タグを完全に保持して書き出し / 読み込み。
 * - TXT:  人が読みやすい形式 (タイトル + 本文 / ☑/□ 項目)。エクスポート専用。
 *
 * 保存先は SAF (`ACTION_CREATE_DOCUMENT`) で取得した Uri、
 * 読み込み元も SAF (`ACTION_OPEN_DOCUMENT`) の Uri。
 */
class BackupManager(private val context: Context) {

    private val repo = MemoRepository.get(context)
    private val tagRepo = TagRepository.get(context)
    private val db = MemoDatabase.get(context)

    // === Export ===

    suspend fun exportJson(uri: Uri) {
        val memos = db.memoDao().getAllActive()
        val tags = tagRepo.getAll()
        val itemsByMemo = mutableMapOf<Long, List<ChecklistItem>>()
        for (memo in memos) {
            if (memo.type == MemoType.CHECKLIST) {
                itemsByMemo[memo.id] = repo.getChecklistItems(memo.id)
            }
        }

        val root = JSONObject().apply {
            put("schema", "simplist-memo")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("tags", JSONArray().also {
                for (t in tags) {
                    it.put(JSONObject().apply {
                        put("id", t.id)
                        put("name", t.name)
                        put("color", t.color)
                        put("sortOrder", t.sortOrder)
                    })
                }
            })
            put("memos", JSONArray().also {
                for (m in memos) {
                    val o = JSONObject().apply {
                        put("id", m.id)
                        put("type", m.type.name)
                        put("title", m.title)
                        put("body", m.body)
                        put("color", m.color)
                        put("priority", m.priority)
                        put("tagId", m.tagId)
                        put("protected", m.isProtected)
                        put("sortOrder", m.sortOrder)
                        put("createdAt", m.createdAt)
                        put("updatedAt", m.updatedAt)
                    }
                    if (m.type == MemoType.CHECKLIST) {
                        val items = itemsByMemo[m.id].orEmpty()
                        o.put("items", JSONArray().also { arr ->
                            for (ci in items) {
                                arr.put(JSONObject().apply {
                                    put("text", ci.text)
                                    put("checked", ci.checked)
                                    put("sortOrder", ci.sortOrder)
                                })
                            }
                        })
                    }
                    it.put(o)
                }
            })
        }
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(root.toString(2).toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun exportTxt(uri: Uri) {
        val memos = db.memoDao().getAllActive()
        val sb = StringBuilder()
        for (memo in memos) {
            sb.append("===\n")
            memo.title?.takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
            when (memo.type) {
                MemoType.TEXT -> memo.body?.let { sb.append(it).append('\n') }
                MemoType.CHECKLIST -> {
                    val items = repo.getChecklistItems(memo.id)
                    for (it in items) {
                        sb.append(if (it.checked) "☑" else "□").append(' ').append(it.text).append('\n')
                    }
                }
            }
            sb.append('\n')
        }
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    // === Import ===

    /**
     * 設定済みのバックアップフォルダ (SAF Tree URI) に JSON を直接書く。
     * - ファイル名は simple_memo_yyyyMMdd_HHmm.json
     * - 既存の同名ファイルがあれば上書き
     * - 戻り値: 書き込みに成功したファイルの URI、失敗時 null
     */
    suspend fun exportJsonToTreeUri(treeUri: Uri): Uri? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val filename = "simple_memo_$ts.json"
        // 同名既存ファイルがあれば削除 (上書き)
        tree.findFile(filename)?.delete()
        val file = tree.createFile("application/json", filename) ?: return null
        return try {
            exportJson(file.uri)
            AppSettings.get(context).lastBackupAt = System.currentTimeMillis()
            file.uri
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    enum class ImportMode { OVERWRITE, MERGE }

    suspend fun importJson(uri: Uri, mode: ImportMode): Int {
        val text = context.contentResolver.openInputStream(uri)?.use {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
        } ?: return 0
        val root = JSONObject(text)
        if (mode == ImportMode.OVERWRITE) {
            db.memoDao().deleteAllTrash()
            // 全削除 → DAO に方法がないので生 SQL: 一旦 trash flag を立ててから purge する代わりに、
            // ここでは簡易: 既存メモを全件 deletePermanently
            val all = db.memoDao().getAllActive()
            for (m in all) db.memoDao().deletePermanently(m.id)
            for (t in tagRepo.getAll()) tagRepo.delete(t.id)
        }

        // タグの復元 (id→新id マップ)
        val tagsArr = root.optJSONArray("tags")
        val tagIdMap = mutableMapOf<Long, Long>()
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val o = tagsArr.getJSONObject(i)
                val oldId = o.optLong("id", 0L)
                val newId = tagRepo.insert(
                    Tag(
                        name = o.getString("name"),
                        color = o.optInt("color", 14),
                        sortOrder = o.optInt("sortOrder", 0),
                    ),
                )
                tagIdMap[oldId] = newId
            }
        }

        // メモの復元
        val memosArr = root.optJSONArray("memos") ?: return 0
        var imported = 0
        for (i in 0 until memosArr.length()) {
            val o = memosArr.getJSONObject(i)
            val type = MemoType.valueOf(o.getString("type"))
            val tagOldId = if (o.isNull("tagId")) null else o.optLong("tagId")
            val tagNewId = tagOldId?.let { tagIdMap[it] }
            val newMemo = Memo(
                type = type,
                title = if (o.isNull("title")) null else o.optString("title").takeIf { it.isNotEmpty() },
                body = if (o.isNull("body")) null else o.optString("body").takeIf { it.isNotEmpty() },
                color = o.optInt("color", 0),
                priority = o.optInt("priority", 0),
                tagId = tagNewId,
                isProtected = o.optBoolean("protected", false),
                sortOrder = if (o.isNull("sortOrder")) null else o.optInt("sortOrder"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
            val newId = repo.insertMemo(newMemo)
            if (type == MemoType.CHECKLIST) {
                val items = o.optJSONArray("items")
                if (items != null) {
                    val list = mutableListOf<ChecklistItem>()
                    for (j in 0 until items.length()) {
                        val io = items.getJSONObject(j)
                        list.add(
                            ChecklistItem(
                                memoId = newId,
                                text = io.getString("text"),
                                checked = io.optBoolean("checked", false),
                                sortOrder = io.optInt("sortOrder", j),
                            ),
                        )
                    }
                    repo.insertChecklistItems(list)
                }
            }
            imported++
        }
        return imported
    }
}
