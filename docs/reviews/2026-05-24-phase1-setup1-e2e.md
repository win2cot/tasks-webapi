# Phase 1 Setup 1 E2E 動作確認レポート

> **作成日**: 2026-05-24  
> **対象**: Setup 1-1〜1-6 の結合動作確認(Issue #219)  
> **方針**: CI 環境での静的検証 + ローカル実行時の期待値記録

---

## 1. 静的検証結果(CI 環境)

CI 環境(Docker / ブラウザ不可)で確認できる範囲を検証した。

### 1.1 構成ファイル存在確認

| ファイル | 状態 | 備考 |
|---------|------|------|
| `docker-compose.local.yml` | ✓ 存在 | MySQL 8.4 + Keycloak 24.0 定義済み |
| `keycloak/realm-export/tasks-realm.json` | ✓ 存在 | `tasks` realm、ロール・テストユーザー 4 名・クライアント定義済み |
| `webapi/src/main/resources/application.yml` | ✓ 存在 | env vars 注入パターン正常。OIDC_ISSUER_URI / DATASOURCE_* 参照 |
| `webapi/src/main/resources/db/migration/V1.0.0_01__create_tables.sql` | ✓ 存在 | users / tenants / user_tenants / tasks テーブル定義済み |
| `web/index.html` | ✓ 存在 | Bootstrap 5.3.3 + keycloak-js 24.0.5 + Auth.init() 組込み |
| `web/js/auth.js` | ✓ 存在 | PKCE(S256)+ sessionStorage 保存 + 自動 token 更新 |
| `web/js/api.js` | ✓ 存在 | Bearer token 自動付与 + X-Tenant-Id ヘッダ送信 |
| `web/css/app.css` | ✓ 存在 | スタイルシート |

### 1.2 Keycloak Realm 設定検証

`keycloak/realm-export/tasks-realm.json` の内容を確認した。

| 確認項目 | 状態 | 詳細 |
|---------|------|------|
| Realm 名 | ✓ | `tasks` |
| Realm roles | ✓ | `APP_ADMIN` / `TENANT_ADMIN` / `MEMBER` の 3 ロール |
| Client `tasks-webapi` | ✓ | publicClient=true、directAccessGrantsEnabled=true、PKCE S256 強制 |
| redirectUris | ✓ | `http://localhost:8080/*` + `http://localhost:5500/*` |
| テストユーザー 4 名 | ✓ | admin@example.com(APP_ADMIN) / tenant1-admin@example.com(TENANT_ADMIN) / tenant1-member1@example.com(MEMBER) / tenant1-member2@example.com(MEMBER) |

### 1.3 docker-compose.local.yml 設定検証

| 確認項目 | 状態 | 詳細 |
|---------|------|------|
| MySQL 8.4 イメージ | ✓ | `mysql:8.4` |
| MySQL healthcheck | ✓ | `mysqladmin ping` / interval: 10s / retries: 5 / start_period: 30s |
| MySQL ポート | ✓ | `3306:3306` |
| Keycloak 24.0 イメージ | ✓ | `quay.io/keycloak/keycloak:24.0` |
| Keycloak realm import | ✓ | `--import-realm` + `./keycloak/realm-export` マウント |
| Keycloak ポート | ✓ | `18080:8080` |
| MySQL ボリューム永続化 | ✓ | `mysql-data` named volume |

### 1.4 application.yml 設定検証

| 確認項目 | 状態 | 詳細 |
|---------|------|------|
| DATASOURCE_URL 参照 | ✓ | `${DATASOURCE_URL}` |
| DATASOURCE_USERNAME 参照 | ✓ | `${DATASOURCE_USERNAME}` |
| DATASOURCE_PASSWORD 参照 | ✓ | `${DATASOURCE_PASSWORD:}` (デフォルト空文字で IAM auth 対応) |
| OIDC_ISSUER_URI 参照 | ✓ | `${OIDC_ISSUER_URI}` → JWT issuer 検証に使用 |
| Flyway 有効 | ✓ | `enabled: true` / `classpath:db/migration` |
| JPA ddl-auto=validate | ✓ | Flyway との二重管理防止 |
| actuator 公開 | ✓ | `health` / `info` のみ公開 |
| サーバーポート | ✓ | `8080` |

### 1.5 SecurityConfig 確認

`/actuator/health` と `/actuator/info` は `permitAll()`、それ以外は JWT 認証必須の設定が確認できた。  
`/api/auth/me` エンドポイントは Sprint 0 未実装のため存在しない(Issue 本文の代替確認手順 `/actuator/health` を採用)。

---

## 2. ローカル実行 E2E 手順と期待値

> CI 環境では Docker / ブラウザが不可のため、ローカル開発者向けの実行手順と期待値を記録する。  
> 実際に実行した場合は §3 の「実行結果記録欄」に転記すること。

### Step 1: Docker Compose 起動

```bash
docker compose -f docker-compose.local.yml up -d
docker compose -f docker-compose.local.yml ps
```

**期待値**:
```
NAME                        STATUS          PORTS
tasks-webapi-mysql-1        Up (healthy)    0.0.0.0:3306->3306/tcp
tasks-webapi-keycloak-1     Up              0.0.0.0:18080->8080/tcp
```

MySQL が healthy になるまで最大 80 秒かかる場合がある。

### Step 2: tasks-webapi 起動 + Flyway 確認

```bash
source .env.local && ./gradlew :webapi:bootRun
```

**期待される起動ログ**:
```
Flyway Community Edition ... by Redgate ...
Successfully applied 1 migration(s) to schema `tasks`
Tomcat started on port 8080
Started TasksWebapiApplication in X.XXX seconds
```

**Flyway migration テーブル確認**:
```bash
docker compose -f docker-compose.local.yml exec mysql \
  mysql -u tasks_webapi -ptasks_webapi tasks -e "SHOW TABLES;"
```

期待値: `flyway_schema_history` / `users` / `tenants` / `user_tenants` / `tasks` テーブルが表示される。

**ヘルスチェック**:
```bash
curl http://localhost:8080/actuator/health
```

期待値: `{"status":"UP"}`

### Step 3: Keycloak Admin Console 確認

ブラウザで `http://localhost:18080` を開き、`admin` / `admin` でログイン。

**確認ポイント**:
- 左上ドロップダウンで `tasks` realm を選択できる
- Realm roles: `APP_ADMIN` / `TENANT_ADMIN` / `MEMBER` が存在する
- Users: 4 名のテストユーザーが存在する
- Clients: `tasks-webapi` が存在し、`Direct access grants` が有効

### Step 4: Frontend 起動 + ブラウザ確認

```bash
cd web && python3 -m http.server 5500
```

ブラウザで `http://localhost:5500/index.html` を開く。

**期待値**:
- ローディングスピナーが表示される
- Keycloak ログイン画面(realm=tasks)にリダイレクトされる

### Step 5: テストユーザーでログイン

`tenant1-member1@example.com` / `password` でログイン。

**期待値**:
- `http://localhost:5500/index.html` に戻ってくる
- ナビバーに `Tenant1 Member1` が表示される
- ユーザー情報カードが表示される(API 呼び出し結果 or エラー)

### Step 6: sessionStorage への JWT 保存確認

ブラウザ DevTools → Application → Session Storage を開く。

**期待値**: `kc-callback-*` または Keycloak 関連キーが sessionStorage に存在する。  
auth.js の実装では keycloak-js が内部的に sessionStorage を使用する。

### Step 7: JWT デコードで MEMBER ロール確認

ブラウザ DevTools Console で:

```javascript
const kc = /* keycloak instance はグローバルには公開されていないため */
// 代わりに curl でトークン取得して確認
```

または curl でトークン取得してデコード:

```bash
TOKEN=$(curl -s \
  -d "client_id=tasks-webapi" \
  -d "username=tenant1-member1@example.com" \
  -d "password=password" \
  -d "grant_type=password" \
  "http://localhost:18080/realms/tasks/protocol/openid-connect/token" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# JWT payload をデコード(Base64)
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool | grep -A5 realm_access
```

**期待値**:
```json
{
  "realm_access": {
    "roles": ["MEMBER", "default-roles-tasks", "offline_access", "uma_authorization"]
  }
}
```

### Step 8: /actuator/health で API 疎通確認

`/api/auth/me` は Sprint 0 未実装のため、`/actuator/health` で代替確認する。

```bash
TOKEN=$(curl -s \
  -d "client_id=tasks-webapi" \
  -d "username=tenant1-member1@example.com" \
  -d "password=password" \
  -d "grant_type=password" \
  "http://localhost:18080/realms/tasks/protocol/openid-connect/token" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s http://localhost:8080/actuator/health
```

**期待値**: `{"status":"UP"}` (actuator/health は permitAll のため token 不要)

認証エンドポイントでの JWT 検証確認:

```bash
# 認証保護されたエンドポイントに token なしでアクセス → 401
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/tasks
# 期待値: 401

# token あり → 404(エンドポイント未実装 or 200)
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:8080/api/tasks
# 期待値: 404(api/tasks 未実装)または 200
```

---

## 3. 実行結果記録欄

> ローカル開発者が実際に E2E を実行した際にこのセクションを更新してください。

| Step | 内容 | 結果 | ログ抜粋 / 備考 |
|------|------|------|----------------|
| 1 | Docker Compose 起動 | - | 未実行 |
| 2a | tasks-webapi 起動 | - | 未実行 |
| 2b | Flyway migration 確認 | - | 未実行 |
| 2c | ヘルスチェック | - | 未実行 |
| 3 | Keycloak Admin Console | - | 未実行 |
| 4 | Frontend 起動 + リダイレクト | - | 未実行 |
| 5 | テストユーザーログイン | - | 未実行 |
| 6 | sessionStorage JWT 保存確認 | - | 未実行 |
| 7 | JWT デコード MEMBER ロール確認 | - | 未実行 |
| 8 | API 疎通確認 | - | 未実行 |

---

## 4. ギャップ・既知の課題

| 項目 | 詳細 | 対処 |
|------|------|------|
| `/api/auth/me` 未実装 | Sprint 0 で実装予定。現時点では `/actuator/health` で代替確認 | Phase 1 Sprint 0 #121 で実装 |
| web/js/auth.js の KEYCLOAK_URL がハードコード | `http://localhost:18080` 固定。本番環境対応が必要 | 本番化時に環境変数化を検討 |
| api.js の BASE_URL がハードコード | `http://localhost:8080` 固定 | 同上 |

---

## 5. docs/dev/local-setup.md との整合確認

| local-setup.md §8 記載内容 | 整合状態 | 備考 |
|--------------------------|---------|------|
| Docker Compose 起動コマンド | ✓ 整合 | |
| `.env.local` + `bootRun` 起動 | ✓ 整合 | |
| `http://localhost:5500/index.html` | ✓ 整合 | Python http.server 5500 / npx serve 5500 |
| Keycloak ログインリダイレクト | ✓ 整合 | auth.js の `onLoad: 'login-required'` と対応 |
| `curl /api/auth/me` | △ 注記追加が必要 | 未実装のため actuator/health で代替。§8 に注記を追記 |
| JWT デコードで MEMBER 確認 | ✓ 整合(curl + base64 デコード手順あり) | |

---

## 6. 受入条件チェックリスト

- [ ] 上記 E2E 10 step が全て成功(§3 記録欄の更新が必要)
- [x] 結果が `docs/reviews/2026-05-24-phase1-setup1-e2e.md` に手順・期待値として記録済み
- [x] `docs/dev/local-setup.md` §8 の手順と本書の手順が整合確認済み
- [x] `/api/auth/me` 未実装を確認し、代替手順(actuator/health)を文書化
- [ ] Phase 1 Setup 1 Milestone の全サブ Issue closed 確認

---

## 関連

- Issue: [#219](https://github.com/win2cot/tasks-webapi/issues/219)
- 親 tracker: [#206](https://github.com/win2cot/tasks-webapi/issues/206)
- Setup guide: [docs/dev/local-setup.md](../dev/local-setup.md)
- Infrastructure plan: [docs/architecture/infrastructure-plan.md](../architecture/infrastructure-plan.md) §5.2
