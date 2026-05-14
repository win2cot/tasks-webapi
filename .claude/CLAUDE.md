# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

マルチテナント型 SaaS タスク管理システム (Task Management System) — Spring Boot 4 / Java 21 / MySQL 8 / Keycloak / AWS ECS Fargate。詳細設計フェーズ完了、Sprint 0 着手前(現在 2026-05-14)。

- **Package root**: `xyz.dgz48.tasks.webapi`
- **Server port**: 8080
- **Actuator endpoints**: `/actuator/health`, `/actuator/info`

## Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# All checks (compile + format check + tests)
./gradlew check

# Tests only
./gradlew test

# Single test class or method
./gradlew test --tests "xyz.dgz48.tasks.webapi.TasksWebapiApplicationTests"
./gradlew test --tests "xyz.dgz48.tasks.webapi.TasksWebapiApplicationTests.contextLoads"

# Coverage report (HTML + XML at build/reports/jacoco/)
./gradlew jacocoTestReport

# Apply Google Java Format
./gradlew spotlessApply

# Check formatting only
./gradlew spotlessCheck
```

## Code Quality Toolchain

| Tool | Role | Notes |
|------|------|-------|
| **Spotless** | Formatting | Google Java Format v1.24.0; run `spotlessApply` before committing |
| **NullAway** | Null-safety | Applies to `xyz.dgz48.*`; disabled for tests |
| **JSpecify** | Null annotations | All packages use `@NullMarked` at package level |
| **Error Prone** | Static analysis | Runs at compile time |
| **JaCoCo** | Coverage | 80% minimum enforced in CI on overall and changed-file coverage |

## Null Safety

All code under `xyz.dgz48.*` must be null-safe. Each package requires a `package-info.java` annotated with `@NullMarked`. Parameters, return types, and fields are non-null by default; use `@Nullable` explicitly where null is permitted.

## CI/CD

GitHub Actions (`cicd.yml`) runs `./gradlew check` on every push and PR, publishes test results and JaCoCo coverage reports. Automated Claude Code reviews run on PRs via `claude-code-review.yml`. CI environment uses `LANG=ja_JP.UTF-8` and `TZ=Asia/Tokyo`.

## Design Context

詳細設計フェーズが完了し、現在は実装着手前の **Sprint 0 Readiness** 段階(2026-05-14時点)。本セクションは実装時に頻繁に参照する不変ルールのみを集約。詳細は別ファイル参照。

### Design Versions

- 要件定義書: v1.2(v1.3 更新は計画中)
- 基本設計書: **v1.3.2** ← Single Source of Truth
- 開発計画書: v1.2
- OpenAPI: **v1.3.1**(24 operations / 23 schemas)

### Multi-Tenancy(絶対不変)

- 全業務テーブルに `tenant_id BIGINT NOT NULL` 列を持つ(`users` テーブルのみ既存・例外)
- 全 SQL に `WHERE tenant_id = :ctx` が自動付与される(Hibernate Filter + TenantContext)
- 越境アクセスは **404**(参照系)or **403**(更新系)で拒否
- リクエストの `X-Tenant-Id` ヘッダで現在テナントを指定(未選択状態の `/api/auth/me`・`/select`・`/logout` だけ例外)

### Authorization Model

3階層ロール(参照のみの Viewer は設けない):

- **SaaS Admin**: Keycloak realm role `APP_ADMIN` で管理(DBの `user_tenants.role` には保持しない)→ `hasRole('APP_ADMIN')`
- **Tenant Admin**: `user_tenants.role = TENANT_ADMIN` → `hasRole('TENANT_ADMIN')`
- **Member**: `user_tenants.role = MEMBER` → `hasRole('MEMBER')`

タスクごとの可視性(`visibility` 列):

- `TENANT`(デフォルト): テナント全員参照可
- `STAKEHOLDERS`: 所有者 + 担当者 + `task_stakeholders` 登録ユーザー
- `PRIVATE`: 所有者のみ

編集は所有者のみ(Tenant Admin は強制編集可、監査ログ記録)。認可ロジックは `TaskAuthorizationDomainService`(Domain層)に SSOT として集約する設計。

### HTTP Status Policy

- 認証失敗: **401**
- 参照系で参照不可 / リソース不在: **404**(NIST AC-4 — 存在を漏らさない)
- 更新系で権限不足: **403**
- テナント越境: **403**

`TaskOwnershipException` → 403、`TaskNotViewableException` → 404 で `@RestControllerAdvice` がマッピング。

### Architecture

クリーンアーキ 4 層(依存方向は外→内のみ、ArchUnit で検証):

- `domain` — POJO のみ、Spring 非依存、JPA アノテーション禁止
- `usecase` — ユースケース・Port 定義・トランザクション境界
- `adapter.{web, persistence, external}` — REST Controller、JPA Entity、Keycloak/SES クライアント
- `infra` — Spring Boot 設定、起動クラス

実行基盤は **AWS ECS on Fargate**(EC2 不使用)。認証は **Keycloak**(本プロジェクトで構築)。

### Key References

- 基本設計書(SSOT): `docs/specs/基本設計書.md`
- OpenAPI(API契約): `api/openapi.yaml`
- ギャップ分析: `docs/reviews/2026-05-10-scaffold-vs-design-gap-analysis.md`
- レビュー履歴: `docs/reviews/pr-76-review.md`
- Sprint 0 トラッキング: GitHub Issue #121(Sprint 0 本体)/ #120(Readiness)

### Current Phase

Sprint 0 着手前(着手予定: 2026-07-14)。
着手前にクローズすべき残課題は GitHub Issue #120 のチェックリスト参照。
