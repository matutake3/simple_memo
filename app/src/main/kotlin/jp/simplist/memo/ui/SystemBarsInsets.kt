package jp.simplist.memo.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk 35 (Android 15) で edge-to-edge が自動有効化され、コンテンツが status bar /
 * gesture nav の下に描画される。何もしないとヘッダーが status bar に隠れて
 * 設定アイコン等が押せない。各 Activity の root view にこれを適用し、
 * system bars の領域だけ padding として逃がす。
 */
fun View.applySystemBarsInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
