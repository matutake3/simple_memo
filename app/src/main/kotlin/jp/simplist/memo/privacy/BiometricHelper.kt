package jp.simplist.memo.privacy

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import jp.simplist.memo.R

/**
 * 生体認証 / 端末認証 (PIN フォールバック) の薄いラッパー。
 * BIOMETRIC_STRONG | DEVICE_CREDENTIAL を要求し、生体が利用できない端末でも
 * 端末 PIN/パスワードで通る挙動にする。
 */
object BiometricHelper {

    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        return mgr.canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit = {}) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.privacy_lock_title))
            .setSubtitle(activity.getString(R.string.privacy_lock_subtitle))
            .setAllowedAuthenticators(ALLOWED)
            .build()
        prompt.authenticate(info)
    }
}
