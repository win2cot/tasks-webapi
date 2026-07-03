# 性能所見メモ — N+1 / クエリ / index(ADR-0039 / Issue #769)

- 日付: 2026-07-03
- 対象: [Issue #769](https://github.com/win2cot/tasks-webapi/issues/769)(Phase 1 Sprint 5 性能チューニング)
- 方式: [ADR-0039](../adr/0039-performance-tuning-measurement-and-regression.md)(測定 → 是正 → 所見記録。定量 SLO なし)
- 前提: 実行基盤は GraalVM Native([ADR-0008](../adr/0008-graalvm-native-image.md))/ テストは Testcontainers MySQL 8.4(H2 不可)/ テナント分離は Hibernate Filter([ADR-0010](../adr/0010-tenant-isolation-hibernate-filter.md))

## 1. 結論(サマリ)

- **N+1**: 主要4経路(タスク一覧 / 関係者一覧 / ダッシュボード tasks / ダッシュボード summary)は既に一括解決済みで N+1 は無い。**クエリ本数を回帰固定するガードを追加**(PR #837)し、将来の退行を CI で検知できるようにした。
- **index**: 主要 endpoint の代表クエリを実 MySQL 8.4 上で `EXPLAIN` した結果、**マルチテナント実配分では `tasks` へのフルスキャン(`type=ALL`)は発生せず**、いずれも `tenant_id` 先頭の複合 index(`idx_tasks_tenant_*`)または index_merge を使用する。**新規 index の追加は不要**と判断した。
- **record-only**: keyword 部分一致(`LIKE '%kw%'`、前方ワイルドカード)は index 不可。ADR-0039 §5 に従い所見記録のみとし、改善(前方一致化 / FULLTEXT 等)は別 Issue 候補とする。

## 2. レイヤー②: N+1 回帰固定(PR #837、`Refs #769`)

ADR-0039 §3 選択肢 B(datasource-proxy, test scope)。実 DataSource を proxy 化してクエリ本数を計数し、別ユーザー所有のタスク / 関係者を多めに seed して「本数がフィクスチャ件数 N に依存せず定数」であることを固定した。N+1 退行時は本数が N に比例して増え、テストが落ちる。

| endpoint | 固定本数 | 対策機構 |
|---|---|---|
| `GET /api/tasks` | 5 | `loadUserMap` の一括 `findAllById` |
| `GET /api/tasks/{id}/stakeholders` | 4 | `users` を JOIN した単一 native query |
| `GET /api/dashboard/tasks` | 5 | 4 セクション query + 一括 `loadUserMap` |
| `GET /api/dashboard/summary` | 2 | STAKEHOLDERS 認可の `EXISTS` 単一 query |

テスト: `TaskQueryCountIT` / `DashboardQueryCountIT`、ヘルパ `QueryCountProbe` / `QueryCountTestConfiguration`。

## 3. レイヤー①: index EXPLAIN 所見

計測スクリプト: `webapi/src/test/java/xyz/dgz48/tasks/webapi/perf/IndexExplainMeasurementIT.java`(`@Disabled`、手動実行)。Hibernate Filter が注入する `tenant_id = ?` を明示的に含めた「MySQL が実際に実行する形」の native SQL を `EXPLAIN` する。

### 3.1 重要な注意 — 計測ボリューム由来の見かけの full scan

**全タスクを 1 テナントに偏らせて seed すると `tenant_id = ?` が非選択的になり、`tasks` が `type=ALL`(full scan)になる**。これは「full scan が最適」というオプティマイザの正しい判断であって index 不足ではない。他テナントにも実データを積んで **マルチテナント実配分**(対象テナント ≒ 全体の 2〜3%)にすると、`tenant_id` 先頭の複合 index が選択され `type=ref` に変わる。

例: `GET /api/tasks`(認可述語 + `ORDER BY due_date` + LIMIT)

| seed | type | key | rows |
|---|---|---|---|
| 単一テナント偏在(≒全行が対象テナント) | `ALL` | NULL | 300 |
| **マルチテナント実配分(対象 ≒ 2.6%)** | **`ref`** | **`idx_tasks_tenant_due`** | 120 |

以下 3.2 の所見は **マルチテナント実配分**(対象テナント 120 タスク / 全体 ≒ 4,620 タスク × 16 テナント)で採取した。

### 3.2 代表クエリの EXPLAIN(マルチテナント実配分)

| # | endpoint / query | type | 使用 key | rows | 所見 |
|---|---|---|---|---|---|
| A-1 | `GET /api/tasks` 一覧(認可述語, ORDER BY due_date, LIMIT) | ref | `idx_tasks_tenant_due` | 120 | tenant で絞り込み後に OR 認可述語を filter。関係者 EXISTS は `task_stakeholders` PK を eq_ref |
| A-2 | `GET /api/tasks` COUNT | ref | `idx_tasks_tenant_due` | 120 | 同上 |
| A-3 | `GET /api/tasks` keyword `LIKE '%kw%'` | ref | `idx_tasks_tenant_due` | 120 | tenant で絞り込み後、LIKE は index 不可のため filter(**record-only**、§4) |
| A-4 | `GET /api/tasks` overdueCount | range | `idx_tasks_tenant_due` | 60 | `due_date < ?` を range スキャン |
| B-1 | `GET /api/tasks/{id}/stakeholders`(native JOIN) | ref / eq_ref | `PRIMARY`(×3) | 3 | `ts` は PK `(task_id,user_id)` 前方一致、`users` は PK eq_ref。`ORDER BY added_at` で filesort(数件、無視可) |
| C-1 | dashboard findOverdue | index_merge | union(`fk_tasks_owner`,`fk_tasks_assignee`) | 5 | `(owner OR assignee)` を index_merge。所有/担当タスクは少数のため良好 |
| C-2 | dashboard findToday | ref | `idx_tasks_tenant_due` | 2 | `due_date = ?` |
| C-3 | dashboard findUpcoming | index_merge | union(owner,assignee) | 5 | C-1 と同型 |
| C-4 | dashboard findCompletedToday | index_merge | union(owner,assignee) | 5 | 同型。filesort(数件) |
| D-1 | `GET /api/dashboard/summary`(EXISTS) | ref | `idx_tasks_tenant_due` | 120 | tenant 絞り込み後に OR + 関係者 EXISTS(PK eq_ref) |
| D-2 | `GET /api/tenant/dashboard/summary` | ref | `idx_tasks_tenant_due` | 120 | `visibility IN (...)` を filter |
| D-3 | `〃` countActiveMembers(`user_tenants`) | ALL | NULL | 33 | 小テーブル(テナント内メンバー数)。full scan が最適。at scale は `idx_ut_tenant (tenant_id,user_id)` が絞り込む(§3.3) |
| E-1 | `GET /api/tenant/users`(`user_tenants`, ORDER BY joined_at) | ALL | NULL | 33 | 同上。`joined_at` は無 index のため filesort。メンバー数は有界のため許容 |
| F-1 | `GET /api/tenants`(admin, status + name LIKE) | index | `PRIMARY`(backward) | 17 | 小テーブル。`name LIKE '%kw%'` は index 不可(**record-only**) |
| F-2 | `〃` countTasksByTenantIds(native, IN batch) | ref | `idx_tasks_tenant_deleted` | 120 | covering(`Using index`) |

### 3.3 `user_tenants`(D-3 / E-1)の `type=ALL` について

本計測では他テナントの **メンバーシップ行を積んでいない**ため `user_tenants` が単一テナント偏在となり `type=ALL`(33 行)になる。§3.1 の `tasks` と同じ機構で、マルチテナント実配分では `idx_ut_tenant (tenant_id, user_id)` により `tenant_id = ?` が絞り込まれる。`GET /api/tenant/users` の `ORDER BY joined_at` は `joined_at` が index に無いため filesort が残るが、**テナント内メンバー数は有界(数十〜数百)** のため実害は小さい。重大事象ではないため index 追加は見送る(不要な書込コストを避ける)。

## 4. record-only(#769 では是正しない、別 Issue 候補)

- **keyword 部分一致**: `tasks.title/description` の `LIKE '%kw%'`(A-3)と `tenants.name` の `LIKE '%kw%'`(F-1)は前方ワイルドカードのため B-Tree index を使えない。tenant 絞り込み後の filter で現状は許容範囲だが、タスク数が多いテナントで悪化しうる。前方一致化(`kw%`)/ MySQL FULLTEXT / 生成列 + index 等の改善は **別 Issue 候補**(ADR-0039 §5)。

## 5. 是正スコープの判断(ADR-0039 §4)

- **新規 index 追加**: なし。既存の `tasks` 7 本(`tenant_id` 先頭複合)+ `task_stakeholders` PK/idx + `user_tenants` idx で主要 endpoint は充足。`type=ALL` は小テーブル(`user_tenants`)または計測ボリューム由来の見かけ上のもので、大テーブル `tasks` に実害のあるフルスキャンは無い。
- **Flyway migration**: 不要(スキーマ変更なし)。

## 6. リリース前チェックリスト([#770](https://github.com/win2cot/tasks-webapi/issues/770))への引き継ぎ

- [x] 主要 endpoint のクエリ本数を回帰テストで固定(N+1 退行の CI 検知)
- [x] 主要 endpoint 代表クエリの `EXPLAIN` 所見を記録、重大事象(大テーブルの `type=ALL`)は無し
- [ ] keyword `LIKE '%kw%'` 改善 — 別 Issue 化を検討(タスク数の多いテナントが現れた段階で優先度再評価)
- [ ] 本番トラフィックでの N+1 継続観測は ADR-0029 の datasource-micrometer 計装(OTLP span)に委譲

## 7. 再現手順

```bash
# N+1 回帰ガード(CI で常時実行)
./gradlew :webapi:test --tests "*QueryCountIT"

# index EXPLAIN 計測(@Disabled を外して手動実行)
#   IndexExplainMeasurementIT の @Disabled を一時的に外し:
./gradlew :webapi:test --tests "*IndexExplainMeasurementIT"
```

## 参考

- [ADR-0039](../adr/0039-performance-tuning-measurement-and-regression.md) — 本メモの方式
- [ADR-0029](../adr/0029-performance-measurement-and-diagnostics.md) §6.1 — ランタイム JDBC 計装(datasource-micrometer)
- [2026-06-05 Hibernate Filter 性能所見](2026-06-05-hibernate-filter-perf.md)(#316)— filter ON/OFF の先行計測(本メモは認可述語・ダッシュボード・関係者・テナント系へ範囲拡大)
