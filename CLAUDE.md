# Simple Memo — アーキテクチャ・ナビゲーション

このファイルは **不具合調査の入り口**。アプリ全体の地図と、症状から該当ファイルを即引きするための索引。
詳細仕様 → `SPEC.md`、ビジュアル → `DESIGN_SPEC.md`、各 `.kt` ファイル冒頭の KDoc。

---

## スタック

- **Kotlin** + Android **AppCompat / Material Components** (**View system**。Compose ではない)
- **Room** で Memo / ChecklistItem / Tag / Template を永続化 (`memos.db`、破壊的マイグレーション禁止)
- **SharedPreferences** で AppSettings (DataStore は v1 では採用せず、軽量 prefs)
- **BiometricPrompt** で プライバシーロック (生体 + 端末 PIN フォールバック)
- **Google Play Billing v7** (`permanent_unlock` ¥250)
- **14 日トライアル** + AdBlock / Lock の anti-reset 三層防御
- **SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`)** で手動バックアップ・復元
- 起動経路: ランチャーのみ (Phase 1)。ウィジェット / ロック画面通知は v1.1 以降で追加予定

---

## ファイル地図 (Phase 1 完了)

```
app/src/main/kotlin/jp/simplist/memo/
├── App.kt                            ★ Application — Trial 初期化 / 通知チャンネル / プリセット投入 / ゴミ箱 7 日 purge
├── ui/
│   ├── MainActivity.kt              ★ メイン画面 (タグフィルタ + メモ一覧 + FAB ペア + 並び替えモード)
│   ├── EditMemoActivity.kt          ★ テキストメモ編集 (自動保存 debounce 500ms)
│   ├── EditChecklistActivity.kt     ★ チェックリスト編集 (ドラッグ並び替え + チェック後の自動下段移動)
│   ├── SettingsActivity.kt          ★ 11 共通項目 + 8 固有項目 + 購入カード
│   ├── OnboardingActivity.kt        ★ 簡易オンボ (ウィジェット案内 + 通知権限)
│   ├── TagManagerActivity.kt        ★ タグ追加・編集・削除 (TagAdapter 同居)
│   ├── TemplateManagerActivity.kt   ★ テンプレ追加・編集・削除 (TemplateAdapter 同居)
│   ├── TrashActivity.kt             ★ ゴミ箱 (TrashAdapter 同居 / 7 日後 purge)
│   ├── UsageGuideActivity.kt        ★ 章立てガイド
│   ├── FaqActivity.kt               ★ Q&A
│   ├── TagEditDialog.kt             ★ タグ作成 / 編集 / 削除ダイアログ
│   ├── adapters/
│   │   ├── MemoListAdapter.kt       ★ メイン画面のメモカード (sortMode 中ドラッグ対応)
│   │   ├── ChecklistItemAdapter.kt  ★ チェックリスト項目行
│   │   └── TagFilterRowBinder.kt    ★ タグフィルタ chip 行 (横スクロール、selected/unselected/カラー対応)
│   └── dialogs/
│       ├── ColorPickerBottomSheet.kt
│       ├── PriorityPickerBottomSheet.kt
│       ├── TagPickerBottomSheet.kt
│       └── TemplatePickerBottomSheet.kt
├── search/
│   ├── SearchActivity.kt            ★ インクリメンタルサーチ (debounce 250ms)
│   └── SearchResultAdapter.kt       ★ ヒットスニペット + 太字ハイライト + 更新日時
├── data/
│   ├── Memo.kt                      ★ Room @Entity (TEXT / CHECKLIST 共通)
│   ├── ChecklistItem.kt             ★ Room @Entity (FK CASCADE on Memo)
│   ├── Tag.kt                       ★ Room @Entity (最大 10、color は 18 色 ID 空間)
│   ├── Template.kt                  ★ Room @Entity (CHECKLIST は itemsCsv で改行区切り保存)
│   ├── Converters.kt                ★ MemoType <-> String
│   ├── MemoDao.kt                   ★ DAO: observeActive / observeTrash / sortOrder 管理 / purgeExpired
│   ├── ChecklistDao.kt              ★ DAO
│   ├── TagDao.kt                    ★ DAO
│   ├── TemplateDao.kt               ★ DAO
│   ├── MemoDatabase.kt              ★ Room database (memos.db、v1)
│   ├── MemoRepository.kt            ★ DAO ラッパ (singleton)
│   ├── TagRepository.kt             ★ 同上、MAX_TAGS=10、削除時の memo.tagId クリアも実装
│   ├── TemplateRepository.kt        ★ 同上
│   ├── AppSettings.kt               ★ SharedPreferences ラッパ (★順 / ウィジェット通知 / デフォ種別 / プライバシー / オンボ完了 / プリセット seed 済 / lastUnlock)
│   └── Presets.kt                   ★ 初回起動時のプリセット投入 (タグ 3 + テンプレ 5)
├── trial/
│   ├── TrialManager.kt              ★ 14 日、salt = "simplist-memo-v1"、canEditMemos / canCreateMemos
│   └── DeviceIdProvider.kt          ★ SSAID + SHA-256
├── billing/
│   └── BillingManager.kt            ★ Play Billing v7 (permanent_unlock)
├── backup/
│   ├── BackupManager.kt             ★ JSON / TXT エクスポート、JSON インポート (overwrite / merge)
│   └── BackupActivity.kt            ★ SAF launcher (CreateDocument / OpenDocument) で UI 提供
├── privacy/
│   ├── BiometricHelper.kt           ★ BiometricPrompt の薄いラッパ (BIOMETRIC_STRONG | DEVICE_CREDENTIAL)
│   └── PrivacyLockController.kt     ★ Activity の onResume で呼ぶ guard
└── util/
    ├── MemoColorUtils.kt            ★ 18 色解決 + HSL 派生色 (アイコン・★・スニペット強調用)
    └── TimeFormat.kt                ★ 「今日 HH:mm」「昨日 HH:mm」「あと N 日」表示

app/src/main/res/
├── layout/
│   ├── activity_main.xml             ★ Toolbar + タグフィルタ + RecyclerView + FAB ペア
│   ├── item_memo_card.xml            ★ メモカード (角丸 16dp、background tint 動的)
│   ├── activity_edit_memo.xml        ★ テキスト編集 (Toolbar + 共有/保護 PillButton)
│   ├── activity_edit_checklist.xml   ★ チェックリスト編集
│   ├── item_checklist_row.xml
│   ├── activity_search.xml           ★ ピル型検索バー + 結果 RecyclerView
│   ├── item_search_result.xml
│   ├── activity_settings.xml         ★ 11 + 8 項目 + 購入カード
│   ├── activity_simple_list.xml      ★ Tag / Template / Trash 共有レイアウト
│   ├── item_tag_row.xml / item_template_row.xml / item_trash_card.xml
│   ├── activity_backup.xml           ★ SAF 連携の Export/Import 行
│   ├── activity_onboarding.xml       ★ ウィジェット案内 + 通知権限カード
│   ├── activity_usage_guide.xml / activity_faq.xml
│   ├── sheet_color_picker.xml        ★ 18 色グリッド + 「色なし」
│   ├── cell_color_swatch.xml         ★ 1 swatch 分 (drawable は code 側で GradientDrawable)
│   ├── dialog_tag_edit.xml           ★ 名前 + 色グリッド
│   └── dialog_template_edit.xml      ★ 名前 + 種別 + タイトル + 本文/項目
├── values/
│   ├── colors.xml                    ★ ライトパレット (paper #F4EEE3 / accent_sage #9DAD8E / 18 色)
│   ├── themes.xml                    Theme.SimpleMemo + ThemeOverlay.SimpleMemo.Dialog / BottomSheet
│   ├── styles_extras.xml             HairLine / SettingsRowInline / SettingsChevron / SectionHeaderInline / Text.* / SimpleMemo.Material*
│   ├── strings.xml                   日本語文言 (em-dash 不使用)
│   ├── dimens.xml                    余白・角丸・FAB サイズ等
│   ├── preloaded_fonts.xml / font_certs.xml   GMS Downloadable Fonts
├── values-night/                     同値の duplicate (本アプリは v1 ライト固定)
├── font/                             inter_light / inter_regular / inter_medium (Downloadable Fonts)
├── drawable/                         アイコン + bg shape (角丸あり、本アプリ固有の例外)
├── menu/                             main_more / memo_context / trash_more
├── mipmap-*/                         ic_launcher (simple_lock から流用、ユーザーの指定画像で再生成予定)
└── xml/
    ├── backup_rules.xml              memos.db / trial_state / purchase_state / onboarding / app_settings 包含
    └── data_extraction_rules.xml     API 31+ 同等

branding/
└── source.png                        ChatGPT 生成オリジナル (パステル虹 + 紙 + セージ鉛筆)
```

★ = Phase 1 で実装済み。

---

## コアコンセプト

### 「ライト固定」の理由
SPEC §0 / DESIGN_SPEC §2.1 の通り、本アプリはシリーズ規約 (dark 固定) から意図的に逸脱する。
メモ帳は日常的に何度も開くアプリで、視覚的な"心地よさ"が継続利用に直結するため。
`values-night/` は同値 duplicate に揃え、システム設定が dark でも light 見え。

### 「角丸あり」の理由 (シリーズ例外)
リファレンス画像が明示的に強い丸めを示しているため、シリーズ規約「全要素 0dp 直角」の
例外条件を満たす。本アプリ固有 (memo) のみで採用。既存 5 本には逆流入させない。
`feedback_corner_radius.md` 末尾で例外を明記済み。

### MemoColorUtils.darkenForOnSurface
カードの 18 色背景の上に乗るアイコン・★・検索ハイライトは、カード色から派生した
「彩度 0.55 / 明度 0.30」の濃色を使う (HSL 変換)。これによりカード色が変わっても
コントラストが保たれ、色相は引き継がれる。タグチップの淡い背景は paper との 50:50 ブレンド。

### トライアル切れ後の挙動
- `canEditMemos()` = false → 編集 / 新規作成 / タグ追加 / テンプレ追加 / バックアップを全て無効化
- 閲覧 / 共有 / 検索 は無料継続
- 各 Activity の操作前に `TrialManager.get().canEditMemos()` でガード
  → false なら「無料期間が終了したため…」のダイアログを出して操作中断
- 設定画面の購入カードから復活

### 並び替えモード
- メイン画面 Toolbar の `ic_sort` で開始
- 開始時: タグフィルタ非表示 / FAB 非表示 / 各カード右端にドラッグハンドル表示 / フィルタは「すべて」に固定 (突入前の selection は復元用に保存)
- ドラッグハンドル長押し → ItemTouchHelper でドラッグ
- リリースで `MemoRepository.applyManualOrder(orderedIds)` を一括適用
- 「完了」または戻るボタンで通常モード復帰
- ⋮ メニューに「すべて更新日順に戻す」を提供 → `clearManualOrders()` で `sortOrder = NULL` 一括リセット

### プリセットの seed
`App.onCreate` で `Presets.seedIfNeeded` を呼ぶ。`AppSettings.presetsSeeded` で 1 回限り。
バックアップ復元と競合しないよう、tag/template の count が 0 のときだけ投入する二重ガード。

---

## アーキテクチャ上の落とし穴

### 1. Room の破壊的マイグレーション禁止
`fallbackToDestructiveMigration()` を絶対に呼ばない。ユーザーのメモが消えると致命傷。
スキーマ変更は `MIGRATION_n_n+1` で ALTER TABLE。

### 2. ListAdapter と sortMode の併用
`MemoListAdapter` は通常モードでは ListAdapter の `submitList()` を使うが、sortMode 中は
`workingList` で完全制御 + `notifyDataSetChanged()` を使い分ける。`getItemCount()` を
override して両モードを切り替えていることに注意。

### 3. setBackgroundTintList と stroke の問題
`bg_color_swatch.xml` の shape drawable に `<stroke>` を持たせると tint で stroke 色も
変わってしまう。色 swatch は `GradientDrawable` を programmatic に組み立て、stroke を
別色に明示する (ColorPickerBottomSheet / TagEditDialog で実装)。

### 4. 自動保存の競合
EditMemo / EditChecklist は debounce 500ms で自動保存しつつ、戻るボタン押下時にも
明示保存する。`onPause` でも保存を行うため、Activity 切替時の取りこぼしを防ぐ。
ただし「ピッカー BottomSheet で値変更」は即時保存 (debounce ではない) で別フロー。

### 5. SAF ファイル拡張子
`ActivityResultContracts.CreateDocument("text/plain")` は MIME を渡す。
JSON は `application/json`、TXT は `text/plain`。OpenDocument 側は複数 MIME を許可
(`arrayOf("application/json", "text/plain", "*/*")`)。

### 6. 全角丸採用 (シリーズ規約の例外)
本アプリのみ角丸を採用 (リファレンス画像準拠)。
カード 16dp / TagChip / FAB / PillButton / 検索バーはピル形状。
他の Simplist アプリには逆流入させない。

### 7. プライバシーロックの session
`PrivacyLockController.guard` は `lastUnlockedAt` から 24h 以内なら認証スキップ。
アプリ終了時に明示的にリセットするコードは入れていない (再起動 = 別 process = 別 session)。
SharedPrefs 側の `lastUnlockedAt` がプロセス再起動でも残るため、`SESSION_TTL_MS` だけが
ガードになる。実用上は十分だが、より厳格にするならプロセス起動時に値を 0 にリセット可。

### 8. Material switch / radio / checkbox の minHeight=48dp
シリーズ規約: `SimpleMemo.MaterialSwitch` 等のスタイルで `minHeight=0dp` を強制。
SettingsActivity / EditChecklistActivity / TemplateManagerActivity の dialog で適用済み。

### 9. 角丸 dialogCornerRadius
ThemeOverlay.SimpleMemo.Dialog で `dialogCornerRadius` = 16dp、`shapeAppearanceMediumComponent`
で 16dp 角丸を明示。BottomSheet は別 ThemeOverlay で上端だけ 24dp。

### 10. 画像準拠のマルチカラーアイコン
編集画面の 🎨 / ⭐ / 🏷 / 🗑 は色を意味付けで使い分け (DESIGN_SPEC §3.3、§7-B)。
変更時は `colors.xml` の `icon_palette` / `icon_priority` / `icon_tag` / `icon_delete` を更新。

---

## 不具合カテゴリ → 容疑者ファイル

| 症状 | まず疑う箇所 |
|---|---|
| **メイン画面が空のまま** | `MainActivity.observeData` の collect / `MemoRepository.observeActive` の Flow / Room schema バージョン |
| **メモが保存されない** | `EditMemoActivity.commitNow` の早期 return / debounce の `saveJob.cancel` / Repository.updateMemo |
| **チェックを ON にしても順序が変わらない** | `ChecklistItemAdapter.submit` の `sortedWith(compareBy(checked, sortOrder))` |
| **検索で結果が出ない** | `SearchActivity.runSearch` の combined 構築 / `indexOf(q, ignoreCase = true)` |
| **タグフィルタが反応しない** | `TagFilterRowBinder.bind` の re-bind タイミング / `MainActivity.refreshList` |
| **並び替えしても元に戻る** | `commitManualOrder` の呼び出し / `ItemTouchHelper.SimpleCallback.clearView` |
| **トライアル切れ後も編集できる** | 各 Activity の `ensureCanEdit()` ガード抜け / `TrialManager.canEditMemos()` |
| **購入後も無料版扱い** | `BillingManager.handlePurchase` → `TrialManager.markPurchased`。ack 失敗 / `purchase_state` キー不一致 |
| **プライバシーロックが反応しない** | `MainActivity.onResume` の `PrivacyLockController.guard` 呼び出し / `BiometricHelper.canAuthenticate` の判定 |
| **18 色のうち色がうまく出ない** | `MemoColorUtils.resolve` の when 文に対応 ID あるか / `colors.xml` の color_* 全 18 件揃っているか |
| **バックアップ復元で項目が一部欠落** | `BackupManager.importJson` の `optJSONArray("items")` / null 安全性 |
| **新規作成画面で前のテンプレが残る** | `EditMemoActivity` の extras 受け取り順 / `EditMemoActivity.EXTRA_TEMPLATE_ID` |
| **オンボーディングが毎回出る** | `AppSettings.onboardingDone` の保存 / `MainActivity.onCreate` のチェック |

---

## ビルド / 動作確認

- Android Studio Hedgehog 以降、実機 Android 8.0+ 必須
- offline-first、ネットワーク権限なし
- 開発中のトライアル無視: `gradle.properties` で `trialBypass=true` (debug 既定 ON)
- 初回起動でプリセットタグ 3 + テンプレ 5 が自動挿入されるため、タグフィルタ行は最初から賑やか

---

## Phase 2 以降の予定

- ウィジェット (テキストメモ / チェックリスト)
- ロック画面通知 (チェックリスト用)
- 自動バックアップ (WorkManager で深夜実行、直近 7 世代)
- ダークモード対応
- 英語対応 (values-en)
- Play Store 申請準備 (privacy ページ / スクショ / 特商法行追加)

---

## Google Play 申請前チェックリスト ★

### 🔴 必須

- [ ] **`targetSdk = 35` / `compileSdk = 35`** ✅ 済み (build.gradle.kts)
- [ ] **`POST_NOTIFICATIONS`** の使用理由を Play Console に明記 (チェックリストのロック画面表示)
- [ ] **`USE_BIOMETRIC`** の使用理由を明記 (プライバシーロック)
- [ ] **完全ローカル動作** (ネットワーク権限なし) を申請文で示す

### 🟡 強く推奨

- [ ] `gradle.properties` の `trialBypass=false` を確認 (リリースビルド前)
- [ ] Release ビルドで `BuildConfig.TRIAL_BYPASS == false` を目視確認
- [ ] 実機 (Android 15 / Pixel / Samsung) で: メモ作成 → 編集 → 共有 → 削除 → 復元 → 検索 → エクスポート → インポート の一連フローを確認
- [ ] `morphyca-site/products/simple-memo/privacy/index.html` を Volume / Calc テンプレから作成してデプロイ
- [ ] Play Console の Privacy Policy URL = `https://morphyca.com/products/simple-memo/privacy/`
- [ ] Play Console の Website 欄 = `https://morphyca.com/products/simple-memo/`
- [ ] Data Safety フォーム: 「データ収集なし / 外部送信なし」
- [ ] アプリ内課金商品 `permanent_unlock` を Play Console で登録 (¥250、買い切り、非消費型)
- [ ] スクリーンショット (DESIGN_SPEC §7 の主要画面)
- [ ] フィーチャーグラフィック (`branding/` の virtual icon をベースに)
- [ ] 特商法ページ `https://morphyca.com/legal/tokushoho/` の表に Simple Memo 行を追加

### 🟢 確認済み (コード側はクリア)

- `Log.*` は `BuildConfig.DEBUG` でガード
- ProGuard / R8 設定済み (Lock から流用、パッケージ名は memo に修正)
- Analytics / Crashlytics / Firebase 等の SDK 未使用
- AndroidManifest の `exported` 属性 API 31+ 仕様準拠 (MainActivity = true、それ以外は false)
- Room の破壊的マイグレーション禁止
- gmail bug なし (連絡先は `info@morphyca.com` 一本化)
- em-dash 不使用 (strings.xml 全箇所確認済み)
- Markdown 記法も strings.xml には不使用
