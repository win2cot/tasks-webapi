# ADR-0004: SaaS Admin 操作時の予約システムユーザー機構を廃止し、`tenants` を監査列例外とする

- **Status**: Accepted
- **Date**: 2026-05-16
- **Deciders**: 開発チーム
- **Tags**: persistence, audit, security

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: 規約踏襲(`tenants` も他業務テーブルと同じ規約に揃え、予約システムユーザー機構を維持)](#選択肢-a-規約踏襲tenants-も他業務テーブルと同じ規約に揃え予約システムユーザー機構を維持)
  - [選択肢 B: `tenants` を監査列例外とし、`audit_logs.actor_sub` のみで追跡](#選択肢-b-tenants-を監査列例外とし-audit_logsactor_sub-のみで追跡)
  - [選択肢 C: `tenants` に `created_by` だけ残す](#選択肢-c-tenants-に-created_by-だけ残す)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響](#良い影響)
  - [悪い影響・制約](#悪い影響制約)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ](#6-実装メモ)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

設計規約 v1.0 §3.5.1(SaaS Admin 操作時の `created_by` / `updated_by`)は、SaaS Admin(Keycloak realm role `APP_ADMIN`)が業務テーブルを書き換える場合に、`created_by` / `updated_by` の **FK 制約上 `users.id` を要求する** 状況との衝突を回避する目的で導入されていた。

具体的な仕組み:

- 予約システム `users` レコード(`oidc_sub = '__system_app_admin__'` 等の固定値)を seed マイグレーション(仮称 `V99__seed_system_users.sql`)で投入
- SaaS Admin 操作時のみ、`created_by` / `updated_by` をこの予約 id に差し替える(差し替え経路は `AuditingEntityListener` または UseCase 入口に集約)
- 実際の操作主体は `audit_logs.actor_sub` に Keycloak `sub` クレームで記録

2026-05-16 に「SaaS Admin スコープ整理」ブレストを実施した結果、以下の方針が確定した(関連 Issue: #158〜#161):

- **SaaS Admin は業務データに触らない / プラットフォーム運用 API のみ**を持つ
- SaaS Admin が触る業務テーブルは `tenants` のみ(`status` 列の `ACTIVE`/`SUSPENDED` 切替)
- テナント作成は顧客セルフサインアップ(モデル B)であり、SaaS Admin による業務テーブル書き込みではない

この方針により §3.5.1 が解決すべき「FK 衝突」は **`tenants` テーブル 1 つに限定**された。1 テーブルのためだけに予約システムユーザー機構を維持するコストの妥当性を再評価する必要が出てきた。

## 2. 検討した選択肢

### 選択肢 A: 規約踏襲(`tenants` も他業務テーブルと同じ規約に揃え、予約システムユーザー機構を維持)

- 概要: §3.5.1 を維持し、`tenants` にも `created_by` / `updated_by` を持たせる。SaaS Admin 操作時は予約 `users` レコードの id に差し替える。
- 利点:
  - 全業務テーブルが共通の監査列規約に従い、一貫性が高い。
  - 将来 SaaS Admin が他テーブルにも書き込むようになった場合の拡張が楽。
- 欠点:
  - 1 テーブルのために予約 oidc_sub 命名・seed migration 番号・差し替え経路(`AuditingEntityListener` or UseCase 入口)の合意・実装が必要。
  - Sprint 0 整合タスクの残課題が増える。

### 選択肢 B: `tenants` を監査列例外とし、`audit_logs.actor_sub` のみで追跡

- 概要: `tenants` テーブルから `created_by` / `updated_by` を削除し、SaaS Admin による状態変更は `audit_logs.actor_sub`(Keycloak `sub`)で追跡する。§3.5.1 は節ごと削除し、§3.5 に `tenants` 例外を明示する。
- 利点:
  - 予約システムユーザー機構そのものが不要(Sprint 0 整合タスク削減)。
  - `tenants` は元々マルチテナント不変の対象外(`tenant_id` 列を持たない特例テーブル)であり、監査の扱いも特例で一貫している。
  - `audit_logs` で履歴として追えるため、列に最終更新者だけ持つより情報量が多い。
- 欠点:
  - 規約に「業務テーブルは全て `created_by` / `updated_by` を持つ」というシンプルなルールに小さな例外が増える。
  - 将来 SaaS Admin が業務テーブルに書く必要が出た場合、本 ADR を Supersede する別 ADR が必要。

### 選択肢 C: `tenants` に `created_by` だけ残す

- 概要: `created_by`(=セルフサインアップ時の作成者)は持つ。`updated_by`(SaaS Admin による状態変更の最終更新者)は持たない。
- 利点: テナントオーナー(初代 Tenant Admin)を 1 クエリで引ける。
- 欠点:
  - 「初代作成者」は所有権の実態と乖離する(テナントオーナーは後から増減し、Tenant Admin の譲渡もあり得る)。`user_tenants` の現役 `TENANT_ADMIN` を引く方が正確。
  - `created_by` と `updated_by` の対称性が崩れる。

## 3. 決定

**採用**: 選択肢 B(`tenants` を監査列例外とし、`audit_logs.actor_sub` のみで追跡)

具体的には:

- 設計規約 §3.5.1 を **節ごと削除**する。
- 設計規約 §3.5 に「`tenants` テーブルは例外として `created_by` / `updated_by` を持たない」旨と理由(3 点)を明記する。
- 基本設計書 §4.2.6 `audit_logs` テーブルに `actor_sub` 列(`VARCHAR(255)`, NULL 許容)を追加し、SaaS Admin 操作の追跡に用いる(関連 PR #159 / Issue #159 で対応)。
- `audit_logs.action` の例値に `TENANT_CREATED` / `TENANT_SUSPENDED` / `TENANT_REACTIVATED` を追加する(同 PR #159)。
- 予約 oidc_sub プレフィックス `__system_*` の命名合意・seed migration(`V99__seed_system_users.sql`)は **不要**(Sprint 0 残課題から削除)。

## 4. 理由

- 2026-05-16 SaaS Admin スコープ整理ブレストにより、SaaS Admin が業務テーブルに書き込む経路が消えた。結果として §3.5.1 が解決すべき問題が `tenants` 1 テーブルに矮小化された。
- `tenants` は元から特例テーブル(no `tenant_id`)であり、監査の扱いも特例にする方が設計の「特殊性の塊」が 1 か所に集約されて整合する。
- 「最終更新者」の情報は `audit_logs` で履歴として追える方が情報量が多く、専用列で持つ価値は薄い。
- 「初代作成者」を `created_by` で持ってもテナントオーナーの実態とは乖離する(選択肢 C の欠点)。
- Sprint 0 整合タスクから「予約システムユーザーの命名合意」「seed migration 設計」「差し替え経路実装」が削除でき、リードタイム短縮に直結する。

## 5. 影響

### 良い影響

- Sprint 0 整合タスク(Issue #137〜#146 系)から「予約 oidc_sub 命名合意」「`V99__seed_system_users.sql` 設計」「`AuditingEntityListener` 差し替え経路実装」が消える。
- 設計規約がシンプルになる(SaaS Admin 関連の特殊運用が「`tenants` の例外注記」1 か所に集約される)。
- `audit_logs.actor_sub` の使い道が明確になる(SaaS Admin 操作追跡の標準手段として位置付けられる)。

### 悪い影響・制約

- 「業務テーブルは全て `created_by` / `updated_by` を持つ」というシンプルなルールに 1 つ例外が増える。
- 将来 SaaS Admin が他の業務テーブルにも書き込む必要が出た場合、本 ADR を Supersede する別 ADR + 予約システムユーザー機構の再導入(or 別解)が必要。

### 既存ドキュメント・規約への波及

- 設計規約 v1.1 で §3.5.1 削除 + §3.5 に `tenants` 例外明示(本 ADR と同 PR で対応 / Issue #160)。
- 基本設計書 v1.4 §4.2.2 `tenants` テーブル定義から `created_by` / `updated_by` を持たない旨を注記(関連 PR #159)。
- 基本設計書 v1.4 §4.2.6 `audit_logs` に `actor_sub` 列を追加(関連 PR #159)。
- 要件定義書 v1.4 §3.1 SaaS運営者の責務改訂 / §3.3.1 テナント管理改訂(関連 PR #158)。

## 6. 実装メモ

- 本 ADR は規約変更の根拠を示すのみ。実装作業は Sprint 0 以降の各 PR(#158〜#161 経由でマージされる文書改訂、および後続の実装 PR)で行う。
- 将来 SaaS Admin が他業務テーブルに書く必要が出た場合の対応案:
  - 案 1: 本 ADR を Supersede し、予約システムユーザー機構を再導入する別 ADR を起こす。
  - 案 2: 該当テーブルも `tenants` と同様に監査列例外として `audit_logs.actor_sub` で追跡する別 ADR を起こす。
  - 案 3: SaaS Admin に対応する `users` 行を JIT で作成し、通常の FK で扱う(モデル B のセルフサインアップ流れで SaaS Admin も `users` 行を持つようになれば実現可能)。

## 7. 参考リンク

- 設計規約 §3.5 / §3.5.1(本 ADR と同 PR で改訂)
- 基本設計書 v1.4 §4.2.2 / §4.2.6(関連 PR / Issue #159)
- 要件定義書 v1.4 §3.1 / §3.3.1(関連 PR / Issue #158)
- Issue #160(本 ADR の起点となるドキュメント反映 Issue)
- Issue #121(Sprint 0 readiness 親 Issue)
- ADR-0001(ADR 制度導入)
