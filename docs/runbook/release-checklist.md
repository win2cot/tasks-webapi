# Runbook: リリース前チェックリスト(MVP)

最終更新: 2026-07-03 | Issue #770(S5Infra-1)/ 計画書 Sprint 5 Infra「最終調整」統合

## 概要

MVP リリース前の最終確認を回すための**実行可能チェックリスト**。デプロイのたびに本チェックリストを上から実施する。判断基準は以下 ADR / runbook に集約し、本書はゲートと手順の索引として機能する。

- デプロイ方式: [ADR-0031](../adr/0031-single-product-version-dispatch-deploy.md)(単一プロダクトバージョン `vX.Y.Z` + `workflow_dispatch` 駆動、[ADR-0026](../adr/0026-component-scoped-deploy-tags.md) / [ADR-0030](../adr/0030-auto-github-release-per-deploy-tag.md) を supersede)
- infra 2 段(plan → 承認 → apply): `deploy.yml` の GitHub Environments 承認ゲート([ADR-0031](../adr/0031-single-product-version-dispatch-deploy.md))。build-once / digest promote は [ADR-0028](../adr/0028-build-once-promote-digest.md)
- 日常運用: [dev-operations.md](dev-operations.md)(デプロイ / ロールバック / 夜間停止 / 障害初動)

前提:

- 定量 SLO は設けない([ADR-0039](../adr/0039-performance-tuning-measurement-and-regression.md) / #769 の方針)。測定 → 是正 → 所見記録で運用する。
- 現状は dev 単一環境。stg/prd 差分(承認ゲート・パラメータ化)は #635 で対応予定。
- ライブな AWS 実測(drift / secrets 値 / コスト)は AWS SSO ログイン下で実施する。本書の棚卸しは **IaC(Terraform)を正本**として記載する。

## 1. テストゲート(全 green 前提)

`main` および対象デプロイ ref で以下 CI がすべて green であること。

| ゲート | workflow | 内容 |
|---|---|---|
| Webapi | `Webapi: CI` | `./gradlew :webapi:check`(全 Testcontainers IT + Spotless + JaCoCo **命令カバレッジ 0.80**) |
| Keycloak | `Keycloak: CI` | `./gradlew :keycloak:check`(SPI + package-info) |
| E2E | `E2E: Test` | Playwright E2E(bootJar + web) |
| Native | `Webapi: Native Build` | `nativeCompile -PciNativeBuild=true`(GraalVM 25、[ADR-0008](../adr/0008-graalvm-native-image.md)) |
| IaC | `Terraform: Plan` / `Lint` / `Security Scan` | plan(§3)/ fmt+validate+IAM wildcard gate / Trivy(HIGH・CRITICAL で fail) |
| Docs / API | `Markdown: Lint` / `OpenAPI: Lint` | markdownlint-cli2@0.22.1 / Spectral |
| Web | `Web: CI` | Biome + `tsc --noEmit` + html-validate + v.Nu |

確認コマンド:

```bash
gh run list --branch main -L 15
curl -s https://api-dev.tasks.dgz48.xyz/actuator/health   # {"status":"UP"}
```

### 1.1 セキュリティ / 認可の回帰ゲート

以下は `Webapi: CI` に含まれる。漏洩・認可は明示的な IT で固定されていること(退行検知)。

- クロステナント漏洩: `TaskCrossTenantIT` / `CrossTenantWriteForbiddenIT` / `security/.../CrossTenantLeakageIT` / `TenantFilterIsolationTest` / `TenantFilterFailClosedIT`
- タスク認可(所有者・担当者・関係者の 3 役割、[ADR-0005](../adr/0005-task-authorization-three-roles.md)): `TaskAuthorizationMatrixIT` / `TaskAuthorizationDomainServiceTest`
- テナント越境監査: `audit/CrossTenantViolationDetectionIT` / `AuthorizationDeniedAuditIT`
- 性能 N+1 回帰(#769): `TaskQueryCountIT` / `DashboardQueryCountIT`
- 監査ハッシュチェーン B-05([ADR-0038](../adr/0038-audit-log-hash-chain-tamper-evidence.md)): `AuditChainVerificationIT` / `VerifyAuditChainUseCaseTest` / `AuditChainVerificationBatchSchedulerTest`

### 1.2 デプロイ後 dev-smoke E2E(post-deploy、[ADR-0041](../adr/0041-post-deploy-dev-e2e-and-email-verification.md))

§1 の CI ゲートは **pre-deploy**(JVM / ローカルスタック)であり、native image 固有の退行や dev 実体の構成差は捕捉できない。これを補うのが **デプロイ後**に dev 実体へ通すブラウザ主軸スモーク `Dev Smoke (post-deploy E2E)`(`.github/workflows/dev-smoke.yml`、`e2e/tests-dev/*.dev-smoke.spec.ts`)。実行タイミングは **§2 のデプロイ完了後**(下記 §2 の post-deploy 手順から参照)。

カバレッジ(実 Keycloak / native write / 実 SES 配信を通しで検証):

| spec | 検証内容 |
|---|---|
| `login-dashboard` | 実 Keycloak ログイン → dashboard(SPI 認証経路) |
| `task-journey` | タスク作成・ステータス変更・削除(native write + ETag、失敗時 afterEach で後片付け) |
| `login-theme` | ログイン画面の新規登録導線(`tasks-login` テーマ) |
| `api-invariants` | 未認証 401 / 非メンバーテナント 403(NIST、認可マトリクス §4.1) |
| `signup-email` | セルフサインアップ double opt-in **フルフロー**: signup → 実 SES 配信 → S3 受信 → 確認リンク → complete(ユーザー作成 + Keycloak 資格プロビジョニング)→ 新規ユーザーでログイン到達 |

実行(手動 `workflow_dispatch`):

```bash
gh workflow run dev-smoke.yml                       # 全 dev-smoke(dev 稼働中に限る)
gh workflow run dev-smoke.yml -f grep='signup email' # 一部のみ
```

前提・注意:

- **dev が稼働中であること**。夜間停止中(§5.2、02:00 JST〜)は先に RDS/ECS を起動する([dev-operations.md](dev-operations.md))。
- GitHub Environment **`dev-smoke`**(OIDC role `platform-dev-smoke` = e2e-mail バケット `inbound/` の S3 読取のみ、`infra/shared/modules/iam_oidc`)と Environment secret **`DEV_SMOKE_KC_ADMIN_CLIENT_SECRET`**(signup で作成した Keycloak ユーザーの後片付け用 `tasks-webapi-admin` client secret)が設定済みであること。
- `signup-email` は **実メール送信 + 実 Keycloak ユーザー作成**を伴う(afterEach で作成ユーザーを削除)。SES は sandbox のため宛先ドメイン `e2e.dgz48.xyz` は検証済み identity。

## 2. デプロイ 2 段手順(plan → 承認 → apply)

[ADR-0031](../adr/0031-single-product-version-dispatch-deploy.md) の dispatch 駆動。詳細は [dev-operations.md](dev-operations.md#デプロイadr-0031-dispatch) と整合。

1. **リリース(成果物作成)**: `vX.Y.Z` タグを push → `Release: Build & Publish` が変更コンポーネントのみ ECR(`tasks-webapi` / `keycloak-custom`)・S3 へ発行。

   ```bash
   git tag v0.2.4 && git push origin v0.2.4
   ```

2. **デプロイ(環境反映)**: `Deploy` を `workflow_dispatch` で実行。

   ```bash
   gh workflow run deploy.yml -f version=v0.2.4 -f environment=dev
   ```

デプロイ workflow のジョブ順と承認ゲート(GitHub Environments):

| ジョブ | GitHub Environment | 承認ゲート |
|---|---|---|
| `verify`(ECR/S3 成果物の存在確認、fail-closed) | `dev`/`stg`/`prd` | stg/prd のみ |
| `plan-platform` / `plan-tasks`(`terraform plan -detailed-exitcode`) | `platform-plan` / `tasks-plan` | なし(read-only) |
| `apply-platform` / `apply-tasks` | `platform-apply` / `tasks-apply` | **stg/prd は required reviewers** |
| `deploy-webapi`(TD 差替 → `ecs wait services-stable`) | 環境 | stg/prd のみ |
| `deploy-keycloak` / `deploy-web`(S3 sync + CloudFront invalidation) | 環境 | stg/prd のみ |

- dev は承認ゲートなし(自動適用)。stg/prd は plan 添付を確認して apply を承認する。
- 目標 digest と現行 digest が一致する場合、`deploy-webapi` は no-op(無駄な rolling update をしない)。
- **ロールバック**: 直前の正常バージョンを再デプロイ(`gh workflow run deploy.yml -f version=<prev> -f environment=dev`)。infra も同 ref に戻るため差分に注意([dev-operations.md](dev-operations.md#ロールバック))。

**post-deploy 疎通(dev)**: デプロイ完了後、dev 実体に対して §1.2 の dev-smoke E2E を実行して native / dev 構成差の退行を確認する。

```bash
gh workflow run dev-smoke.yml   # 完了後 Actions で全 spec green を確認(dev 稼働中に限る)
```

## 3. IaC drift ゼロ確認

drift 検知は **PR 時**(`Terraform: Plan` = `plan-platform` / `plan-tasks`)と **デプロイ時**(`deploy.yml` の plan ジョブ)の `terraform plan` で行う。定時 drift ジョブは無い。

- クリーン判定: `terraform plan -detailed-exitcode` の **exit 0 = No changes**(`plan-platform` は "No changes in platform/dev"、`plan-tasks` は "No changes in tasks/dev")。exit 2 = 差分あり、exit 1 = エラー。
- リリース前ゲート: **対象 ref で plan が「変更なし」であること**(手作業の console 変更が Terraform state に取り込まれていない = drift ゼロ)。差分が出た場合は IaC へ反映するか、意図しない手動変更を戻す。

チェック:

- [ ] `Terraform: Plan`(PR)が platform / tasks とも "No changes"
- [ ] デプロイ dispatch 後の `plan-platform` / `plan-tasks` が想定どおり(初回のみ差分、以降 no-op)

## 4. Secrets 棚卸し(Parameter Store)

正本は IaC。SecureString はプレースホルダで作成後 SSM で実値を設定する運用(`lifecycle.ignore_changes = [value]`)。**リリース前に全 SecureString が実値に設定済み**であることを確認する。

### 4.1 tasks スタック(`infra/modules/parameter_store/main.tf`)

| パス | 種別 | 用途 |
|---|---|---|
| `/tasks/<env>/db/password` | SecureString | RDS master パスワード |
| `/tasks/<env>/db/keycloak-spi-read-password` | SecureString | Keycloak SPI read-only MySQL ユーザーのパスワード(#322) |
| `/tasks/<env>/db/keycloak-spi-read-username` | String | 同 read-only ユーザー名 |
| `/tasks/<env>/keycloak/admin-password` | SecureString | Keycloak 初回ブート admin パスワード |
| `/tasks/<env>/keycloak/oauth-client-secret` | SecureString | webapi OAuth2 client secret |
| `/tasks/<env>/keycloak/smtp-password` | SecureString | Keycloak SMTP(SES)パスワード |
| `/tasks/<env>/app/audit-hash-key-v1` | SecureString | 監査ハッシュチェーン HMAC 鍵 v1([ADR-0038](../adr/0038-audit-log-hash-chain-tamper-evidence.md)) |
| `/tasks/<env>/app/keycloak-admin-client-secret` | SecureString | Keycloak Admin REST 用 confidential client secret([ADR-0040](../adr/0040-onboarding-registration-and-credential-provisioning.md)) |
| `/tasks/<env>/app/jwt-issuer` | String | JWT issuer URI |
| `/tasks/<env>/app/tenant-default-id` | String | 既定テナント id |

### 4.2 platform スタック

| パス | 種別 | 用途 |
|---|---|---|
| `/platform/<env>/keycloak-db-password` | SecureString | Keycloak DB パスワード(`infra/shared/modules/keycloak_db/main.tf`) |
| `/platform/<env>/*`(vpc / subnet / alb / ses / keycloak-db-endpoint 等) | String / StringList | インフラ出力の受け渡し(秘匿情報ではない) |

確認コマンド(要 AWS SSO):

```bash
# SecureString が placeholder のままでないか(値は伏せて存在と更新日を確認)
aws ssm get-parameters-by-path --path /tasks/dev --recursive --region ap-northeast-1 \
  --query 'Parameters[].{Name:Name,Type:Type,LastModified:LastModifiedDate}' --output table
```

チェック:

- [ ] 上記 SecureString がすべて実値に設定済み(placeholder / `CHANGE_ME` でない)
- [ ] 不要・失効した secret が残っていない

## 5. コスト棚卸し(dev)

正本は IaC。夜間停止で稼働時間を抑制する([dev-operations.md](dev-operations.md#夜間停止スケジュール620)が SSOT)。

### 5.1 サイジング

| リソース | サイズ | 備考 |
|---|---|---|
| ECS webapi(Fargate) | cpu 1024 / mem 2048、desired 1 | ADOT サイドカーは `essential=false`(`ecs_service.tf`) |
| ECS keycloak(Fargate) | cpu 512 / mem 1024 | health check grace 120s(`shared/modules/keycloak/main.tf`) |
| RDS `tasks-<env>-mysql` | `db.t4g.micro` / gp3 20→100GiB / single-AZ / IAM auth | `infra/modules/rds/main.tf` |
| RDS `platform-<env>-keycloak-db` | `db.t4g.micro` / gp3 20GiB / single-AZ | `infra/shared/modules/keycloak_db/main.tf` |

### 5.2 夜間停止スケジュール(EventBridge Scheduler → Lambda、tz Asia/Tokyo)

| スケジュール | tasks(`tasks-dev-scheduler`) | platform(`platform-dev-scheduler`、tasks の 15 分前) |
|---|---|---|
| 平日 RDS start | `cron(45 18 ? * MON-FRI *)` | `cron(30 18 ? * MON-FRI *)` |
| 土日 RDS start | `cron(45 9 ? * SAT-SUN *)` | `cron(30 9 ? * SAT-SUN *)` |
| 平日 ECS start | `cron(0 19 ? * MON-FRI *)` | `cron(45 18 ? * MON-FRI *)` |
| 土日 ECS start | `cron(0 10 ? * SAT-SUN *)` | `cron(45 9 ? * SAT-SUN *)` |
| 毎日 stop | `cron(0 2 * * ? *)`(ECS desired=0 + RDS stop) | `cron(0 2 * * ? *)` |

チェック:

- [ ] 想定外の常時稼働リソース(停止漏れ ECS/RDS、未使用 NAT/EIP 等)が無い
- [ ] コスト実測(Cost Explorer、要 AWS)で前月比の異常が無い

## 6. 監視・アラート・runbook 整合

### 6.1 現状(ログ基盤)

- CloudWatch Logs: `/tasks/<env>/webapi`(アプリ)/ `/tasks/<env>/audit`(監査・越境違反)/ `/ecs/platform-<env>/keycloak` / `/ecs/tasks-<env>/webapi-metrics`(ADOT EMF → X-Ray + CloudWatch)/ scheduler Lambda。
- 構造化ログ([ADR-0019](../adr/0019-structured-logging-boot-standard.md)): Logback JSON(`level` / `event`)。将来の metric filter アラートの基盤。
- ADOT サイドカー([ADR-0029](../adr/0029-performance-measurement-and-diagnostics.md)): traces + metrics(OTLP、既定 OFF)。

### 6.2 アプリ側アラート

- 監査ハッシュチェーン B-05([ADR-0038](../adr/0038-audit-log-hash-chain-tamper-evidence.md)): 毎日 02:00 の検証バッチ(ShedLock、[ADR-0037](../adr/0037-scheduled-batch-shedlock-and-ses.md))。改ざん検出は **fail-open**、通知バッチ経由でアラート + 構造化ログ記録。

### 6.3 未配線ギャップ(リリース前に要判断)

- **infra レベルの CloudWatch Alarm / SNS / metric filter は未配線**(`infra/modules/logging/main.tf` のコメントで、infra ADR-0005(ログ基盤、`infra/docs/adr/0005-logging-platform.md`。app の [ADR-0005](../adr/0005-task-authorization-three-roles.md) とは番号衝突する別 ADR)の Alarm/SNS 連携として明示的に後回し)。現状のアラートはアプリ側(B-05 バッチ + 構造化ログ)のみ。
- 対応: MVP のアラート要件を確定し、metric filter → Alarm → SNS 配線を別 Issue 化する(本チェックリストで **既知ギャップ**として記録)。

### 6.4 デプロイ後スモークによる実体確認([ADR-0041](../adr/0041-post-deploy-dev-e2e-and-email-verification.md))

pre-deploy CI(§1)と infra アラート(§6.1–6.3)に加え、**デプロイ済み実体の疎通**は §1.2 の dev-smoke E2E で確認する。native / 実 Keycloak / 実 SES 配信という **CI では捕捉できない層**を通しで検証し、デプロイ後の即時退行検知に用いる(手動 `gh workflow run dev-smoke.yml`)。

チェック:

- [ ] 主要ログ group にログが出力されている(`aws logs tail /tasks/dev/webapi --since 15m`)
- [ ] B-05 検証バッチが日次で成功している(監査ログに不整合 ERROR が無い)
- [ ] infra アラート未配線を関係者が認識(別 Issue 追跡)
- [ ] デプロイ後 dev-smoke E2E(§1.2)が全 spec green(native / 実 Keycloak / 実 SES 配信を通し確認)

## 7. 最終ゲート サマリ

- [ ] §1 全 CI green(カバレッジ 0.80 / 漏洩・認可・性能・監査の回帰 IT green)
- [ ] §1.2 デプロイ後 dev-smoke E2E が全 spec green(native / 実 Keycloak / 実 SES 配信)
- [ ] §2 デプロイ 2 段(plan → 承認 → apply)を runbook どおり実施できる
- [ ] §3 IaC drift ゼロ(plan が No changes)
- [ ] §4 Secrets 棚卸し(SecureString 実値設定済み)
- [ ] §5 コスト棚卸し(サイジング / 夜間停止 / 停止漏れなし)
- [ ] §6 監視・runbook 整合、アラート未配線ギャップを記録
- [ ] ロールバック手順([dev-operations.md](dev-operations.md#ロールバック))を確認

## 参考

- [ADR-0031](../adr/0031-single-product-version-dispatch-deploy.md)(dispatch デプロイ)/ [ADR-0028](../adr/0028-build-once-promote-digest.md)(build-once・digest promote)
- [ADR-0039](../adr/0039-performance-tuning-measurement-and-regression.md) — 性能(#769、SLO 非設定)
- [dev-operations.md](dev-operations.md) — dev 運用(デプロイ / ロールバック / 夜間停止 / 障害初動)
- [2026-07-03 性能所見](../reviews/2026-07-03-adr0039-performance-findings.md) — #769 の N+1 / index 所見
- Issue #770(本チェックリスト)/ #771(Sprint 5 テスト・リリース前 統括)
