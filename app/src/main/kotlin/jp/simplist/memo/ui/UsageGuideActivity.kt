package jp.simplist.memo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.simplist.memo.R
import jp.simplist.memo.databinding.ActivityUsageGuideBinding

/**
 * 使い方ガイド (DESIGN_SPEC §7-K)。
 * 章ごとに H2 + 本文 を縦に並べる。AdBlock の流用パターン。
 */
class UsageGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsageGuideBinding

    private data class Chapter(val title: String, val body: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsageGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val chapters = listOf(
            Chapter("はじめに", "シンプルメモ帳 & ToDo は、広告なし・完全ローカル保存のメモアプリです。テキストメモとチェックリストを 1 つのアプリで扱えます。"),
            Chapter("メモを作る / リストを作る", "メイン画面右下の「メモ」「リスト」ボタンから新規作成できます。長押しでテンプレートから作成できます。"),
            Chapter("色とタグでメモを整理する", "編集画面のパレットアイコンで 18 色から色を選べます。タグアイコンで 1 メモに 1 タグを設定できます。タグはメイン画面の上部でフィルタとして使えます。"),
            Chapter("並び替えモード", "メイン画面のツールバーにある並び替えアイコン (⇅) をタップすると、ドラッグハンドルが表示され、メモを手動で並び替えられます。完了で通常モードに戻ります。"),
            Chapter("メモを検索する", "ツールバーの虫眼鏡アイコンから検索できます。タイトル / 本文 / チェック項目すべてが対象です。"),
            Chapter("バックアップを取る", "設定画面の「バックアップ・復元」から JSON / TXT 形式でエクスポートできます。機種変更時は JSON でエクスポートして新機種でインポートしてください。"),
            Chapter("ゴミ箱", "削除されたメモは 7 日間ゴミ箱に保持されます。設定画面の「ゴミ箱」から復元・完全削除ができます。"),
            Chapter("プライバシーロック", "設定画面の「プライバシーロック」で生体認証を有効にすると、アプリ起動時に認証を要求します。"),
        )

        val inflater = LayoutInflater.from(this)
        for (chapter in chapters) {
            val title = TextView(this).apply {
                text = chapter.title
                setTextAppearance(R.style.Text_H2)
                val mTop = (24f * resources.displayMetrics.density).toInt()
                setPadding(0, mTop, 0, (8f * resources.displayMetrics.density).toInt())
            }
            binding.contentContainer.addView(title)
            val body = TextView(this).apply {
                text = chapter.body
                setTextAppearance(R.style.Text_Body)
            }
            binding.contentContainer.addView(body)
        }
    }
}
