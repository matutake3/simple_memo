# シンプルメモ帳＆ToDoリスト — DESIGN SPEC v1

**最終更新**: 2026-05-05  
**位置付け**: 別 AI / デザイナー渡し用の意匠仕様書。実装着手前の一次情報。  
**前提**: Simplist シリーズ規約（AdBlock / Volume / Calc / Alarm / Lock の 5 本で確立）の **実装フレームワークは継承するが、配色・トーンは独自路線**。本書はシリーズ共通規約からの逸脱事項と、本アプリ固有の意匠を定義する。

---

## 0. このドキュメントの読み方

- 「§」記号で始まる章番号は本書内参照用。
- 数値（dp / sp / HEX）はすべて確定値。Android リソースとしてそのまま実装可能。
- 画面ごとの章（§7）には ASCII モックアップを必ず併記する。
- 不明点・未決事項は §13 にリストする。
- **本書はシリーズ規約からの「許可された逸脱」を明文化したもの**。本書に書かれていない事項は既存シリーズ規約に従う。

---

## 1. プロダクト・コンセプト

### 1.1 アプリの位置付け
- **製品名**: シンプルメモ帳＆ToDoリスト（英: Simple Memo）
- **シリーズ**: Simplist Android シリーズ第 6 作
- **パッケージ**: `jp.simplist.memo`
- **目的**: 広告クリーン × ウィジェット安定 × ローカル保存 × 必要十分な機能、で日本のメモ帳市場の不満点を地味に潰す

### 1.2 デザインのキーワード
- **柔らかい** — 直角の冷たさを避け、適度に丸めた角で温かみを出す
- **温もりのある** — クリーム地と低彩度パステルを基調にした「日常の道具」感
- **静かに賑やか** — メモごとに色を持つことで一覧画面が彩りを帯びるが、彩度は抑えて主張しすぎない
- **広告ゼロの清潔感** — 余白をたっぷり取り、UI 要素を絞る

### 1.3 デザインで「やらないこと」
- 既存シリーズの `paper #F8F8F6` / `ink` / `graphite` / `hair` パレットの継承
- 既存シリーズのアクセント `accent_calc #7A8268` の流用（独自セージを定義）
- 全要素 0dp 直角（リファレンスが丸めを示しているため例外扱い）
- 派手なグラデーション、3D 質感、shadow / elevation の多用
- イラスト・ステッカー・絵文字の濫用（Inter フォント＋線アイコンの範囲内で表現）
- Compose（View system + ViewBinding 固定）
- PreferenceFragmentCompat（手書き LinearLayout 設定画面）

---

## 2. テーマ・モード前提

### 2.1 ライト基調（既存シリーズと反転）
- v1 はライトテーマのみ提供
- `values/colors.xml` にライト値を直書き
- `values-night/` も同値の duplicate にして実機で常にライト見え（Calc / Lock の方針を「ライト固定」として踏襲）
- 理由: メモ帳は日中に開く頻度が高く、クリーム地のほうが視認性と心理的快適さが上回る。Dark mode は V2 で検討。
- `values/themes.xml` の `windowLightStatusBar=true`、`windowBackground=@color/paper`

### 2.2 minSdk / targetSdk
- minSdk: 26（既存シリーズ準拠）
- targetSdk: 35

---

## 3. カラーシステム

### 3.1 ベース（背景・面）

クリーム地を中心にした 3 段階階層。既存シリーズの dark 3 段階を反転させたイメージ。

| 名前 | HEX | 用途 |
|---|---|---|
| `paper` | `#F4EEE3` | Activity 背景。クリーム地。 |
| `paper_dim` | `#EFE8DA` | 1 段沈み。検索バーの内側、選択中 row のホバー、Snackbar 地 |
| `surface` | `#FBF6EC` | 1 段持ち上げ。ダイアログ・Bottom sheet・カード内のさらに上層 |

### 3.2 文字・線色

| 名前 | HEX | 用途 |
|---|---|---|
| `ink` | `#3D3A36` | 主要文字（タイトル・本文）。ウォームダーク。純黒は使わない。 |
| `ink_secondary` | `#6E6962` | 副次文字、説明文 |
| `graphite` | `#8A8580` | 補助文字、メタ情報、placeholder |
| `graphite_dim` | `#B8B2A8` | 無効状態の文字、最薄文字（バージョン番号など） |
| `hair` | `#E2DCD0` | hairline、border、divider |
| `hair_subtle` | `#EBE6DA` | より目立たない区切り |

### 3.3 アクセント（セージグリーン主軸）

シリーズの olive とは別物。より明るく、ミント寄りの中性セージ。

| 名前 | HEX | 用途 |
|---|---|---|
| `accent_sage` | `#9DAD8E` | プライマリ FAB（リスト）、保護ボタン、セージ系アイコン |
| `accent_sage_dark` | `#869978` | 押下時、stroke、focus |
| `accent_sage_dim` | `#C3CDB7` | 無効状態、低彩度バリアント |
| `accent_sage_text` | `#FFFFFF` | アクセント色背景上の文字（白）。コントラスト確保用 |

#### 3.3.1 副アクセント（ソフトブルー）

「メモ」FAB と共有ボタンに用いる第 2 アクセント。

| 名前 | HEX | 用途 |
|---|---|---|
| `accent_blue` | `#A8C5D6` | 「メモ」FAB、共有ボタン、リンク化テキスト |
| `accent_blue_dark` | `#90B1C4` | 押下時 |

**ルール**: アクセント色は意味付けで使い分ける:
- セージ＝**チェックリスト系**（リスト FAB、保護、タグアイコン、検索アイコン、選択状態）
- ブルー＝**テキストメモ系**（メモ FAB、共有、リンク）
- 色パレットアイコンのカラフルさは **編集画面ツールバー限定**（§7-B 参照）

### 3.4 18色カラーパレット（メモ・タグ共通）

くすみ系パステル 18 色＋デフォルト。色相環 + トーン整理順で並べる。

| ID | 名前 | HEX | 用途想定 |
|---|---|---|---|
| 0 | `color_default` | `#F4EEE3` | デフォルト（背景と同色＝色なし） |
| 1 | `color_cream` | `#F4E5B5` | クリームイエロー |
| 2 | `color_apricot` | `#EFCBA3` | アプリコット |
| 3 | `color_peach` | `#F4C9A7` | ピーチ |
| 4 | `color_coral` | `#F4B6A8` | コーラル ★プリセットタグ「買い物」 |
| 5 | `color_rose` | `#E8B5C4` | ローズ |
| 6 | `color_pink` | `#F4D0CE` | ペールピンク |
| 7 | `color_mauve` | `#C8A8C4` | モーブ |
| 8 | `color_lavender` | `#D7C9E0` | ラベンダー |
| 9 | `color_lilac` | `#C8B8D6` | ライラック |
| 10 | `color_dusty_blue` | `#A8B8CC` | ダスティブルー |
| 11 | `color_sky` | `#BDD8E8` | スカイブルー ★プリセットタグ「プライベート」 |
| 12 | `color_aqua` | `#B8D4D0` | アクア |
| 13 | `color_mint` | `#C8DCC8` | ミント |
| 14 | `color_sage` | `#C2D2B4` | セージ ★プリセットタグ「仕事」 |
| 15 | `color_olive` | `#C4C29A` | オリーブ |
| 16 | `color_beige` | `#E5D9C2` | ウォームベージュ |
| 17 | `color_greige` | `#D5CCC0` | グレージュ |
| 18 | `color_dust_yellow` | `#E0C788` | ダスティイエロー（予備） |

**注**: ID 0 はリスト中の選択 UI では「色なし」として扱う。データ上は `Memo.color = 0` と保存。

#### 3.4.1 タグチップ用の薄色変換

タグはメモと同じ ID を持つが、チップ表示時は彩度・明度を控えめにレンダリングする:

- 背景: 元の色を `paper` と 50:50 でブレンド（実装は `ColorUtils.blendARGB(memoColor, paper, 0.5f)`）
- テキスト: 元の色の HSL で明度を `0.30` に固定した値（暗めの色付き文字）

これによりメモカード（彩度そのまま）とタグチップ（薄め）が同色 ID でも視覚的に区別できる。

#### 3.4.2 検索ヒット強調色

検索結果スニペットの太字ハイライトは、**そのカードの色 ID から派生した「強調色」** を使う:

- 背景がメモカード色そのまま
- 強調テキスト: HSL で明度を `0.35`、彩度を `0.55` に固定（元色から濃く・鮮やかに）
- 例: 黄カード（`#F4E5B5`）→ ゴールド強調（推定 `#A88A2E`）／橙カード（`#F4C9A7`）→ オレンジ強調（推定 `#B26A38`）

### 3.5 状態色

| 名前 | HEX | 用途 |
|---|---|---|
| `state_pressed_overlay` | `#000000` (alpha 6%) | 押下フィードバック（ライト地に黒 overlay） |
| `state_focused_overlay` | `#000000` (alpha 10%) | フォーカス時 |
| `state_disabled_alpha` | (alpha 38%) | 無効化要素全般 |

### 3.6 配色マップ（一画面分の例 — メイン画面）

```
[paper #F4EEE3]               ← Activity 全面
  ├─ Toolbar text [ink]
  ├─ Tag chip selected: bg=surface + shadow
  ├─ Tag chip unselected: bg=hair_subtle + text=ink_secondary
  ├─ MemoCard bg=color_cream/peach/...   ← 各カードの色
  │    ├─ Title [ink]
  │    ├─ TypeIcon [カード色から派生した濃色]
  │    └─ Star [カード色から派生した濃色]
  ├─ Hairline = transparent (カードの間は余白で区切る、線は不要)
  └─ FAB:
       メモ [accent_blue] icon [white]
       リスト [accent_sage] icon [white]
```

---

## 4. タイポグラフィ

### 4.1 フォント
- **Inter のみ**
- ウェイト 3 種:
  - `inter_light.ttf` (300) — placeholder、メタ情報、最薄文字
  - `inter_regular.ttf` (400) — 本文・通常文字
  - `inter_medium.ttf` (500) — タイトル・見出し・ボタンラベル

> **シリーズ既存の `extralight` (200) は不採用**。本アプリには 84sp の超細表示が登場しないため。

### 4.2 用途別スタイル

| スタイル名 | sp | weight | letterSpacing | 色 | 用途 |
|---|---|---|---|---|---|
| `H1` | 22sp | 500 (medium) | 0 | ink | 画面タイトル（メイン Toolbar など） |
| `H2` | 18sp | 500 (medium) | 0 | ink | カード見出し、Section header |
| `Title.Card` | 16sp | 500 (medium) | 0 | ink | メモカード内のメインタイトル |
| `Body.Large` | 16sp | 400 (regular) | 0 | ink | 設定行タイトル、本文 |
| `Body` | 14sp | 400 (regular) | 0 | ink | 本文標準、メモ編集本文 |
| `Body.Sub` | 14sp | 400 (regular) | 0 | ink_secondary | サマリー、検索スニペット |
| `Caption` | 13sp | 400 (regular) | 0 | graphite | 補助情報、設定行サマリー、検索結果の日時 |
| `Caption.Dim` | 12sp | 400 (regular) | 0 | graphite_dim | バージョン番号、最薄文字 |
| `Section` | 12sp | 500 (medium) | 0.05 | ink_secondary | セクションヘッダー |
| `Button` | 14sp | 500 (medium) | 0.02 | accent_sage_text or ink | ボタン上の文字 |
| `Tag` | 13sp | 500 (medium) | 0 | カード色派生濃色 | タグチップの文字 |
| `Placeholder` | 14sp | 300 (light) | 0 | graphite | EditText の hint |

### 4.3 行間 (lineSpacingMultiplier)
- 本文系（Body / Body.Sub）: `1.45`（既存シリーズの `1.35` よりわずかに広く、柔らかい印象に）
- タイトル系（H1 / H2 / Title.Card）: `1.2`
- ボタン: `1.0`

### 4.4 数字専用処理
- 検索結果の日時表示は tabular nums を有効化: `android:fontFeatureSettings="tnum"`

---

## 5. レイアウト基礎

### 5.1 余白ユニット
- 基本: 4dp の倍数（`4 / 8 / 12 / 16 / 20 / 24 / 32`）
- 16dp と 20dp が最も多い（既存シリーズの 24dp より少しコンパクトに）
- 4dp は chip 内部 padding と icon 周りだけ

### 5.2 標準余白パターン

| 用途 | 値 |
|---|---|
| Activity 左右余白 | 20dp |
| MemoCard 内部 padding | 16dp（縦）/ 20dp（横） |
| MemoCard 同士の縦間隔 | 8dp |
| Row 高さ (minHeight) | 56dp |
| Row 内部 paddingVertical | 16dp |
| Section header marginTop | 28dp |
| Section header marginBottom | 8dp |
| FAB margin (right/bottom) | 20dp |
| FAB 同士の間隔 | 12dp |

### 5.3 角丸ルール — **本アプリは丸めを採用** ★シリーズ規約の例外

| 要素 | 角丸 |
|---|---|
| MemoCard | 16dp（柔らかい印象を作る主要素） |
| ダイアログ | 16dp |
| BottomSheet 上端 | 24dp |
| TagChip / フィルタチップ | **完全ピル（高さの半分）** |
| FAB（メモ／リスト） | **完全円形** |
| 共有 / 保護ボタン | **完全ピル** |
| 検索バー | **完全ピル** |
| 通常ボタン（PrimaryButton） | 12dp |
| TextField outline | 12dp |
| Snackbar | 12dp |
| ツールバーのアイコンボタン | 円形 ripple（背景は透明、ripple は丸い） |

**根拠**: リファレンス画像（`branding/` 内のメイン画面・編集画面・検索画面）で明示的に丸めが示されているため、シリーズ規約「全要素 0dp 直角」の例外条件を満たす。本アプリ固有の意匠であり、既存シリーズへの逆流入はしない。

### 5.4 hairline / divider
- 太さ: 1dp、色: `hair #E2DCD0`
- メモ一覧画面では **hairline を使わない**（カード間は 8dp 余白のみで区切る）
- 設定画面の row 同士も hairline なし（行内 paddingVertical=16dp で間が取れる）
- 入れる場所: AppBar 下境界（Toolbar と本体の境）、編集画面のタイトル / 本文の区切り、Section の下

### 5.5 elevation / shadow
- カード本体は **elevation=0**（影なし、フラット）
- 選択中タグチップのみ **elevation=2dp** で「白くて浮いている」感を演出
- FAB は **elevation=4dp**（Material 標準より控えめ）

### 5.6 Material コンポーネントの落とし穴対策
シリーズ共通鉄則を踏襲: `MaterialSwitch` / `MaterialRadioButton` / `MaterialCheckBox` は **デフォルト minHeight=48dp** が設定されており、56dp 行に縦中央寄せで載せると崩れる。

```xml
<style name="SimplistMemo.MaterialSwitch" parent="Widget.Material3.CompoundButton.MaterialSwitch">
    <item name="android:minHeight">0dp</item>
</style>
<style name="SimplistMemo.MaterialRadioButton" parent="Widget.Material3.CompoundButton.RadioButton">
    <item name="android:minHeight">0dp</item>
</style>
<style name="SimplistMemo.MaterialCheckBox" parent="Widget.Material3.CompoundButton.CheckBox">
    <item name="android:minHeight">0dp</item>
</style>
```

---

## 6. 共通コンポーネント

### 6.1 SettingsRow (設定行 / chevron 行)

シリーズ共通の手書き LinearLayout 形式を踏襲。

```
[ 56dp minHeight, paddingVertical=16dp, paddingStart/End=20dp, ?selectableItemBackground ]
├── [LinearLayout vertical, weight=1]
│    ├── Title  (Body.Large, 16sp, ink)
│    └── Summary (Caption, 13sp, graphite, marginTop=2dp) ← 任意
└── Chevron (24dp, drawable=ic_chevron_right, tint=graphite, marginStart=12dp)
```

ドット継承スタイル（`Settings.Row.LabelGroup` 等）は使用 NG。すべて行ごとにインライン。

### 6.2 SectionHeader

```
TextView
  text = "ブロック設定" など
  textAppearance = Section (12sp, 500, letterSpacing=0.05, ink_secondary)
  paddingStart/End = 20dp
  marginTop = 28dp
  marginBottom = 8dp
  textAllCaps = false
```

### 6.3 PrimaryButton (sage)

```
- Height: 52dp
- 横幅: match_parent または wrap_content + minWidth=140dp
- Background: accent_sage（押下時 accent_sage_dark、無効時 accent_sage_dim）
- 角丸: 12dp
- TextColor: accent_sage_text (white)
- TextAppearance: Button (14sp, 500, letterSpacing=0.02)
- 影: elevation=0
```

### 6.4 OutlinedButton (副アクション)

```
- Height: 44dp
- Background: 透明
- Stroke: 1dp hair
- 角丸: 12dp
- TextColor: ink
- TextAppearance: Button
```

### 6.5 PillButton（共有 / 保護 等の編集画面下部ボタン）

リファレンス画像の編集画面下部に登場する完全ピル型ボタン。

```
- Height: 40dp
- Padding: horizontal 20dp、vertical 8dp
- 角丸: 高さの半分（完全ピル）
- 背景: 種別ごとに色分け
   - 共有: accent_blue (alpha 25%) bg、icon=accent_blue、text=accent_blue_dark
   - 保護: accent_sage (alpha 25%) bg、icon=accent_sage、text=accent_sage_dark
- アイコン: 18dp、tint = 該当アクセント色
- TextAppearance: Button (14sp, 500)
```

### 6.6 TagChip（タグフィルタ・タグ表示用）

#### 6.6.1 タグフィルタチップ（メイン画面のフィルタ行）

```
- Height: 32dp
- Padding: horizontal 14dp
- 角丸: 16dp（完全ピル）
- 文字: Tag (13sp, 500)
- 状態:
   - 選択中（=フィルタ ON）: bg=surface、文字=ink、elevation=2dp
   - 未選択（タグ色あり）: bg=blendARGB(タグ色, paper, 0.5f)、文字=タグ色から派生した濃色
   - 「すべて」「未分類」（タグ色なし）: bg=hair_subtle、文字=ink_secondary
   - 「+ 追加」: bg=hair_subtle、文字=ink_secondary、icon=ic_add 14dp
```

#### 6.6.2 タグ管理画面の TagRow（タグの編集行）

```
- 横並び LinearLayout、minHeight 56dp
- 左: タグ色のドット 12dp circle（タグ色 fill）
- 中: タグ名 (Body.Large)
- 右: chevron / 削除アイコン
```

### 6.7 MemoCard（メイン画面の各メモカード） ★ 2026-05-05 実画像確定 ★

リファレンス画像のメイン画面に登場する**横長カード**。本アプリの意匠的中核。

```
[ background=memoColor, 角丸=16dp, padding=16dp, marginX=20dp, marginY=4dp ]
LinearLayout horizontal, gravity=center_vertical
├── TypeIcon (24dp, marginEnd=14dp)
│    ├─ TEXT 種別: ic_note (角に折れの入った紙面)、tint = カード色から派生した濃色（HSL 明度 0.30）
│    └─ CHECKLIST 種別: ic_check_box (チェック付き四角)、tint = カード色から派生した濃色
├── [LinearLayout vertical, weight=1]
│    └── MainText (Title.Card, ink)
│         ・タイトルあり → タイトル
│         ・タイトルなし → 本文 / 最初の項目の冒頭 1 行
└── PriorityStars (横並び LinearLayout、starCount 個)
     ・color = カード色から派生した濃色（HSL 明度 0.30、彩度 0.55）
     ・size = 18dp、間隔 = 0dp
     ・priority = 0 のときは View.GONE
```

**カードの背景色とアイコン・★の色の関係**:
- カード色 `colorXX` に対して、アイコン・★・タイトルアイコン色 = `darkenForOnSurface(colorXX)`
- `darkenForOnSurface` の実装: HSL 変換 → 彩度 0.55 / 明度 0.30 に固定 → ARGB に戻す
- これによりカード色によらず常にコントラスト確保＆色相は引き継がれる

**TypeIcon の塗り**:
- 画像準拠で「アウトライン」スタイル（fill ではなく stroke 1.5dp）
- ic_note は紙の右上の折れラインまで再現（リファレンスどおり）

**カードのタップ／長押し**:
- タップ → 編集画面遷移
- 長押し → コンテキストメニュー（`PopupMenu` を `view` の center にアンカー）

### 6.8 FAB ペア（メモ／リスト） ★ 2026-05-05 実画像確定 ★

メイン画面右下に**横並びで 2 つ**配置する完全円形 FAB。リファレンス画像準拠。

```
配置: 右下、marginEnd=20dp、marginBottom=20dp、互いの間隔=12dp

[FAB メモ]
- 直径 56dp、円形、elevation=4dp
- 背景: accent_blue (#A8C5D6) を minimal saturation に薄めた半透明（実装は accent_blue + alpha 1.0、ただし上に subtle inner shadow なし）
- アイコン: ic_pencil 22dp、tint=accent_blue_dark
- ラベル: 「メモ」を FAB 内部下寄せ、Caption 11sp medium、tint=accent_blue_dark
   ※ 画像準拠で「アイコン上＋テキスト下」の縦並び配置

[FAB リスト]
- 直径 56dp、円形、elevation=4dp
- 背景: accent_sage (#9DAD8E) のソフト版（accent_sage_dim 寄りの色合い、推定 #B5C4A8）
- アイコン: ic_check_square 22dp、tint=accent_sage_dark
- ラベル: 「リスト」、accent_sage_dark
```

**FAB の長押し**: テンプレート選択ダイアログを表示（§4.4 SPEC §8.2 参照）。

**ラベル付き FAB の実装**: Material `ExtendedFloatingActionButton` ではなく、自作の `LinearLayout`（縦）を `MaterialCardView` または角丸 100% の背景に乗せる（メモアイコン上＋テキスト下の縦並びを再現するため）。

### 6.9 Toolbar（メイン画面）

```
[ height=56dp, paper bg, paddingX=16dp ]
├── ic_menu (24dp, ink, marginStart=4dp)
├── Title "シンプルメモ帳 & ToDo" (H1, 22sp medium ink, marginStart=12dp, weight=1)
├── ic_sort (24dp, ink, marginEnd=8dp)        ← 並び替えモード切替
├── ic_search (24dp, ink, marginEnd=8dp)
└── ic_more_vert (24dp, ink, marginEnd=4dp)
下に 1dp hairline (hair_subtle)
```

**ic_sort の状態**:
- 通常モード: アイコン `ic_sort` 24dp、tint=ink、押下で並び替えモードへ
- 並び替えモード中: アイコンの代わりに「完了」TextButton（accent_sage_dark、14sp medium）。押下で通常モードに戻る

**注意**: メイン画面以外のツールバーは画面ごとに構成が異なる（§7 参照）。

---

## 7. 画面ごとの詳細仕様

### §7-A. メイン画面 (`MainActivity`) — メモ一覧 ★ 2026-05-05 実画像確定 ★

**位置付け**: アプリのトップ画面。タグフィルタ＋メモカード一覧＋FAB 2 つ。

**構成（実画像準拠）**:
```
┌─────────────────────────────────────┐
│ [Toolbar paper, 56dp]                │
│ ☰  シンプルメモ帳 & ToDo  ⇅ 🔍  ⋮  │
│ ─────────────────────────── hairline │
│                                     │
│ [Tag filter row, paper, 横スクロール]  │
│ paddingY=16dp, paddingX=20dp         │
│ chip間の gap=8dp                     │
│ [すべて][未分類][仕事][プライベート]  │
│ [買い物][+ 追加]                     │
│                                     │
│ [paper 全面、ScrollView]              │
│ paddingY=4dp                         │
│                                     │
│ ┌─MemoCard 16dp 角丸───────────┐    │
│ │ 📄 来週のプレゼン資料準備  ★★│    │← 黄カード
│ └────────────────────────────┘    │
│ ┌─MemoCard─────────────────────┐    │
│ │ ☑ 買い物リスト             ★│    │← 桃カード
│ └────────────────────────────┘    │
│ ┌─MemoCard─────────────────────┐    │
│ │ 📄 Netflix の解約は12月まで  │    │← 水色カード
│ └────────────────────────────┘    │
│ ┌─MemoCard─────────────────────┐    │
│ │ ☑ 卵 牛乳 パン              │    │← ピンクカード
│ └────────────────────────────┘    │
│ ┌─MemoCard─────────────────────┐    │
│ │ 📄 読書メモ:思考は…       ★★★│    │← ラベンダーカード
│ └────────────────────────────┘    │
│   ...                              │
│                                     │
│             20dp / 20dp margin     │
│                          (📝)(☑) │ ← FAB ペア
└─────────────────────────────────────┘
```

**Tag filter row の挙動**:
- 横スクロール（`HorizontalScrollView`）、内部に `LinearLayout horizontal`
- 「すべて」が初期選択
- 「+ 追加」タップ → タグ作成ダイアログ（§7-G.2）

**EmptyState（メモが 0 件のとき）**:
- 画面中央に縦並び
- アイコン: `ic_note_add` 64dp graphite
- タイトル: H2 ink_secondary 「メモがありません」
- 説明: Body.Sub graphite「下の + ボタンから新しいメモを作りましょう」

**Toolbar の検索アイコン (ic_search)** タップ → SearchActivity 遷移。

**Toolbar の ⇅ (ic_sort)** タップ → 並び替えモード（次項 §7-A.1）へ。

**Toolbar の ⋮ (ic_more_vert)** タップ → PopupMenu（表示モード / ゴミ箱表示 / すべて更新日順に戻す 等。詳細は SPEC §22 で確定）。

**Toolbar の ☰ (ic_menu)** タップ → SettingsActivity 起動。

#### §7-A.1 並び替えモード ★ 2026-05-05 追加 ★

**位置付け**: メイン画面のサブモード。ユーザーがメモを手動で並び替えるための状態。

**構成（並び替えモード時の表示）**:
```
┌─────────────────────────────────────┐
│ [Toolbar paper, 56dp]                │
│ ☰  シンプルメモ帳 & ToDo    完了    │ ← ⇅ が「完了」TextButton に
│ ─────────────────────────── hairline │
│                                     │
│ [Tag filter row は非表示]            │
│                                     │
│ [paper 全面、ScrollView]              │
│ paddingY=12dp                        │
│                                     │
│ ┌─MemoCard─────────────────────┐    │
│ │ 📄 来週のプレゼン資料準備 ★★ ≡│    │ ← 右端に DragHandle
│ └────────────────────────────┘    │
│ ┌─MemoCard─────────────────────┐    │
│ │ ☑ 買い物リスト             ★ ≡│    │
│ └────────────────────────────┘    │
│ ┌─MemoCard─────────────────────┐    │
│ │ 📄 Netflix の解約は12月まで  ≡│    │
│ └────────────────────────────┘    │
│   ...                              │
│                                     │
│ [FAB は非表示]                       │
└─────────────────────────────────────┘
```

**並び替えモードでの差分**:
- Toolbar 右端: `ic_sort` の代わりに「完了」TextButton（accent_sage_dark、14sp medium）
- Tag filter row: `View.GONE`（モード移行時にフィルタは解除し、全件表示で並び替え）
- 各 MemoCard 右端に **DragHandle**（24dp、`ic_drag_handle`、tint = カード色から派生した濃色）を表示
- ★とハンドルが同居する場合は `★(s) [4dp gap] ≡` の順
- FAB ペア（メモ／リスト）: `View.GONE`（誤タップ防止）
- カード長押し時のコンテキストメニュー: 無効化（長押しはドラッグ開始のみ）

**ドラッグ操作**:
- `ItemTouchHelper.SimpleCallback`（`UP | DOWN`）
- カード長押し → ドラッグ開始時に scale 1.0 → 1.02、elevation 0 → 8dp（持ち上がる感）
- 移動中は他カードがアニメーションでスライド
- ドロップ時 elevation 0 へ戻し、`MemoRepository` の `sortOrder` を一括更新

**モード遷移アニメ**:
- 通常 → 並び替え: Tag filter row と FAB が 200ms フェードアウト + slide。ハンドルがカードの右側に slide-in
- 並び替え → 通常: 上記の逆再生
- 「完了」押下時、または Android 戻るボタンで通常モードへ

**「完了」押下時の挙動**:
- 並び替え結果は逐次 commit 済みなので「保存」処理は不要
- 通常モードに戻る際は元のタグフィルタ選択状態を復元（モード突入時に保存しておく）

**「すべて更新日順に戻す」（⋮ メニューから、並び替えモード中のみ表示）**:
- 確認ダイアログ「並び替え順をリセットして、更新日順に戻しますか？」
- OK で全 Memo の `sortOrder` を null に更新
- 並び替えモードを抜けて通常モードへ戻る

### §7-B. メモ編集画面 (`EditMemoActivity`) ★ 2026-05-05 実画像確定 ★

**位置付け**: テキストメモの編集。

**構成**:
```
┌─────────────────────────────────────┐
│ [Toolbar paper, 56dp]                │
│ ←   🎨   ⭐   🏷    🗑              │
│ ─────────────────────────── hairline │
│                                     │
│ [paper 全面、ScrollView]              │
│ paddingX=20dp                        │
│                                     │
│ Title (TextInputEditText, 18sp        │
│        medium ink, hint="タイトル(任意)")│
│  marginTop=20dp                      │
│ ─── hairline (hair, marginY=12dp) ── │
│                                     │
│ Body (TextInputEditText, 16sp         │
│       regular ink,                   │
│       hint="ここに本文を入力...",      │
│       lineSpacingMultiplier=1.45)    │
│ minHeight=画面残り全体                │
│                                     │
│                                     │
│ [画面下部、Pinned LinearLayout]       │
│ ─── hairline ─────────────────────  │
│ paddingY=12dp、gravity=center        │
│ [PillButton "📤 共有"][PillButton "🔒 保護"] │
└─────────────────────────────────────┘
```

**ツールバーアイコンの色**（マルチカラーがリファレンス画像の最大の特徴）:

| アイコン | 用途 | tint |
|---|---|---|
| ← | 戻る | `ink_secondary` |
| 🎨 (ic_palette) | 色選択 | `accent_blue_dark` (#90B1C4) |
| ⭐ (ic_star_outline) | 優先度 | `#D4A93C`（ゴールド系、優先度を象徴） |
| 🏷 (ic_label_outline) | タグ | `accent_sage_dark` (#869978) |
| 🗑 (ic_delete) | 削除 | `#C8896E`（くすみコーラル系、警告を控えめに表現） |

**タイトル / 本文の divider**:
- タイトル下に hairline 1dp（`hair`）
- 本文側にはなし（自然なテキスト流れ）

**色選択ダイアログ**（🎨 タップ）:
- BottomSheet で表示
- 上端角丸 24dp、bg=surface
- 18 色を 3 行 × 6 列のグリッドで配置
- 各色は 40dp の円形 swatch、選択中は外周 stroke 2dp `ink`
- 「色なし」（ID 0）は左上に「なし」テキストで配置

**優先度ダイアログ**（⭐ タップ）:
- BottomSheet で表示
- ラジオ 4 つ: なし / ★ / ★★ / ★★★

**タグ選択ダイアログ**（🏷 タップ）:
- BottomSheet
- 既存タグ一覧（タグ色のドット + 名前）+ 「タグなし」+ 「+ 新規タグ作成」

**自動保存**:
- 入力変更時に debounce 500ms で `MemoRepository.update`
- 戻るボタン押下時にも明示的に保存
- 保存中は「保存中…」を Toolbar 下に細い `LinearProgressIndicator` で表示（accent_sage、3dp）

**自動リンク（Linkify）**:
- フォーカス外 / 表示モードでは Linkify を有効化
- 編集中は Linkify を解除（誤タップ防止）
- リンク色 = `accent_blue_dark`

**画面下部の PillButton (共有 / 保護)**:
- リファレンス画像どおり、画面下端に余白を取って横並び 2 つ
- 共有 = soft blue ピル
- 保護 = soft sage ピル
- 高さ 40dp、間隔 12dp、画面下から 24dp 浮かせる

### §7-C. リスト編集画面 (`EditChecklistActivity`)

**位置付け**: チェックリストの編集。

**構成**:
```
┌─────────────────────────────────────┐
│ [Toolbar (memo編集と同じ)]            │
│ ←   🎨   ⭐   🏷    🗑              │
│ ─────────────────────────── hairline │
│                                     │
│ Title (TextInputEditText)             │
│ ─── hairline ───                     │
│                                     │
│ [RecyclerView (項目リスト)]            │
│ ┌─────────────────────────────┐    │
│ │ ☐ 卵                  [≡]   │    │
│ │ ☐ 牛乳                [≡]   │    │
│ │ ─────────── (subtle hairline) │    │
│ │ ☑ パン                [≡]   │← グレーアウト │
│ └─────────────────────────────┘    │
│                                     │
│ [+ 項目を追加 row, ink_secondary]      │
│                                     │
│ [画面下部、Pinned]                    │
│ ─── hairline ─────────────────────  │
│ [TextButton "完了済みを削除"][PillButton "📤共有"]│
└─────────────────────────────────────┘
```

**ChecklistItemRow**:
```
- minHeight=48dp、paddingX=20dp
- LinearLayout horizontal、gravity=center_vertical
- 左: MaterialCheckBox + SimplistMemo.MaterialCheckBox スタイル
       未チェック: stroke=ink_secondary、check=なし
       チェック: bg=accent_sage、check mark white
- 中: EditText (Body, 14sp regular ink、hint なし、weight=1)
       チェック済みのときは textColor=graphite + strikethrough
- 右: DragHandle (24dp, ic_drag_handle, tint=graphite_dim)
```

**並び替え**: `ItemTouchHelper` で長押し → ドラッグ。

**チェック済みの自動移動**:
- チェック ON 時: 200ms フェード後にリスト下段に移動（既存チェック済み群の最上段に挿入）
- チェック OFF 時: 元の位置（sortOrder 復元）には戻さず、未チェック群の最下段に挿入

**「完了済みを削除」**:
- TextButton、tint=accent_sage_dark
- 押下で「完了済みの項目をすべて削除します」ダイアログ → OK で一括削除

### §7-D. 検索画面 (`SearchActivity`) ★ 2026-05-05 実画像確定 ★

**位置付け**: インクリメンタルサーチ。

**構成（実画像準拠）**:
```
┌─────────────────────────────────────┐
│ [Toolbar paper, 56dp, paddingX=12dp] │
│ ← [🔍 SearchField______________ ✕] ✕│
│ ─────────────────────────── hairline │
│                                     │
│ [paper 全面、ScrollView]              │
│ paddingX=20dp                        │
│                                     │
│ "検索結果：N 件" (Caption, ink_secondary)│
│  marginTop=16dp、marginBottom=12dp   │
│                                     │
│ ┌─SearchResultCard──────────────┐  │
│ │ 📄 来週のプレゼン資料準備    ☆ │  │← 黄カード
│ │  ...グラフ追加、**プレゼン**で │  │
│ │   使うデータも...             │  │
│ │                  今日 10:32   │  │
│ └─────────────────────────────┘  │
│ ┌─SearchResultCard──────────────┐  │
│ │ ☑ 買い物リスト             ☆ │  │← 橙カード
│ │  ...来週の**プレゼン**用コーヒ│  │
│ │   ー豆を買う...               │  │
│ │                  昨日 20:15   │  │
│ └─────────────────────────────┘  │
└─────────────────────────────────────┘
```

**SearchField**:
- TextInputLayout（外形は完全ピル、Outlined Box ではなく `bg=paper_dim` の filled ピル）
- 高さ 40dp、内部 paddingX=14dp
- icon=ic_search 18dp tint=accent_sage_dark（leftIcon）
- 右端の ✕ で入力クリア
- placeholder text="検索ワード入力"、graphite

**Toolbar 右端の ✕**:
- ic_close 24dp tint=ink_secondary
- 押下で SearchActivity 終了 → MainActivity に戻る

**SearchResultCard**:
```
- メイン画面の MemoCard とほぼ同じ意匠（カード色付き、角丸 16dp）
- ただし内部レイアウトは縦並び:
  Row1: TypeIcon + Title + 星
  Row2: スニペット (Body.Sub ink_secondary、太字部分は強調色)
  Row3: 更新日時 (Caption graphite、右寄せ)
- 星アイコンは未点灯（star_outline）でカード色から派生した薄色 tint
   ※ 一覧と違って★の点灯数は表示しない方針（リファレンス画像準拠）
   ※ もし点灯★を表示するなら priority に応じて
```

**スニペット抽出**:
- 検索ワードの最初のヒット位置 ±30 文字
- 前後を `...` で省略
- 検索ワードの部分は `<b>` タグで囲み、`HtmlCompat.fromHtml` でレンダ
- 強調色 = カード色から派生した濃色（§3.4.2 の `darkenForOnSurface` ロジック）

**インクリメンタルサーチの IME 対応**:
- `EditText` の `addTextChangedListener` で onTextChanged を受ける
- ただし `getInputType() & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE` で確定文字を検出は不可なので、
- `onUpdateSelection` を override して、コンポーズ範囲（`composingRegionStart..composingRegionEnd`）外の文字数だけで検索を発火する実装にする
- これにより「お」と打ち始めた瞬間の大量ヒットを防ぐ

### §7-E. 設定画面 (`SettingsActivity`)

**位置付け**: シリーズ共通の手書き LinearLayout 形式。

**構成（上から順、計 11 + 8 項目）**:
```
1. 使い方ガイド
2. よくある質問

[Section header "メモの設定"]
3-1. ★順を有効にする       (Switch)
3-2. ウィジェット通知       (Switch)
3-3. デフォルトのメモ種別    (chevron → ダイアログ: メモ / リスト)
3-4. テンプレート管理       (chevron → TemplateActivity)
3-5. タグ管理               (chevron → TagActivity)
3-6. プライバシーロック     (Switch + 認証フロー)

[Section header "データ"]
3-7. バックアップ・復元     (chevron → BackupActivity)
3-8. ゴミ箱                 (chevron → TrashActivity)

[Section header "アプリ情報"]
4. お問い合わせ
5. このアプリを評価する
6. 友達に共有

[Section header "プライバシーについて"]
   (静的本文 14sp ink_secondary, padding 20dp)
7. プライバシーポリシー
8. 特定商取引法

[Section header "ライセンス"]
9. 無料期間と購入カード     (大カード、動的状態)
10. 購入を復元

(画面最下)
11. バージョン (Caption.Dim, center, marginBottom 24dp)
```

**「無料期間と購入カード」の 3 状態**:
| 状態 | カード見た目 | アクション |
|---|---|---|
| トライアル中 | bg=surface、角丸 16dp、Title "無料期間中"、Body "残り XX 日"、PrimaryButton "永続版を購入 ¥250" | Play Billing flow |
| トライアル切れ | bg=surface、Title "無料期間が終了しました"、Body "新規メモの作成・編集には永続版が必要です。既存のメモは引き続き閲覧・共有できます"、PrimaryButton "永続版を購入 ¥250" | Play Billing flow |
| 購入済み | bg=surface、Title "永続版 ありがとうございます"、Body.Sub "全機能をご利用いただけます" | アクションなし |

### §7-F. ゴミ箱画面 (`TrashActivity`)

**構成**:
```
┌─────────────────────────────────────┐
│ [Toolbar]                           │
│ ←  ゴミ箱                  ⋮       │
│ ─────────────────────────── hairline │
│                                     │
│ [説明 row, paper_dim bg, paddingY=12dp]│
│ Body.Sub ink_secondary               │
│ "7日間経過したメモは自動的に完全削除されます"│
│                                     │
│ [メモカード一覧 (一覧画面と同様)、       │
│  ただし右下に "あと N 日" バッジ追加]    │
│                                     │
│ メモカード長押し → ダイアログ:          │
│ "復元 / 完全削除 / キャンセル"        │
│                                     │
└─────────────────────────────────────┘
```

**残り日数バッジ**:
- カード右下に小さく `Caption.Dim` で「あと 5 日」と表示
- 残り 1 日のときのみ強調色（`#C8896E` くすみコーラル）

**Toolbar の ⋮**:
- 「すべて復元」「すべて完全削除」のメニュー

### §7-G. タグ管理画面 (`TagActivity`)

**位置付け**: タグの追加・編集・削除。

#### §7-G.1 構成
```
┌─────────────────────────────────────┐
│ [Toolbar]                           │
│ ←  タグ管理                          │
│ ─────────────────────────── hairline │
│                                     │
│ [TagRow リスト]                       │
│ ●(セージ) 仕事             >        │
│ ●(水色)   プライベート     >        │
│ ●(コーラル) 買い物         >        │
│ ●(beige)  メモ             >        │
│  ...                                │
│                                     │
│ [+ 新しいタグを追加 row, ink_secondary]│
└─────────────────────────────────────┘
```

#### §7-G.2 タグ作成 / 編集ダイアログ
- BottomSheet
- タグ名 EditText
- 色選択（18 色 grid、§7-B 編集画面の色選択と同形式）
- 「保存 / キャンセル / 削除」ボタン

### §7-H. テンプレート管理画面 (`TemplateActivity`)

タグ管理に準ずる。プリセット 5 つ + ユーザー追加分。各 row はテンプレート名と種別アイコン。

### §7-I. バックアップ・復元画面 (`BackupActivity`)

```
┌─────────────────────────────────────┐
│ [Toolbar] ←  バックアップ・復元       │
│                                     │
│ [Section "自動バックアップ"]          │
│ Switch: 自動バックアップを有効にする   │
│ Caption: "毎日深夜に直近 7 世代を保存" │
│                                     │
│ [Section "手動エクスポート"]          │
│ Row: テキスト形式 (.txt) でエクスポート │
│ Row: JSON 形式 (.json) でエクスポート  │
│                                     │
│ [Section "インポート"]                │
│ Row: ファイルから復元する              │
│  ↳ 確認ダイアログ "上書き / マージ"    │
└─────────────────────────────────────┘
```

### §7-J. オンボーディング (`OnboardingActivity`)

**位置付け**: 初回起動時の簡易案内。本アプリは特殊権限が少ないため、Volume の OnboardingActivity をベースに**ウィジェット案内＋通知権限のみ**の簡素な構成にする。

```
┌─────────────────────────────────────┐
│ [paper #F4EEE3]                     │
│                                     │
│ 24dp marginTop                      │
│ Title (H1, 22sp medium, ink)         │
│ "シンプルメモ帳 & ToDo を始める"     │
│                                     │
│ 8dp                                  │
│ Subtitle (Body, 14sp, ink_secondary, │
│           lineSpacing 1.45)          │
│ "広告なし・完全ローカル保存。         │
│  まずは最初のメモを書いてみましょう。" │
│                                     │
│ 24dp                                 │
│ ┌─ FeatureCard 1 ───────────────┐  │
│ │ ホーム画面ウィジェット          │  │
│ │ よく使うリストをホーム画面に貼る │  │
│ │ ことができます (後で配置 OK)    │  │
│ └────────────────────────────────┘  │
│                                     │
│ ┌─ PermissionCard ───────────────┐  │
│ │ 通知 (Android 13+)              │  │
│ │ チェックリストをロック画面に表示│  │
│ │ するために使います             │  │
│ │              [許可する]        │  │
│ └────────────────────────────────┘  │
│                                     │
│ [PrimaryButton "始める" 52dp]        │
│ 24dp marginBottom                   │
└─────────────────────────────────────┘
```

**FeatureCard 構造**:
```
- bg=surface、角丸 16dp、padding 16dp、marginBottom 12dp
- Title (Body.Large, ink, 500)
- Body (Body.Sub, ink_secondary, marginTop=4dp, lineSpacing 1.45)
```

### §7-K. 使い方ガイド (`UsageGuideActivity`)

シリーズ共通。AdBlock の `UsageGuideActivity` ベース。

**章構成案**:
1. はじめに — このアプリでできること
2. メモを作る / リストを作る
3. 色とタグでメモを整理する
4. ウィジェットをホーム画面に貼る
5. ロック画面でチェックリストを見る
6. メモを検索する
7. バックアップを取る
8. 機種変更時のデータ移行

### §7-L. FAQ (`FaqActivity`)

シリーズ共通。AdBlock の sealed class スタイルを流用。

**Q&A 例**:
- Q. データはクラウドに保存されますか？  
  A. いいえ、すべて端末内に保存されます。外部にデータが送信されることは一切ありません。
- Q. 機種変更時はどうすればいいですか？  
  A. 設定画面の「バックアップ・復元」から JSON 形式でエクスポートし、新機種でインポートしてください。
- Q. ウィジェットでチェックを付けたら、アプリ側にも反映されますか？  
  A. はい、即座に同期されます。
- Q. 無料期間が終わると、もう使えなくなりますか？  
  A. 既存メモの閲覧・共有・検索とウィジェットのチェック操作は無料で継続できます。新規作成・編集には買い切りの永続版が必要です。

---

## 8. アイコン体系

### 8.1 アプリアイコン

**方向性**（branding/ にユーザー追加予定の image を真とする）:
- パステル虹グラデーション（コーラル → イエロー → グリーン → ブルー → パープル）の背景
- 中央: 白い紙のメモ + セージ色の鉛筆
- 紙には 3 本の線（メモ書きを抽象化）
- 全体的に角丸でやわらかいフォルム

**Adaptive icon**:
- 背景: パステル虹グラデーション
- 前景: 紙＋鉛筆ベクター

### 8.2 アプリ内 UI アイコン

**規格**: Material Symbols Outlined ベース、または手書きベクター。tint で色変更可能に。

**必須セット**:
| アイコン名 | サイズ | 用途 |
|---|---|---|
| `ic_menu` | 24dp | ハンバーガー、Toolbar 左端 |
| `ic_search` | 24dp / 18dp | 検索 |
| `ic_sort` | 24dp | 並び替えモード切替（メイン画面 Toolbar、上下矢印 ⇅ のモチーフ） |
| `ic_more_vert` | 24dp | その他メニュー |
| `ic_arrow_back` | 24dp | 戻る |
| `ic_close` | 24dp | 閉じる |
| `ic_chevron_right` | 24dp | 設定行 |
| `ic_palette` | 24dp | 色選択 |
| `ic_star_outline` | 24dp / 18dp | 優先度（未点灯） |
| `ic_star_filled` | 18dp | 優先度（点灯） |
| `ic_label_outline` | 24dp | タグ |
| `ic_delete` | 24dp | 削除 |
| `ic_share` | 18dp | 共有 |
| `ic_lock` | 18dp | 保護 ON |
| `ic_lock_open` | 18dp | 保護 OFF |
| `ic_pencil` | 22dp | メモ FAB |
| `ic_check_square` | 22dp | リスト FAB |
| `ic_note` | 24dp | テキストメモカード（紙の右上に折れ） |
| `ic_check_box` | 24dp | チェックリストカード |
| `ic_check_box_filled` | 24dp | チェック済み |
| `ic_drag_handle` | 24dp | ドラッグハンドル |
| `ic_add` | 14dp / 24dp | + 追加 |
| `ic_note_add` | 64dp | EmptyState |

### 8.3 通知アイコン

- statusBar 用 monochrome アイコン: 24dp、白塗り（OS が tint）
- モチーフ: アプリアイコンの簡略化版（紙＋鉛筆の輪郭）

---

## 9. 通知

### 9.1 チェックリストのロック画面通知
- **チャンネル名**: "チェックリスト"
- **重要度**: IMPORTANCE_LOW（音・バイブなし、ロック画面に常時表示）
- **タイトル**: チェックリストのタイトル（または「無題のリスト」）
- **本文**: 各項目を `☑/□ テキスト` 形式で連結
- **アクション**: なし（ロック画面では直接チェック ON/OFF 不可、Android 通知の仕様）
- **タップ**: 該当リストの編集画面を開く

### 9.2 トライアル終了通知
- **重要度**: IMPORTANCE_DEFAULT
- **タイトル**: "無料期間が終了しました"
- **本文**: "永続版を購入すると新規メモを作成できます。既存メモは引き続き閲覧できます"
- **タップ**: SettingsActivity 起動

### 9.3 バックアップ完了通知
- **重要度**: IMPORTANCE_MIN（ステータスバーに出さない）
- 作成しなくても可。ユーザーから要望があれば V2 で追加。

---

## 10. アニメーション・インタラクション

### 10.1 トランジション
- Activity 遷移: Material default の slide
- BottomSheet: standard slide-up 200ms
- Dialog: center scale 100ms

### 10.2 押下フィードバック
- 全 clickable に `?attr/selectableItemBackground` または rounded rect ripple
- Ripple 色: `state_pressed_overlay`（黒 6%）

### 10.3 状態変化アニメ
- Switch ON/OFF: Material default の thumb スライド
- Checkbox check: Material default
- メモカード長押し → コンテキストメニュー: スケール 0.97 → 1.0、duration 100ms（軽く沈む感）
- チェックリスト項目チェック後の下段移動: 200ms fade + slide

### 10.4 NG な動き
- bouncy / spring physics（柔らかさは色とフォルムで表現、動きは静か）
- ページ全体の parallax
- カードの hover 時 lift（モバイルは hover なし）
- スプラッシュ時のロゴ動き（splash は OS 標準のみ）

---

## 11. アクセシビリティ

### 11.1 contentDescription 必須箇所
- すべてのアイコン-only ボタン（toolbar 全アイコン、FAB、chevron、close、add）
- MemoCard 全体（"テキストメモ、来週のプレゼン資料準備、優先度高"）
- TagChip（"タグ、仕事"）

### 11.2 タッチターゲット
- 最小 48dp × 48dp。Switch / Checkbox / Radio もコンテナ Row が 48dp+ になっていれば OK。
- TagChip は高さ 32dp だが、行全体 48dp 以上の容器に入れる（Tag filter row の paddingY=16dp で確保）

### 11.3 文字サイズ
- システムの font scale 200% でも崩れないこと
- 重要文言は固定 sp 指定 OK だが、本文は sp ベース（dp 禁止）

### 11.4 コントラスト
- ink (#3D3A36) / paper (#F4EEE3): WCAG AA 達成（コントラスト比 9.8:1）
- ink_secondary (#6E6962) / paper: AA 達成（5.4:1）
- graphite (#8A8580) / paper: AA Large 達成（3.6:1）— 補助情報用途に限定
- accent_sage (#9DAD8E) on white: 文字用途の場合は accent_sage_dark を使う（コントラスト比確保）

### 11.5 色弱対応
- メモカードの色だけで情報を伝えない（タイプアイコンとタイトルテキストで補完）
- タグも色＋名前の両方で識別可能

---

## 12. リソース構成（実装ガイド）

### 12.1 colors.xml の主要定数

```xml
<!-- Base -->
<color name="paper">#F4EEE3</color>
<color name="paper_dim">#EFE8DA</color>
<color name="surface">#FBF6EC</color>

<!-- Text / Line -->
<color name="ink">#3D3A36</color>
<color name="ink_secondary">#6E6962</color>
<color name="graphite">#8A8580</color>
<color name="graphite_dim">#B8B2A8</color>
<color name="hair">#E2DCD0</color>
<color name="hair_subtle">#EBE6DA</color>

<!-- Accent (sage) -->
<color name="accent_sage">#9DAD8E</color>
<color name="accent_sage_dark">#869978</color>
<color name="accent_sage_dim">#C3CDB7</color>
<color name="accent_sage_text">#FFFFFF</color>

<!-- Accent (blue) -->
<color name="accent_blue">#A8C5D6</color>
<color name="accent_blue_dark">#90B1C4</color>

<!-- Toolbar icon colors (multi-color scheme) -->
<color name="icon_palette">#90B1C4</color>
<color name="icon_priority">#D4A93C</color>
<color name="icon_tag">#869978</color>
<color name="icon_delete">#C8896E</color>

<!-- 18-color palette (memo + tag) -->
<color name="color_default">#F4EEE3</color>
<color name="color_cream">#F4E5B5</color>
<color name="color_apricot">#EFCBA3</color>
<color name="color_peach">#F4C9A7</color>
<color name="color_coral">#F4B6A8</color>
<color name="color_rose">#E8B5C4</color>
<color name="color_pink">#F4D0CE</color>
<color name="color_mauve">#C8A8C4</color>
<color name="color_lavender">#D7C9E0</color>
<color name="color_lilac">#C8B8D6</color>
<color name="color_dusty_blue">#A8B8CC</color>
<color name="color_sky">#BDD8E8</color>
<color name="color_aqua">#B8D4D0</color>
<color name="color_mint">#C8DCC8</color>
<color name="color_sage">#C2D2B4</color>
<color name="color_olive">#C4C29A</color>
<color name="color_beige">#E5D9C2</color>
<color name="color_greige">#D5CCC0</color>
<color name="color_dust_yellow">#E0C788</color>
```

### 12.2 dimens.xml

```xml
<dimen name="row_min_height">56dp</dimen>
<dimen name="row_padding_vertical">16dp</dimen>
<dimen name="screen_padding_horizontal">20dp</dimen>

<!-- Corner radii -->
<dimen name="card_corner">16dp</dimen>
<dimen name="dialog_corner">16dp</dimen>
<dimen name="bottom_sheet_corner">24dp</dimen>
<dimen name="button_corner">12dp</dimen>
<dimen name="text_field_corner">12dp</dimen>
<!-- Pill shapes are rendered via Cap.ROUND or radius=height/2 in code -->

<dimen name="card_padding_h">20dp</dimen>
<dimen name="card_padding_v">16dp</dimen>
<dimen name="card_gap">8dp</dimen>

<dimen name="section_margin_top">28dp</dimen>
<dimen name="section_margin_bottom">8dp</dimen>

<dimen name="fab_size">56dp</dimen>
<dimen name="fab_margin">20dp</dimen>
<dimen name="fab_gap">12dp</dimen>

<dimen name="tag_chip_height">32dp</dimen>
<dimen name="tag_chip_padding_h">14dp</dimen>
```

### 12.3 styles_extras.xml の必須スタイル

```xml
<!-- Material Override (minHeight=0) -->
<style name="SimplistMemo.MaterialSwitch" parent="Widget.Material3.CompoundButton.MaterialSwitch">
    <item name="android:minHeight">0dp</item>
</style>
<style name="SimplistMemo.MaterialRadioButton" parent="Widget.Material3.CompoundButton.RadioButton">
    <item name="android:minHeight">0dp</item>
</style>
<style name="SimplistMemo.MaterialCheckBox" parent="Widget.Material3.CompoundButton.CheckBox">
    <item name="android:minHeight">0dp</item>
</style>

<!-- TextAppearance -->
<style name="Text.H1" parent="">
    <item name="android:fontFamily">@font/inter_medium</item>
    <item name="android:textSize">22sp</item>
    <item name="android:textColor">@color/ink</item>
</style>
<style name="Text.H2" parent="">
    <item name="android:fontFamily">@font/inter_medium</item>
    <item name="android:textSize">18sp</item>
    <item name="android:textColor">@color/ink</item>
</style>
<style name="Text.Title.Card" parent="">
    <item name="android:fontFamily">@font/inter_medium</item>
    <item name="android:textSize">16sp</item>
    <item name="android:textColor">@color/ink</item>
</style>
<style name="Text.Body.Large" parent="">
    <item name="android:fontFamily">@font/inter_regular</item>
    <item name="android:textSize">16sp</item>
    <item name="android:textColor">@color/ink</item>
</style>
<style name="Text.Body" parent="">
    <item name="android:fontFamily">@font/inter_regular</item>
    <item name="android:textSize">14sp</item>
    <item name="android:textColor">@color/ink</item>
    <item name="android:lineSpacingMultiplier">1.45</item>
</style>
<style name="Text.Body.Sub" parent="">
    <item name="android:fontFamily">@font/inter_regular</item>
    <item name="android:textSize">14sp</item>
    <item name="android:textColor">@color/ink_secondary</item>
    <item name="android:lineSpacingMultiplier">1.45</item>
</style>
<style name="Text.Caption" parent="">
    <item name="android:fontFamily">@font/inter_regular</item>
    <item name="android:textSize">13sp</item>
    <item name="android:textColor">@color/graphite</item>
</style>
<style name="Text.Caption.Dim" parent="">
    <item name="android:fontFamily">@font/inter_regular</item>
    <item name="android:textSize">12sp</item>
    <item name="android:textColor">@color/graphite_dim</item>
</style>
<style name="Text.Section" parent="">
    <item name="android:fontFamily">@font/inter_medium</item>
    <item name="android:textSize">12sp</item>
    <item name="android:letterSpacing">0.05</item>
    <item name="android:textColor">@color/ink_secondary</item>
</style>
<style name="Text.Button" parent="">
    <item name="android:fontFamily">@font/inter_medium</item>
    <item name="android:textSize">14sp</item>
    <item name="android:letterSpacing">0.02</item>
</style>
<style name="Text.Tag" parent="">
    <item name="android:fontFamily">@font/inter_medium</item>
    <item name="android:textSize">13sp</item>
</style>
<style name="Text.Placeholder" parent="">
    <item name="android:fontFamily">@font/inter_light</item>
    <item name="android:textSize">14sp</item>
    <item name="android:textColor">@color/graphite</item>
</style>
```

### 12.4 ファイル配置

```
res/
 ├─ values/
 │   ├─ colors.xml
 │   ├─ dimens.xml
 │   ├─ strings.xml
 │   ├─ styles_extras.xml
 │   ├─ themes.xml
 │   └─ preloaded_fonts.xml
 ├─ values-night/  (同値 duplicate、ライト固定の安全策)
 ├─ font/
 │   ├─ inter_light.ttf
 │   ├─ inter_regular.ttf
 │   └─ inter_medium.ttf
 ├─ drawable/
 │   ├─ ic_*.xml (Vector アイコン群)
 │   ├─ bg_card_memo.xml (shape, 角丸 16dp + 引数で色を渡せるよう layer-list)
 │   ├─ bg_button_primary.xml (shape, 角丸 12dp + accent_sage)
 │   ├─ bg_button_outlined.xml (shape, 角丸 12dp + stroke hair)
 │   ├─ bg_pill_share.xml (shape, 完全ピル + accent_blue 25%)
 │   ├─ bg_pill_protect.xml (shape, 完全ピル + accent_sage 25%)
 │   ├─ bg_chip_filter.xml (selector + shape, 完全ピル)
 │   ├─ bg_search_field.xml (shape, 完全ピル + paper_dim)
 │   └─ bg_fab_memo.xml / bg_fab_list.xml (oval shape)
 └─ mipmap-*/
     └─ ic_launcher (foreground/background)
```

### 12.5 ヘルパー関数（Kotlin）

カード色から派生色を計算する関数を `ui/ColorUtils.kt` に置く:

```kotlin
object MemoColorUtils {
    /** カード色から、その上に重ねるアイコン・★・強調文字の色を計算 */
    fun darkenForOnSurface(@ColorInt cardColor: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(cardColor, hsl)
        hsl[1] = 0.55f  // 彩度
        hsl[2] = 0.30f  // 明度
        return ColorUtils.HSLToColor(hsl)
    }

    /** カード色から、タグチップの淡い背景色を計算 */
    fun blendForChipBackground(@ColorInt cardColor: Int, @ColorInt paper: Int): Int {
        return ColorUtils.blendARGB(cardColor, paper, 0.5f)
    }
}
```

---

## 13. 未決事項（実装着手前にユーザー確認）

### 13.1 確定済み（本書 §3-4 で反映済み）
- アクセント主軸 = `accent_sage #9DAD8E`（独自定義）
- 副アクセント = `accent_blue #A8C5D6`
- 18 色パレット = §3.4 の HEX で確定（画像からの発色推定）
- 角丸 = 本アプリ固有の例外として採用（§5.3 の表）
- ライト基調（v1）、ダークモード提供は V2 で検討
- IAP 商品 ID = `permanent_unlock` ¥250
- トライアル = 14 日

### 13.2 未確定 — デザインに影響する
- アプリアイコンの最終確定（branding/ にユーザー追加予定の image を真とする）
- マルチカラーアイコンの色相選定の微調整余地（特に `icon_priority` のゴールド系、`icon_delete` のくすみコーラル）
- BottomSheet で 18 色を 3×6 配置するか 6×3 配置するか（実機での操作性検証で確定）
- 検索結果カードの星表示有無（現案: 表示しない）

### 13.3 未確定 — デザインに直接影響しないため別途確認
- 自動バックアップの実行時間帯（深夜固定 vs アイドル時）
- ウィジェット表示時の色トーン（壁紙との相性）
- インクリメンタルサーチのデバウンス値（120ms / 250ms）

---

## 14. 別 AI / デザイナー渡し時のチェックリスト

このドキュメントを別 AI に渡す際、以下を必ずセットで提供:

- [ ] 本 DESIGN_SPEC.md（本書）
- [ ] SPEC.md（機能仕様）
- [ ] リファレンス画像 4 点（メイン画面・編集画面・検索画面・アプリアイコン）
- [ ] Inter フォント実物（`inter_light/regular/medium.ttf`）
- [ ] 兄弟アプリの UI スクリーンショット（Calc / Alarm / Lock — シリーズ規約理解用）

---

**この仕様書は v1 の凍結版**。実装中に発見された矛盾・抜け漏れは追記版（v1.1, v1.2, ...）として更新する。本アプリは既存シリーズの規約から意図的に逸脱している部分があるため、シリーズ既存 4 本のリファレンスをコピー流用する際は本書の §3 / §5.3 を真として上書きすること。
