package jp.simplist.memo.data

/**
 * メモ一覧の並び替え基準。AppSettings に永続化。
 *
 * - UPDATED: 更新日順（デフォルト・最も使われる「最後に編集したメモを上に」）
 * - CREATED: 作成日順
 * - TITLE:   名前順（タイトル昇順、空タイトルは下段）
 * - PRIORITY: 重要度順（★が多いものを上に）
 * - CUSTOM:  ユーザーが「カスタム並び替え」でドラッグして決めた順序を保持
 */
enum class ListSortMode { UPDATED, CREATED, TITLE, PRIORITY, CUSTOM }
