package jp.simplist.memo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import jp.simplist.memo.R
import jp.simplist.memo.databinding.ActivityUsageGuideBinding

/**
 * 使い方ガイド (DESIGN_SPEC §7-K)。
 * 章ごとに H2 + 本文 を縦に並べる。AdBlock の流用パターン。
 */
class UsageGuideActivity : ThemedActivity() {

    private lateinit var binding: ActivityUsageGuideBinding

    private data class Chapter(val title: String, val body: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsageGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsInsets()
        binding.backButton.setOnClickListener { finish() }

        val chapters = listOf(
            Chapter(
                "はじめに",
                "シンプルメモ帳 & ToDo は、広告なし・完全ローカル保存のメモアプリです。" +
                    "テキストメモとチェックリストを 1 つのアプリで扱えます。",
            ),
            Chapter(
                "メモを作る / リストを作る",
                "メイン画面右下の「メモ」「リスト」ボタンから新規作成できます。" +
                    "長押しすると保存済みのテンプレートを呼び出せます。" +
                    "新規作成画面ではタイトル欄に自動でフォーカスが入ります。",
            ),
            Chapter(
                "リストの項目を素早く編集する",
                "チェックリストの編集画面では次の操作で素早く項目を扱えます:\n" +
                    "・Enter キー: 直下に新しい項目を追加してフォーカス\n" +
                    "・空の項目で Backspace: その項目を削除して 1 つ前にフォーカスを戻す\n" +
                    "・左右にスワイプ: 項目を削除 (5 秒以内なら「元に戻す」で復元可能)\n" +
                    "・項目右端のハンドルを長押ししてドラッグ: 並び替え\n" +
                    "・フッター直上の「完了済みを削除」: チェック済みの項目をまとめて削除",
            ),
            Chapter(
                "「よく使う項目」で素早く追加する",
                "リスト編集画面の下部 (項目とフッターの間) に、" +
                    "過去に頻繁に入力した項目をチップとして表示します。\n" +
                    "・タップ: その項目をリスト末尾に追加\n" +
                    "・長押し: 確認後、その単語を候補から除外する (今後表示されなくなる)\n" +
                    "・既にリストに含まれている項目は候補から自動的に隠されます\n" +
                    "・設定 → メモの設定 →「よく使う項目を表示」で機能の ON/OFF と表示数 (5 / 8 / 10 / 12) を変更できます",
            ),
            Chapter(
                "色・重要度・タグで整理する",
                "編集画面下部のフッターから設定できます。\n" +
                    "・🎨 色: 18 色から選択。カード背景に反映され、一覧で見分けやすくなります\n" +
                    "・⭐ 重要度: 1〜3 の星を付けると、並び替えで重要度順に並べられます\n" +
                    "・🏷 タグ: 1 メモに 1 タグ。メイン画面上部のタグフィルタで絞り込めます。" +
                    "設定の「タグマークをカードに表示」を ON にすると、カードの右側にタグの先頭文字も表示されます。",
            ),
            Chapter(
                "メモを並び替える",
                "メイン画面ツールバーの ⇅ アイコンで並び替え基準 (更新日順 / 作成日順 / タイトル順 / 重要度順) を選べます。\n" +
                    "好きな順に手動でドラッグして並べたい場合は、右上の ⋮ メニューから「カスタム並び替え」を選んでください。" +
                    "各カードの右端にドラッグハンドルが出るので、長押しで上下に動かせます。「完了」で通常モードに戻ります。",
            ),
            Chapter(
                "メモを検索する",
                "ツールバーの虫眼鏡アイコンから検索できます。タイトル / 本文 / チェックリスト項目のすべてが対象です。",
            ),
            Chapter(
                "複数のメモをまとめて操作する",
                "メイン画面でメモを長押しすると複数選択モードに入ります。続けて他のメモをタップして追加選択できます。\n" +
                    "下のフッターから タグ変更・保護切替・複製・全選択・ゴミ箱に移動 が一括でできます。" +
                    "左上の ✕ または戻るボタンで通常モードに戻ります。",
            ),
            Chapter(
                "ホーム画面に置く（ウィジェット）",
                "ホーム画面の何もない場所を長押し → ウィジェット → 「シンプルメモ帳」から、" +
                    "テキストメモ用 / チェックリスト用 の 2 種類を貼り付けられます。\n" +
                    "配置直後にどのメモを表示するか選択する画面が出ます。" +
                    "ウィジェットの色はメモのカラー設定に連動します。\n" +
                    "チェックリストウィジェットの「+ 項目を追加」を押せば、アプリを開かず項目を追加できます。",
            ),
            Chapter(
                "ロック画面に常駐させる",
                "設定の「ウィジェット通知（チェックリスト）」を ON にすると、ホーム画面に配置済みのチェックリストが" +
                    "ロック画面と通知バーに常駐表示されます。\n" +
                    "通知右上の ∨ をタップして展開すると、各項目をタップで チェック/未チェック を反転できます" +
                    "（ロック解除不要）。Android 13 以降は通知許可が必要です。",
            ),
            Chapter(
                "スタイルを切り替える",
                "設定の「表示」セクションで「標準」と「スタイリッシュ」を切り替えできます。\n" +
                    "・標準: クリーム地 + 角丸 + マルチカラーアイコンの暖かい雰囲気\n" +
                    "・スタイリッシュ: 白地 + 直角 + モノトーンの無機質なノート風\n" +
                    "配置済みのウィジェットも自動で追従します。",
            ),
            Chapter(
                "バックアップを取る",
                "設定画面の「バックアップ・復元」から JSON / TXT 形式でエクスポートできます。" +
                    "機種変更時は JSON でエクスポートして新機種でインポートしてください。\n" +
                    "保存先フォルダを選んで「自動バックアップ」を ON にすると、定期的に自動で書き出されます。",
            ),
            Chapter(
                "ゴミ箱",
                "削除されたメモは 7 日間ゴミ箱に保持されます。設定画面の「ゴミ箱」から復元・完全削除ができます。" +
                    "7 日経過すると自動で完全削除されます。",
            ),
            Chapter(
                "プライバシーロック",
                "設定画面の「プライバシーロック」で生体認証を有効にすると、アプリ起動時に認証を要求します。" +
                    "認証が完了するまで一覧の内容は隠されます。",
            ),
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
