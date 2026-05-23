# タスク管理システム — 詳細設計成果物索引

要件定義書・基本設計書・開発計画書(各 v1.2、2026-05-05承認)を入力として作成した、**機械可読な詳細設計成果物**の索引。

> Officeドキュメント(.docx/.xlsx/.pptx)は使用しません。すべて Markdown / Mermaid / OpenAPI / SQL で版管理します。

## ファイル一覧

### 設計仕様書(Markdown)

| 文書 | パス | 内容 |
|---|---|---|
| 要件定義書 v1.4.3 | `specs/要件定義書.md` | 業務要件・機能要件・非機能要件・技術スタック |
| 基本設計書 v1.4.4 | `specs/基本設計書.md` | アーキ・画面・DB・API・セキュリティ |
| 開発計画書 v1.2 | `specs/開発計画書.md` | 体制・スケジュール・リスク・GitHub運用マッピング |

### Flyway 初期スキーマ(本PRには未含、Sprint 0 で投入予定)

設計書 §4.2 に基づく Flyway マイグレーション(7テーブル: tenants / user_tenants / tasks / task_stakeholders / audit_logs / user_notification_settings / shedlock、tenant_id 複合インデックス、論理FKコメント付き)は、既存 `src/main/resources/db/migration/V1.0.0_01__create_tables.sql` との整合確認を行ったうえで別PRで投入する。

詳細は [scaffold ↔ 設計書 v1.3 整合性ギャップ分析](reviews/2026-05-10-scaffold-vs-design-gap-analysis.md) §G-2 / §G-3、および対応Issue(Sprint 0 候補 N1 / N2)を参照。

### API仕様

| ファイル | 内容 |
|---|---|
| `../api/openapi.yaml` | OpenAPI 3.1(v1.4.5)。28エンドポイント・27スキーマ・認可ルール記述・エラーレスポンス共通定義 |

### クラス図 / シーケンス図(Mermaid)

| ファイル | 目的 |
|---|---|
| `diagrams/class-diagram-overview.mmd`       | クリーンアーキの4層と依存方向 |
| `diagrams/class-diagram-task-domain.mmd`    | Task 集約・値オブジェクト・ドメインサービス |
| `diagrams/class-diagram-usecase-task.mmd`   | UseCase層(タスク関連)とPort依存 |
| `diagrams/sequence-01-oidc-login.mmd`       | OIDCログイン + 既存users突合フロー |
| `diagrams/sequence-02-task-create.mmd`      | タスク作成(認証→認可→ドメイン→永続化→監査) |
| `diagrams/sequence-03-task-list-authz.mmd`  | 当日表示+期限切れ常時表示+visibilityフィルタ |
| `diagrams/sequence-04-task-edit-authz.mmd`  | 所有者チェックと TaskOwnershipException |
| `diagrams/sequence-05-stakeholder-add.mmd`  | 関係者追加(所有者・担当者が実施、visibility 自動昇格なし、ADR-0005) |

## 利用方法

### 設計仕様書
GitHub上ではそのままレンダリングされます。VS Codeでは Markdown プレビュー(`Ctrl+Shift+V`)で表示。

### Flyway SQL
```bash
./gradlew :webapi:flywayMigrate \
  -Pflyway.url=jdbc:mysql://localhost:3306/taskflow \
  -Pflyway.user=app
```

### OpenAPI
```bash
# Lint
npx @stoplight/spectral-cli lint api/openapi.yaml

# Swagger UI
docker run -p 8080:8080 -e SWAGGER_JSON=/api/openapi.yaml \
  -v $(pwd)/api:/api swaggerapi/swagger-ui
```

### Mermaid
GitHub上ではMarkdown内の ` ```mermaid ` ブロックで自動レンダリング。
ローカル:
```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i docs/diagrams/sequence-01-oidc-login.mmd -o /tmp/login.png
```
VS Code拡張: "Mermaid Preview"

## 整合性メモ

- API操作数(基本設計書) と OpenAPI operations: ともに **28**(A-01〜A-28)。A-23/A-24 は通知設定 v1.3.1 で追加、A-25/A-26/A-27 は SaaS Admin スコープで v1.4.1 で追加、A-28 は S-15 テナント運営者向けダッシュボードとして v1.4.5 で追加(ADR-0005)
- DDLのテーブル数とER図上の業務テーブル数: ともに **5(+運用2)**
- 認可ルールはシーケンス図(03/04/05)と OpenAPI の `description` で重複記述。実装時は `TaskAuthorizationDomainService` に1箇所で集約

## 次ステップ

- 詳細設計レビュー(GitHub PRで `/docs` に提案 → 指摘収集)
- Sprint 0 着手判定
- Spring Boot プロジェクト雛形作成(クリーンアーキの空骨組 + ArchUnit)
- Terraform で ECS Fargate 基盤 IaC
- Keycloak Realm 定義 JSON のエクスポート
