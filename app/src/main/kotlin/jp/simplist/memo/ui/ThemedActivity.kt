package jp.simplist.memo.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.simplist.memo.R
import jp.simplist.memo.data.AppSettings
import jp.simplist.memo.data.StyleMode

/**
 * 全 Activity の基底。styleMode に追従して以下を自動で行う:
 *  - onCreate: 現在の styleMode に対応するテーマを setTheme()
 *  - onResume: 設定値が onCreate 時と変わっていたら recreate() (他画面で切替されたとき自分も追従)
 *
 * 例外: QuickAddItemActivity は独自テーマ (Theme.SimpleMemo.QuickAdd) を使うため
 * 引き続き AppCompatActivity を直接継承する。
 */
abstract class ThemedActivity : AppCompatActivity() {

    private lateinit var appliedStyleMode: StyleMode

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedStyleMode = AppSettings.get(this).styleMode
        setTheme(
            when (appliedStyleMode) {
                StyleMode.STANDARD -> R.style.Theme_SimpleMemo
                StyleMode.STYLISH -> R.style.Theme_SimpleMemo_Stylish
            },
        )
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (AppSettings.get(this).styleMode != appliedStyleMode) {
            recreate()
        }
    }
}
