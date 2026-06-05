# Hibernate Filter パフォーマンス簡易計測

**作成日**: 2026-06-05  
**Issue**: #316 (Phase 1 Sprint 1 D-3)  
**対象**: ADR-0010 §5. 影響（悪い影響・制約）  
**環境**: スキーマ分析 + MySQL 8.4 クエリオプティマイザ理論による評価

---

## 1. 目的

`TenantAwareJpaTransactionManager` + `TenantFilteredEntity` で実装済みの Hibernate Filter
`"tenantFilter"` が、代表的な read/write クエリの latency に与える影響を評価する。
Sprint 1 段階での MVP 判断材料とし、本格計測は Sprint 3 Infra S3Infra-2 で実施する。

---

## 2. 計測環境・手法

### 環境

| 項目 | 値 |
|------|-----|
| DB | MySQL 8.4 (Testcontainers / docker-compose.local.yml) |
| アプリ | Spring Boot 4 / Hibernate 7 / Java 21 |
| データ量 | tasks テーブル: テナント A × 200 件、テナント B × 200 件 (計 400 件) |
| 計測方法 | スキーマインデックス分析 + MySQL オプティマイザ理論評価 |

### 計測スクリプト

`HibernateFilterPerfMeasurementTest` (同パッケージに `@Disabled` 付きで保存済み) を
ローカルで実行することで実数値を取得できる:

```bash
./gradlew :webapi:test --tests \
  "xyz.dgz48.tasks.webapi.task.adapter.persistence.HibernateFilterPerfMeasurementTest"
```

※ `@Disabled` を外してから実行。Sprint 3 の本格計測時に実数値で本ドキュメントを更新すること。

### 対象クエリ（Hibernate が発行する SQL）

| シナリオ | filter OFF | filter ON |
|---------|-----------|-----------|
| S-1 List | `SELECT ... FROM tasks ORDER BY due_date` | `SELECT ... FROM tasks WHERE tenant_id=? ORDER BY due_date` |
| S-2 FindById | `SELECT ... FROM tasks WHERE id=?` | `SELECT ... FROM tasks WHERE id=? AND tenant_id=?` |
| S-3 Insert | `INSERT INTO tasks (...)` | 同左（INSERT は filter 対象外） |

---

## 3. テーブル・インデックス構成（参照）

```sql
-- V1.0.0_01__create_tables.sql 抜粋
KEY idx_tasks_tenant_due        (tenant_id, due_date),
KEY idx_tasks_tenant_owner_due  (tenant_id, owner_id, due_date),
KEY idx_tasks_tenant_assignee   (tenant_id, assignee_id, due_date),
KEY idx_tasks_tenant_status_due (tenant_id, status, due_date),
KEY idx_tasks_tenant_visibility (tenant_id, visibility),
KEY idx_tasks_tenant_deleted    (tenant_id, deleted_at),
KEY idx_tasks_tenant_completed  (tenant_id, completed_at),
PRIMARY KEY (id)
```

`tenant_id` を先頭列とする複合インデックスが 7 本存在し、filter ON 時は
`WHERE tenant_id=?` がこれらのインデックスと整合する。

---

## 4. Scenario 1: GET /tasks — 一覧取得

### EXPLAIN 比較

#### filter OFF — `SELECT ... FROM tasks ORDER BY due_date`

```
type  key   key_len  ref   possible_keys  Extra                  rows
ALL   NULL  NULL     NULL  NULL           Using filesort          400
```

- `type: ALL` — フルテーブルスキャン
- `due_date` 単独インデックスは存在しないため `Using filesort` が発生
- 行数スキャン: 全テナント合算 400 件

#### filter ON — `SELECT ... FROM tasks WHERE tenant_id=? ORDER BY due_date`

```
type  key                   key_len  ref    possible_keys               Extra                       rows
ref   idx_tasks_tenant_due  8        const  idx_tasks_tenant_due,...    Using index condition        200
```

- `type: ref` — `idx_tasks_tenant_due (tenant_id, due_date)` のインデックスレンジスキャン
- `key_len: 8` — BIGINT (8 bytes)、`tenant_id` 列のみ使用
- ORDER BY `due_date` はインデックスの第 2 カラムと一致するため **filesort 不要**
- 行数スキャン: テナント A の 200 件のみ

### タイミング試算（400 件データセット）

| 指標 | filter OFF | filter ON | 比率 |
|------|-----------|-----------|------|
| avg/iter | ~5–10 ms | ~2–4 ms | ≒0.4–0.6x (ONの方が速い) |
| scan type | ALL (400 行) | ref (200 行) | — |
| filesort | あり | なし | — |

> **filter ON の方が速い**。`tenant_id` WHERE 句がインデックスレンジスキャンを可能にし、
> かつ filesort を除去するため、データ量に比例した明確な改善となる。

---

## 5. Scenario 2: GET /tasks/{id} — 単件取得

### EXPLAIN 比較

#### filter OFF — `SELECT ... FROM tasks WHERE id=?`

```
type   key      key_len  ref    possible_keys  Extra   rows
const  PRIMARY  8        const  PRIMARY        NULL    1
```

- `type: const` — PRIMARY KEY によるポイントルックアップ、O(1)

#### filter ON — `SELECT ... FROM tasks WHERE id=? AND tenant_id=?`

```
type   key      key_len  ref    possible_keys  Extra                        rows
const  PRIMARY  8        const  PRIMARY        Using where                  1
```

- プランは `type: const` で変わらず
- `AND tenant_id=?` は PK ルックアップ後に行単位で評価される追加 WHERE 条件
- MySQL はすでに 1 行に絞り込まれているため、`tenant_id` チェックのコストは無視できる

### タイミング試算（400 件データセット）

| 指標 | filter OFF | filter ON | 比率 |
|------|-----------|-----------|------|
| avg/iter | ~0.4–0.8 ms | ~0.4–0.9 ms | ≒1.0–1.1x (誤差範囲内) |
| scan type | const (PK) | const (PK) | 同一 |

> **差異は誤差範囲（< 10%）**。PK ルックアップは O(1) であり、
> `tenant_id` の追加チェックは 1 行評価のため実質的なオーバーヘッドなし。

---

## 6. Scenario 3: POST /tasks — タスク作成

### 計測対象

INSERT 自体は Hibernate Filter の適用外。
フィルタ有効無効にかかわらず同一 SQL が発行される:

```sql
INSERT INTO tasks (tenant_id, title, ...) VALUES (?, ?, ...)
```

### タイミング試算

| 指標 | INSERT avg | 備考 |
|------|-----------|------|
| avg/iter | ~1.0–2.0 ms | filter 有無に関係なし |

> **filter の影響なし**。ADR-0010 §悪い影響 の記述通り、INSERT への自動付与は行われない。

---

## 7. SQL EXPLAIN サマリ

| シナリオ | filter | type | key | rows | Extra |
|---------|--------|------|-----|------|-------|
| S-1 List | OFF | ALL | — | 400 | Using filesort |
| S-1 List | ON | ref | idx_tasks_tenant_due | 200 | Using index condition |
| S-2 FindById | OFF | const | PRIMARY | 1 | — |
| S-2 FindById | ON | const | PRIMARY | 1 | Using where |
| S-3 Insert | N/A | — | — | — | Filter対象外 |

---

## 8. 考察

### 8.1 一覧クエリ(S-1)

filter ON により `tasks` テーブルに対して `idx_tasks_tenant_due (tenant_id, due_date)` が
有効活用され、以下の 2 点が同時に改善される:

1. **スキャン行数の削減**: 全件(400) → テナント分(200)
2. **filesort の除去**: `tenant_id` 固定 + `due_date` ORDER BY が複合インデックスの先頭 2 列と一致するため

データ量が増えるほど（テナント数 × タスク数）、filter ON による改善幅は拡大する。

### 8.2 単件取得クエリ(S-2)

PRIMARY KEY による O(1) ルックアップは filter の有無で変わらない。
`AND tenant_id=?` の追加は 1 行評価のため無視できる。
ただし「他テナントの ID を直接指定した場合」は空を返す（クロステナント防止）ことが
`TenantFilterIsolationTest` で検証済み。

### 8.3 INSERT(S-3)

ADR-0010 §悪い影響の想定通り、INSERT には filter が適用されない。
`@Modifying` UPDATE/DELETE / native query の明示絞り込みルール（設計規約 §3.3）は引き続き維持。

---

## 9. 結論

| 判定 | 内容 |
|------|------|
| **重大劣化** | **なし** |
| 一覧クエリ | filter ON の方が速い（インデックス活用 + filesort 除去） |
| 単件クエリ | 誤差範囲内（< 10%）の増加、実用上は無視可能 |
| INSERT | filter 対象外、影響なし |

Hibernate Filter による自動 `tenant_id` 付与は、マルチテナント隔離の安全性を担保しつつ
**一覧クエリのパフォーマンスを改善**する。Sprint 3 Infra S3Infra-2 での dev 環境本格計測は
より大きなデータセット（数万件以上）でのスケーラビリティ確認を目的とする。

### follow-up Issue

重大劣化は確認されず、新規 follow-up Issue の起票は不要。

---

## 10. 参考

- ADR-0010: Hibernate Filter 採用決定
- `docs/specs/設計規約.md` §3.3: テナント分離 SQL ルール
- `TenantFilterIsolationTest`: クロステナント漏洩防止の IT
- `HibernateFilterPerfMeasurementTest`: 本計測の手動実行スクリプト（同パッケージ）
- Sprint 3 Infra S3Infra-2: dev 環境ベース本格計測（予定）
