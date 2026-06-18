# Runbook: フルスタック起動手順(local / CI)

最終更新: 2026-06-18 | Issue #538 / ADR-0023 / ADR-0032

## 概要

ローカル検証(#662)および CI E2E(ADR-0023)で「ログイン → /api/auth/me → タスク CRUD」が
通るフルスタックを起動する手順。

構成:

| 層 | local | CI |
|---|---|---|
| stateful 依存 | `docker compose up`(mysql + keycloak) | 同左 |
| webapi | ホスト上で `bootRun` | ホスト上で `bootJar` → `java -jar` |
| web 静的配信 | `npm run serve`(port 5500) | 同左 |

web は `web/serve.mjs` が起動し、本番 CloudFront RHP(#529)相当のセキュリティヘッダを全
レスポンスに付与する。これにより #541 の CSP 違反0アサーションが本番同等の環境で動作する。

---

## local 起動手順

### 1. 依存サービスを起動

```bash
# リポジトリルートで実行
docker compose -f docker-compose.local.yml up -d --wait
```

`--wait` を付けると mysql と keycloak の healthcheck が通るまでブロックする
(最大 150 秒ほどかかる場合がある)。

### 2. webapi を起動

```bash
# 別ターミナルで実行
source .env.local && ./gradlew :webapi:bootRun
```

起動確認:

```bash
curl -s http://localhost:8080/actuator/health
# {"status":"UP"}
```

### 3. web を起動

```bash
# 別ターミナルで実行
cd web && npm run serve
```

起動ログに `web  ready → http://localhost:5500` が出たら準備完了。

### 4. 動作確認

ブラウザで `http://localhost:5500/` を開き、Keycloak ログイン → タスク画面表示を確認する。
詳細な確認手順は [local-setup.md §8](../dev/local-setup.md#8-動作確認-e2e) を参照。

---

## CI 起動手順(e2e.yml イメージ)

```yaml
- name: Start stateful dependencies
  run: docker compose -f docker-compose.local.yml up -d --wait

- name: Build webapi JAR
  run: ./gradlew :webapi:bootJar

- name: Start webapi
  run: |
    source .env.local
    java -jar webapi/build/libs/tasks-webapi-*.jar &

- name: Wait for webapi readiness
  run: |
    for i in $(seq 1 30); do
      curl -sf http://localhost:8080/actuator/health && break
      sleep 3
    done

- name: Start web
  run: cd web && npm run serve &

- name: Wait for web readiness
  run: |
    for i in $(seq 1 10); do
      curl -sf http://localhost:5500/ && break
      sleep 1
    done

- name: Run Playwright tests
  run: npx playwright test
```

---

## readiness gating まとめ

| サービス | gating 方法 |
|---|---|
| keycloak | `docker compose up --wait`(healthcheck: `/dev/tcp` → `GET /realms/tasks` 200 OK) |
| mysql | 同上(healthcheck: `mysqladmin ping`) |
| webapi | `GET /actuator/health` が `{"status":"UP"}` を返すまでポーリング |
| web | `GET http://localhost:5500/` が 200 を返すまでポーリング |

---

## 環境変数

`web/serve.mjs` は以下の環境変数を参照する(デフォルト値はローカル開発用):

| 変数 | デフォルト | 説明 |
|---|---|---|
| `PORT` | `5500` | web 配信ポート |
| `LOCAL_API_ORIGIN` | `http://localhost:8080` | CSP connect-src に追加する webapi の origin |
| `LOCAL_AUTH_ORIGIN` | `http://localhost:18080` | CSP connect-src に追加する Keycloak の origin |

`webapi` の環境変数は [local-setup.md §4](../dev/local-setup.md#4-環境変数設定) / [.env.local.example](../../.env.local.example) を参照。

---

## 関連ドキュメント

- [local-setup.md](../dev/local-setup.md) — 初回セットアップ手順
- [ADR-0023](../adr/0023-minimal-e2e-harness.md) — CI E2E 基盤
- [ADR-0032](../adr/0032-agent-driven-verification-layer.md) — エージェント駆動検証レイヤー(実行スタック構成)
- [ADR-0022 §3.2](../adr/0022-security-response-headers.md) — 静的配信セキュリティヘッダ
- [web/security-headers.json](../../web/security-headers.json) — ヘッダ値の単一正本(prod Terraform #529 と共有)
- [web/serve.mjs](../../web/serve.mjs) — 静的配信サーバ実装
