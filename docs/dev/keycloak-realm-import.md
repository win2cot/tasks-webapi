# Keycloak Realm Initial Import 手順

**対象**: dev 環境 / ローカル開発環境
**バージョン**: v1.0 (2026-06-07)

---

## 目次

1. [概要](#1-概要)
2. [import 手段](#2-import-手段)
3. [冪等性ポリシー](#3-冪等性ポリシー)
4. [local vs dev の差分](#4-local-vs-dev-の差分)
5. [初回起動手順](#5-初回起動手順)
6. [Realm 設定変更時の re-import 手順](#6-realm-設定変更時の-re-import-手順)
7. [動作確認](#7-動作確認)
8. [トラブルシューティング](#8-トラブルシューティング)

---

## 1. 概要

Keycloak Realm (`tasks`) の設定は `keycloak/realm-export/tasks-realm.json` で管理している。
dev 環境および local 環境の Keycloak Custom Image 起動時に、この JSON を自動 import する仕組みを
`--import-realm` フラグで実現する。

### 採用した import 手段の根拠

| 手段 | 採否 | 理由 |
|---|---|---|
| `start --import-realm`(本手順) | **採用** | local と dev で同一メカニズム / ECS Fargate にも適用可 / 冪等 |
| `kc.sh import`(one-shot ECS Task) | 代替(re-import 時のみ) | 初回は不要だが、強制 override が必要な場合に使用 |
| `KC_IMPORT` 環境変数 | 不採用 | Keycloak 25 以降で非推奨 / Quarkus ベースでは動作しない |
| Init container | 不採用 | ECS Fargate でのコンテナ起動順序制御が複雑 |

---

## 2. import 手段

### 仕組み

`--import-realm` フラグにより、Keycloak 起動時に `/opt/keycloak/data/import/` ディレクトリ内の
JSON ファイルを Realm として登録する。

#### Dockerfile の変更点

`keycloak/Dockerfile` Stage 3 (Runtime) にて Realm JSON をイメージに組み込み:

```dockerfile
# Realm JSON をイメージにベイク
COPY keycloak/realm-export/ /opt/keycloak/data/import/

# --import-realm で起動時に自動 import
CMD ["start", "--optimized", "--import-realm"]
```

#### ECS タスク定義の変更点

`infra/shared/modules/keycloak/main.tf` のコンテナ定義に `command` を明示:

```hcl
command = ["start", "--optimized", "--import-realm"]
```

---

## 3. 冪等性ポリシー

Keycloak 26 の `--import-realm` は **IGNORE_EXISTING** 戦略がデフォルト。

| 起動タイミング | Realm の状態 | 挙動 |
|---|---|---|
| 初回起動 | DB に Realm なし | JSON から Realm を作成 |
| 2 回目以降の通常起動 | DB に Realm あり | **スキップ** (IGNORE_EXISTING) |
| ECS サービス再デプロイ | DB に Realm あり | スキップ (DBの Realm は保持) |
| 強制 re-import | 任意 | 手動手順が必要 (→ [セクション 6](#6-realm-設定変更時の-re-import-手順)) |

> **ポイント**: Realm 設定は Keycloak の DB (RDS) に永続化される。
> ECS タスクが再起動・再デプロイされても DB の Realm は失われない。

---

## 4. local vs dev の差分

| 項目 | local (Docker Compose) | dev (ECS Fargate) |
|---|---|---|
| 起動コマンド | `start-dev --import-realm` | `start --optimized --import-realm` |
| DB | H2 embedded (インメモリ) | RDS MySQL 8.4 |
| Realm JSON の供給元 | volume mount (`keycloak/realm-export/` → `/opt/keycloak/data/import/`) | Docker Image にベイク済み |
| Issuer URI | `http://localhost:18080/realms/tasks` | `https://auth-dev.dgz48.xyz/realms/tasks` |
| redirect URI | `http://localhost:*` および `https://*.dgz48.xyz` (JSON に両方列挙済み) | `https://*.dgz48.xyz` (JSON に含まれる) |
| Admin パスワード | 固定値 `admin` (env var) | SSM SecureString (`/tasks/dev/keycloak/admin-password`) |

### 環境差分の吸収方法

Realm JSON (`tasks-realm.json`) は local/dev 共通のベース定義。
`redirectUris` / `webOrigins` には local (`http://localhost:*`) と dev (`https://*.dgz48.xyz`) の
両方が列挙済みのため、初回 import 後に Admin Console での上書きは不要。
issuer URI など他の環境固有差分は Keycloak が自動解決する(Realm の `hostname` プロファイルを使用)。

> **注意**: local は H2 embedded のため、`docker compose down` するたびに Realm データが消える。
> 再起動時に `--import-realm` が自動で再 import するため問題ない。
> dev は RDS MySQL に永続化されるため、コンテナ再起動で Realm は保持される。

---

## 5. 初回起動手順

### local 環境

```bash
# Docker Compose で Keycloak を起動 (realm-export をボリュームマウント)
docker compose -f docker-compose.local.yml up -d

# Realm import を確認
docker compose -f docker-compose.local.yml logs keycloak | grep -i "import\|realm"
# 期待: "Realm tasks imported" または "Imported realm" のログ
```

### dev 環境 (ECS)

**Step 1**: ECR に最新の Keycloak Custom Image を push する (#324 参照)

**Step 2**: `terraform apply` で ECS Service を起動 or 新しいタスク定義をデプロイ:

```bash
# ECS Service の最新タスク定義でデプロイ
aws ecs update-service \
  --cluster platform-dev-cluster \
  --service platform-dev-keycloak \
  --force-new-deployment \
  --region ap-northeast-1
```

**Step 3**: ECS タスクが RUNNING になるまで待機:

```bash
aws ecs wait services-stable \
  --cluster platform-dev-cluster \
  --services platform-dev-keycloak \
  --region ap-northeast-1
```

**Step 4**: CloudWatch Logs で import を確認:

```bash
aws logs filter-log-events \
  --log-group-name /ecs/platform-dev/keycloak \
  --filter-pattern "import OR realm OR Realm" \
  --region ap-northeast-1 \
  --query 'events[].message' \
  --output text
```

**Step 5**: `tasks-realm.json` の `redirectUris` / `webOrigins` には `https://*.dgz48.xyz` が
含まれるため、import 後の Admin Console での手動追加は不要。
確認する場合は Admin Console → Clients → `tasks-webapi` → Settings タブ → Valid redirect URIs に
`https://*.dgz48.xyz/*` が存在することを確認する。

---

## 6. Realm 設定変更時の re-import 手順

`--import-realm` はデフォルトで IGNORE_EXISTING のため、**Realm が既存の場合は JSON の変更が
自動反映されない**。変更を反映するには以下のいずれかを実行する。

### 方法 A: Realm 削除 → 自動再 import (推奨)

Realm を削除するとDBから完全に消える。次回 ECS タスク起動時に `--import-realm` が
再 import する。**テストユーザーや Realm 設定が初期化される**ことに注意。

```bash
# 1. Keycloak Admin Console (https://auth-dev.dgz48.xyz) にログイン
# 2. 左上の Realm ドロップダウン → tasks realm を選択
# 3. Realm settings → 最下部 "Delete realm" をクリック → 確認ダイアログで削除

# 4. ECS Service を force new deployment で再起動
aws ecs update-service \
  --cluster platform-dev-cluster \
  --service platform-dev-keycloak \
  --force-new-deployment \
  --region ap-northeast-1
```

### 方法 B: kc.sh import --override true (one-shot ECS Task)

Realm を削除せずに JSON で上書き import する。

```bash
# ECS Run Task で one-shot import を実行
# ネットワーク設定は既存サービスと同じ値を指定する (terraform output で取得可)
CLUSTER=platform-dev-cluster
TASK_DEF=platform-dev-keycloak
SUBNET_ID=<private subnet ID>   # /platform/dev/private-subnet-ids
SG_ID=<keycloak SG ID>          # module.keycloak.sg_id

aws ecs run-task \
  --cluster "$CLUSTER" \
  --task-definition "$TASK_DEF" \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_ID],securityGroups=[$SG_ID],assignPublicIp=DISABLED}" \
  --overrides '{
    "containerOverrides": [{
      "name": "keycloak",
      "command": [
        "/opt/keycloak/bin/kc.sh",
        "import",
        "--dir", "/opt/keycloak/data/import",
        "--override", "true"
      ]
    }]
  }' \
  --region ap-northeast-1
```

> **補足**: `kc.sh import` は import 完了後にプロセスが終了する one-shot コマンド。
> 実行中は ECS Service の通常タスクはそのまま稼働し続ける。

### 方法 C: Admin Console での手動変更

スキーマ変更でなく、特定の設定値のみ変更したい場合は Admin Console から直接編集して保存できる。
この場合 Realm JSON との差分が生じるため、変更後に Admin Console から Realm export を再実施して
`keycloak/realm-export/tasks-realm.json` を更新すること。

---

## 7. 動作確認

### Realm import 確認 (local)

```bash
# tasks realm が存在することを確認
curl -s http://localhost:18080/realms/tasks/.well-known/openid-configuration | python3 -m json.tool | grep issuer
# 期待: "issuer": "http://localhost:18080/realms/tasks"
```

### Realm import 確認 (dev)

```bash
# Keycloak Admin API でテナント一覧を取得 (admin token 取得が必要)
ADMIN_TOKEN=$(curl -s \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=<ADMIN_PASSWORD>" \
  -d "grant_type=password" \
  "https://auth-dev.dgz48.xyz/realms/master/protocol/openid-connect/token" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "https://auth-dev.dgz48.xyz/admin/realms" \
  | python3 -m json.tool | grep '"realm"'
# 期待: "realm": "tasks"
```

### 認証フロー確認 (dev)

```bash
# tasks-webapi client でトークン取得 (Direct Access Grants 有効時)
curl -s \
  -d "client_id=tasks-webapi" \
  -d "username=tenant1-member1@example.com" \
  -d "password=password" \
  -d "grant_type=password" \
  "https://auth-dev.dgz48.xyz/realms/tasks/protocol/openid-connect/token" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK' if 'access_token' in d else d)"
# 期待: OK
```

---

## 8. トラブルシューティング

### Q1. ECS タスクが RUNNING にならない (Realm import で起動失敗)

**確認方法**:

```bash
aws logs filter-log-events \
  --log-group-name /ecs/platform-dev/keycloak \
  --filter-pattern "ERROR" \
  --region ap-northeast-1 \
  --query 'events[].message' \
  --output text
```

**よくある原因**:

| 原因 | 対処 |
|---|---|
| DB 接続エラー (`KC_DB_URL` / `KC_DB_PASSWORD` 不正) | SSM パラメータ値を確認 |
| Realm JSON に構文エラー | `keycloak/realm-export/tasks-realm.json` を validate |
| `KC_HOSTNAME` が不正 | ECS タスク定義の `KC_HOSTNAME` 環境変数を確認 |

### Q2. 2 回目以降の起動で Realm 設定が更新されない

IGNORE_EXISTING の仕様通り。Realm 設定を更新するには
[セクション 6](#6-realm-設定変更時の-re-import-手順) の re-import 手順を実行する。

### Q3. local の Realm が存在しない

`start-dev` は H2 インメモリ DB のため、`docker compose down` で Realm データが消える。
コンテナ再起動で自動 import される。

```bash
# 強制再起動で再 import
docker compose -f docker-compose.local.yml down
docker compose -f docker-compose.local.yml up -d keycloak

# ログで確認
docker compose -f docker-compose.local.yml logs keycloak | grep -i "import\|realm"
```

---

## 関連ドキュメント

- [local-setup.md](local-setup.md) — ローカル開発セットアップ全体手順
- [keycloak/Dockerfile](../../keycloak/Dockerfile) — Dockerfile (realm JSON ベイク箇所)
- [keycloak/realm-export/tasks-realm.json](../../keycloak/realm-export/tasks-realm.json) — Realm 定義 JSON
- [infra/shared/modules/keycloak/main.tf](../../infra/shared/modules/keycloak/main.tf) — ECS タスク定義
- [docs/architecture/parameter-store.md](../architecture/parameter-store.md) — SSM パラメータ一覧
