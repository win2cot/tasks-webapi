# ADR-0025: フロントエンド品質ゲート — Biome + html-validate + Nu Html Checker による web/ CI 整備

- **Status**: Accepted
- **Date**: 2026-06-12
- **Deciders**: win2cot, 開発チーム
- **Tags**: frontend, ci, quality, tooling

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

フロント(`web/`)は ADR-0022 で npm プロジェクト化され、ADR-0024 の light DOM Custom Elements 化により JS 16 ファイル(うち Custom Elements 10 部品)+ HTML 2 + CSS 1(計約 1,780 行)まで成長した。一方、既存 GitHub Actions workflow(16 本)はいずれも `web/**` を paths-filter の対象にしておらず、CI ゲートが存在しない。具体的なギャップは次のとおり。

- **Renovate の patch automerge が無検証で通る**: `web/**` の依存更新(bootstrap / keycloak-js / @fontsource)は CI ゼロのまま automerge され、`scripts/copy-vendor.js` が参照する dist パスの変化で本番配信資産が黙って壊れ得る。
- **lint / format が皆無**: Java 側の Spotless(google-java-format)+ NullAway 相当が無く、JS のコーディング規約も未整備。lint ルールセットが事実上の規約となるため、ツール選定は規約制定そのものである。
- **HTML Living Standard 準拠の担保が無い**: プロジェクトとして WHATWG HTML Living Standard への準拠を方針とするが、検証手段が無い。
- **ADR-0022「inline 全廃」の退行検知が無い**: inline 撤去(#527、完了済)後に `onclick=` 等が書き戻されても検知できない。

なお、コンポーネント単体テストは ADR-0024 が「当面 ADR-0023 の E2E(スモークテスト層)でカバーし、ランナーは複雑化が顕在化した時点で再検討」と既決のため、本 ADR のスコープ外とする。型チェック(JSDoc + `tsc --checkJs`)も本 ADR では決定せず、後続 Issue で段階導入を判断する。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: 何もしない(現状維持)

- 概要: `web/**` への CI ゲートを設けない。
- 利点: 作業ゼロ。
- 欠点: 上記ギャップ 4 点が全て残る。特に Renovate automerge の無検証通過は本番配信の静かな破壊につながる。却下。

### 選択肢 B: ESLint + Prettier(+ stylelint + html-validate)

- 概要: デファクト標準の ESLint(flat config)で JS lint、Prettier で format、CSS lint は stylelint、HTML 検証は html-validate を追加。
- 利点: 業界デファクトで情報・ルール資産が最も厚い。Web Components 向け eslint-plugin-wc(v3.1.0、現役)等を追加可能。
- 欠点: dev 依存が 4〜6 パッケージ(eslint / prettier / eslint-config-prettier / stylelint + 各 config)に増え、ツール間の競合回避設定の維持が必要。
- リスク・未知数: 特になし(枯れている)。

### 選択肢 C: Biome + html-validate + Nu Html Checker(v.Nu)

- 概要: lint + format を単一バイナリの Biome に集約し(JS / CSS / JSON)、HTML Living Standard 準拠検証は専用 validator 二層(html-validate 常設 + v.Nu 常設)で担保する。
- 利点: dev 依存最小(lint/format は 1 パッケージ)。単一 `biome.json` で設定が完結し、google-java-format と同型の「opinionated な単一フォーマッタ、format は議論しない」運用に乗る。CSS lint も追加ツールなしで組込み。
- 欠点: ルールエコシステムは ESLint より小さい(プラグインは GritQL ベースの lint のみ)。HTML の format/lint は明示 opt-in かつ a11y / Vue 系中心。
- リスク・未知数: 将来 wc / アクセシビリティ系ルールを厚く効かせたくなった場合に物足りない可能性。ただし Biome は ESLint からの migrate コマンドを公式提供しており、逆方向(設定資産の移行)も小規模なら手作業で現実的。

**HTML Living Standard 準拠について(B / C 共通の前提)**: lint/format ツール(ESLint + Prettier / Biome のいずれも)は Living Standard 準拠検証(コンテンツモデル・要素・属性の妥当性)を守備範囲としない。準拠担保には専用 validator が必須であり、本論点はツール選定の分岐に中立。

- **Nu Html Checker(v.Nu)**: WHATWG checker のリファレンス実装(validator.w3.org/nu の実体)。Living Standard 追従が最も忠実。Java 製で CI では Docker イメージ(`ghcr.io/validator/validator`)実行が定石。custom element 名(ハイフン付き)は仕様上 valid として受理するが、個々の custom element の属性・内容の検証は範囲外。
- **html-validate**: npm 製オフライン validator。バンドルの html5 要素メタデータでコンテンツモデルを検証し、**custom element のメタデータを自前定義して検証対象に組み込める**(ADR-0024 の CE 10 部品と好相性)。`no-inline-style` 等のルールで ADR-0022 の inline 退行ガードも兼ねられる。ただしリファレンス実装ではないため、Living Standard 追従の網羅性は v.Nu が上。

## 3. 決定(Decision)

**採用**: 選択肢 C

1. `web-ci.yml` を新設する(既存 workflow の house style 踏襲: paths-filter `web/**`、`LANG=ja_JP.UTF-8` / `TZ=Asia/Tokyo`、concurrency)。
2. ジョブ構成は次の 4 段とする。
   - `npm ci`(lockfile 整合検証)+ `node scripts/copy-vendor.js` 実行 + 主要 vendor ファイルの存在チェック(Renovate automerge のゲート)
   - `biome ci`(JS / CSS / JSON の lint + format 検査。`biome.json` が JS コーディング規約の SSOT)
   - html-validate(custom element メタデータ定義込み。#527 で inline 撤去済みのため `no-inline-style` 等の inline 退行ガードは導入時から有効化)
   - v.Nu(Docker 実行。`index.html` / `tasks.html` の Living Standard 準拠の正本チェック)
3. コンポーネント単体テストは導入しない(ADR-0024 の既決に従う)。型チェック(JSDoc + `tsc --checkJs --noEmit`)は後続 Issue で別途判断する。

## 4. 理由(Rationale)

- **依存とメンテ面の最小化**: vanilla JS + バンドラなしの小規模 SPA に対し、lint/format の dev 依存が 1 パッケージで済む。ESLint 案はツール間競合回避(eslint-config-prettier 等)の維持が恒常コストになる。
- **プロジェクト方針との整合**: Java 側の Spotless + google-java-format と同じ「opinionated な単一ツール、スタイルは議論しない」思想に揃う。
- **ESLint の強みを使う場面が少ない**: フレームワーク非依存の light DOM CE はクラス定義 + `customElements.define` の素朴な構造で、巨大プラグイン資産(eslint-plugin-wc 含む)の効果が限定的。必要になった時点で公式 migrate 経路により乗り換え可能な規模。
- **Living Standard 準拠は validator 二層で正面から担保**: 常設の html-validate(CE メタデータ + inline 退行ガード)とリファレンス実装 v.Nu の併用。HTML は 2 ファイルのみで、毎 PR 両方実行してもコストは無視できる。
- **鮮度検証済み(2026-06-12 確認)**: Biome 2.4.16(2026-05-27 release)/ ESLint v10.4.1(2026-05-29)/ Prettier 3.8.4(2026-06-09)/ html-validate 11.5.3 / v.Nu(2026-05-29 release)。いずれも活発にメンテされており、鮮度面で脱落する候補は無かった。

## 5. 影響(Consequences)

### 良い影響(Positive)

- `web/**` の Renovate patch automerge に初めて CI ゲートが立ち、vendor 配信資産の静かな破壊を検知できる。
- `biome.json` が JS / CSS コーディング規約の SSOT として機能し、レビューでのスタイル指摘が消える。
- HTML Living Standard 準拠と ADR-0022 inline 全廃の両方が機械検知になる(Issue/Project audit の「機械検知第一」方針とも整合)。

### 悪い影響・制約(Negative)

- 既存 JS 16 ファイル + CSS / JSON への format / lint 一括適用で大きめの diff が一度発生する(機能変更と混ぜず専用 PR で実施する)。
- custom element を追加・変更するたびに html-validate のメタデータ追従が必要。
- v.Nu の Docker イメージ pull 分だけ CI 時間が伸びる(HTML 2 ファイルのため実行自体は数秒)。
- Biome のルールエコシステムは ESLint より小さく、特殊ルールの追加要求には GritQL プラグイン自作か将来の乗り換えで対応することになる。

### 既存ドキュメント・規約への波及

- JS コーディング規約は独立文書を新設せず、**本 ADR + `biome.json` + html-validate 設定を当面の正本**とする。`docs/specs/コーディング規約.md` は Java 規約のまま変更しない(将来 JS 規約を `docs/specs/` に立てる場合も設定ファイルを SSOT とする)。
- repo ルート `.claude/CLAUDE.md` の Commands に web/ 向けコマンド(lint / format / validate)を実装 PR で追記する。

## 6. 実装メモ(Implementation Notes)

- Issue 分割(親 tracker + sub-issue):
  1. `web-ci.yml` 新設(npm ci + copy-vendor スモーク。最優先・他と独立)
  2. Biome 導入(`biome.json` + 既存 JS / CSS / JSON への一括適用 + web-ci.yml への組込み)
  3. html-validate + v.Nu 導入(CE メタデータ定義 + inline 退行ガード有効化 + web-ci.yml への組込み)
  4. 型チェック段階導入の判断(JSDoc + `tsc --checkJs --noEmit`。導入要否自体を後続で議論)
- `biome.json` は `recommended` ルールセットを基点とし、逸脱は最小限・理由付きで設定に残す。
- html-validate は `html-validate:recommended` を基点に、CE 10 部品のメタデータを `web/` 配下の設定で定義する。
- v.Nu は `ghcr.io/validator/validator` を docker run で実行し、対象は `web/*.html`(vendor/ は対象外)。

## 7. 参考リンク(References)

- ADR-0022(npm 導入の SSOT、CSP inline 全廃)/ ADR-0023(E2E スモークテスト層)/ ADR-0024(light DOM Custom Elements、単体テスト当面 E2E カバーの既決)
- Biome — Language support: <https://biomejs.dev/internals/language-support/>
- Biome — HTML lint rules: <https://biomejs.dev/linter/html/rules/>
- html-validate: <https://html-validate.org/usage/index.html>
- Nu Html Checker: <https://github.com/validator/validator>
- WHATWG HTML Living Standard: <https://html.spec.whatwg.org/>
