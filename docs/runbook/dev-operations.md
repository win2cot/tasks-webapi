# Runbook: dev 運用手順(初版)

最終更新: 2026-06-25 | Issue #752 / ADR-0031 / infrastructure-plan v5 §6.5(S4Infra-1)

## 概要

本 runbook は dev 環境(AWS ECS Fargate)の日常運用・障害対応初動・夜間停止スケジュールの
オペレーションをまとめる。対象は tasks スタック(webapi + RDS + フロントエンド)。platform スタック
(Keycloak)は独立管理のため、必要箇所のみ参照する(#625)。

前提:

- AWS CLI が設定済み(`aws sts get-caller-identity` で疎通確認、リージョンは `ap-northeast-1`)
- `gh` CLI が認証済み(`gh auth status`)
- 関連 runbook: [release-checklist.md](release-checklist.md)(リリース前チェックリスト)/
  [fullstack-startup.md](fullstack-startup.md)(ローカル起動)/
  [soak-test.md](soak-test.md)(ソークテスト・ヒープダンプ)/
  [authentication.md](authentication.md)(OIDC)/ [browser-verification.md](browser-verification.md)(UI 検証)

## クイックリファレンス(dev リソース名)

| 種別 | 識別子 |
|---|---|
| ECS クラスタ | `tasks-dev-cluster` |
| ECS サービス(webapi) | `tasks-dev-webapi` |
| ECS タスク定義 family | `tasks-dev-webapi` |
| RDS インスタンス | `tasks-dev-mysql`(MySQL 8.4、DB 名 `tasks`) |
| webapi ログ | `/tasks/dev/webapi` |
| 監査ログ | `/tasks/dev/audit` |
| ADOT(可観測性)ログ | `/ecs/tasks-dev/adot` |
| スケジューラ Lambda | `tasks-dev-scheduler` |
| API エンドポイント | `https://api-dev.tasks.dgz48.xyz` |
| フロントエンド | `https://tasks-dev.dgz48.xyz` |
| ECR レジストリ | `138285070797.dkr.ecr.ap-northeast-1.amazonaws.com` |

> ECR リポジトリは `tasks-webapi` / `keycloak-custom`。イメージタグは単一プロダクトバージョン
> `vX.Y.Z`(ADR-0031)。

## デプロイ(ADR-0031 dispatch)

リリースとデプロイは分離している([ADR-0031](../adr/0031-single-product-version-dispatch-deploy.md))。

1. **リリース(成果物作成)**: `vX.Y.Z` タグを push すると
   [release-build.yml](../../.github/workflows/release-build.yml) が変更コンポーネントのみビルドし、
   ECR(`tasks-webapi` / `keycloak-custom`)・S3(`tasks-dev-frontend/web/vX.Y.Z/`)へ成果物を発行する。

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

2. **デプロイ(環境へ反映)**: [deploy.yml](../../.github/workflows/deploy.yml) を
   `workflow_dispatch` で実行する。`version` と `environment` を指定する。

   ```bash
   gh workflow run deploy.yml -f version=v0.1.0 -f environment=dev
   ```

   GitHub UI からも実行可: Actions → Deploy → Run workflow → version / environment を入力。

デプロイ workflow の流れ:

- `verify`: ECR イメージ・S3 バンドルの存在を確認(fail-closed)。変更のないコンポーネントは skip。
- `plan-platform` / `plan-tasks` → `apply-platform` / `apply-tasks`: Terraform を当該バージョンの
  ref で plan/apply(infra も同バージョンに揃う)。
- `deploy-webapi`: ECS タスク定義のイメージのみ差し替えて新リビジョン登録 →
  `update-service` → `aws ecs wait services-stable` で安定化待ち。**目標 digest と現行 digest が
  一致する場合は no-op**(無駄な rolling update をしない)。
- `deploy-web`: S3 `web/live/` へ sync + CloudFront invalidation。

> dev は承認ゲートなし。stg/prd は GitHub Environments の required reviewers による承認が必要。

### デプロイ確認

```bash
# ヘルスチェック(permitAll、認証不要)
curl -s https://api-dev.tasks.dgz48.xyz/actuator/health
# {"status":"UP"}

# ECS が実際に動かしているイメージ
aws ecs describe-task-definition --task-definition tasks-dev-webapi \
  --query 'taskDefinition.containerDefinitions[0].image' --output text
```

## ロールバック

専用のロールバック自動化はない。**直前の正常バージョンを再デプロイ**する。

```bash
# 例: v0.1.0 で問題発生 → v0.0.9 に戻す
gh workflow run deploy.yml -f version=v0.0.9 -f environment=dev
```

注意点:

- deploy はバージョン一貫(infra + イメージを同じタグの ref で揃える)。古いバージョンに戻すと
  **Terraform 状態もそのバージョンのものに戻る**。アプリだけでなく infra 差分にも注意する。
- ECS タスク定義は ECR digest を解決して比較するため、古いタグの digest へ rolling update される。
- 直前まで動いていたバージョンは GitHub の Deployments(Environments)履歴で確認できる。

## ECS / RDS の再起動

### webapi(ECS)を再起動

タスクを入れ替える(同一イメージのまま新タスクを起動 → 旧タスクを停止)。

```bash
aws ecs update-service \
  --cluster tasks-dev-cluster \
  --service tasks-dev-webapi \
  --force-new-deployment

# 安定化待ち
aws ecs wait services-stable --cluster tasks-dev-cluster --services tasks-dev-webapi
```

### RDS(MySQL)を再起動

```bash
aws rds reboot-db-instance --db-instance-identifier tasks-dev-mysql
```

> RDS 再起動中は webapi が DB 接続エラーになる。先に ECS を `--desired-count 0`(下記)で止めるか、
> 短時間のダウンを許容する。

## 夜間停止スケジュール(#620)

コスト節約のため、夜間は ECS を停止(`desiredCount=0`)し RDS を stop する。実装は
EventBridge Scheduler → Lambda(`tasks-dev-scheduler`、fire-and-forget)。タイムゾーンは Asia/Tokyo。
定義は [infra/environments/dev/scheduler.tf](../../infra/environments/dev/scheduler.tf)。

| スケジュール名 | cron(JST) | 動作 |
|---|---|---|
| `tasks-dev-rds-start-weekday` | `45 18 ? * MON-FRI *` | 平日 18:45 RDS start(ECS の 15 分前) |
| `tasks-dev-rds-start-weekend` | `45 9 ? * SAT-SUN *` | 土日 09:45 RDS start |
| `tasks-dev-ecs-start-weekday` | `0 19 ? * MON-FRI *` | 平日 19:00 ECS start(desiredCount=1) |
| `tasks-dev-ecs-start-weekend` | `0 10 ? * SAT-SUN *` | 土日 10:00 ECS start |
| `tasks-dev-stop-daily` | `0 2 * * ? *` | 毎日 02:00 ECS desiredCount=0 + RDS stop |

> RDS を ECS の 15 分前に起動するのは、RDS の暖機が済んでから webapi を立ち上げるため。

### 時間外に dev を手動で起動する

スケジュール時刻前に使いたい場合、RDS → ECS の順に手動起動する(RDS が `available` になってから ECS)。

```bash
aws rds start-db-instance --db-instance-identifier tasks-dev-mysql
aws rds wait db-instance-available --db-instance-identifier tasks-dev-mysql

aws ecs update-service \
  --cluster tasks-dev-cluster --service tasks-dev-webapi --desired-count 1
```

### 一時的に停止を見送る(夜間も起動を維持)

`tasks-dev-stop-daily` を一時的に無効化する。EventBridge Scheduler の `update-schedule` は
**全項目の置換**(部分更新不可)で扱いづらいため、AWS Console での無効化を推奨する:
Console → EventBridge → Scheduler → Schedules → `tasks-dev-stop-daily` → Disable。

> スケジュールは Terraform 管理のため、手動無効化は次回 `terraform apply`(= 次回 deploy)で
> ENABLED に戻る(= 通常運用へ自動復帰)。恒久的に変えたい場合は scheduler.tf を修正する。

## 障害対応の初動

### 1. ヘルス・稼働状況の確認

```bash
# アプリのヘルス
curl -s https://api-dev.tasks.dgz48.xyz/actuator/health

# ECS サービスの稼働数・デプロイ状態
aws ecs describe-services --cluster tasks-dev-cluster --services tasks-dev-webapi \
  --query 'services[0].{desired:desiredCount,running:runningCount,deployments:deployments[].rolloutState}'

# RDS の状態(stopped なら夜間停止中)
aws rds describe-db-instances --db-instance-identifier tasks-dev-mysql \
  --query 'DBInstances[0].DBInstanceStatus' --output text
```

> `{"status":"DOWN"}` や接続不可のとき、まず RDS が `available` か(夜間停止中でないか)を確認する。

### 2. ログ確認

```bash
# webapi アプリログ(構造化 JSON、logback)をリアルタイム追尾
aws logs tail /tasks/dev/webapi --follow

# 直近 30 分を抽出
aws logs tail /tasks/dev/webapi --since 30m

# エラーのみ(構造化ログの level でフィルタ)
aws logs tail /tasks/dev/webapi --since 1h --filter-pattern '{ $.level = "ERROR" }'
```

主要ロググループ:

- `/tasks/dev/webapi` — webapi アプリログ(Spring Boot / logback、JSON)
- `/tasks/dev/audit` — 監査ログ(ADR-0038 のハッシュチェーン不整合 ERROR もここに出る)
- `/ecs/tasks-dev/adot` — ADOT Collector(可観測性サイドカー、ADR-0029)

AWS Console: CloudWatch → Logs → Log groups → 対象グループ → Logs Insights で JSON フィールド検索。

### 3. 診断ビルド(ヒープダンプ / JFR)

診断バリアントの**別ビルドは不要**。ADR-0029 §6.3(#587)により
`--enable-monitoring=heapdump,jfr` は全環境共通の単一イメージに常時付与されている
([webapi/build.gradle.kts](../../webapi/build.gradle.kts))。取得手順は
[soak-test.md](soak-test.md)(メモリリーク検知・ヒープダンプ取得)を参照。

詳細は [ADR-0029](../adr/0029-performance-measurement-and-diagnostics.md)(性能測定・診断)。

### 4. アプリ再起動 / ロールバック判断

- 一時的な不調(メモリ肥大・スタック)→ [ECS 再起動](#ecs--rds-の再起動)で新タスクに入れ替え。
- 直近デプロイ起因が濃厚 → [ロールバック](#ロールバック)で直前バージョンを再デプロイ。
- DB 起因(接続枯渇等)→ RDS のメトリクス(CloudWatch)を確認し、必要なら
  [RDS 再起動](#rdsmysqlを再起動)。

## 関連ドキュメント

- [ADR-0031: 単一プロダクトバージョンの dispatch デプロイ](../adr/0031-single-product-version-dispatch-deploy.md)
- [ADR-0029: 性能測定・診断](../adr/0029-performance-measurement-and-diagnostics.md)
- [infrastructure-plan.md](../architecture/infrastructure-plan.md) §6.5(S4Infra-1)
- [deploy.yml](../../.github/workflows/deploy.yml) / [release-build.yml](../../.github/workflows/release-build.yml)
- [scheduler.tf](../../infra/environments/dev/scheduler.tf)(夜間停止)
