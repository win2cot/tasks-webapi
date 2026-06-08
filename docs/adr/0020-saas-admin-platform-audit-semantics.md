# ADR-0020: SaaS Admin プラットフォーム操作の監査セマンティクス(`tenant_id` 帰属・read 監査・Tenant Admin 可視性)

- **Status**: Accepted
- **Date**: 2026-06-08
- **Deciders**: win2cot
- **Tags**: audit, security, authorization, multi-tenancy

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [論点A: SaaS Admin 操作レコードの `tenant_id` 帰属](#論点a-saas-admin-操作レコードの-tenant_id-帰属)
  - [論点B: SaaS Admin の read 系操作の監査要否](#論点b-saas-admin-の-read-系操作の監査要否)
- [3. 決定(Decision)](#3-決定decision)
  - [3.1 `tenant_id` 帰属ルール](#31-tenant_id-帰属ルール)
  - [3.2 read 系操作の監査](#32-read-系操作の監査)
  - [3.3 action 標準値の追加](#33-action-標準値の追加)
  - [3.4 Tenant Admin への可視性(A-22)](#34-tenant-admin-への可視性a-22)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

Issue #499(#451 ログ設計議論からの派生)で、SaaS Admin(Keycloak realm role `APP_ADMIN`)によるプラットフォーム操作が `audit_logs`(ADR-0013)のスコープに含まれるかを確認した結果、土台(`audit_logs.tenant_id` nullable / `actor_sub` / `TENANT_CREATED`・`TENANT_SUSPENDED`・`TENANT_REACTIVATED`)は既に揃っており、`tenant_id` 前提との整合(プラットフォーム API は TenantContext を構築しないが `tenant_id` は nullable 設計)も問題ないことが確認された。

一方で次の論点が未決であることが判明した:

1. **`tenant_id` 帰属**: 基本設計書 §4.2.6 は `tenant_id` の備考を「NULL=システム横断 / SaaS Admin 操作」と記述している。これに従うと、特定テナントを対象とする SaaS Admin 操作(停止・再開・名称更新)も `tenant_id=NULL` となり、対象テナントの Tenant Admin が自テナントへの運営操作を `GET /api/audit-logs`(A-22)で確認できない。
2. **read 系の監査要否**: SaaS Admin は全テナントの名称・所属ユーザー数・タスク数・プラットフォームメトリクスを横断的に閲覧できる(A-04 / A-25 / A-27)。現行 MVP の監査方針は write 系と認可違反(`*_DENIED`)のみで、read は対象外。プライバシー高感度な横断閲覧が無記録になる(NIST AU-2)。
3. **action カバレッジの欠落**: テナント名更新(A-06 `PUT /api/tenants/{id}`、S-14「テナント名編集」)に対応する `action` 標準値が存在しない(`TENANT_CREATED` / `TENANT_SUSPENDED` / `TENANT_REACTIVATED` はあるが rename がない)。S-14 は「操作は監査ログに記録」と明記しているが記録すべき値がない。

本 ADR はこれらを統一的に決定する。なお差分計算・書き込み機構そのもの(`detail JSON` の field-by-field diff、`hash_chain`、B-03/B-05 バッチ)は ADR-0013 で確定済であり、本 ADR は **どの操作を・どの `tenant_id` で・誰に見える形で記録するか** のセマンティクスのみを扱う。

関連: ADR-0004(`tenants` 監査列例外・`actor_sub` 方針)/ ADR-0005(2 軸直交・タスク認可 3 役割)/ ADR-0013(監査ログ差分粒度)/ ADR-0010(Hibernate Filter)/ 基本設計書 §4.2.6 / §6.2 / §6.2.3。

## 2. 検討した選択肢(Options Considered)

### 論点A: SaaS Admin 操作レコードの `tenant_id` 帰属

- **A-1: `tenant_id=NULL` 固定(現行記述の踏襲)**
  - 利点: 既存記述「NULL=SaaS Admin 操作」をそのまま維持。プラットフォーム軸操作がテナント軸クエリに一切混ざらず、2 軸の分離が物理的に明快。
  - 欠点: 対象テナントの Tenant Admin が自テナントへの停止・再開・改名を A-22 で確認できない。テナント側から見た運営操作の透明性がゼロ。SaaS Admin 用の横断参照 API も未提供のため、実質「DB 直アクセスでしか追えない」状態。
- **A-2: 特定テナント対象操作は `tenant_id=対象テナント`、横断操作のみ `NULL`(採用)**
  - 利点: 対象テナントの監査証跡が当該テナントに帰属し、Tenant Admin が A-22(Hibernate Filter 自動絞り込み)で自テナントへの運営操作を確認できる。NIST AC-4 の「テナントは自テナントに関する記録を保有」と整合。一覧・メトリクス閲覧のような単一対象を持たない横断操作は引き続き `NULL`。
  - 欠点: `tenant_id` の意味が「操作主体のテナント」ではなく「操作対象のテナント」になるケースが生じ、INSERT 経路で TenantContext 非依存に対象テナント id を明示設定する実装が要る。基本設計書 §4.2.6 の備考改訂が必要。

### 論点B: SaaS Admin の read 系操作の監査要否

- **B-1: read は監査しない(タスク GET 非監査と一貫)**
  - 利点: 監査量が小さく、write+denied のみのシンプルな方針を維持。
  - 欠点: SaaS Admin による全テナント横断閲覧(顧客の名称・規模)が無記録。内部不正・過剰アクセスの事後追跡ができない。
- **B-2: read も監査する(採用)**
  - 利点: SaaS Admin の cross-tenant 閲覧をプライバシー高感度イベントとして記録。NIST AU-2 のうち特権アクセスの可視化要件に整合。テナント詳細閲覧(A-25)は対象テナントに帰属させることで、Tenant Admin が「SaaS 運営者が自テナントを閲覧した」ことまで把握できる。
  - 欠点: read 用 action 値の新設が必要。閲覧頻度次第で監査量が増える(ただし SaaS Admin の母数は小さく実害は限定的)。

## 3. 決定(Decision)

論点A=**A-2**、論点B=**B-2** を採用する。

### 3.1 `tenant_id` 帰属ルール

`audit_logs.tenant_id` は「**操作対象テナント**」を表すものとし、次のとおり設定する。SaaS Admin 操作に加え、A-05 セルフサインアップ(actor: サインアップ顧客)も `tenant_id` 帰属ルールの適用対象となるため、actor 列を付して一覧化する:

| 操作 | API | actor | `tenant_id` | `entity_type` / `entity_id` |
|---|---|---|---|---|
| テナント停止 | A-26 `PATCH /tenants/{id}/status` | SaaS Admin | 対象テナント id | `tenant` / 対象 id |
| テナント再開 | A-26 | SaaS Admin | 対象テナント id | `tenant` / 対象 id |
| テナント名更新 | A-06 `PUT /tenants/{id}` | SaaS Admin | 対象テナント id | `tenant` / 対象 id |
| テナント詳細閲覧 | A-25 `GET /tenants/{id}` | SaaS Admin | 対象テナント id | `tenant` / 対象 id |
| テナント一覧閲覧 | A-04 `GET /tenants` | SaaS Admin | **NULL**(単一対象なし) | `tenant` / NULL |
| プラットフォームメトリクス閲覧 | A-27 `GET /platform/metrics` | SaaS Admin | **NULL**(横断集計) | NULL / NULL |
| テナント作成(セルフサインアップ) | A-05 `POST /tenants` | サインアップ顧客 | 作成テナント id | `tenant` / 作成 id |

INSERT 経路は TenantContext を構築しないため、対象テナント id を UseCase 入力から明示的に `audit_logs.tenant_id` へ設定する(Hibernate Filter は当該 INSERT に対して個別 disableFilter 運用、ADR-0013 §3 の例外規定を踏襲)。

### 3.2 read 系操作の監査

SaaS Admin の read 系操作(A-04 / A-25 / A-27)を成功時に記録する。対象が単一テナントの A-25 は当該テナントに帰属(3.1)、横断系の A-04 / A-27 は `tenant_id=NULL`。`detail` には絞り込み条件・件数等の最小限のコンテキストを記録してよい(PII は出さない、ID 参照のみ。コーディング規約 §7 / ADR-0019 整合)。

### 3.3 action 標準値の追加

`audit_logs.action` 標準値に以下を追加する:

- `TENANT_UPDATED`(テナント名更新・将来のテナント属性編集の汎用値、A-06)
- `TENANT_VIEWED`(テナント詳細閲覧、A-25)
- `TENANT_LIST_VIEWED`(テナント一覧閲覧、A-04)
- `PLATFORM_METRICS_VIEWED`(プラットフォームメトリクス閲覧、A-27)

`TENANT_UPDATED` の `detail` は ADR-0013 の field-by-field diff 形式(`[{ "field": "name", "old": ..., "new": ... }]`)に従う。

### 3.4 Tenant Admin への可視性(A-22)

`tenant_id=対象テナント` を設定した結果として、対象テナントの Tenant Admin は `GET /api/audit-logs`(A-22)で自テナントに対する SaaS Admin の運営操作(停止・再開・改名・詳細閲覧)を確認できる。これは意図した透明性であり、2 軸直交モデル(ADR-0005)の例外ではなく「テナント軸の参照範囲=自テナントに帰属する全記録」という原則の自然な帰結と位置づける。横断操作(一覧・メトリクス、`tenant_id=NULL`)は単一テナントに帰属しないため A-22 には現れない。SaaS Admin 自身による横断的な監査ログ参照 API(`tenant_id IS NULL` 含む全件)は MVP では提供せず(必要時は DB 直クエリ)、API 化は Phase 2 で要否を再判定する。

## 4. 理由(Rationale)

- 対象テナントへの帰属(A-2)は、運営操作の透明性をテナント側に提供し、NIST AC-4(テナントは自テナントに関する情報を保有)と AU-2(特権操作の監査)を同時に満たす。`tenant_id=NULL` 固定では透明性が失われ、別途 SaaS Admin 参照 API を MVP で作る必要が生じる方が重い。
- read 監査(B-2)は、SaaS Admin の cross-tenant 閲覧という最も特権的なアクセスを可視化する。母数が小さく監査量増の実害が限定的な一方、内部統制上の価値が高い。
- `TENANT_UPDATED` ほか action 追加は、過去の `LOGIN_FAILED`(#180)・`*_DENIED` 8 値(#185)と同種の標準値拡張であり、設計の一貫性を保つ。
- 横断操作を `NULL` に残すのは、単一対象を持たない操作を無理に特定テナントへ帰属させない方が意味論的に正確であるため。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 対象テナントの Tenant Admin が自テナントへの運営操作を A-22 で確認でき、運営の説明責任が果たせる。
- SaaS Admin の全特権操作(write + read)が監査証跡として残り、事後追跡が可能になる。
- action 標準値が拡張され、tenant 編集・閲覧系のイベント分類が明確になる。

### 悪い影響・制約(Negative)

- `audit_logs.tenant_id` の意味が「操作主体のテナント」から「操作対象のテナント」に拡張され、INSERT 経路で対象テナント id を明示設定する実装が要る(プラットフォーム API は TenantContext 非依存のため)。
- read 監査により監査量が増える(SaaS Admin 母数が小さいため軽微)。
- 基本設計書 §4.2.6 の備考「NULL=SaaS Admin 操作」が誤りになるため改訂が必要(本 ADR が当該記述を上書き)。

### 既存ドキュメント・規約への波及

- `docs/specs/基本設計書.md` §4.2.6: `tenant_id` 備考を「対象テナント / 横断操作のみ NULL」に改訂、`action` 標準値に 4 値追加。
- `docs/specs/基本設計書.md` §6.2.3 / §6.1 NIST マッピング: read 監査・特権操作の可視性を AU-2 / AC-4 の実装欄に反映。
- `docs/specs/認可マトリクス.md` / OpenAPI(A-06 / A-22 / A-25 / A-26 / A-27): A-22 のスコープ(SaaS Admin 操作が対象テナントに見える)・read 監査の副作用を反映。
- ADR-0013: `tenant_id IS NULL` 例外の記述と整合(本 ADR で「特定テナント対象は NULL ではない」を明確化)。
- ADR-0004: `actor_sub` 追跡方針は不変。`tenant_id` 帰属の精緻化のみ。

## 6. 実装メモ(Implementation Notes)

- 着手順序(本 ADR Accepted 後、別 Issue):
  1. 設計反映 Issue: 基本設計書 §4.2.6 / §6.2.3 / §6.1 + 認可マトリクス + OpenAPI 改訂。
  2. 実装 Issue: `TenantAuditDiffDomainService`(ADR-0013 §6 の `TaskAuditDiffDomainService` テンプレに倣う)+ SaaS Admin write(A-06 / A-26)/ read(A-04 / A-25 / A-27)経路への監査記録挿入。`tenant_id` は対象テナント id を明示設定、横断系は NULL。
- **A-05(セルフサインアップ)の実装特記**: A-05 `POST /tenants` は TenantContext が存在しない状態で呼ばれる初回テナント作成であり、actor は SaaS Admin ではなくサインアップ顧客。監査ログへの `tenant_id` 設定は、UseCase 内でテナント生成直後に確定した新テナント id を明示的に `audit_logs.tenant_id` へ設定する(TenantContext 経由では取得不可のため直接代入)。Hibernate Filter の個別 disableFilter 運用は他の SaaS Admin write 経路と同様(ADR-0013 §3 例外規定)。
- read 監査は成功レスポンス確定後に記録(認可違反は既存 `ROLE_BASED_DENIED` 経路)。
- `detail` への記録は PII 出力禁止(ADR-0019 / コーディング規約 §7)。テナント名のような識別子は `TENANT_UPDATED` の diff(旧名→新名)として記録するのは可(業務証跡として必要)、read 系では件数・条件のみに留める。

## 7. 参考リンク(References)

- Issue #499(本 ADR 起票元、カバレッジ確認)
- Issue #451(ログ設計、発見元)
- ADR-0004(`tenants` 監査列例外・`actor_sub`)
- ADR-0005(2 軸直交・タスク認可 3 役割)
- ADR-0013(監査ログ差分粒度)
- ADR-0010(Hibernate Filter)
- ADR-0019(構造化ログ・PII 出力禁止)
- 基本設計書 §4.2.6 / §6.1 / §6.2 / §6.2.3 / §5(A-04〜A-27)
- NIST SP 800-53 AU-2(イベントログ)/ AU-3(記録内容)/ AC-4(情報フロー制御)
