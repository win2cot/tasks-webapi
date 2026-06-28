# ADR-0040: オンボーディング(会員登録)と Keycloak credential プロビジョニング方式

- **Status**: Proposed
- **Date**: 2026-06-28
- **Deciders**: @win2cot
- **Tags**: security, onboarding, keycloak, frontend, backend

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

dev 動作確認(2026-06-28、Issue #817)で、**「Keycloak アカウントも無い完全新規の人」がサービスを開始する導線が存在しない**ことが判明した。`registrationAllowed=false`(`keycloak/realm-export/tasks-realm.json`)で Keycloak ホスト型の自由登録は無効、`TasksJwtAuthenticationConverter`(L35-36)は未登録 `sub` を `UserNotRegisteredException` で弾き JIT 作成しない、`web/` に会員登録・招待受諾画面が無い。

オンボーディングは **2 フロー**を要する(#817 でユーザー確認済み):

- **Flow A(招待受諾)**: 既存テナントの Tenant Admin が email で招待 → 受諾 → 参加。フロー方式は **ADR-0017 §3.1 で決定済(自前招待トークン)**。
- **Flow B(テナント作成 / 創業者)**: 完全新規の人が自分でテナントを立ち上げる。`POST /api/tenants`(`CreateTenantUseCase`)は **認証済み・テナント未所属ユーザー前提**で実装済だが、その手前の「アカウント作成」が無い。

両フローに共通する未確定/未実装の核心が 2 つある:

1. **会員登録時の Keycloak credential 転送機構**: ADR-0006 §3.3 は「資格情報(password / MFA)は Keycloak が SoT、SPI は `CredentialInputValidator` を実装しない(Keycloak ネイティブ credential store)」と決定済。一方 ADR-0006 §3.3 末尾は会員登録を「本システム 1 画面(profile = project DB、password = Keycloak へ転送)」とする。**この「Keycloak へ転送」の具体機構は未確定・未実装**。SPI は profile を read-only federate(+ email のみ write、+ 初回 oidc_sub correlation write)するが、password を受ける口を持たない。

2. **JIT(要件 §3.2.1)と SPI 設計(ADR-0006)の不整合**: 要件 §3.2.1 は「初回ログイン時、`oidc_sub` が users に無ければ JIT で users 行を自動作成(セルフサインアップ前提)」とする。しかし ADR-0006 では **users 行は本システムの登録経路(招待 API / 会員登録)が作成**し、SPI は app `users` を read-only federate する。**users 行が無い人は SPI lookup で見つからず Keycloak 認証自体が不可能 → JWT を取れず JIT が発火しない**(鶏卵)。すなわち「ログイン時に users 行を作る JIT」は SPI 構成と両立せず、§3.2.1 の JIT は実体としては ADR-0006 §3.2 の「初回ログイン時 `oidc_sub` correlation write」(既存行への突合キー書き戻し。行**作成**ではない)を指すべきである。

関連: ADR-0006(SPI / 属性所有 / credential 所在)、ADR-0017(招待・credential 回復フロー)、要件定義書 §3.2.1 / §3.3.1 / §3.3.2、Issue #817 / #793(招待発行 API)。

## 2. 検討した選択肢(Options Considered)

会員登録時に **password を Keycloak の credential store に設定する機構**をどう実装するか。フロー(ADR-0017)・画面(ADR-0006「1 画面」)は決定済のため、本 ADR の主争点はこの機構と、上記 JIT 不整合の整理である。

### 選択肢 A: Keycloak Admin REST API(confidential service-account クライアント)

- 概要: 会員登録ユースケースが、project DB に `users` 行(または既存行)を確保した後、**Keycloak Admin REST API**(`PUT /admin/realms/{realm}/users/{id}/reset-password`)を confidential client(client_credentials grant)で呼び、password を Keycloak ネイティブ credential store に設定する。Keycloak 上の user 表現は SPI federation で app `users` を参照(必要なら `addUser` correlation)。
- 利点:
  - **ADR-0006 §3.3 と完全整合**: 資格情報は Keycloak が SoT のまま。SPI は credential に一切関与しない設計を維持。
  - MFA / WebAuthn / password policy / reset(ADR-0017 §3.2 の Keycloak ホスト型)をフル活用できる。
  - 機構が標準 API。Keycloak バージョン更新に追従しやすい。
- 欠点:
  - confidential client(service account)+ client secret の管理(SSM SecureString)が要る。最小権限ロール(`manage-users` 等)の付与設計が要る。
  - app → Keycloak への同期呼び出しが登録トランザクションに加わる(補償が要る、ADR-0006 §3.3「順序+リトライ/補償」)。
- リスク・未知数: SPI federated user に対する Admin API `reset-password` の対象 user id 解決(federation link の id)。dev 実機での疎通確認が必要。

### 選択肢 B: SPI を credential 書込対応に拡張(`CredentialInputUpdater`)

- 概要: SPI に `CredentialInputUpdater`(必要なら `CredentialInputValidator`)を実装し、会員登録経路で credential も SPI 経由(= app 側ストア)で設定・検証する。
- 利点: app から見て単一経路(SPI)で profile + credential を扱える。Admin API client が不要。
- 欠点:
  - **ADR-0006 §3.3 の中核決定(資格情報は Keycloak SoT、SPI は credential 非関与)を覆す**。password ハッシュ保管・MFA・WebAuthn・reset を自前で抱えることになり、Keycloak の credential 機能の利点を失う。
  - セキュリティ実装責任(ハッシュ方式・総当り対策・MFA)が増える。
- リスク・未知数: ADR-0006 改訂(Supersede 相当)が必要。MVP のスコープを大きく超える。

### 選択肢 C: 登録時に Keycloak action token(execute-actions-email / UPDATE_PASSWORD)

- 概要: 会員登録では password を受け取らず、app が `users` 行作成後に Keycloak `execute-actions-email`(UPDATE_PASSWORD)を発行、利用者は Keycloak ホスト画面で password を初期設定する。
- 利点: credential 設定を Keycloak に完全委譲。app は password を扱わない。
- 欠点:
  - **ADR-0017 §2 で招待について明確に却下した方式**(26.6 one-time 化の影響、Keycloak テーマ保守、「1 画面で完結」の UX を壊す)。会員登録 1 画面(ADR-0006)とも不整合。
  - メール往復が 1 段増え、オンボーディング完了率を下げる。
- リスク・未知数: ADR-0017 の決定と衝突。

## 3. 決定(Decision)

**採用**: 選択肢 A(Keycloak Admin REST API)。あわせて JIT 不整合を下記方針で整理する。

> 本 ADR は **Proposed**。credential 機構(A/B/C)はレビューで確定する。以下は提案する決定内容。

### 3.1 credential 転送 = Keycloak Admin REST API

- 会員登録ユースケース(後述 3.3 の共有プリミティブ)は、project DB に profile を書いた後、**confidential service-account クライアント**で Keycloak Admin API を呼び password を設定する。
- ADR-0006 §3.3「password は Keycloak が SoT、SPI は credential 非関与」を**維持**する(選択肢 A はこれを崩さない)。
- client secret は SSM SecureString(`/tasks/${env}/app/keycloak-admin-client-secret` 等)で管理し、最小権限ロール(realm-management の `manage-users` 限定)を付与する。Terraform は既存 `webapi_ssm` IAM で充足するか別途確認する。

### 3.2 JIT 不整合の整理(要件 §3.2.1 改訂)

- **「初回ログイン時に users 行を JIT 作成する」経路は設けない**(SPI 構成では新規 user は users 行が無いと認証不能で、login-time JIT は原理的に発火しない)。
- users 行の作成主体は **会員登録(Flow A 受諾 / Flow B 創業者)** とする。初回ログイン時に発生するのは ADR-0006 §3.2 の **`oidc_sub` correlation write(既存行への突合キー書き戻し)**のみ。
- `TasksJwtAuthenticationConverter` の `findByOidcSub(sub).orElseThrow(UserNotRegisteredException)` は **維持**する(correlation 済の登録ユーザーのみ通す)。要件 §3.2.1 の「JIT で users 行を自動作成」記述を「会員登録で作成 + 初回ログインで oidc_sub correlation」に改訂する。

### 3.3 共有プリミティブ「会員登録」+ 2 フローの結線

両フローは **会員登録プリミティブ**(`RegisterMemberUseCase` 仮称)を共有する:

> 入力: email, full_name, full_name_kana, department_name?(profile)+ password。
> 処理: ① project DB に `users` 行 upsert(profile、ADR-0006 属性所有に従う)→ ② Keycloak Admin API で credential 設定(3.1)。順序は project 先 → Keycloak 後、未完了マーク + リトライ/補償(ADR-0006 §3.3)。

- **Flow A(招待受諾、ADR-0017 §3.1)**:
  - `GET /api/invitations/{token}`(受諾画面用、**トークン非消費**): テナント名・招待者・email を返す。期限切れ/使用済みは状態を返し案内画面へ。
  - `POST /api/invitations/{token}/accept`(受諾確定、トークン消費): 未登録なら会員登録プリミティブ実行 +`user_tenants` 紐付け + トークン USED を **1 トランザクション**。登録済みなら「ログインして参加」→ ログイン後に紐付け + 消費。
  - 画面: `web/invitation.html`(受諾 + 未登録なら会員登録 1 画面)。
- **Flow B(創業者 / セルフサインアップ)**:
  - `POST /api/signup`(公開、未認証可、招待トークン無し): 会員登録プリミティブのみ実行(`user_tenants` 紐付けは無し)。完了後ログイン → 既存 `POST /api/tenants`(S-11、実装済)でテナント作成。
  - 画面: `web/signup.html`(会員登録 1 画面)+ ログイン画面に「新規登録」導線。
- 招待トークンの TTL / one-time / 再送は ADR-0017 §3.1 のまま。

### 3.4 スコープ境界

- password reset / email 検証 / MFA setup は **Keycloak ホスト型のまま**(ADR-0017 §3.2、追加実装なし)。
- SaaS Admin の別レルム分離は本 ADR 対象外(#816 で単一レルム維持を確認済)。

## 4. 理由(Rationale)

- 選択肢 A は **ADR-0006 §3.3 の中核(credential = Keycloak SoT)を崩さず**、MFA / reset / password policy を Keycloak に委譲し続けられる。B は ADR-0006 を覆し自前 credential 管理の負債を負う、C は ADR-0017 で却下済の方式に戻る。
- 「会員登録プリミティブ」を共有することで、Flow A と Flow B の重複(users 行作成 + credential 設定)を 1 箇所に集約できる。差分は「招待トークン検証 + user_tenants 紐付け」の有無のみ。
- JIT を「行作成」でなく「oidc_sub correlation」と明確化することで、要件 §3.2.1 と ADR-0006 の矛盾を解消し、`TasksJwtAuthenticationConverter` の現行挙動(未登録は弾く)を正当化できる。
- 捨てる利点: B の「単一経路(SPI のみ)」の単純さ、C の「app が password を一切持たない」安心感。前者は ADR-0006 と非両立、後者は UX とメール往復コストで不採用。

## 5. 影響(Consequences)

### 良い影響(Positive)

- ゼロからの参入経路(Flow A / B)が両方成立し、dev が seed 依存でなくなる。
- credential 管理が Keycloak に一元化されたまま、会員登録 UX は本システム 1 画面で完結。
- 登録ロジックが 1 プリミティブに集約され、テスト・監査点が単一化。

### 悪い影響・制約(Negative)

- Keycloak Admin 用 confidential client + secret(SSM)+ 最小権限ロールの新規運用要素。
- 登録トランザクションに app→Keycloak 同期呼び出しが入り、補償(未完了マーク + リトライ)の実装・テストが要る。
- `POST /api/signup` は未認証公開エンドポイント。レート制限 / email 形式検証 / 重複ハンドリング(E_CONFLICT)/ bot 対策の検討が要る。

### 既存ドキュメント・規約への波及

- 要件定義書 §3.2.1(JIT 記述)を「会員登録で users 行作成 + 初回ログインで oidc_sub correlation」に改訂。
- 要件定義書 §3.3.2 / ADR-0017 §3.1 の受諾フローに、本 ADR のエンドポイント名(`/api/invitations/{token}` 等)を結線。
- ADR-0006 は改訂不要(選択肢 A は §3.3 と整合)。相互参照を追記。
- `keycloak/realm-export`(Admin client 定義)/ Terraform(SSM secret)/ `infra` への波及。

## 6. 実装メモ(Implementation Notes)

ADR 承認後の PR 分割案(各単独で green):

1. **PR1(設計確定)**: 本 ADR Accepted 化 + 要件 §3.2.1/§3.3.2 改訂 + Keycloak Admin client / SSM secret の infra 定義。
2. **PR2(会員登録プリミティブ + credential 転送)**: `RegisterMemberUseCase` + Keycloak Admin API クライアント(provider パターン、dev はモック可)+ 補償。IT は Testcontainers + Keycloak(または Admin API クライアントの contract テスト)。
3. **PR3(Flow A 受諾)**: `GET /api/invitations/{token}` + `POST .../accept` + `web/invitation.html`。E2E ハッピーパス(招待 → 受諾 → 参加)。
4. **PR4(Flow B サインアップ)**: `POST /api/signup` + `web/signup.html` + ログイン画面の新規登録導線。E2E(サインアップ → ログイン → テナント作成)。

検証: dev native 実機で「招待受諾で参加」「サインアップ → テナント作成」が通ること。credential 設定は native 環境での Admin API 疎通を実機確認(#810 と同様、JVM テストだけでは不足)。

## 7. 参考リンク(References)

- ADR-0006: Keycloak User Storage SPI(`docs/adr/0006-keycloak-user-storage-spi.md`)§3.1–§3.3
- ADR-0017: 招待・credential 回復フロー(`docs/adr/0017-invitation-and-credential-recovery-flows.md`)§3.1–§3.3
- 要件定義書 §3.2.1 / §3.3.1 / §3.3.2(`docs/specs/要件定義書.md`)
- Issue #817(オンボーディング未実装)、#793(招待発行 API)、#816(テナントコンテキスト漏れ)
- Keycloak Admin REST API: `PUT /admin/realms/{realm}/users/{id}/reset-password`
