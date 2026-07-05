# Keycloak 連携機能 シーケンス図

Keycloak と連携する主要フローの実装シーケンス。関連 ADR: [ADR-0006](../adr/0006-keycloak-user-storage-spi.md)(User Storage SPI federation)/ [ADR-0040](../adr/0040-onboarding-registration-and-credential-provisioning.md)(オンボーディング + credential provisioning)/ [ADR-0041](../adr/0041-post-deploy-dev-e2e-and-email-verification.md)(dev E2E)。

前提となる識別モデル(#862 で全環境に配線):

- **app `users` テーブルが SoT**。Keycloak は **User Storage SPI**(`tasks-webapi-user-storage`)で `users` を read-only federation する(接続は read-only DB ユーザー `keycloak_spi_read`)。
- **credential(パスワード)は Keycloak が SoT**(ADR-0006 §3.3)。SPI は `CredentialInputValidator` を実装せず、資格情報は Keycloak native store が持つ。
- federated ユーザーの JWT `sub` は `f:<component-id>:<users.id>` 形式。プロフィール(`firstName`/`lastName`/`email` 等)は SPI が `users.full_name`/`email` から供給する。
- dev/local のシード4ユーザーは realm-export のローカルユーザーとして共存し、ログインでローカルが優先(shadow)される。

---

## 1. ログイン → JWT 認証 → oidc_sub correlation → ロール解決

SPA が Keycloak で OIDC 認証(PKCE)し、取得した JWT を webapi に提示。webapi は `sub` から app ユーザーを解決し、権限を確定する。

```mermaid
sequenceDiagram
    autonumber
    actor U as User (Browser)
    participant SPA as SPA (web)
    participant KC as Keycloak (auth-<env>)
    participant SPI as User Storage SPI
    participant DB as app RDS (users)
    participant API as webapi
    participant JAC as TasksJwtAuthenticationConverter
    participant COR as OidcSubCorrelationService

    U->>SPA: アクセス
    SPA->>KC: OIDC Authorization Code + PKCE
    KC->>SPI: getUserByUsername/Email(email)
    SPI->>DB: SELECT ... FROM users WHERE email=?
    DB-->>SPI: users 行
    SPI-->>KC: UserModel(federated, credentials=KC native)
    KC->>KC: パスワード検証(native store)
    KC-->>SPA: ID/Access Token (sub, email)
    SPA->>API: REST + Bearer JWT (X-Tenant-Id)
    API->>JAC: convert(jwt)
    JAC->>COR: resolve(sub, email)
    alt findByOidcSub(sub) ヒット
        COR-->>JAC: users 行(correlation 済)
    else 未ヒット & email が pending 行に一致
        COR->>DB: UPDATE users SET oidc_sub=sub WHERE email=? (pending)
        COR-->>JAC: users 行(初回 correlation)
    end
    JAC->>DB: app_admin_users に sub 存在?
    DB-->>JAC: (存在すれば ROLE_APP_ADMIN)
    Note over API: テナント別ロール(TENANT_ADMIN/MEMBER)は<br/>X-Tenant-Id + user_tenants から TenantContext で解決
    JAC-->>API: TasksAuthenticationToken(principal, authorities)
    API-->>SPA: 認可済みレスポンス
```

`isAnonymized()` / `isInactive()` の場合は認証を拒否する(`UserAnonymizedException` / `UserInactiveException`)。

---

## 2. 会員登録(セルフサインアップ, double opt-in)

`POST /api/signup/request` → 確認メール → `POST /api/signup/{token}/complete` で users 行 upsert + Keycloak credential provisioning。登録直後はテナント未所属(ADR-0040 §3.5)。

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SPA as SPA (signup.html)
    participant API as webapi
    participant DB as app RDS
    participant SES as SES / メール
    participant KC as Keycloak Admin API
    participant SPI as User Storage SPI

    U->>SPA: メール入力 → 登録
    SPA->>API: POST /api/signup/request {email}
    API->>DB: INSERT signup_requests(PENDING, token_hash)
    API->>SES: 確認メール送信(署名リンク: signup-complete.html?token)
    API-->>SPA: 202(列挙耐性: 常に同一応答)
    SES-->>U: 確認メール
    U->>SPA: 確認リンク → signup-complete.html
    SPA->>API: GET /api/signup/{token}(非消費・email エコー)
    U->>SPA: 氏名 / パスワード入力 → 完了
    SPA->>API: POST /api/signup/{token}/complete
    API->>API: CompleteSignupUseCase(token 検証)
    API->>DB: upsertPendingMember: INSERT users(oidc_sub="pending:email", full_name,...)
    API->>KC: provisionCredential(email, fullName, password)
    Note over API,KC: KeycloakAdminCredentialAdapter<br/>client_credentials(tasks-webapi-admin)
    API->>KC: GET /admin/realms/tasks/users?email=&exact=true
    KC->>SPI: searchForUser(email)
    SPI->>DB: SELECT ... users WHERE email=?
    DB-->>SPI: 直前に upsert した行
    SPI-->>KC: federated user (f:<comp>:<id>)
    KC-->>API: user id
    Note over API,KC: 見つからなければ POST /users で<br/>ローカル作成(SPI 未活性環境向け find-or-create)
    API->>KC: PUT /users/{id}/reset-password(パスワード)
    API->>KC: PUT /users/{id}(emailVerified=true)
    API->>DB: signup_requests → USED
    API-->>SPA: 201(登録完了)
    U->>SPA: 作成ユーザーでログイン(→ 図1)
    Note over U,SPA: 初回ログインで oidc_sub を pending → 実 sub に correlation。<br/>テナント未所属のため「テナント作成」導線へ着地。
```

Keycloak 失敗時は signup トークンを消費しない(再試行で回復、ADR-0040 §3.5)。列挙耐性のためメール送信失敗は握りつぶす。

---

## 3. User Storage SPI federation(Keycloak → app users 読取経路)

Keycloak が federated ユーザーを解決する内部経路。`keycloak_spi_read`(SELECT のみ)で app `users` に JDBC 接続する。

```mermaid
sequenceDiagram
    autonumber
    participant KC as Keycloak core
    participant F as ...ProviderFactory
    participant P as ...UserStorageProvider
    participant R as UserRepository (JDBC)
    participant DB as app RDS (users)
    participant A as UserAdapter

    KC->>F: create(session, component)
    Note over F: 接続情報を component config →<br/>env(SPI_DB_JDBC_URL/USERNAME/PASSWORD)で解決
    F->>R: new UserRepository(jdbcUrl, keycloak_spi_read, pw)
    KC->>P: getUserByEmail / searchForUser
    P->>R: findByEmail / search
    R->>DB: SELECT ... FROM users WHERE ... (DriverManager)
    DB-->>R: UserRow
    R-->>P: UserRow
    P->>A: adapt(row)
    Note over A: full_name → firstName & lastName(必須項目)<br/>status ACTIVE → enabled / emailVerified=true<br/>credential は KC native(SPI は非提供)
    A-->>KC: UserModel(federated)
```

`priority=0` / `cachePolicy=NO_CACHE`。realm への component 登録は realm-export に含む(CI/local は fresh import で有効)が、deployed は `--import-realm` IGNORE_EXISTING のため Admin API で登録する。

---

## 4. 招待受諾(テナントへの参加, Flow A)

`GET /api/invitations/{token}` で内容表示 → `POST` で受諾。新規ユーザーは会員登録プリミティブを共有し、テナント紐付け + トークン消費を原子化する(ADR-0040 §3.5、#831)。

```mermaid
sequenceDiagram
    autonumber
    actor U as 招待された User
    participant SPA as SPA (invitation.html)
    participant API as webapi
    participant DB as app RDS
    participant KC as Keycloak Admin API

    U->>SPA: 招待リンク → invitation.html?token
    SPA->>API: GET /api/invitations/{token}(非消費)
    API-->>SPA: 招待内容(テナント名 / email)
    U->>SPA: 氏名 / パスワード入力 → 参加
    SPA->>API: POST /api/invitations/{token}/accept
    API->>API: AcceptInvitationUseCase
    API->>DB: RegisterMemberUseCase.register → UserRegistrationPort.upsertPendingMember(users)
    API->>KC: provisionCredential(...)(→ 図2 と同じ Keycloak 経路)
    API->>DB: MembershipFinalizer: user_tenants 紐付け + invitation USED(原子化)
    API-->>SPA: 201
    U->>SPA: ログイン(→ 図1)。所属テナントありのため dashboard へ。
```

---

## 参考

- [ADR-0006](../adr/0006-keycloak-user-storage-spi.md) / [ADR-0040](../adr/0040-onboarding-registration-and-credential-provisioning.md) / [ADR-0041](../adr/0041-post-deploy-dev-e2e-and-email-verification.md)
- 実装: `security/adapter/web/TasksJwtAuthenticationConverter` / `security/usecase/OidcSubCorrelationService` / `tenant/adapter/web/SignupController` / `user/usecase/RegisterMemberUseCase` / `user/adapter/external/KeycloakAdminCredentialAdapter` / `keycloak/`(SPI)
- dev 配線の詳細(SPI_DB_*、SSM、DB ユーザー、component 追加手順)は `infra/environments/dev/rds.tf` と ADR-0006/0040。
