# ADR-0039: 性能チューニングの測定・N+1 回帰固定・index レビュー方針

- **Status**: Accepted
- **Date**: 2026-06-26
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: performance, testing, persistence, observability

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [レイヤー1: 発掘(ランタイム/ローカル測定)](#レイヤー1-発掘ランタイムローカル測定)
  - [レイヤー2: 回帰固定(テスト)](#レイヤー2-回帰固定テスト)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

[Issue #769](https://github.com/win2cot/tasks-webapi/issues/769)(Phase 1 / Sprint 5)は MVP リリース前に明らかな性能ボトルネックを是正する。**定量 SLO は設けず**、「測定 → 是正 → 所見記録」のサイクルで対応する方針が確定している(win2cot 決定 2026-06-25)。本 ADR は、この Issue を回すうえで判断を要する **測定の進め方・N+1 回帰の固定手段・index レビューの完了基準・是正スコープの線引き** を確定する(claude-automation = 議論先行)。

[ADR-0029](0029-performance-measurement-and-diagnostics.md) でアプリの計測基盤(Spring Boot 4 公式 OpenTelemetry スターター / OTLP / Application Signals)は確定済みである。本 ADR は ADR-0029 を前提とし、その「使い方」と「テストでの回帰固定の仕組み」を補完する位置づけ(ADR-0029 を supersede しない)。特に **ADR-0029 §6.1 は JDBC 計装として `datasource-micrometer-spring-boot:2.2.1` を採用済み(Native Image 両立を確認済み)** であり、本 ADR はこれを前提・維持する。

現状認識(2026-06-26 コード確認):

- 「明らかな N+1」とされた主要 3 箇所は **既に対策済み**である。
  - タスク一覧 / ダッシュボードの owner・assignee 解決 — `loadUserMap` が `findAllById` で一括取得(`TaskController` / `DashboardController`)。
  - 関係者一覧(`GET /api/tasks/{id}/stakeholders`)— `users` を JOIN した単一 native query(`TaskStakeholderJpaRepository#findWithUserInfoByTaskIdAndTenantId`)。
  - ダッシュボード summary の STAKEHOLDERS 認可判定 — `findVisibleSummaryRows` の `EXISTS` 相関サブクエリ(ループ外 1 クエリ)。
- index も `tasks` に `tenant_id` 先頭の複合 index 7 本、`task_stakeholders` は PK `(task_id, user_id)` + `idx_ts_user_tenant` を保有。
- したがって #769 の主眼は「新規 N+1 の発掘・是正」よりも、**現状に N+1 が無いことの検証 → 回帰ガード(クエリ本数固定)の追加 → EXPLAIN ベースの index 妥当性確認** に寄る。

制約:

- テストは Testcontainers MySQL 8.4 が前提(コーディング規約、H2 不可)。
- 実行バイナリは GraalVM Native Image([ADR-0008](0008-graalvm-native-image.md))。ただしテストは JVM モードで実行されるため、テスト専用の計測ライブラリは Native 互換の検証対象外。
- `@Profile` 禁止(コーディング規約 §20.2)。ON/OFF・環境差は外部化プロパティで吸収。
- MVP 直前であり、#769 では ADR-0029 が所掌するランタイム計装の実装作業を重複して持ち込まず、テスト層の回帰機構に集中したい。

## 2. 検討した選択肢(Options Considered)

性能測定は **① 発掘(ランタイム / ローカルでボトルネックを探す)** と **② 回帰固定(是正後にクエリ本数をテストで固定する)** の 2 レイヤーに分かれ、それぞれ適した手段が異なる。

### レイヤー1: 発掘(ランタイム/ローカル測定)

#### 選択肢 D: ADR-0029 の OTel(ローカル otel-lgtm)+ Hibernate `show_sql`(採用案)

- 概要: 既定の計測基盤(ADR-0029)をローカルで起動し、endpoint レイテンシと自動計装の span を確認。N+1 の有無は `show_sql`(+ `format_sql`)の出力で目視確認する。
- 利点: 追加依存ゼロ。ADR-0029 と一直線。発掘は一時的作業のためローカルで十分。
- 欠点: ローカル一回性の発掘手段。継続的な観測は ADR-0029 のランタイム計装(datasource-micrometer 含む)が別途担う。

#### 選択肢 E: datasource-micrometer のランタイム span を発掘に用いる(ADR-0029 §6.1 で採用済み)

- 概要: ADR-0029 §6.1 で JDBC 計装として `datasource-micrometer-spring-boot:2.2.1` が **採用済み**。これが実装済みであれば、その `connection` / `query` span を発掘に活用できる。
- 利点: 本番でも N+1 兆候を trace 上で継続観測できる(ADR-0029 の OTLP 経路に統合済、Native Image 両立も ADR-0029 §6.1 で確認済)。
- 欠点・リスク: ADR-0029 の計装実装(#584-587 系)が未完了の段階では発掘に使えない。#769 として計装の実装そのものはスコープ外。

### レイヤー2: 回帰固定(テスト)

#### 選択肢 A: Hibernate Statistics

- 概要: `hibernate.generate_statistics=true`(テスト時のみ)で `Statistics#getPrepareStatementCount()` 等を取得し、本数差分を assert。
- 利点: 追加依存ゼロ(Hibernate 内蔵)。
- 欠点: 取得できるのは **本数のみ**。どの SQL が反復したかは出ず、N+1 検出時の原因特定に `show_sql` 併用が要る。

#### 選択肢 B: datasource-proxy(test scope)(採用案)

- 概要: テスト scope で proxy 化した `DataSource` を注入し、実行クエリを記録・カウント(`QueryCountHolder` 等)。
- 利点: **本数 + 実 SQL 文字列** が取れ、N+1 回帰テストが素直に書ける(同型クエリの反復を直接検出)。テスト限定・手動配線のため Spring / SB バージョン非依存(JDBC レベルの proxy)。テストは JVM 実行のため Native 互換は無関係。
- 欠点: テスト用 `DataSource` 差し替えの初期配線が要る。テスト間で `QueryCountHolder` のリセットが必要(運用ルールで吸収)。

#### 選択肢 C: datasource-micrometer をテストでも利用

- 概要: 選択肢 E をテストでも使い、meter registry を読んで assert。
- 欠点: メトリクスからの本数 assert は煩雑で、回帰テストには不向き。ランタイム依存をテストの正否に巻き込む。

## 3. 決定(Decision)

1. **回帰固定(②)= 選択肢 B(datasource-proxy, test scope)** を採用する。クエリ本数を assert する小さなテストヘルパを用意し、Testcontainers 統合テストで N+1 回帰を固定する。**既に対策済みの箇所(`loadUserMap` 一括解決・関係者 JOIN・summary `EXISTS`)にも回帰ガードを張る** — 回帰固定こそが本 Issue の主目的である。
2. **発掘(①)= 選択肢 D(ADR-0029 の OTel + `show_sql`)を主に用いる**。datasource-micrometer による JDBC 計装は **ADR-0029 §6.1 で採用済み**(本 ADR は維持し supersede しない)。その計装が実装済みであれば発掘に span を活用し、未実装の段階では `show_sql` で代替する。**#769 のスコープに datasource-micrometer の計装実装そのものは含めない**(ADR-0029 の実装 Issue で扱う)。テスト層の回帰固定(②)には datasource-micrometer を用いない(選択肢 C は不採用)。
3. **index レビューの完了基準**: 主要 endpoint の代表クエリを MySQL 8.4 上で `EXPLAIN` し、所見(`type` / 使用 index / `rows`)を docs に記録する。`type=ALL`(フルスキャン)等の重大事象は是正する。「合否」ではなく **所見記録 + 重大事象の是正** をもって完了とする。
4. **是正スコープの線引き**: N+1 と欠損 index は #769 内で是正する(fetch join / `@EntityGraph` / Flyway index 追加)。**測定で見つかった重大ボトルネックも、可能な範囲で #769 内で是正する**。ただし read model 化・キャッシュ導入等の **アーキテクチャ大改修で PR が肥大化する場合に限り、別 Issue に切り出してよい**(逃げ道)。
5. **keyword 検索(LIKE 部分一致)の扱い**: 現状挙動を `EXPLAIN` で所見記録するのみとし、前方一致化 / FULLTEXT 等の改善は **別 Issue 候補**に切り出す(#769 では是正しない)。
6. **定量 SLO は設けない**(#769 / ADR-0029 の方針踏襲)。p95 等の閾値判定は行わず、測定 → 是正 → 所見記録のサイクルで運用する。

## 4. 理由(Rationale)

- 発掘と回帰固定は目的が異なる(前者は一時的探索、後者は恒久的ガード)。レイヤーを分けることで、各々に最小の道具を割り当てられる。
- N+1 回帰テストは「本数」だけでなく「どの SQL が反復したか」が見えると、テストも是正も書きやすい。datasource-proxy はこの点で Hibernate Statistics に勝る。捨てた利点は「依存ゼロ」だが、テスト scope の単一依存は許容範囲。
- datasource-micrometer は ADR-0029 §6.1 で既に採用済みのランタイム計装であり、本 ADR で再決定・追加するものではない。#769 の発掘は計装の実装状況に依存せず進められるよう、ローカルの OTel + `show_sql` を主手段とする。
- 現状すでに主要 N+1 は対策済みのため、本 Issue は「壊さないための回帰ガード」の価値が最も高い。スコープを検証 + ガード + index 所見に寄せるのが実態に合う。
- SLO を設けない判断は、単独運用・MVP 段階で定量目標の維持コストに見合う負荷試験基盤が無いため。重大事象は所見ベースで個別是正する。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 主要 endpoint のクエリ本数がテストで固定され、将来の実装変更による N+1 退行を CI で検知できる。
- 測定・是正の判断基準(完了基準・スコープ線引き)が明文化され、#769 着手後の判断ブレが減る。
- 回帰固定は test scope の datasource-proxy のみで、ランタイム/本番イメージ(ADR-0029 の計装構成)に影響を与えない。

### 悪い影響・制約(Negative)

- datasource-proxy のテスト配線(proxy `DataSource` + `QueryCountHolder` リセット)を新規に整備・維持する必要がある。
- 本番での継続的な N+1 可視化は ADR-0029 のランタイム計装(datasource-micrometer)に依存するため、その計装が実装されるまで発掘は `show_sql` 主体になる。
- SLO 不在のため「どこまでが重大か」は所見ベースの定性判断に依存する。

### 既存ドキュメント・規約への波及

- 追加 index が出た場合、`docs/specs/設計規約.md` §3.1 の Flyway 命名(`V{semver}_{seq}__{snake_case}.sql`、Sprint 2 以降は新規 `V1.0.x` ファイル)に従う。
- 測定所見メモを `docs/` 配下(例: `docs/reviews/` 配下の性能所見メモ)に残し、リリース前チェックリスト(#770)へ引き継ぐ。
- テストの新パターン(クエリ本数 assert ヘルパ)はコーディング規約のテスト節に追補する余地があるが、本 ADR を SSOT として参照する形で足りる(規約改訂は任意)。

## 6. 実装メモ(Implementation Notes)

- **主要 endpoint(測定・回帰固定の対象、実装時に最終確定)**: `GET /api/tasks`(一覧)/ `GET /api/tasks/{id}` / `GET /api/tasks/{id}/stakeholders` / `GET /api/dashboard` / `GET /api/dashboard/summary` / `GET /api/tenant/dashboard/summary` / `GET /api/tenant/users` / 管理者向けテナント一覧。
- **datasource-proxy 配線**: テスト scope のみ。`@TestConfiguration` で `ProxyDataSourceBuilder` により実 `DataSource` をラップ。各テストの計測区間前に `QueryCountHolder.clear()`、区間後に `QueryCountHolder.getGrandTotal()` を assert。artifact バージョンは導入時に最新安定版を確認して pin。
- **テストデータ規模**: N+1 検出は **小フィクスチャ**(関係者 2〜3 / タスク数十件)で十分(本数は N に依存しないため)。index 妥当性確認の `EXPLAIN` は **代表ボリュームの seed** で行う(規模は実装時に決定)。
- **ダッシュボードのクエリ本数所見**: 1 画面で 4 セクション + summary + user 解決 ≒ 6 クエリになる構造は N+1 ではないが、集約余地の有無を `EXPLAIN` 所見と併せて記録する(是正は重大度次第)。
- **発掘手順**: ローカルで OTel(otel-lgtm)+ `show_sql` / `format_sql` を有効化し、主要 endpoint を叩いて SQL 反復とレイテンシを確認。
- **PR 分割**: ① 回帰テスト基盤 + 既存箇所のガード、② index 所見 + 必要な Flyway 追加、③ 所見メモ docs。重大ボトルネック是正が大改修になる場合のみ別 Issue。
- **CI**: 既存 `:webapi:check`(Testcontainers)で回帰テストが回ること、green を確認。

## 7. 参考リンク(References)

- [Issue #769](https://github.com/win2cot/tasks-webapi/issues/769) — 性能チューニング(本 ADR の対象)
- [ADR-0029](0029-performance-measurement-and-diagnostics.md) §6.1 — 計測基盤(OTel / Application Signals)。JDBC 計装に `datasource-micrometer-spring-boot:2.2.1` を採用済み(本 ADR が前提とする)。
- [ADR-0008](0008-graalvm-native-image.md) — GraalVM Native Image
- [ADR-0010](0010-tenant-isolation-hibernate-filter.md) — テナント分離(Hibernate Filter)
- `docs/specs/設計規約.md` §3.1 — Flyway マイグレーション命名
- `docs/specs/要件定義書.md` §4.1 — 性能要件(参考。本 ADR では SLO 化しない)
