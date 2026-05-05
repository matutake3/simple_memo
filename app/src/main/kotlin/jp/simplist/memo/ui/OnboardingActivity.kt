package jp.simplist.memo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import jp.simplist.memo.R
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.databinding.ActivityOnboardingBinding

/**
 * 簡易オンボーディング (DESIGN_SPEC §7-J)。
 * - ウィジェット案内 (informational only)
 * - 通知権限 (Android 13+)
 * - 「始める」ボタンで完了フラグを保存して finish。
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        renderNotifState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.notifGrantButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.startButton.setOnClickListener {
            AppSettings.get(this).onboardingDone = true
            finish()
        }
        renderNotifState()
    }

    override fun onResume() {
        super.onResume()
        renderNotifState()
    }

    private fun renderNotifState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 13 未満は権限不要
            binding.notifCard.visibility = android.view.View.GONE
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        binding.notifGrantButton.text =
            if (granted) getString(R.string.onboarding_granted) else getString(R.string.onboarding_grant)
        binding.notifGrantButton.isEnabled = !granted
        binding.notifGrantButton.alpha = if (granted) 0.5f else 1.0f
    }
}
