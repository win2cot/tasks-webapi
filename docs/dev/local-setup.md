# ローカル開発セットアップガイド

> **目標**: このガイドに従うことで、新規開発者が **30 分以内に** ローカル環境を立ち上げられる。
>
> **対象**: Setup 1-1〜1-5 の成果物([docker-compose.local.yml](../../docker-compose.local.yml) / [keycloak/realm-export/](../../keycloak/realm-export/) / Flyway / [application.yml](../../webapi/src/main/resources/application.yml) / [web/](../../web/)) を使った統合環境。
>
> **バージョン**: v1.0 (2026-05-24)

---

## 目次

1. [前提条件](#1-前提条件)
2. [リポジトリ取得](#2-リポジトリ取得)
3. [Docker Compose 起動](#3-docker-compose-起動)
4. [環境変数設定](#4-環境変数設定)
5. [`./gradlew :webapi:bootRun` 起動](#5-gradlew-webapibootrun-起動)
6. [Keycloak Realm 動作確認](#6-keycloak-realm-動作確認)
7. [Frontend skeleton 起動](#7-frontend-skeleton-起動)
8. [動作確認 E2E](#8-動作確認-e2e)
9. [トラブルシューティング](#9-トラブルシューティング)

---

## 1. 前提条件

以下のツールがインストール済みであること。

| ツール | バージョン | インストール方法 |
|--------|-----------|-----------------|
| WSL 2 (Ubuntu 24.04) | 24.04 LTS | `wsl --install -d Ubuntu-24.04` |
| Docker Desktop | 4.x 以上 | [公式サイト](https://www.docker.com/products/docker-desktop/) からインストール後、Settings → Resources → WSL integration を有効化 |
| JDK 21 | 21 (LTS) | SDKMAN! 推奨: `sdk install java 21-tem` |
| gh CLI | 2.x 以上 | `sudo apt install gh` または [公式サイト](https://cli.github.com/) |
| Git | 2.x 以上 | `sudo apt install git` |

### JDK 21 のインストール (SDKMAN! 推奨)

```bash
# SDKMAN! のインストール
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# JDK 21 のインストール
sdk install java 21-tem
sdk use java 21-tem

# 確認
java -version
# openjdk version "21.x.x" ...
```

### Docker Desktop の WSL Integration 確認

```bash
# WSL 内で Docker が使えることを確認
docker version
docker compose version
```

---

## 2. リポジトリ取得

```bash
git clone git@github.com:win2cot/tasks-webapi.git
cd tasks-webapi
```

SSH キーが未設定の場合は [GitHub SSH 鍵の設定ガイド](https://docs.github.com/ja/authentication/connecting-to-github-with-ssh) を参照。

---

## 3. Docker Compose 起動

```bash
# リポジトリルートで実行
docker compose -f docker-compose.local.yml up -d
```

起動後、コンテナの状態を確認する:

```bash
docker compose -f docker-compose.local.yml ps
```

期待する出力:

```text
NAME                STATUS          PORTS
tasks-webapi-mysql-1      Up (healthy)    0.0.0.0:3306->3306/tcp
tasks-webapi-keycloak-1   Up              0.0.0.0:18080->8080/tcp
```

### 各サービスの説明

| サービス | ポート | 用途 |
|----------|--------|------|
| MySQL 8.4 | `localhost:3306` | アプリケーション DB |
| Keycloak 24.0 | `localhost:18080` | 認証・認可サーバー |

> **注意**: MySQL は起動後 healthcheck が `healthy` になるまで最大 80 秒かかる場合がある。  
> Keycloak は初回起動時に `keycloak/realm-export/tasks-realm.json` を自動インポートする。

### Docker Compose 停止

```bash
docker compose -f docker-compose.local.yml down

# データを初期化してやり直す場合 (ボリュームも削除)
docker compose -f docker-compose.local.yml down -v
```

---

## 4. 環境変数設定

アプリケーション起動に必要な環境変数をまとめた `.env.local` ファイルを作成する。
リポジトリルートにある [.env.local.example](../../.env.local.example) をテンプレートとして使うと便利。

```bash
# リポジトリルートで実行
cp .env.local.example .env.local
# 必要に応じて .env.local を編集し、DATASOURCE_PASSWORD 等の実際の値を設定する
```

または手動で作成する場合:

```bash
# リポジトリルートで実行
cat > .env.local << 'EOF'
export DATASOURCE_URL=jdbc:mysql://localhost:3306/tasks?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=SERVER&forceConnectionTimeZoneToSession=true
export DATASOURCE_USERNAME=tasks_webapi
export DATASOURCE_PASSWORD=tasks_webapi
export OIDC_ISSUER_URI=http://localhost:18080/realms/tasks
EOF
```

### 環境変数一覧

| 変数名 | ローカル開発値 | 説明 |
|--------|----------------|------|
| `DATASOURCE_URL` | `jdbc:mysql://localhost:3306/tasks?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=SERVER&forceConnectionTimeZoneToSession=true` | MySQL JDBC URL |
| `DATASOURCE_USERNAME` | `tasks_webapi` | DB ユーザー名 |
| `DATASOURCE_PASSWORD` | `tasks_webapi` | DB パスワード |
| `OIDC_ISSUER_URI` | `http://localhost:18080/realms/tasks` | Keycloak realm の issuer URI |

> **セキュリティ注意**: `.env.local` は `.gitignore` に追加されている。コミットしないこと。  
> 本番環境の値は ECS タスク定義の `environment` / Parameter Store SecureString 経由で注入される(詳細は[設計規約 §5.4](../specs/設計規約.md#54-通信データ保護) / [infrastructure-plan §3.4](../architecture/infrastructure-plan.md) 参照)。

---

## 5. `./gradlew :webapi:bootRun` 起動

```bash
# .env.local を読み込んでアプリを起動
source .env.local && ./gradlew :webapi:bootRun
```

### 起動確認

起動ログに以下が出力されれば成功:

```text
Flyway Community Edition ... by Redgate ...
Successfully applied N migration(s) to schema `tasks`
Tomcat started on port 8080
Started TasksWebapiApplication in X.XXX seconds
```

### ヘルスチェック

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Flyway Migration の確認

初回起動時、`webapi/src/main/resources/db/migration/` 配下の SQL が自動実行される。

```bash
# MySQL に接続して migration が適用されたことを確認
docker compose -f docker-compose.local.yml exec mysql \
  mysql -u tasks_webapi -ptasks_webapi tasks -e "SHOW TABLES;"
```

---

## 6. Keycloak Realm 動作確認

### Admin Console へのアクセス

ブラウザで `http://localhost:18080` を開く。

| 項目 | 値 |
|------|-----|
| URL | `http://localhost:18080` |
| ユーザー名 | `admin` |
| パスワード | `admin` |

ログイン後、左上のドロップダウンで `tasks` realm を選択する。

### Realm の確認ポイント

- **Realm 名**: `tasks`
- **Roles** (Realm roles): `APP_ADMIN` / `TENANT_ADMIN` / `MEMBER`
- **Client**: `tasks-webapi`

### テストユーザー一覧

| ユーザー名 | パスワード | ロール | 用途 |
|------------|-----------|--------|------|
| `admin@example.com` | `admin` | `APP_ADMIN` | SaaS 管理者 |
| `tenant1-admin@example.com` | `password` | `TENANT_ADMIN` | テナント 1 管理者 |
| `tenant1-member1@example.com` | `password` | `MEMBER` | テナント 1 メンバー 1 |
| `tenant1-member2@example.com` | `password` | `MEMBER` | テナント 1 メンバー 2 |

### JWT トークンの取得 (curl)

```bash
# tenant1-member1@example.com でトークンを取得する例
TOKEN=$(curl -s \
  -d "client_id=tasks-webapi" \
  -d "username=tenant1-member1@example.com" \
  -d "password=password" \
  -d "grant_type=password" \
  "http://localhost:18080/realms/tasks/protocol/openid-connect/token" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

echo "TOKEN: ${TOKEN:0:50}..."
```

---

## 7. Frontend skeleton 起動

`web/` ディレクトリを軽量 HTTP サーバーで配信する。

### VS Code Live Server (推奨)

VS Code の拡張機能 [Live Server](https://marketplace.visualstudio.com/items?itemName=ritwickdey.LiveServer) を使う場合:

1. VS Code で `web/index.html` を開く
2. 右下の `Go Live` をクリック
3. ブラウザで `http://localhost:5500/index.html` が開く

### Python HTTP サーバー

```bash
cd web
python3 -m http.server 5500
```

ブラウザで `http://localhost:5500/index.html` を開く。

### npx serve

```bash
cd web
npx serve -l 5500 .
```

---

## 8. 動作確認 E2E

> **詳細手順**: Setup 1-7 E2E 動作確認手順と共有。本セクションはクイックリファレンス。

### 完全フローの確認

1. ブラウザで `http://localhost:5500/index.html` を開く
2. Keycloak ログイン画面にリダイレクトされることを確認
3. テストユーザー(例: `tenant1-member1@example.com` / `password`)でログイン
4. `Tasks` アプリのトップ画面が表示されることを確認
5. ナビバーにユーザー名が表示されることを確認

### API 直接確認 (curl)

```bash
# 1. JWT トークンを取得
TOKEN=$(curl -s \
  -d "client_id=tasks-webapi" \
  -d "username=tenant1-member1@example.com" \
  -d "password=password" \
  -d "grant_type=password" \
  "http://localhost:18080/realms/tasks/protocol/openid-connect/token" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

# 2. ヘルスチェックで API サーバー疎通を確認
curl -s http://localhost:8080/actuator/health
# 期待レスポンス: {"status":"UP"}

# 3. JWT 認証の動作確認 (token なし → 401)
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/tasks
# 期待: 401

# 4. JWT デコードで realm_access.roles に MEMBER が含まれることを確認
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool | grep -A5 realm_access
# 期待: "roles": ["MEMBER", ...]
```

> **注意**: `/api/auth/me` は Phase 1 Sprint 0 で実装予定のため現時点では未提供。  
> 代替として `/actuator/health` で API サーバーの起動確認、JWT デコードでロール確認を行う。

### 確認チェックリスト

- [ ] `docker compose ps` → MySQL: `Up (healthy)` / Keycloak: `Up`
- [ ] `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] Flyway migration ログが起動時に出力される
- [ ] ブラウザで `http://localhost:5500/index.html` → Keycloak ログイン画面にリダイレクト
- [ ] テストユーザーでログイン → Tasks 画面表示(ナビバーにユーザー名表示)
- [ ] `curl /api/tasks`(token なし) → 401 Unauthorized
- [ ] JWT デコードで `realm_access.roles` に `MEMBER` が含まれることを確認

---

## 9. トラブルシューティング

### Q1. Docker WSL Integration が動かない

**症状**: WSL 内で `docker: command not found` または `Cannot connect to the Docker daemon`

**対処**:

1. Docker Desktop を起動していることを確認
2. Docker Desktop → Settings → Resources → WSL Integration で Ubuntu-24.04 が有効になっていることを確認
3. WSL を再起動: PowerShell で `wsl --shutdown` → Ubuntu を再起動
4. それでも解決しない場合、Docker Desktop を再インストール

```bash
# 確認コマンド
docker info
```

---

### Q2. MySQL ポート競合 (`port is already allocated`)

**症状**: `docker compose up` 時に `Error: port 3306 already in use` または `bind: address already in use`

**対処**:

```bash
# ポートを使用しているプロセスを確認
sudo lsof -i :3306

# ローカルの MySQL が起動している場合は停止
sudo systemctl stop mysql

# または docker-compose.local.yml の ports を変更
# ports:
#   - "3307:3306"  # 3307 に変更
# その場合 .env.local の DATASOURCE_URL も更新すること
# DATASOURCE_URL=jdbc:mysql://localhost:3307/tasks?...
```

---

### Q3. Keycloak Realm import 失敗

**症状**: Keycloak が起動しているが `tasks` realm が存在しない

**確認方法**:

```bash
# Keycloak コンテナのログを確認
docker compose -f docker-compose.local.yml logs keycloak | grep -i "import\|error\|realm"
```

**対処**:

```bash
# コンテナを再作成 (--force-recreate で確実にインポートを再実行)
docker compose -f docker-compose.local.yml down
docker compose -f docker-compose.local.yml up -d --force-recreate keycloak

# realm-export ファイルが存在することを確認
ls keycloak/realm-export/tasks-realm.json
```

---

### Q4. Flyway 接続エラー (`Unable to obtain connection`)

**症状**: `./gradlew :webapi:bootRun` 時に Flyway が `Connection refused` または `Access denied` でエラー

**原因と対処**:

| 原因 | 確認方法 | 対処 |
|------|----------|------|
| MySQL が起動していない | `docker compose ps` | `docker compose -f docker-compose.local.yml up -d` |
| MySQL がまだ healthy でない | `docker compose ps` の STATUS | healthy になるまで最大 80 秒待つ |
| `.env.local` が読み込まれていない | `echo $DATASOURCE_URL`(空なら未読込) | `source .env.local && ./gradlew :webapi:bootRun` で再起動(`.env.local` に `export` があることを確認) |
| DATASOURCE_URL の値が間違い | `.env.local` を確認 | [セクション 4](#4-環境変数設定) の値を再確認 |

```bash
# MySQL への接続テスト
docker compose -f docker-compose.local.yml exec mysql \
  mysqladmin ping -h 127.0.0.1 -u tasks_webapi -ptasks_webapi
# mysqld is alive
```

---

### Q5. Keycloak トークン取得失敗 (`401 Unauthorized`)

**症状**: `curl` でトークン取得時に `{"error":"unauthorized_client"}` または `{"error":"invalid_grant"}`

**対処**:

```bash
# Keycloak が起動しているか確認
curl -s http://localhost:18080/realms/tasks/.well-known/openid-configuration | grep issuer

# Client が public grant type を許可しているか確認 (Keycloak admin console)
# Clients → tasks-webapi → Settings → Direct access grants → Enabled
```

---

## 関連ドキュメント

- [CLAUDE.md](../../.claude/CLAUDE.md) — コマンドリファレンス / コード品質ツール
- [docker-compose.local.yml](../../docker-compose.local.yml) — Docker Compose 定義
- [.env.local.example](../../.env.local.example) — 環境変数テンプレート(`.env.local` にコピーして使用)
- [application.yml](../../webapi/src/main/resources/application.yml) — Spring Boot 設定 / env vars コメント
- [keycloak/realm-export/tasks-realm.json](../../keycloak/realm-export/tasks-realm.json) — Keycloak Realm 定義
- [docs/specs/開発計画書.md](../specs/開発計画書.md) — 開発計画 §4.3.1 / §11.1
- [docs/architecture/infrastructure-plan.md](../architecture/infrastructure-plan.md) — Infrastructure Plan v5 §5.2 (Setup 1-6)
