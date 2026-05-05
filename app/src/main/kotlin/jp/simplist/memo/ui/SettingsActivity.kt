package jp.simplist.memo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.simplist.memo.BuildConfig
import jp.simplist.memo.R
import jp.simplist.memo.backup.BackupActivity
import jp.simplist.memo.billing.BillingManager
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.data.MemoType
import jp.simplist.memo.databinding.ActivitySettingsBinding
import jp.simplist.memo.privacy.BiometricHelper
import jp.simplist.memo.trial.TrialManager

/**
 * 設定画面 (DESIGN_SPEC §7-E)。
 * シリーズ共通 11 項目 + 本アプリ固有 8 項目。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings
    private var billing: BillingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AppSettings.get(this)
        billing = BillingManager(this) { renderPurchaseCard() }
        billing?.connect()

        binding.backButton.setOnClickListener { finish() }

        binding.rowUsageGuide.setOnClickListener { startActivity(Intent(this, UsageGuideActivity::class.java)) }
        binding.rowFaq.setOnClickListener { startActivity(Intent(this, FaqActivity::class.java)) }

        // ★順スイッチは廃止 (メイン画面の ⇅ 並び替え機能に統合)

        // ウィジェット通知
        binding.switchWidgetNotif.isChecked = settings.widgetNotificationEnabled
        binding.switchWidgetNotif.setOnCheckedChangeListener { _, c -> settings.widgetNotificationEnabled = c }

        // デフォルト種別
        renderDefaultType()
        binding.rowDefaultType.setOnClickListener { showDefaultTypeDialog() }

        binding.rowTemplateManager.setOnClickListener { startActivity(Intent(this, TemplateManagerActivity::class.java)) }
        binding.rowTagManager.setOnClickListener { startActivity(Intent(this, TagManagerActivity::class.java)) }

        // プライバシーロック
        binding.switchPrivacyLock.isChecked = settings.privacyLockEnabled
        binding.switchPrivacyLock.setOnCheckedChangeListener { _, c ->
            if (c && !BiometricHelper.canAuthenticate(this)) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.privacy_lock_unavailable)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
                binding.switchPrivacyLock.isChecked = false
                return@setOnCheckedChangeListener
            }
            settings.privacyLockEnabled = c
        }

        binding.rowBackup.setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }
        binding.rowTrash.setOnClickListener { startActivity(Intent(this, TrashActivity::class.java)) }

        binding.rowContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.contact_email)))
            startActivity(Intent.createChooser(intent, getString(R.string.settings_contact)))
        }
        binding.rowRate.setOnClickListener {
            val pkg = packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            } catch (_: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
            }
        }
        binding.rowShare.setOnClickListener {
            val pkg = packageName
            val text = "${getString(R.string.app_name)} https://play.google.com/store/apps/details?id=$pkg"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.settings_share)))
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
        }
        binding.rowTokushoho.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.tokushoho_url))))
        }
        binding.rowRestorePurchase.setOnClickListener {
            billing?.queryPurchasesOnce()
            renderPurchaseCard()
        }

        binding.purchaseButton.setOnClickListener {
            billing?.launchPurchase(this)
        }

        binding.versionText.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        renderPurchaseCard()
    }

    override fun onDestroy() {
        super.onDestroy()
        billing?.release()
    }

    private fun renderDefaultType() {
        binding.defaultTypeSummary.text = getString(
            if (settings.defaultMemoType == MemoType.TEXT) R.string.settings_default_memo_type_memo
            else R.string.settings_default_memo_type_checklist,
        )
    }

    private fun showDefaultTypeDialog() {
        val labels = arrayOf(
            getString(R.string.settings_default_memo_type_memo),
            getString(R.string.settings_default_memo_type_checklist),
        )
        val initial = if (settings.defaultMemoType == MemoType.TEXT) 0 else 1
        var picked = initial
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_default_memo_type)
            .setSingleChoiceItems(labels, initial) { _, w -> picked = w }
            .setPositiveButton(R.string.action_ok) { _, _ ->
                settings.defaultMemoType = if (picked == 0) MemoType.TEXT else MemoType.CHECKLIST
                renderDefaultType()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun renderPurchaseCard() {
        val tm = TrialManager.get()
        when {
            tm.isPurchased() -> {
                binding.purchaseTitle.setText(R.string.trial_purchased_title)
                binding.purchaseSummary.setText(R.string.trial_purchased_desc)
                binding.purchaseButton.visibility = View.GONE
            }
            tm.state() == TrialManager.State.EXPIRED -> {
                binding.purchaseTitle.setText(R.string.trial_expired_title)
                binding.purchaseSummary.setText(R.string.trial_expired_desc)
                binding.purchaseButton.text = billing?.formattedPrice()?.let { "永続版を購入 $it" }
                    ?: getString(R.string.trial_purchase_button)
                binding.purchaseButton.visibility = View.VISIBLE
            }
            else -> {
                binding.purchaseTitle.setText(R.string.trial_title)
                val days = tm.remainingDays()
                binding.purchaseSummary.text = if (days > 0) {
                    getString(R.string.trial_remaining_days, days)
                } else {
                    getString(R.string.trial_remaining_hours, tm.remainingHours())
                }
                binding.purchaseButton.text = billing?.formattedPrice()?.let { "永続版を購入 $it" }
                    ?: getString(R.string.trial_purchase_button)
                binding.purchaseButton.visibility = View.VISIBLE
            }
        }
    }
}
