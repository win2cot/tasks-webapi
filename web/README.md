# web/ — フロントエンド

静的 SPA (HTML5 / CSS3 / 素の JS / Bootstrap 5)。npm プロジェクト。  
技術選定の背景は `docs/adr/0022-*.md`〜`docs/adr/0025-*.md` 参照。

## コマンド

```bash
# lockfile から依存をインストール
npm ci

# vendor アセットを node_modules からコピー
npm run copy-vendor

# Biome — JS / CSS / JSON lint + format 確認
npx biome ci .

# html-validate — HTML Living Standard 準拠 + CE メタデータ検証
npm run html-validate
```

## Custom Element メタデータの追従手順

`web/js/components/app-*.js` に新しい Custom Element を追加・変更したとき、  
**`.htmlvalidate.json` の `elements[1]` オブジェクト**を更新してください。

### 新しい CE を追加するとき

```jsonc
// .htmlvalidate.json の elements 配列 2 番目のオブジェクトに追記
"app-my-widget": {
  "flow": true,       // <main> / <div> / <section> 等の flow content 文脈で使う場合
  "phrasing": true,   // <span> / インライン文脈でも使う場合は追加
  "attributes": {
    "my-attr": {},                   // 任意文字列
    "toggle": { "boolean": true }   // boolean 属性 (presence のみ意味を持つ)
  }
}
```

確認事項:
1. `observedAttributes` に列挙した属性はすべて `attributes` に追記する
2. ページ HTML (`index.html` / `tasks.html`) に要素を追記したら `npm run html-validate` で green を確認

### CE を変更・削除するとき

- 属性名を変更: `attributes` キーを新旧で更新
- 要素を削除: 対応するエントリごと削除

### inline event handler ガードについて

`onclick` 等 36 種の on\* 属性は `.htmlvalidate.json` の `"*"` (全要素共通) エントリで  
`{ "deprecated": "..." }` に設定されており、書き戻すと `no-deprecated-attr` エラーになります。  
ADR-0022 の CSP `script-src 'self'` と二重で保護しています。

新しい on\* 属性を追加したい場合は ADR-0022 を参照して設計を見直してください。
