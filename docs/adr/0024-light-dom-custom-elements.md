# ADR-0024: 画面実装の部品化規約 — light DOM Custom Elements(Shadow DOM 不使用)

- **Status**: Accepted
- **Date**: 2026-06-11
- **Deciders**: win2cot, 開発チーム
- **Tags**: frontend, ui, architecture

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

フロント(`web/`)はビルドレスな静的 SPA で、技術スタックは「HTML5 / CSS3 / 素の JavaScript / Bootstrap 5、SPA フレームワークは MVP では不採用」(`docs/specs/ui/design-system.md` §2.1)。一方、確定済みの UI(`docs/specs/ui/screen-flow.md` §5)は行内編集 6 項目・右ドロワー・説明ポップオーバー・完了時のセクション間移動など**再利用部品と DOM の動的差し替えが多い**構成であり、部品の初期化・破棄・イベントリスナ解除を律する規約が存在しない。このままでは実装ごとに部品構造がバラつき、リスナのリークや二重初期化を個別レビューで潰すことになる。

前提の変化として、ADR-0022 により `web/` に npm が導入され(npm 導入の SSOT は ADR-0022。同 ADR は「後続のフロント技術選定(コンポーネント化方針など)はこの npm 導入を前提にできる」と明記)、CSP は inline 全廃・`script-src 'self'` に確定した。また ADR-0023 により Playwright E2E(スモークテスト層)が Sprint 3 で立つ。

ブラウザ標準の Web Components(Custom Elements / Shadow DOM / HTML Templates)はコア仕様が全主要ブラウザで Baseline 到達済(Declarative Shadow DOM は 2024-08 Baseline)。ただし Scoped Custom Element Registries は Chromium 系のみで、依存できない。

本 ADR は「フレームワークなしの部品化規約をどう定めるか」を決める。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: 素の JS + 命名規約のみ(現状維持)

- 概要: 部品化の仕組みを導入せず、関数・クラスの命名規約と個別実装で進める。
- 利点: 追加の規約整備・学習コストゼロ。設計書改訂不要。
- 欠点: ライフサイクル(DOM 挿入/削除時の初期化・破棄・リスナ解除)の規約を自前で発明することになる。再利用部品が多い確定 UI では実装ごとの構造バラつき・リーク・二重初期化のリスクが大きい。
- リスク・未知数: 「SPA 風単一ページ」で DOM 差し替えが多いほど無規約の負債が累積する。

### 選択肢 B: light DOM Custom Elements(Shadow DOM 不使用)

- 概要: 部品を `HTMLElement` 継承の Custom Elements として定義し、`attachShadow()` は使わない。内部 HTML は通常 DOM(light DOM)に展開し、スタイルは Bootstrap + `--app-*` トークンに委ねる。
- 利点: ライフサイクル(`connectedCallback` / `disconnectedCallback` / `attributeChangedCallback`)がブラウザ標準で規約が薄い。Bootstrap CSS・テナントテーマ CSS 変数(design-system.md §2.4 / §9)・CSP(外部 js 定義で inline 不要)と全整合。ビルド工程・ランタイム依存ゼロでビルドレス方針の枠内。`<app-status-badge>` のように HTML 宣言で部品配置でき可読性が上がる。
- 欠点: データバインディングは無く、属性変更 → 再描画は部品ごとに自前実装。コンポーネント単体テストのランナーは別途検討(当面は ADR-0023 の E2E でカバー)。
- リスク・未知数: 部品数が増えたとき属性監視・再描画の boilerplate が重複する(→ §6 再評価トリガ)。

### 選択肢 C: Shadow DOM 込みのフルカプセル化

- 概要: 各部品が Shadow DOM を持ち、スタイルとマークアップを完全隔離する。
- 利点: スタイル完全隔離(他者製ウィジェット混載環境では有効)。
- 欠点: Bootstrap のグローバル CSS は shadow 境界を貫通せず、部品ごとにスタイル再注入が必要で Bootstrap 基盤方針(design-system.md §6)と正面衝突。ARIA の ID 参照が境界で分断され、デバッグ性も落ちる。
- リスク・未知数: 単一チーム開発でスタイル衝突源が存在せず、隔離が守る相手がいない。コストのみ発生。

### 選択肢 D: Lit 等の軽量 Web Components ライブラリ

- 概要: Custom Elements の上に宣言的テンプレート・リアクティブ属性を提供する Lit(約 5KB)を npm 導入する。
- 利点: 属性監視・再描画の boilerplate を解消。標準の上に乗るため選択肢 B で書いた部品と共存・段階移行可能。
- 欠点: 「素の JS」方針からの逸脱。ビルドレス維持のためには bundler 要否(bare module specifier 解決)の検討が必要。MVP の部品規模に対して過剰。
- リスク・未知数: 現時点では不要。必要性が顕在化した時点で安価に再評価できる(npm は導入済)。

## 3. 決定(Decision)

**採用**: 選択肢 B(light DOM Custom Elements)

具体:

- **部品の実装単位**: 再利用 UI 部品(行内編集セル・ドロワー・ポップオーバー・バッジ等)は `HTMLElement` 継承の Custom Elements として定義する。`attachShadow()` は使用しない(light DOM)。Shadow DOM を採用したくなった場合は新規 ADR を立てる。
- **タグ命名**: `app-` プレフィックス + kebab-case(例: `<app-status-badge>` / `<app-task-drawer>`)。Scoped Custom Element Registries に依存せずグローバル registry で一意性を担保する。
- **配置と定義**: `web/js/components/` 配下に 1 ファイル 1 部品。`customElements.define()` は部品ファイル内で行う。外部 js ファイル定義のため CSP `script-src 'self'`(ADR-0022)と整合し、inline script は使わない。
- **マークアップ雛形**: 部品の静的マークアップは **HTML Templates**(`<template>`)で定義し、`content.cloneNode(true)` で展開する。雛形は部品ファイル内でモジュールロード時に一度だけ構築する(`document.createElement('template')`。複数ページで部品を使うためページ HTML 側への `<template>` 配置はしない)。データ反映は clone 後に `textContent` / 属性設定で行い、**データを混ぜた `innerHTML` 文字列連結は禁止**(XSS 防止)。
- **スタイル**: 部品内に style を持ち込まず、Bootstrap クラス + `--app-*` トークン(design-system.md §2.4)を light DOM 上でそのまま使う。
- **フォーム**: Bootstrap のフォーム要素を light DOM として内包する形を基本とし(通常の `<form>` 送信に参加できる)、ElementInternals(form-associated custom elements)は必要が生じるまで使わない。
- **テスト**: 当面は ADR-0023 のスモークテスト E2E でカバーする。コンポーネント単体テストランナー(Web Test Runner 等)は部品の複雑化が顕在化した時点で別途検討する。
- **Lit は現時点で不採用**(§6 の再評価トリガで再検討)。

## 4. 理由(Rationale)

- **規約が最も薄い**: ライフサイクルをブラウザ標準に委ねることで、自前規約の発明・教育・レビューコストを回避できる。標準仕様であるため実装担当(Claude 自動実装を含む)の既知知識がそのまま効き、出力が安定する。
- **既存決定と全整合**: Bootstrap 5 基盤・テナントテーマ CSS 変数・ADR-0022 CSP(inline 全廃)・ビルドレス方針のいずれも変更せずに導入できる。CSS カスタムプロパティは継承で部品に届き、light DOM なので Bootstrap クラスもそのまま効く。
- **マークアップとデータ注入の分離**: HTML Templates の雛形 + clone 後の `textContent` / 属性反映により、データ入りの `innerHTML` 連結を構造的に排除でき、XSS 面でも堅い。
- **漸進導入が可能**: 既存 js を一括書き換えせず、新規部品から適用できる。失敗時の撤退コストも小さい(Custom Elements は通常の DOM 要素として残る)。
- **Shadow DOM の隔離は本プロジェクトでは便益がない**: 単一チームで全 CSS を統制しており、隔離の対価(Bootstrap 再注入・ARIA 分断)だけが残る。
- 捨てる利点: Shadow DOM の完全なスタイル隔離、Lit の宣言的再描画。前者は守る相手不在、後者は再評価トリガ付きで保留とする。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 部品の初期化・破棄・属性反映がブラウザ標準のライフサイクルに統一され、リーク・二重初期化を構造的に防げる。
- 画面 HTML が `<app-*>` タグの宣言的配置になり、screen-flow.md の画面構造と実装の対応が読みやすくなる。
- ランタイム依存・ビルド工程の追加なし。npm(ADR-0022)とも競合しない。

### 悪い影響・制約(Negative)

- データバインディングが無いため、属性変更 → 再描画(`observedAttributes` + `attributeChangedCallback`)は部品ごとに実装する。部品数増加で boilerplate が重複し得る(→ §6 再評価トリガ)。
- タグ名はグローバル一意(`app-` プレフィックスで担保)。Scoped registry は使えない前提を維持する。
- コンポーネント単体テストの手段は未整備のまま(当面 E2E カバー)。

### 既存ドキュメント・規約への波及

- `docs/specs/ui/design-system.md` §2.1 に部品化規約(light DOM Custom Elements)を追記する(本 ADR と同一 PR)。
- 画面実装系 Issue は本規約を前提に実装する。コーディング規約への JS 節追加は画面実装の着手時に要否を判断する。

## 6. 実装メモ(Implementation Notes)

- 適用は新規部品から。既存の `web/js/auth.js` / `api.js` / `tenant-switcher.js` は UI 部品ではないため書き換え対象外。
- 部品の雛形:

```js
const tpl = document.createElement('template');
tpl.innerHTML = '<span class="badge"></span>'; // 静的マークアップのみ(データは混ぜない)。Bootstrap クラスがそのまま効く

class AppStatusBadge extends HTMLElement {
  static get observedAttributes() {
    return ['status'];
  }
  connectedCallback() {
    this.replaceChildren(tpl.content.cloneNode(true));
    this.render();
  }
  attributeChangedCallback() {
    if (this.firstElementChild) this.render();
  }
  render() {
    this.firstElementChild.textContent = this.getAttribute('status') ?? ''; // データは textContent / 属性で反映
  }
}
customElements.define('app-status-badge', AppStatusBadge);
```

- **再評価トリガ**(該当時に新規 ADR で再検討):
  1. 属性監視・再描画の boilerplate が複数部品で重複し保守負担が顕在化した → Lit 薄載せ(npm 導入済のため追加コスト小。bundler 要否を含め検討)。
  2. サードパーティ製ウィジェットの混載等、スタイル衝突源が実際に発生した → Shadow DOM の限定採用。
  3. 独自フォーム部品(Bootstrap フォーム要素の内包で表現できない入力)が必要になった → ElementInternals(form-associated custom elements)の採用。

## 7. 参考リンク(References)

- `docs/specs/ui/design-system.md` §2.1 / §2.4 / §6 — フロントエンドスタック・トークン駆動・コンポーネント方針
- `docs/specs/ui/screen-flow.md` §5 — 行内編集・ドロワー・ポップオーバー等の確定 UI
- ADR-0022 — セキュリティレスポンスヘッダー(npm 導入の SSOT / CSP inline 全廃)
- ADR-0023 — 最小 E2E テスト基盤(スモークテスト層、当面のテストカバー先)
- MDN: Web Components(Custom Elements / Shadow DOM / HTML Templates)
- web.dev: Declarative Shadow DOM(Baseline 2024-08)
