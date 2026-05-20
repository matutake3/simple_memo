package jp.simplist.memo.ui

import android.os.Bundle
import android.widget.TextView
import jp.simplist.memo.R
import jp.simplist.memo.databinding.ActivityFaqBinding

/**
 * FAQ。AdBlock の sealed class スタイルを参考に、QA を縦並びでレンダ。
 */
class FaqActivity : ThemedActivity() {

    private lateinit var binding: ActivityFaqBinding

    private data class QA(val q: String, val a: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsInsets()
        binding.backButton.setOnClickListener { finish() }

        val qas = listOf(
            QA(
                "データはクラウドに保存されますか？",
                "いいえ、すべて端末内に保存されます。外部にデータが送信されることは一切ありません。",
            ),
            QA(
                "機種変更時はどうすればいいですか？",
                "設定画面の「バックアップ・復元」から JSON 形式でエクスポートし、新機種でインポートしてください。" +
                    "自動バックアップを使っている場合は、保存フォルダを新機種に移してインポートでも復元できます。",
            ),
            QA(
                "ウィジェットでチェックを付けたら、アプリ側にも反映されますか？",
                "はい、即座に同期されます。逆にアプリ側で変更した内容もウィジェットとロック画面通知に即座に反映されます。",
            ),
            QA(
                "無料期間が終わると、もう使えなくなりますか？",
                "既存メモの閲覧・共有・検索、ウィジェットおよび通知でのチェック操作は無料で継続できます。" +
                    "新規作成・編集・タグやテンプレの追加には買い切りの永続版が必要です。",
            ),
            QA(
                "メモの色は後から変更できますか？",
                "はい。編集画面下部のパレットアイコンから 18 色のいずれかにいつでも変更できます。",
            ),
            QA(
                "重要度って何ですか？",
                "編集画面下部の星アイコンから 1〜3 の重要度を設定できます。" +
                    "並び替え基準を「重要度順」にすると、星の多いメモが上に並びます。",
            ),
            QA(
                "間違えて削除したメモを復元できますか？",
                "削除されたメモは 7 日間ゴミ箱に保持されます。設定 → ゴミ箱から復元できます。" +
                    "7 日経過すると自動で完全削除されます。",
            ),
            QA(
                "リストの項目を素早く削除するには？",
                "項目を左右どちらかにスワイプすると削除できます。直後に画面下に出る" +
                    "「元に戻す」を 5 秒以内にタップすれば復元できます。" +
                    "空の項目で Backspace、または「完了済みを削除」ボタンでまとめて消すこともできます。",
            ),
            QA(
                "「よく使う項目」のチップって何ですか？",
                "リスト編集画面の下部に、過去全リストで頻繁に入力した項目をチップで表示します。" +
                    "タップで末尾に追加、長押しで「候補から除外」できます。" +
                    "設定 → メモの設定 →「よく使う項目を表示」で ON/OFF と件数 (5 / 8 / 10 / 12) を変更できます。",
            ),
            QA(
                "メモを誤って削除しないようにできますか？",
                "編集画面下部の鍵アイコンを ON にすると、そのメモは削除時に確認ダイアログが必要になります。" +
                    "複数選択での一括削除でも、保護中のメモはスキップされます。",
            ),
            QA(
                "複数のメモをまとめて操作したい",
                "メイン画面でメモを長押し → 他のメモをタップして追加選択 → 下のフッターから" +
                    "タグ変更・保護切替・複製・全選択・ゴミ箱への移動 が一括でできます。",
            ),
            QA(
                "ロック画面でチェックを付けることはできますか？",
                "はい。ホーム画面にウィジェットを配置したチェックリストは、設定の「ウィジェット通知」を ON にすると" +
                    "ロック画面に常駐表示されます。通知を展開して各項目をタップすれば、ロック解除なしでチェックを" +
                    "ON/OFF できます。",
            ),
            QA(
                "ウィジェットを置くにはどうすればいいですか？",
                "ホーム画面の何もない場所を長押し → ウィジェット → 「シンプルメモ帳」を選び、" +
                    "テキストメモ用かチェックリスト用のどちらかを貼り付けてください。" +
                    "ウィジェットの色は紐づけたメモのカラー設定に従って自動で決まります。",
            ),
            QA(
                "「スタイル」って何ですか？",
                "設定の「表示」から「標準」と「スタイリッシュ」を切り替えできます。" +
                    "標準はクリーム地 + 角丸 + マルチカラーで暖かい雰囲気、" +
                    "スタイリッシュは白地 + 直角 + モノトーンで無機質なノート風です。" +
                    "配置済みのウィジェットも自動で追従します。",
            ),
            QA(
                "テンプレートはどう使いますか？",
                "設定 → テンプレート管理 から作成・編集できます。" +
                    "よく使うリスト（買い物リスト、出張パッキング、毎日の TODO など）を登録しておくと、" +
                    "メイン画面の メモ / リスト ボタンを長押しした時に呼び出して新規作成できます。",
            ),
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
