package jp.simplist.memo.backup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import jp.simplist.memo.R
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.databinding.ActivityBackupBinding
import jp.simplist.memo.trial.TrialManager
import jp.simplist.memo.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private lateinit var settings: AppSettings

    // === SAF launchers ===

    /** ワンショット保存 (Tree URI 未設定時のフォールバック) */
    private val createTxt = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) doExportTxt(uri)
    }
    private val createJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) doExportJson(uri)
    }
    private val openJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmAndImport(uri)
    }
    /** バックアップフォルダ選択 (永続化) */
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onTreePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = AppSettings.get(this)

        binding.backButton.setOnClickListener { finish() }

        binding.rowBackupFolder.setOnClickListener {
            // 既存設定があってもピッカーを開いて変更可能にする
            openTree.launch(null)
        }

        binding.switchBackupAuto.setOnCheckedChangeListener(null)
        binding.switchBackupAuto.isChecked = settings.autoBackupEnabled
        binding.switchBackupAuto.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (settings.backupFolderUri == null) {
                    binding.switchBackupAuto.isChecked = false
                    Toast.makeText(this, R.string.backup_auto_needs_folder, Toast.LENGTH_LONG).show()
                    openTree.launch(null)
                } else {
                    settings.autoBackupEnabled = true
                    BackupScheduler.enable(this)
                }
            } else {
                settings.autoBackupEnabled = false
                BackupScheduler.disable(this)
            }
        }

        binding.rowExportTxt.setOnClickListener { exportTxtFlow() }
        binding.rowExportJson.setOnClickListener { exportJsonFlow() }
        binding.rowImport.setOnClickListener {
            if (!ensureCanEdit()) return@setOnClickListener
            openJson.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        renderFolderSummary()
        renderAutoSummary()
    }

    private fun renderFolderSummary() {
        val folder = settings.backupFolderUri
        if (folder == null) {
            binding.backupFolderSummary.setText(R.string.backup_folder_unset)
        } else {
            val tree = runCatching { DocumentFile.fromTreeUri(this, Uri.parse(folder)) }.getOrNull()
            binding.backupFolderSummary.text = tree?.name ?: folder
        }
    }

    private fun renderAutoSummary() {
        val last = settings.lastBackupAt
        val lastText = if (last == 0L) {
            getString(R.string.backup_last_never)
        } else {
            TimeFormat.relative(last)
        }
        binding.backupAutoSummary.text = buildString {
            append(getString(R.string.backup_auto_desc))
            append('\n')
            append(getString(R.string.backup_last_format, lastText))
        }
    }

    private fun onTreePicked(uri: Uri) {
        try {
            // 永続化された URI 権限を取得 (端末再起動後もアクセスできる)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}
        settings.backupFolderUri = uri.toString()
        renderFolderSummary()
        // 自動バックアップが ON ならフォルダ変更で再スケジュール
        if (settings.autoBackupEnabled) BackupScheduler.enable(this)
    }

    // === Manual export ===

    /** Tree URI が設定されていればそこへ直接書く。未設定なら SAF ワンショット picker。 */
    private fun exportJsonFlow() {
        val folder = settings.backupFolderUri
        if (folder != null) {
            lifecycleScope.launch {
                val mgr = BackupManager(this@BackupActivity)
                try {
                    val result = withContext(Dispatchers.IO) {
                        mgr.exportJsonToTreeUri(Uri.parse(folder))
                    }
                    if (result != null) {
                        Toast.makeText(this@BackupActivity, R.string.backup_export_done, Toast.LENGTH_SHORT).show()
                        renderAutoSummary()
                    } else {
                        Toast.makeText(this@BackupActivity, "エクスポートに失敗しました", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@BackupActivity, "エクスポートに失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            createJson.launch("simple_memo_$ts.json")
        }
    }

    private fun exportTxtFlow() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        // TXT は読み物用なのでフォルダ固定の対象外。毎回保存先を選ばせる。
        createTxt.launch("simple_memo_$ts.txt")
    }

    private fun doExportJson(uri: Uri) {
        val mgr = BackupManager(this)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { mgr.exportJson(uri) }
                Toast.makeText(this@BackupActivity, R.string.backup_export_done, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@BackupActivity, "エクスポートに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doExportTxt(uri: Uri) {
        val mgr = BackupManager(this)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { mgr.exportTxt(uri) }
                Toast.makeText(this@BackupActivity, R.string.backup_export_done, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@BackupActivity, "エクスポートに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmAndImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_dialog_title)
            .setItems(arrayOf(getString(R.string.backup_import_overwrite), getString(R.string.backup_import_merge))) { _, which ->
                val mode = if (which == 0) BackupManager.ImportMode.OVERWRITE else BackupManager.ImportMode.MERGE
                runImport(uri, mode)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun runImport(uri: Uri, mode: BackupManager.ImportMode) {
        val mgr = BackupManager(this)
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { mgr.importJson(uri, mode) }
                Toast.makeText(this@BackupActivity, getString(R.string.backup_import_done, count), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@BackupActivity, R.string.backup_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureCanEdit(): Boolean {
        if (TrialManager.get().canEditMemos()) return true
        AlertDialog.Builder(this)
            .setMessage(R.string.trial_locked_message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
        return false
    }
}
