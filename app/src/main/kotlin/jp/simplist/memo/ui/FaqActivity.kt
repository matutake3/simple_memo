package jp.simplist.memo.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.simplist.memo.R
import jp.simplist.memo.databinding.ActivityFaqBinding

/**
 * FAQ。AdBlock の sealed class スタイルを参考に、QA を縦並びでレンダ。
 */
class FaqActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFaqBinding

    private data class QA(val q: String, val a: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val qas = listOf(
            QA("データはクラウドに保存されますか？", "いいえ、すべて端末内に保存されます。外部にデータが送信されることは一切ありません。"),
            QA("機種変更時はどうすればいいですか？", "設定画面の「バックアップ・復元」から JSON 形式でエクスポートし、新機種でインポートしてください。"),
            QA("ウィジェットでチェックを付けたら、アプリ側にも反映されますか？", "はい、即座に同期されます。"),
            QA("無料期間が終わると、もう使えなくなりますか？", "既存メモの閲覧・共有・検索とウィジェットのチェック操作は無料で継続できます。新規作成・編集には買い切りの永続版が必要です。"),
            QA("メモの色は後から変更できますか？", "編集画面のパレットアイコンから 18 色のいずれかにいつでも変更できます。"),
            QA("間違えて削除したメモを復元できますか？", "削除されたメモは 7 日間ゴミ箱に保持されます。設定 → ゴミ箱から復元できます。"),
            QA("メモを誤って削除しないようにできますか？", "編集画面の「保護」ボタンを ON にすると、削除時に確認ダイアログが出るようになります。"),
            QA("ロック画面でチェックを付けることはできますか？", "Android の通知仕様上、通知からのチェック ON/OFF はできません。一覧の表示のみ可能です。"),
        )

        for (qa in qas) {
            val q = TextView(this).apply {
                text = "Q. ${qa.q}"
                setTextAppearance(R.style.Text_Body_Large)
                val pad = (16f * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, 4)
            }
            binding.contentContainer.addView(q)
            val a = TextView(this).apply {
                text = "A. ${qa.a}"
                setTextAppearance(R.style.Text_Body_Sub)
                setPadding(0, 0, 0, (8f * resources.displayMetrics.density).toInt())
            }
            binding.contentContainer.addView(a)
        }
    }
}
