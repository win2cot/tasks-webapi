# Keycloak 連携機能 シーケンス図

Keycloak と連携する主要フローの実装シーケンス。関連 ADR: [ADR-0006](../adr/0006-keycloak-user-storage-spi.md)(User Storage SPI federation)/ [ADR-0040](../adr/0040-onboarding-registration-and-credential-provisioning.md)(オンボーディング + credential provisioning)/ [ADR-0017](../adr/0017-invitation-and-credential-recovery-flows.md)(招待・資格回復)/ [ADR-0041](../adr/0041-post-deploy-dev-e2e-and-email-verification.md)(dev E2E)。

前提となる識別モデル(#862 で全環境に配線):

- **app `users` テーブルが SoT**。Keycloak は **User Storage SPI**(`tasks-webapi-user-storage`)で `users` を read-only federation する(接続は read-only DB ユーザー `keycloak_spi_read`)。
- **credential(パスワード)は Keycloak が SoT**(ADR-0006 §3.3)。SPI は `CredentialInputValidator` を実装せず、資格情報は Keycloak native store が持つ。
- federated ユーザーの JWT `sub` は `f:{component-id}:{users.id}` 形式。プロフィール(`firstName`/`lastName`/`email`)は SPI が `users.full_name`/`email` から供給する。
- dev/local のシード4ユーザーは realm-export のローカルユーザーとして共存し、ログインでローカルが優先(shadow)される。
- ブラウザ SPA(`web`)は **keycloak-js**(`onLoad:'login-required'`, `pkceMethod:'S256'`, flow=standard)で **OIDC Authorization Code フロー + PKCE(S256)** を使う。

図中の `box` は**同一プロセス**を表す(SPI は別サービスではなく Keycloak プロセス内の provider、`Tasks…Converter`/`OidcSubCorrelationService`/`TenantContextFilter` は webapi 内部クラス)。

---

## 1. ログイン → JWT 認証 → oidc_sub correlation → ロール解決

SPA が Keycloak で **認可コードフロー + PKCE** で認証し、得た JWT を webapi に提示。webapi は **段階A(JWT 認証)**と**段階B(TenantContextFilter)**で app RDS を複数回参照して principal と権限を確定する。

```mermaid
sequenceDiagram
    autonumber
    actor U as User (Browser)
    participant SPA as SPA (web / keycloak-js)
    box Keycloak (auth-dev)
        participant KC as Keycloak core
        participant SPI as User Storage SPI
    end
    box webapi (native)
        participant JAC as Tasks…JwtAuthConverter
        participant COR as OidcSubCorrelationService
        participant TCF as TenantContextFilter
    end
    participant DB as app RDS

    Note over U,KC: OIDC Authorization Code + PKCE(S256)
    U->>SPA: アクセス
    SPA->>KC: GET /authorize (response_type=code, code_challenge=S256)
    KC-->>U: ログイン画面
    U->>KC: 認証情報入力
    KC->>SPI: getUserByEmail(email)  (詳細は図3)
    SPI->>DB: SELECT ... FROM users WHERE email=?
    DB-->>SPI: users 行
    SPI-->>KC: UserModel(federated, credential=KC native)
    KC->>KC: パスワード検証(native store)
    KC-->>SPA: 302 redirect_uri?code=... (認可コード)
    SPA->>KC: POST /token (code + code_verifier)
    KC-->>SPA: ID / Access / Refresh Token (sub, email)

    Note over SPA,DB: 段階A — JWT 認証(TasksJwtAuthenticationConverter)
    SPA->>JAC: REST + Bearer JWT (+ X-Tenant-Id)
    Note over JAC: JWT 署名は JWKS で検証(Keycloak、キャッシュ)
    JAC->>COR: resolve(sub, email)
    COR->>DB: findByOidcSub(sub)
    alt 未ヒット & email が pending 行に一致
        COR->>DB: findByEmail → UPDATE users SET oidc_sub=sub (初回 correlation)
    end
    COR-->>JAC: users 行(anonymized/inactive なら 401)
    JAC->>DB: app_admin_users.existsByOidcSub(sub)?
    DB-->>JAC: (存在すれば ROLE_APP_ADMIN)

    Note over TCF,DB: 段階B — テナント解決(TenantContextFilter)
    alt 免除パス(/api/auth・/api/tenants・/api/signup・/api/invitations・/api/platform・/actuator・/api/users/me)
        TCF-->>SPA: 素通り(テナント検証なし)
    else X-Tenant-Id 指定あり
        TCF->>DB: user_tenants.findActiveRole(userId, tenantId)
        alt ACTIVE メンバー
            TCF->>TCF: + ROLE_TENANT_ADMIN / ROLE_MEMBER, TenantContext.set(tenantId)
        else 非メンバー
            TCF-->>SPA: 403
        end
    else X-Tenant-Id なし(非免除)
        TCF->>DB: userTenantsResolverService.resolveInitial(userId) (ADR-0016)
        alt 所属あり
            TCF->>TCF: 初期テナント + role, TenantContext.set
        else 所属 0 件
            TCF-->>SPA: 403
        end
    end
    Note over DB: 以降の業務クエリは Hibernate フィルタで WHERE tenant_id=:ctx
    TCF-->>SPA: 認可済みレスポンス
```

参照する app RDS テーブルは **`users`(correlation)/ `app_admin_users`(SaaS Admin)/ `user_tenants`(テナントロール + メンバー検証)** の3つ。

---

## 2. 会員登録(セルフサインアップ, double opt-in)

`POST /api/signup/request` → 確認メール → `POST /api/signup/{token}/complete` で users 行 upsert + Keycloak credential provisioning。登録直後はテナント未所属(ADR-0040 §3.5)。

`provisionCredential(...)` は **アプリの1メソッド**(`KeycloakAdminCredentialAdapter`)で、その内部で Keycloak Admin REST を複数回呼ぶ(下図の枠内)。

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SPA as SPA (signup.html)
    box webapi
        participant API as Signup/CompleteSignup/RegisterMember
    end
    participant SES as SES / メール
    box Keycloak
        participant KC as Keycloak Admin API
        participant SPI as User Storage SPI
    end
    participant DB as app RDS

    U->>SPA: メール入力 → 登録
    SPA->>API: POST /api/signup/request {email}
    API->>DB: INSERT signup_requests(PENDING, token_hash)
    API->>SES: 確認メール送信(signup-complete.html?token)
    API-->>SPA: 202(列挙耐性: 常に同一応答)
    SES-->>U: 確認メール
    U->>SPA: 確認リンク → signup-complete.html
    SPA->>API: GET /api/signup/{token}(非消費・email エコー)
    U->>SPA: 氏名 / パスワード入力 → 完了
    SPA->>API: POST /api/signup/{token}/complete
    API->>DB: UserRegistrationPort.upsertPendingMember: INSERT users(oidc_sub="pending:email", full_name,…)

    rect rgb(238,238,238)
        Note over API,SPI: provisionCredential(email, fullName, password) — KeycloakAdminCredentialAdapter
        API->>KC: ① POST /token (client_credentials, tasks-webapi-admin)
        API->>KC: ② GET /users?email=&exact=true
        KC->>SPI: searchForUser(email) → 直前 upsert した行を federation(図3)
        KC-->>API: federated user id (f:comp:id)
        Note over API,KC: 見つからなければ POST /users で作成(find-or-create の防御分岐。SPI 有効時は upsert 済み行が federation されるため通常は発火しない)
        API->>KC: ③ PUT /users/{id}/reset-password(パスワード設定)
        API->>KC: ④ PUT /users/{id}(emailVerified=true)
    end

    API->>DB: signup_requests → USED
    API-->>SPA: 201(登録完了)
    U->>SPA: 作成ユーザーでログイン(→ 図1)
    Note over U,SPA: 初回ログインで oidc_sub を pending → 実 sub に correlation。テナント未所属のため「テナント作成」導線へ着地。
```

Keycloak 失敗時は signup トークンを消費しない(再試行で回復、ADR-0040 §3.5)。列挙耐性のためメール送信失敗は握りつぶす。

---

## 3. User Storage SPI federation(図1・図2 の `KC→SPI` の内部展開)

**単独のフローではなく、図1 のログイン認証 / 図2 の Admin ユーザー検索から Keycloak が呼ぶ内部経路のズームイン**(だから利用者は登場しない)。`keycloak_spi_read`(SELECT のみ)で app `users` に JDBC 接続し、`UserModel` を組み立てる。

```mermaid
sequenceDiagram
    autonumber
    box Keycloak プロセス(SPI JAR)
        participant KC as Keycloak core
        participant F as ...ProviderFactory
        participant P as ...UserStorageProvider
        participant R as UserRepository (JDBC)
        participant A as UserAdapter
    end
    participant DB as app RDS (users)

    Note over KC: 図1 のログイン認証 / 図2 の Admin 検索から呼ばれる
    KC->>F: create(session, component)
    Note over F: 接続情報を component config → env(SPI_DB_JDBC_URL/USERNAME/PASSWORD)で解決
    F->>R: new UserRepository(jdbcUrl, keycloak_spi_read, pw)
    KC->>P: getUserByEmail / searchForUser
    P->>R: findByEmail / search
    R->>DB: SELECT ... FROM users WHERE ... (DriverManager)
    DB-->>R: UserRow
    R-->>P: UserRow
    P->>A: adapt(row)
    Note over A: full_name → firstName & lastName(必須項目) / status ACTIVE → enabled / emailVerified=true / credential は KC native(SPI 非提供)
    A-->>KC: UserModel(federated)
```

`priority=0` / `cachePolicy=NO_CACHE`。realm への component 登録は realm-export に含む(CI/local は fresh import で有効)が、deployed は `--import-realm` IGNORE_EXISTING のため Admin API で登録する。

---

## 4. 招待(発行 → 受諾)

Tenant Admin が招待を**発行**(`InviteUserUseCase`)し、招待された利用者が**受諾**する(`AcceptInvitationUseCase`)。受諾は会員登録プリミティブを共有し、テナント紐付け + トークン消費を原子化する(ADR-0040 §3.5・ADR-0017、#831)。

```mermaid
sequenceDiagram
    autonumber
    actor Adm as Tenant Admin
    actor U as 招待された User
    participant SPA as SPA
    box webapi
        participant API as Invite/AcceptInvitation/RegisterMember
    end
    participant SES as SES / メール
    box Keycloak
        participant KC as Keycloak Admin API
    end
    participant DB as app RDS

    Note over Adm,DB: 発行(InviteUserUseCase、要 ROLE_TENANT_ADMIN)
    Adm->>SPA: メンバー招待(email, role)
    SPA->>API: POST /api/tenant/users/invite {email, role} (X-Tenant-Id)
    API->>DB: isAlreadyMember(tenantId, email)?  (既メンバーなら 409)
    API->>DB: revokePending(既存 PENDING を REVOKED)
    API->>DB: INSERT invitations(token_hash, tenant_id, email, role, TTL 7d, PENDING)
    API->>SES: 招待メール送信(invitation.html?token)
    API-->>SPA: 201(招待作成・メール送信)
    SES-->>U: 招待メール

    Note over U,DB: 受諾(AcceptInvitationUseCase)
    U->>SPA: 招待リンク → invitation.html?token
    SPA->>API: GET /api/invitations/{token}(非消費)
    API-->>SPA: 招待内容(テナント名 / email)
    U->>SPA: 氏名 / パスワード入力 → 参加
    SPA->>API: POST /api/invitations/{token}/accept
    API->>DB: RegisterMemberUseCase.register → UserRegistrationPort.upsertPendingMember(users)
    rect rgb(238,238,238)
        Note over API,KC: provisionCredential(...) — Keycloak Admin API 群(図2 の ①〜④ と同じ)
        API->>KC: token → find-or-create → reset-password → emailVerified
    end
    API->>DB: MembershipFinalizer: user_tenants 紐付け + invitation USED(原子化)
    API-->>SPA: 201
    U->>SPA: ログイン(→ 図1)。所属テナントありのため dashboard へ。
```

---

## 参考

- [ADR-0006](../adr/0006-keycloak-user-storage-spi.md) / [ADR-0040](../adr/0040-onboarding-registration-and-credential-provisioning.md) / [ADR-0017](../adr/0017-invitation-and-credential-recovery-flows.md) / [ADR-0041](../adr/0041-post-deploy-dev-e2e-and-email-verification.md)
- 実装: `security/adapter/web/TasksJwtAuthenticationConverter` / `security/adapter/web/TenantContextFilter` / `security/usecase/OidcSubCorrelationService` / `tenant/adapter/web/{SignupController,InvitationController,TenantMemberController}` / `tenant/usecase/{InviteUserUseCase,AcceptInvitationUseCase}` / `user/usecase/RegisterMemberUseCase` / `user/adapter/external/KeycloakAdminCredentialAdapter` / `keycloak/`(SPI)
- dev 配線の詳細(SPI_DB_*、SSM、DB ユーザー、component 追加手順)は `infra/environments/dev/rds.tf` と ADR-0006/0040。
