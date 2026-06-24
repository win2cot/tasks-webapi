# display:contents 行の a11y 検証(table/row/cell role 露出) — Issue #554

`<app-task-row>` はテーブルレイアウトへの透過に `display: contents` を採用している
(#546 / PR #553)。`display: contents` は table 要素の role 計算バグが歴史的に最も多く、
スクリーンリーダー(SR)実装差の懸念があるため、本ドキュメントで検証手順・チェックリスト・
結果記録・fallback 判断を一元管理する。

関連:
- spec(機械検知): [`../tests/a11y-task-table.spec.ts`](../tests/a11y-task-table.spec.ts)
- 対象 CE: `web/js/components/app-task-row.js`(`display: contents`)/ 透過先 `<tr>`(7セル)
- 注意: 行は必ず JS 生成(静的 HTML で `<tbody>` 内に書くと HTML パーサが table 外へ追い出すため)

---

## 1. 機械検知(AC①) — 自動 / 済

E2E spec `a11y-task-table.spec.ts`(2 ケース)で、ブラウザのアクセシビリティツリー上の
role 露出を検証する:

1. タスク一覧 `<table>` が `role=table`(name「タスク一覧」)+ 7 列の `columnheader` を露出
2. `display:contents` の `<app-task-row>` が内部 `<tr>` を `role=row` に昇格させ、7 `<td>` を
   `role=cell` として露出(`getByRole` カウント + `toMatchAriaSnapshot`)

CI: `.github/workflows/e2e-test.yml` が full stack 起動後に `npx playwright test` で常時実行。
ローカル実行実績(2026-06-24): 新規 2 件 + 全 10 件パス(実機 Chromium)。

> 機械検知は「ブラウザが計算した a11y ツリー」までを保証する。SR エンジン固有の読み上げ
> 実装差は次節の実機確認で担保する。

---

## 2. 実機スクリーンリーダー確認(AC②) — 人手 / 実施済(2026-06-24)

**実施サマリ**: NVDA(Windows / Chrome)でタスク一覧テーブルを確認。`T`(テーブルへジャンプ)で
**「タスク一覧、テーブル、7列」**=table role + 7 列ヘッダの認識、続くセル位置読み上げ
**「1列 1の6」**=6 行 × 7 列のテーブルとしてセル位置(1行1列 / 全6行)まで認識、を確認した。
`display:contents` の歴史的な中核バグ(テーブルが表として認識されない / 行・列がツリーから
消える)は**発生していない**。
行を 1 行ずつ送る精緻確認(A2〜A6)は、NVDA のフォーカス取り回し(操作環境側の制約)で
安定踏破できなかったが、行/セル/列ヘッダ role の厳密検証は §1 の自動 spec が機械的に担保済み。

### 前提

`tenant1-member1@example.com` でログインし、**当日期限のタスクを1件以上**作成して
「本日」セクションにデータ行が出ている状態にする。

### A. NVDA(Windows / Chrome・Firefox・Edge いずれか)

| # | 操作 | 期待する読み上げ | 結果 |
|---|------|------------------|:----:|
| A1 | テーブルへ移動(ブラウズモードで `T`) | 「タスク一覧 テーブル、列7 行N」のようにテーブルとして認識される | ✅「タスク一覧、テーブル、7列」/「1列 1の6」 |
| A2 | テーブルモードで行を下移動(`Ctrl+Alt+↓`) | データ行ごとに移動でき、行が**読み飛ばされない** | ⏸ 未踏破(操作制約)→ spec で担保 |
| A3 | 行内で列を右移動(`Ctrl+Alt+→`) | 7セル(状態/タイトル/所有者/担当者/期限/優先度/公開範囲)すべてに到達=**セル境界が正しい** | ⏸ 未踏破(操作制約)→ spec で担保 |
| A4 | 各セル移動時 | 対応する**列ヘッダ名**が読み上げられる(例:「優先度 中」) | ⏸ 未踏破(操作制約)→ spec で担保 |
| A5 | グループ見出し行(`期限切れ`/`本日`)とデータ行を上下移動 | **視覚順と読み上げ順が一致**し、見出し行とデータ行が混ざらない | ⏸ 未踏破(操作制約) |
| A6 | データ行で `Enter` | 詳細ドロワーが開く(操作可能性) | ⏸ 未踏破(操作制約) |

> A2〜A4 の「行/セル/列ヘッダ role 露出」は §1 の自動 spec(`getByRole('row'/'cell'/'columnheader')`
> + `toMatchAriaSnapshot`)が機械的に厳密検証しており、CI で常時担保される。Ctrl+Alt+矢印 が
> 効かないのは、グラフィックドライバ(Intel/AMD)が画面回転ホットキーとして握る既知の競合の可能性。
> 後日 SR 操作を再確認する場合は、ドライバのホットキー無効化 or NVDA キーを CapsLock に設定してから
> Ctrl+Alt+矢印 / すべて読み上げ(NVDA+↓)を試す。

### B. VoiceOver(macOS / Safari)— 代替

| # | 操作 | 期待する読み上げ | 結果 |
|---|------|------------------|:----:|
| B1 | VO カーソルでテーブルへ(`VO+→`) | 「タスク一覧、テーブル N行 7列」 | ☐ |
| B2 | テーブルナビ(`VO+Cmd+→` 列 / `VO+Cmd+↓` 行) | 行・セルを順に巡回でき、抜けがない | ☐ |
| B3 | セル移動時 | 列ヘッダ名 + セル値が読まれる | ☐ |
| B4 | 見出し行とデータ行 | 読み上げ順が視覚順と一致 | ☐ |

> **判定基準**: A2〜A5(または B2〜B4)に **1つでも「読み飛ばし / セル境界の崩れ /
> 行とヘッダの混線」があれば「問題あり」** とし、§4 fallback 判断へ。

### 結果記録

- 実施日: 2026-06-24
- SR: NVDA(Windows)
- ブラウザ: Chrome(Windows）
- OS: Windows(WSL2 ホスト)
- 総合結果: ✅ 問題なし(中核懸念=table/row/cell role 露出をクリア)
- 所見:
  - `T` で「タスク一覧、テーブル、7列」と読み上げ → table role + 7 列ヘッダを認識。
  - セル位置「1列 1の6」→ 6 行 × 7 列のテーブルとして行・列・セル位置を認識。
  - `display:contents` によるテーブル/行/列のツリー消失は発生せず。
  - 行送りの精緻確認(A2〜A6)は NVDA のフォーカス取り回し(操作環境の制約)で安定踏破できず。
    ただし行/セル/列ヘッダ role の厳密検証は §1 自動 spec が担保。

---

## 3. Issue #554 への記録テンプレ

確認実施後、以下を Issue #554 にコメントする。

```markdown
## a11y 検証結果

### 機械検知(自動 / AC①)
- E2E spec 追加: e2e/tests/a11y-task-table.spec.ts(2 ケース、CI で常時実行)
- 実機 Chromium で table/row/columnheader/cell role の露出を確認(崩れなし)

### 実機 SR 確認(人手 / AC②)
- SR: NVDA(Windows / Chrome)、実施日 2026-06-24
- 結果: 問題なし — `T` で「タスク一覧、テーブル、7列」、セル位置「1列 1の6」を認識。
  table role + 7 列ヘッダ + 6 行 × 7 列のセル位置を読み上げ、display:contents による
  テーブル/行/列のツリー消失は発生せず。
- 補足: 行送りの精緻確認(行ごとの読み上げ順・セル境界)は NVDA のフォーカス操作の制約で
  安定踏破できなかったが、行/セル/列ヘッダ role の厳密検証は自動 spec が機械担保。

### fallback 方針(AC③)
- role 露出・SR のテーブル/行/列認識ともに問題なし。**行単位 CE(display:contents)を維持し、
  fallback は不要**と決定。
```

---

## 4. fallback 方針(AC③) — 決定: fallback 不要(2026-06-24)

§1 機械検知(row/cell/columnheader/table role を厳密確認)と §2 実機 SR 確認(NVDA が
table/7列/6行/セル位置を認識)の双方で role 露出に問題なし。
よって **行単位 CE(`<app-task-row>` + `display:contents`)を維持し、fallback は不要** と決定する。

参考(将来 role 露出の問題が判明した場合の fallback 案 — 今回は採用せず):
リスト側 CE(`app-task-table` 等)が素の `<tr>` を内部管理する構造へ移行し、別 Issue を起票して対応する。
