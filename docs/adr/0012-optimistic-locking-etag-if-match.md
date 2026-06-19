# ADR-0012: 楽観ロック方式に ETag / If-Match を採用する

- **Status**: Accepted
- **Date**: 2026-06-01
- **Deciders**: win2cot
- **Tags**: api, persistence, concurrency

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [選択肢 A: tasks.version + JPA @Version + ETag / If-Match + 412](#選択肢-a-tasksversion--jpa-version--etag--if-match--412)
  - [選択肢 B: updated_at を ETag 源にする](#選択肢-b-updated_at-を-etag-源にする)
  - [選択肢 C: 適用しない(後勝ち上書き受容)](#選択肢-c-適用しない後勝ち上書き受容)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

Issue #329 で PATCH /api/tasks/{id} 化が確定し、ダッシュボードの行内編集 6 項目(ステータス / 期限 / 担当者 / 優先度 / タイトル / 説明)を任意組み合わせで部分更新できるようになった。マルチテナント SaaS で複数ユーザーが同じタスクを同時に触ることが現実的な MVP 利用形態であり、別項目を同時に編集した場合の後勝ち上書きを防ぐ手段を ADR として確定する必要がある。

Issue #150 派生 5(Sprint 0 UX 原則)で `tasks.completed_at` 追加の Flyway migration が同時期に動く想定であり、同じ migration window に楽観ロック用カラムを相乗りさせるなら追加コストが小さい。

関連:

- Issue #332(本 ADR の起票元)
- Issue #329(PATCH /api/tasks/{id} 化確定)
- Issue #150(Sprint 0 UX 原則、`tasks.completed_at` DDL 派生)
- ADR-0011(独自 record ErrorResponse、ErrorCode enum 拡張先)

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: tasks.version + JPA @Version + ETag / If-Match + 412

- 概要: `tasks.version BIGINT NOT NULL DEFAULT 0` を新設、Entity に `@Version`、GET 系レスポンスで `ETag: W/"<version>"` 返却、PATCH / DELETE 要求は `If-Match` ヘッダ必須、不一致は 412 Precondition Failed を返す。
- 利点:
  - JPA `@Version` が update 時に自動でインクリメント + 楽観ロック例外を投げる、実装が定型的
  - ms 粒度のような不可視競合窓がなく、整合性の意味が単調(version 整数)
  - `version` は楽観ロック専用カラムなので、`updated_at`(監査・表示用)と責務が分離される
- 欠点:
  - DDL 変更が必要(ただし #150 派生 5 と同 migration window で吸収可)
  - 全 GET レスポンスに ETag ヘッダを乗せる責務がフロントとサーバ両方に発生
- リスク・未知数:
  - ErrorCode enum に `E_PRECONDITION_FAILED` を追加する ADR-0011 派生 PR が必要
  - 将来 PUT を復活させる場合(現状 #329 で廃止)も同様に If-Match 必須化する想定

### 選択肢 B: updated_at を ETag 源にする

- 概要: `updated_at` の Unix ms timestamp を ETag 源に使う(`ETag: W/"<unix_ts_ms>"`)。DDL 変更不要。
- 利点: 既存カラムで賄える、Flyway migration 不要
- 欠点:
  - ms 粒度の競合不可視窓が残る(同一 ms 内の連続更新で後勝ち)
  - サーバ時計の skew で予期せぬ 412 が起きうる
  - `updated_at` は監査・表示用と楽観ロック用で責務が混在する
  - JPA `@Version` のような言語側サポートが弱く、リポジトリ層に手動チェックが必要
- リスク・未知数: なし

### 選択肢 C: 適用しない(後勝ち上書き受容)

- 概要: 楽観ロックは適用せず、screen-flow §5 の警告表示で軽減、本格対応は Phase 2 以降に持ち越す。
- 利点: Sprint 0 / Phase 1 のスコープ最小化、実装ゼロ
- 欠点:
  - マルチテナント SaaS で行内編集 6 項目の同時編集を前提にすると、MVP 後に同時編集ロストが顕在化しやすい
  - 監査ログ ADR(別 ADR、未起票)との整合がとれず、後発で乗せ替えコストが高い
- リスク・未知数: なし

## 3. 決定(Decision)

**採用**: 選択肢 A(tasks.version + JPA @Version + ETag / If-Match + 412)

- write 系 endpoint(PATCH / DELETE)で `If-Match` ヘッダを必須化する
- POST(新規作成)は対象 resource 不在のため `If-Match` 不要
- **単件 GET**: `ETag: W/"<version>"` レスポンスヘッダで返却する
- **一覧 GET の各要素**: HTTP ヘッダに複数リソースの ETag を個別設定できないため、レスポンスボディの各タスクオブジェクトに `"version"` フィールドを含める形で返却する(クライアントはこの値を使い PATCH / DELETE 時に `If-Match: W/"<version>"` を組み立てる)
- 不一致は 412 Precondition Failed + ErrorResponse(`E_PRECONDITION_FAILED`)
- `tasks.version BIGINT NOT NULL DEFAULT 0` を Flyway migration で追加し、Issue #150 派生 5 の `tasks.completed_at` 追加と同 migration に相乗りさせる

## 4. 理由(Rationale)

- マルチテナント SaaS の MVP として、行内編集 6 項目の同時編集ロストを許容するリスクは高すぎる(案 C 不採用)
- `updated_at` ベース(案 B)の ms 競合窓と clock skew リスクは、ETag の役割としては不安定。`version` 整数の単調性のほうが運用上の予測可能性が高い
- #150 派生 5 で同時期に `tasks.completed_at` 追加 migration が走るため、`version` カラム追加の DDL コストは限界費用としてほぼゼロ
- JPA `@Version` は Spring Data JPA / Hibernate でデファクトの実装、ライブラリの境界を越えない
- ETag / If-Match は HTTP 標準で、OpenAPI / クライアントツールチェーン(axios interceptor 等)のサポートが厚く、フロント側の実装コストも定型化できる

## 5. 影響(Consequences)

### 良い影響(Positive)

- 行内編集 6 項目で異なるユーザが別カラムを同時編集した場合に、後勝ち上書きが防げる(412 で再取得を促す)
- `version` 専用カラムによって、楽観ロックと監査(`updated_at` / 監査ログ ADR 別建て)の責務分離が明確
- 将来の write 系 endpoint 追加時も、ETag / If-Match の運用パターンが定式化されるため設計コストが下がる

### 悪い影響・制約(Negative)

- 全 GET レスポンスで ETag ヘッダ生成 + フロント側で `If-Match` 付与の手間が増える
- 412 ハンドリングのフロント UX(再取得 + 差分マージ or 警告)を別途定義する必要がある
- `tasks` テーブルの DDL に追加カラムが増える(限界費用は小さいが、migration 履歴は残る)

### 既存ドキュメント・規約への波及

- `docs/specs/設計規約.md`: 楽観ロック方式 + write 系 endpoint で `If-Match` 必須の方針を追記
- `docs/specs/コーディング規約.md`: JPA Entity への `@Version` 付与方針 + UseCase 層からの ETag ヘッダ返却方針を追記
- `docs/specs/基本設計書.md`: `tasks` テーブル定義に `version BIGINT NOT NULL DEFAULT 0` を追加
- `api/openapi.yaml`(v1.5.x): GET レスポンスヘッダに `ETag`、PATCH / DELETE に `If-Match` parameter + 412 response 追加; 一覧レスポンスのタスクオブジェクトに `version` フィールド追加
- ADR-0011: ErrorCode enum に `E_PRECONDITION_FAILED` を追加(派生 PR)

## 6. 実装メモ(Implementation Notes)

着手順序(派生 Issue):

1. **DDL + Entity + UseCase**: `tasks.version` カラム追加 Flyway migration(`tasks.completed_at` と同 migration に相乗り)+ Entity に `@Version` + Domain `Task` の version 取り回し + UpdateTaskUseCase / DeleteTaskUseCase の OptimisticLockException ハンドリング
2. **ErrorCode 拡張**: ADR-0011 ErrorCode enum に `E_PRECONDITION_FAILED` を追加 + ExceptionHandler で 412 マッピング
3. **OpenAPI 反映(v1.5.x)**: GET 系レスポンスに `ETag` ヘッダ、PATCH / DELETE に `If-Match` parameter(必須)+ 412 response + ErrorResponse 例
4. 設計規約 / コーディング規約 / 基本設計書 の追記 PR(上記 3 件と同 sprint で消化)

検証:

- 統合 IT(UpdateTaskIT): If-Match なし → 400(`E_VALIDATION`、ADR-0011 既存 ErrorCode)、If-Match 古い version → 412(`E_PRECONDITION_FAILED`)、If-Match 一致 → 200 + 新 ETag
- 同時実行 IT: 2 リクエスト並走時の片方が 412 を受けることを確認

## 7. 参考リンク(References)

- Issue #332(本 ADR 起票)
- Issue #329(PATCH /api/tasks/{id} 化確定)
- Issue #150(Sprint 0 UX 原則、`tasks.completed_at` DDL)
- ADR-0011(独自 record ErrorResponse)
- RFC 7232(HTTP Conditional Requests)

---

## Amendment 1: 楽観ロック適用基準の追記(2026-06-19, Issue #680)

### 経緯

`PATCH /api/tasks/{id}/status` が If-Match required で実装されていたが、OpenAPI 未定義・フロント未送出のため常に 400 になることが判明。事故分析の結果、本 endpoint は楽観ロック対象外と確定した。

### 適用基準(§scope)

楽観ロック(If-Match)は **「呼び出し側が観測していない変更を上書きしうる write」にのみ適用する**。

判定マトリクス:

| 観点 | 対象とする | 対象外とする |
|---|---|---|
| フィールド範囲 | 多フィールドを束ねる PATCH / PUT | 単一フィールドの絶対値 SET |
| 上書き実害 | 他人の未編集列を巻き込む lost update が起きる | 同フィールドのみ最後勝ちで自然 |
| 頻度 / UX | 低頻度 or 412 再試行コストが許容範囲 | 高頻度で 412 が誤検知摩擦のみを生む |

決定マトリクス(現行 endpoint):

| Endpoint | 適用 | 理由 |
|---|---|---|
| `PATCH /api/tasks/{id}` | ✅ 対象 | 多フィールド read-modify-write; lost update リスク高 |
| `DELETE /api/tasks/{id}` | ✅ 対象 | 論理削除; 低頻度・高実害 |
| `PATCH /api/tasks/{id}/status` | ❌ 対象外 | 単一フィールド SET; last-write-wins で自然 |
| `PATCH /api/tasks/{id}/visibility` | ✅ 対象 | stakeholders 同時置換を含む; 低頻度・高実害 |

### 実装変更(Issue #680)

- `ChangeTaskStatusUseCase.execute` から `ifMatchVersion` 引数と version 一致チェックを撤去
- `TaskController.changeStatus` から `@RequestHeader(IF_MATCH)` を撤去
- バイパス実装: `TaskRepository.saveStatus` を JPQL UPDATE(`version` 列非更新)で実装し、JPA `@Version` チェックをバイパスして last-write-wins を実現
- `updatedAt` はアプリ層で `LocalDateTime.now(clock)` を渡してアップデート
