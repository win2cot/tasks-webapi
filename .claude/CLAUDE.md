# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

マルチテナント型 SaaS タスク管理システム (Task Management System) — Spring Boot 4 / Java 21 / MySQL 8 / Keycloak / AWS ECS Fargate。詳細設計フェーズ完了、Sprint 0 着手前(現在 2026-05-14)。

- **Package root**: `xyz.dgz48.tasks.webapi`
- **Server port**: 8080
- **Actuator endpoints**: `/actuator/health`, `/actuator/info`

## Commands

This repository is a Gradle multi-project (monorepo). Run subproject-targeted tasks with `:webapi:` prefix.

```bash
# Build
./gradlew :webapi:build

# Run
./gradlew :webapi:bootRun

# All checks (compile + format check + tests)
./gradlew :webapi:check

# Tests only
./gradlew :webapi:test

# Single test class or method
./gradlew :webapi:test --tests "xyz.dgz48.tasks.webapi.TasksWebapiApplicationTests"
./gradlew :webapi:test --tests "xyz.dgz48.tasks.webapi.TasksWebapiApplicationTests.contextLoads"

# Coverage report (HTML + XML at build/reports/jacoco/)
./gradlew :webapi:jacocoTestReport

# Apply Google Java Format
./gradlew :webapi:spotlessApply

# Check formatting only
./gradlew :webapi:spotlessCheck
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

## Conventions (must read before editing code)

Before making non-trivial code or design changes, read the following Japanese-Markdown convention docs. They are the authoritative ruleset; this file only summarizes a tiny subset.

- `docs/specs/設計規約.md` — architecture (Spring Modulith + per-feature clean layers), package layout, multi-tenant rules, auth response policy (404 for read denial, 403 for write denial, 401 unauth), Flyway naming, OpenAPI-first, error response schema.
- `docs/specs/コーディング規約.md` — Java/Spring style, null-safety, allowed Lombok annotations, JPA entity pattern, DTO records, exception hierarchy, tests (Testcontainers MySQL 8.4, no H2), logging, naming.

Architecture-level decisions are recorded as ADRs under `docs/adr/` (template: `docs/adr/0000-template.md`; index starts at `docs/adr/0001-record-architecture-decisions.md`). When a change introduces a new library, a new framework-level pattern, or revises an existing convention, open an ADR in the same PR.

## CI/CD

GitHub Actions (`cicd.yml`) runs `./gradlew :webapi:check` on every push and PR, publishes test results and JaCoCo coverage reports. Automated Claude Code reviews run on PRs via `claude-review.yml`. CI environment uses `LANG=ja_JP.UTF-8` and `TZ=Asia/Tokyo`.

## Design Context

詳細設計フェーズが完了し、現在は実装着手前の **Sprint 0 Readiness** 段階(2026-05-14時点)。本セクションは実装時に頻繁に参照する不変ルールのみを集約。詳細は別ファイル参照。

## 設計ドキュメント

主要な設計ドキュメント(各最新版はファイルヘッダ参照):

- 要件定義書: `docs/specs/要件定義書.md`
- 基本設計書: `docs/specs/基本設計書.md`
- 設計規約: `docs/specs/設計規約.md`
- コーディング規約: `docs/specs/コーディング規約.md`
- 開発計画書: `docs/specs/開発計画書.md`
- ADR: `docs/adr/` 配下(各 ADR 個別ファイル)

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

- `TENANT`(デフォルト): テナント内全員(`TENANT_ADMIN` / `MEMBER` 区別なし)
- `STAKEHOLDERS`: 所有者 + 担当者 + `task_stakeholders` 登録ユーザー
- `PRIVATE`: 所有者 + 担当者

タスク認可は**所有者・担当者・関係者の 3 役割のみで評価**する(ADR-0005)。Tenant Admin の業務タスク特権は撤廃済(編集・削除・ステータス変更・公開範囲変更・関係者編集における Tenant Admin 例外はすべてなし)。Tenant Admin はテナント運営権限(招待 / ロール管理 / 監査ログ参照 / S-15 テナント運営者向けダッシュボード参照)のみを持つ。

- 編集 / 論理削除 / 公開範囲変更: 所有者のみ
- ステータス変更 / 関係者追加・削除: 所有者・担当者
- 物理削除: MVP 未提供(解約 #167 で統合)
- 関係者リストへの登録: visibility に関係なく可能。ただし**関係者であることが参照権限として機能する**のは visibility = STAKEHOLDERS のときのみ(visibility = TENANT のときはテナントメンバー全員、PRIVATE のときは所有者・担当者のみが参照可)

認可ロジックは `TaskAuthorizationDomainService`(Domain 層)に SSOT として集約する設計。

### HTTP Status Policy

- 認証失敗: **401**
- 参照系で参照不可 / リソース不在: **404**(NIST AC-4 — 存在を漏らさない)
- 更新系で権限不足: **403**
- テナント越境: **403**

`TaskOwnershipException` → 403、`TaskNotViewableException` → 404 で `@RestControllerAdvice` がマッピング。

### Architecture

**Spring Modulith による feature-by-package + 各 feature 内部でクリーンアーキ 4 層** のハイブリッド方式を採用する(詳細は `docs/specs/設計規約.md` §1)。

- **外側(モジュール境界)**: feature 単位パッケージ(`xyz.dgz48.tasks.webapi.<feature>` 例: `task` / `user` / `security` / `tenant` / `notification` / `audit` / `dashboard` / `shared`)。feature 間の不正参照は `ModularityTests` の `ApplicationModules.verify()` が CI で検知。
- **内側(各 feature 内部)**: 依存方向は外→内のみ。
  - `domain` — POJO のみ、Spring 非依存、JPA アノテーション禁止
  - `usecase` — ユースケース・Port 定義・トランザクション境界(`@Transactional` はここ)
  - `adapter.{web, persistence, external}` — REST Controller、JPA Entity、Keycloak/SES クライアント
  - `infra` — feature 固有の Spring 設定
- feature 間連携は Spring Modulith の `@ApplicationModuleListener`(イベント)または `@NamedInterface` で公開した型のみ。他 feature の `internal` 配下を直接参照しない。
- ArchUnit による静的検証の導入は **ADR-0002 により当面保留**(`ApplicationModules.verify()` に集約)。

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
