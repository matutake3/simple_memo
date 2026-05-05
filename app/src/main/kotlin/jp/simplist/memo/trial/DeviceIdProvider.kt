package jp.simplist.memo.trial

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Derives a stable, device-and-signing-key-scoped identifier from SSAID
 * (Settings.Secure.ANDROID_ID), then hashes it with a per-app salt so we
 * never store or transmit the raw SSAID itself.
 *
 * See AdBlock's DeviceIdProvider for the full rationale; this is a
 * straight port with the salt rotated to the alarm-specific value.
 */
object DeviceIdProvider {

    private const val SALT = "simplist-memo-v1"

    @SuppressLint("HardwareIds")
    fun deviceHash(context: Context): String {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "fallback-no-ssaid"
        return sha256("$SALT|$raw")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
