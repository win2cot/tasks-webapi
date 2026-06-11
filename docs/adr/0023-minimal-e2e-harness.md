# ADR-0023: 最小 E2E テスト基盤 — Playwright(TypeScript)+ hermetic compose、機能完了ごとに段階拡充

- **Status**: Accepted
- **Date**: 2026-06-11
- **Deciders**: win2cot, 開発チーム
- **Tags**: testing, e2e, frontend, ci, quality

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

現状、自動化されたブラウザ E2E テストは存在しない。あるのは **手動の E2E 動作確認**(Issue #233 = 手順書 + 証跡)と、開発計画書の **システムテスト・UAT フェーズ**(2026-08-11〜09-04、QA/テスター 1 名、UAT 合格率 95%)のみ。フロントは `web/` のビルドレスな静的 SPA。`docker-compose.local.yml` は **mysql + keycloak のみ**で webapi/web は別建て。

これでは、(1) 実装完了機能の「最低限動く」を早期・継続確認する手段が無く退行が UAT まで露見しない、(2) ADR-0022 の CSP 自動ガード(`securitypolicyviolation` 0 の assert)が乗る土台が無い。

win2cot の意図は「システムテストを待たず、**実装が終わった機能から順に最小の煙感知 E2E を足す**」継続的 E2E。重い UAT は上位層として残す。本 ADR は**最小の自動 E2E 基盤**を立て、機能完了ごとに段階拡充する枠組みを定める(深いカバレッジ設計はテスト工程送り)。

なお ADR-0022 によりフロント(`web/`)に npm が導入されるため、Node ツールチェーンは既にプロジェクトに入る。本 E2E を Node/TypeScript で組んでも言語/生態系の追加コストは小さい。

## 2. 検討した選択肢(Options Considered)

### ツール: Playwright vs Cypress

- **Playwright(採用)**: ヘッドレス Chromium、CI 親和性、`securitypolicyviolation` 捕捉・ネットワーク傍受、マルチオリジン(別ドメイン Keycloak)に強い。
- Cypress: CI 並列・マルチオリジンで取り回しが劣る。不採用。

### テストコード言語: TypeScript vs JavaScript vs Java(TypeScript を採用)

- **TypeScript(採用)**: Playwright を**フルに使い倒す**ための第一級ターゲット。純正テストランナー **`@playwright/test`** が付属し、フィクスチャ・並列実行・自動リトライ・`expect` の auto-waiting アサーション・HTML レポート・trace viewer が一式揃う。型安全でテストコードの誤りも早期検知。Playwright が `.ts` をネイティブ実行するため追加バンドラ不要。
- JavaScript: 同等に動くが型安全を失う。純正ランナーは TS と同様に使える。TS を上位互換として採用。
- Java(Playwright for Java): 開発陣の主言語で Gradle/JUnit と一体化できるが、**純正ランナーが無く JUnit を自前配線**(フィクスチャ/並列/レポートを組む手間)、新機能追従と資料も TS/JS に劣る。「Playwright をフル活用」の目的に最も遠く不採用。

### CI 実行環境: hermetic compose vs デプロイ済み dev

- **compose で hermetic full stack(採用)**: compose を拡張し mysql+keycloak+webapi+web 静的配信を CI 内で起動し叩く。再現性・隔離が高い。起動コスト(数分)はある。
- デプロイ済み dev: 共有環境・データ汚染・並行でフレーキー。不採用。

### 着手時期 / 拡充モデル

- **Sprint 3 で scaffold + 既存機能 backfill、以降 DoD で段階拡充(採用)**。大きく 8 月一括は意図に反し不採用。

## 3. 決定(Decision)

**採用**: Playwright(TypeScript / `@playwright/test`)による最小 E2E 基盤を新規 `e2e/`(Node パッケージ)に置き、hermetic compose full stack に対し CI で実行する。Sprint 3 で scaffold + 既存機能を backfill し、以降は機能完了ごとに段階拡充する。

具体:

- **配置**: リポジトリ直下に `e2e/`(独自 `package.json` + `@playwright/test` + TypeScript)。`web/`(資産のみ)・`webapi/`(Gradle)とは独立。Renovate 標準 npm manager の管理対象に入る。
- **実行スタック**: `docker-compose` を拡張し、既存 mysql+keycloak に **webapi + web 静的配信**を加えて full stack を hermetic 起動。新ワークフロー `e2e.yml` が起動 → **`npx playwright test`** → trace/動画 アーティファクト。PR で実行(対象パス/スケジュールで調整可)。
- **拡充モデル(DoD 化)**: UI 機能の **完了の定義(DoD)に「ハッピーパスの煙感知 spec 1 本」を含める**。Sprint 3 で既存完了機能(Keycloak ログイン往復 / テナント切替 / タスク一覧 + CRUD)を backfill。以降は機能ごとに追加。
- **CSP 自動ガード(ADR-0022 (b))**: 共通フィクスチャで `page.on('console')` / `securitypolicyviolation` イベントを購読し 0 を assert。Report-Only → enforce の移行を機械で守る。
- **位置づけ**: 8 月の システムテスト/UAT は重い受入層として残す。本基盤はその下の**高速・継続の煙感知層**。

## 4. 理由(Rationale)

- **Playwright をフル活用**: TS + `@playwright/test` で純正ランナー(フィクスチャ/並列/リトライ/trace/HTML レポート)を最大限使える。型安全でテストコードの誤りも早期検知。`.ts` ネイティブ実行で追加バンドラ不要。
- **Node 追加コストが小さい**: ADR-0022 でフロントに npm が入るため Node 生態系は既にプロジェクトに存在し、E2E を TS にしても整合する。依存は Renovate npm manager で追従。
- **早期・継続の退行検知**: 機能完了ごとに最小 E2E を足し、UAT を待たず「最低限動く」を担保。テストピラミッドの観点でも妥当。
- **hermetic compose で再現性**: 既存 compose + Testcontainers 文化があり、full stack を CI で立てる素地がある。
- **機械検知優先の原則と整合**: CSP 違反含む検知可能事象を人手監視でなく自動アサーションで守る。
- 捨てる利点: Java での言語統一(Gradle/JUnit 一体化)は、Playwright のフル活用(純正ランナー)と資料の厚さを優先して捨てる。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 実装済み機能の動作を早期・継続的に自動検証。ADR-0022 の CSP 自動ガードの土台ができる。
- `e2e/` が npm パッケージとして Renovate(npm)管理に乗る。Playwright の trace/レポート/並列をフルに使える。

### 悪い影響・制約(Negative)

- 新規 `e2e/` Node パッケージ・新 CI ワークフロー `e2e.yml`・compose 拡張(webapi+web 追加)が必要。
- CI に full stack 起動(数分)+ `npx playwright install` が加わる。対象パス絞り込み等で緩和。
- テストコードは Java(バックエンド)と別言語の TS になる(ただし Node は ADR-0022 で既に導入済)。
- DoD 変更(機能完了に煙感知 spec)を規約に反映する必要。

### 既存ドキュメント・規約への波及

- `docs/specs/コーディング規約.md`(またはテスト方針)に「UI 機能の DoD = 煙感知 E2E spec 1 本」を明記。
- 開発計画書のテスト体系に本基盤(継続層)と UAT(受入層)の位置づけを追記。
- `docker-compose` に webapi+web 静的配信サービスを追加(local/CI 共用 or プロファイル)。
- ADR-0022 の CSP 自動ガード(b)は本基盤の成立に blocked。

## 6. 実装メモ(Implementation Notes)

着手(Sprint 3):

1. **scaffold**: `e2e/`(`package.json` + `@playwright/test` + TypeScript 設定)。最小 1 spec(トップ表示)で疎通。CI で `npx playwright install`。
2. **compose 拡張**: mysql+keycloak に webapi + web 静的配信を追加し full stack を一括起動可能に。
3. **CI ワークフロー `e2e.yml`**: full stack 起動 → `npx playwright test` → trace/動画 アーティファクト。
4. **backfill**: Keycloak ログイン往復 / テナント切替 / タスク一覧 + CRUD のハッピーパス(`*.spec.ts`)。
5. **CSP 共通フィクスチャ**: `securitypolicyviolation` を購読し 0 assert(ADR-0022 (b))。
6. **DoD 規約反映**: 機能完了 = 煙感知 spec 1 本。

カバレッジの深さはテスト工程で別途議論。

## 7. 参考リンク(References)

- Issue #233 — 手動 E2E 動作確認手順 + 証跡(既存)
- `docs/specs/開発計画書.md` — システムテスト/UAT フェーズ(2026-08-11〜)
- `docker-compose.local.yml` — 現 compose(mysql+keycloak)
- **ADR-0022 — セキュリティレスポンスヘッダー(CSP 自動ガードの依頼元 / npm 導入元)**
- Playwright / `@playwright/test` 公式
