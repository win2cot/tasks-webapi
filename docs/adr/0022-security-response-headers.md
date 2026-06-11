# ADR-0022: セキュリティレスポンスヘッダー方針 — 最小許可・既定厳格の CSP とヘッダー群

- **Status**: Accepted
- **Date**: 2026-06-11
- **Deciders**: win2cot, 開発チーム
- **Tags**: security, frontend, api, infra, csp, headers

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
  - [3.1 ヘッダー分担基準(per-header 単一オーナー)](#31-ヘッダー分担基準per-header-単一オーナー)
  - [3.2 フロント(CloudFront Response Headers Policy)](#32-フロントcloudfront-response-headers-policy)
  - [3.3 API(Spring Security .headers())](#33-apispring-security-headers)
  - [3.4 導入手順](#34-導入手順)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

本システムのレスポンスセキュリティヘッダーは、これまで部分的・暗黙的にしか整備されていない。現状:

- **ALB(`infra/shared/modules/alb/main.tf`)**: HTTPS Listener で HSTS を注入(dev 短期 / prd 1 年、`preload` なし=共有 apex `dgz48.xyz`)。TLS 1.3 ポリシー・HTTP→HTTPS 301・`drop_invalid_header_fields` 済み。
- **API(`security/adapter/web/SecurityConfig.java`)**: `csrf.disable()` / `STATELESS` / OAuth2 リソースサーバのみ。`.headers()` 未設定で Spring デフォルト(`X-Frame-Options: DENY` / `nosniff` / `Cache-Control`)に暗黙依存。**CSP / Referrer-Policy / Permissions-Policy なし**。
- **フロント(`web/` の静的 SPA、S3 + CloudFront 配信)**: セキュリティヘッダー皆無。`index.html` / `tasks.html` は外部 CDN(`cdn.jsdelivr.net` から Bootstrap・keycloak-js)を SRI 付きで読み、inline `<script>` 各 1・`onclick=` / `style=` 多数(`tasks.html` 21 箇所)。

CSP 設計に効く実装事実: keycloak-js は `checkLoginIframe: false`(iframe 不使用→認証 origin への `frame-src` 不要)、ログインはトップレベルリダイレクト・トークン交換/discovery は `fetch`(= `connect-src`)。フロントは API origin(`api-<env>.tasks.dgz48.xyz`)と Keycloak origin(`auth-<env>.dgz48.xyz`)へ `fetch`。Bootstrap5 は `data:` URI の SVG を背景に使う(`img-src data:` 必要)。

**外部資産の供給方針(2026-06-11 決定)**: 本番運用まで見据え、Bootstrap・keycloak-js は **第三者 CDN を使わず npm 取り込みで自前配信(S3/CloudFront)** する(§4)。よって CSP は `script-src 'self'` / `style-src 'self'` に絞れる。現状 `index.html` と `tasks.html` で同一 `bootstrap@5.3.3` バンドルの SRI 値が食い違う既存バグがあるが、自前配信化で SRI 自体が不要になり解消する。

本 ADR は **「必要なものだけを最小限許可し、それ以外は厳格に拒否する」** 方針(win2cot 指示)を、現実装の修正を許容したうえで全レスポンス面に適用する。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: 現状維持(暗黙デフォルト依存)

- 概要: Spring/ALB デフォルトのみ、CSP 無し。
- 欠点: XSS 緩和の中核 CSP 不在。明文化・退行検知不能。責務分界未定義。却下。

### 選択肢 B: 最小許可・既定厳格を二面で明示(採用)

- 概要: `default-src 'none'` を起点に必要 source だけ allowlist した CSP を **フロント(CloudFront)** に配信、**API(Spring)** には JSON 専用の防御的最小 CSP。inline は全廃して `'unsafe-inline'` を排除。外部資産は **npm 自前配信**で `script-src/style-src 'self'`。HSTS の SSOT は TLS 終端。関連ヘッダーも明示。
- 利点: XSS 主経路を最大限に塞ぐ。明示で退行をテスト検知。責務分界が説明可能。
- 欠点: フロント inline 撤去 + npm 取り込み + CloudFront RHP 新設の実装コスト。

### 選択肢 C: nonce ベース CSP

- 概要: inline を残し per-response nonce。
- 欠点: nonce はレスポンス毎の動的生成前提で、SSR の無い静的 S3+CloudFront と原理的に不適。却下。

### CDN 継続 vs 自前配信(選択肢 B 内の論点、自前配信を採用)

- CDN+SRI 継続: 実装変更は最小だが、(1) keycloak-js を第三者に依存=**auth 経路の SPOF**、(2) cache partitioning で公共 CDN の性能便益は消失済み、(3) CSP に第三者 origin が残る、(4) Renovate 標準では CDN URL を管理できず実質バージョン未管理。
- **npm 自前配信(採用)**: `script-src 'self'` に最小化、auth 経路の第三者依存を排除、Renovate 標準 npm manager で管理(改善)。コストはビルドレスなフロントに npm + deploy 時コピー(バンドラ不要)が一段増えること。

## 3. 決定(Decision)

**採用**: 選択肢 B(自前配信版)。

### 3.1 ヘッダー分担基準(per-header 単一オーナー)

レスポンスヘッダーの出力層は次の基準で一意に定める。

> **各ヘッダーは1つの層だけが出力する(単一オーナー)。オーナーは「そのヘッダーが守るべき全レスポンスに到達でき、かつアプリ正しい値を出せる最下層」とする。二重出力は禁止。**

帰結:

- **HSTS → その経路の TLS 終端**(API=ALB、静的=CloudFront)。HSTS は接続(transport)スコープであり、かつ **ALB 自身が生成する 404 fixed-response や HTTP→HTTPS リダイレクトにも乗る必要がある**(Spring から到達不能)。
- **CSP / X-Frame-Options / Referrer-Policy / Permissions-Policy / X-Content-Type-Options / Cache-Control → アプリ層**(API=Spring、静的=CloudFront、auth=Keycloak)。値が**コンテンツ/アプリ固有**で、特に **ALB listener は共有**(tasks-webapi と Keycloak を同一 listener で収容、infra ADR-0006)のため listener レベルに API 用 CSP を置くと Keycloak のログイン画面を壊す。ALB は技術的にこれらを listener 属性で出せるが**出さない**。
- 完全な single-source にできないのは、API 経路だけ **TLS 終端(ALB)≠コンテンツ origin(Spring)が別層** かつ **ALB が共有** という構造による必然。静的経路は TLS 終端=コンテンツ origin が CloudFront 単一なので分割は起きない。
- **二重出力の排除**を必ず担保する: Spring の HSTS writer は disable(§3.3)、ALB は CSP/nosniff/X-Frame 等を出さない。

### 3.2 フロント(CloudFront Response Headers Policy)

CSP(`<env>` は Terraform 変数でテンプレート化、外部資産は自前配信のため `'self'`):

```text
default-src 'none';
script-src 'self';
style-src 'self';
img-src 'self' data:;
font-src 'self';
connect-src 'self' https://api-<env>.tasks.dgz48.xyz https://auth-<env>.dgz48.xyz;
frame-ancestors 'none';
base-uri 'self';
form-action 'self';
object-src 'none'
```

- `frame-src` 不要(keycloak-js `checkLoginIframe: false`)。Report-Only 期間で iframe 要求が出た場合のみ `frame-src https://auth-<env>.dgz48.xyz` 追加。
- `'unsafe-inline'` / `'unsafe-eval'` は付けない。

固定ヘッダー:

| ヘッダー | 値 | 備考 |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains`(dev 短期) | `preload` なし。CloudFront が静的経路の TLS 終端=オーナー |
| `X-Content-Type-Options` | `nosniff` | |
| `Referrer-Policy` | `no-referrer` | 緩和が要れば `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=(), accelerometer=(), gyroscope=(), magnetometer=()` | 未使用機能を全無効化 |
| `X-Frame-Options` | `DENY` | `frame-ancestors 'none'` の後方互換 |
| `Cross-Origin-Opener-Policy` | `same-origin` | Keycloak はリダイレクト方式で安全 |
| `Cross-Origin-Resource-Policy` | `same-origin` | |

> `Cross-Origin-Embedder-Policy` は付与しない(COEP は他オリジン資産の読込を壊しうるため)。

### 3.3 API(Spring Security .headers())

JSON 専用の防御的最小 CSP:

```text
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
```

加えて明示(デフォルト依存をやめテストで固定):

- `X-Content-Type-Options: nosniff`(明示)
- `X-Frame-Options: DENY`(明示)
- `Referrer-Policy: no-referrer`
- `Cache-Control: no-store`
- **HSTS writer は無効化**(`httpStrictTransportSecurity().disable()`)。HSTS の SSOT は ALB(§3.1)、`forward-headers-strategy` の有無に依らず二重送出させない。

`csrf.disable()` は据置(ステートレス JWT、設計規約 §5.6)。

### 3.4 導入手順

最終形は enforce(`Content-Security-Policy`)。enforce 切替の前に dev/stg で短期 `Content-Security-Policy-Report-Only` を有効化し違反を観測してから切替(検証体制は §6)。

## 4. 理由(Rationale)

- **XSS 緩和の費用対効果が最大**: `default-src 'none'` + 最小 allowlist + inline 全廃で注入スクリプト実行を広く塞ぐ。捨てる利点(inline の手軽さ)はリファクタ一度で解消。
- **自前配信が本番運用に堪える**: 第三者 CDN は (1) auth 経路 SPOF、(2) cache partitioning で性能便益消失、(3) CSP に第三者 origin が残る。npm 自前配信なら `script-src 'self'` に絞れ、可用性は自社インフラに閉じ、Renovate 標準 npm manager でバージョン管理も改善する(現状 CDN URL は実質未管理)。ビルドレスなフロントに最小のコピー手順が増えるのが唯一の実コスト(バンドラ不要)。
- **静的配信に nonce は不向き**: SSR の無い S3+CloudFront で安全な nonce 運用手段が無く、選択肢 C は不適。
- **責務分界が基準で一意化**(§3.1): per-header 単一オーナー + 二重出力禁止で SSOT が明確になり、HSTS 二重送出のような曖昧さを排除。
- **退行検知**: ヘッダーを IaC とコードに明示、API は MockMvc で値を assert、フロントは E2E(ADR-0023)で `securitypolicyviolation` 0 を assert。

## 5. 影響(Consequences)

### 良い影響(Positive)

- フロント・API とも XSS / クリックジャッキング / MIME スニッフィング / referrer 漏洩への既定防御が明示され、テストで守れる。
- 第三者 CDN 依存(特に auth 経路 SPOF)を排除。SRI 不一致の既存バグも解消。
- ヘッダーの SSOT が層ごとに一意。`script-src 'self'` で将来さらに絞る余地も残る。

### 悪い影響・制約(Negative)

- `web/` の inline 撤去に加え、**npm 取り込み + deploy 時 S3 コピー**の手順が増える(ビルドレス→最小のコピー工程)。
- CloudFront に Response Headers Policy を新設し distribution へ紐付ける IaC 作業。
- CSP の `connect-src` を env 別 origin にテンプレート化し、API/Keycloak URL 変更時に追従が要る。

### 既存ドキュメント・規約への波及

- `docs/specs/設計規約.md` §5.4 に「セキュリティレスポンスヘッダー」節を新設(本 ADR を SSOT 参照、§3.1 の分担基準を明記)。§5.5 据置。
- `docs/specs/基本設計書.md` SC-8 近傍にヘッダー方針 + HSTS の SSOT(TLS 終端)を追記。
- infra: CloudFront モジュールに `aws_cloudfront_response_headers_policy` 追加。
- **本 ADR をもって `web/` に npm(`package.json` + Node)を導入する(npm 導入の SSOT は本 ADR)**。ビルドレスだったフロント初の npm 化で、最小のビルド工程(資産コピー)を伴う。`package.json` は Renovate 標準 npm 管理対象に入る(`renovate.json` の `area/web` ルールが実機能する)。後続のフロント技術選定(コンポーネント化方針など)はこの npm 導入を前提にできる。
- CSP 違反の自動検証は ADR-0023(最小 E2E 基盤)に相乗り。

## 6. 実装メモ(Implementation Notes)

着手順(レイヤー横断 + テスト種別が多く分割推奨):

1. **フロント自前配信 + inline 撤去**(`web/`): `package.json` に bootstrap・keycloak-js を追加し deploy 時に `node_modules/.../dist` を S3 へコピー。HTML の CDN タグを `'self'` 参照へ置換(SRI 不一致バグも同時解消)。inline `<script>` を `js/` へ、`onclick=` を `addEventListener` へ、`style=` を `css/app.css` クラスへ移行。機能等価を手動確認。
2. **API ヘッダー**(`SecurityConfig.java`): §3.3 を実装、`SecurityConfigTest`(MockMvc)で各ヘッダー値を assert。
3. **CloudFront RHP**(`infra`): §3.2 を env テンプレートで定義し distribution に紐付け。HSTS は CloudFront(静的経路の TLS 終端)が出す。
4. **Report-Only → enforce**: 3 でまず Report-Only を dev/stg に付与 → 検証(下記) → enforce 切替。

### CSP 検証体制(Report-Only 期間、hybrid)

「コンソールを見る」を曖昧な背景作業にしない。担当・完了基準を明示する。

- **(a) 手動パス(オーナー: win2cot)**: enforce 切替前に stg で DevTools を開き、決めたフロー(Keycloak ログイン/トークン更新、タスク CRUD、モーダル/ドロップダウン=Popper、カレンダー)を一巡して違反を記録。完了基準=列挙フローで違反 0。本 ADR 内の独立タスクとして計画に乗せる。
- **(b) 自動ガード(機械検知、ADR-0023 に相乗り)**: Playwright E2E の全ページ共通フィクスチャで `securitypolicyviolation` イベント 0 を assert。回帰を継続的に守る。**E2E 基盤(ADR-0023)の成立に blocked**、Sprint 3 以降。

「Issue/Project audit は機械検知優先」の原則に沿い、(a) は着手時の一過性確認、(b) を恒常ガードとする二段構え。

## 7. 参考リンク(References)

- `webapi/.../security/adapter/web/SecurityConfig.java` / `application.yml` — 現 API 設定
- `web/index.html` / `web/tasks.html` / `web/js/auth.js` / `web/js/api.js` — フロント現況
- `infra/shared/modules/alb/main.tf` — HSTS 注入(API 経路の TLS 終端)
- `renovate.json` — 依存管理(npm 取り込み後に web 資産も対象化)
- `docs/specs/設計規約.md` §5.4 / §5.5 / `docs/specs/基本設計書.md` SC-8
- infra ADR-0006 — 共有 ALB / CloudFront はフロント静的配信専用
- **ADR-0023 — 最小 E2E 基盤(CSP 自動ガードの相乗り先)**
- MDN: Content-Security-Policy / OWASP Secure Headers Project
