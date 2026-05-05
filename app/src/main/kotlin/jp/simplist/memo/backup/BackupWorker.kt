package jp.simplist.memo.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import jp.simplist.memo.data.AppSettings

/**
 * 定期自動バックアップを実行する WorkManager Worker。
 * - AppSettings.backupFolderUri に書き込む
 * - 端末アイドル + Wi-Fi 不要 + ストレージ十分の制約で起動
 * - 失敗時はリトライ (WorkManager のデフォルトバックオフ)
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings.get(applicationContext)
        if (!settings.autoBackupEnabled) return Result.success()
        val folderStr = settings.backupFolderUri ?: return Result.success()
        val tree = Uri.parse(folderStr)
        return try {
            val mgr = BackupManager(applicationContext)
            val result = mgr.exportJsonToTreeUri(tree)
            if (result != null) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
