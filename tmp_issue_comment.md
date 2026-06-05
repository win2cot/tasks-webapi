## 着手宣言 + 実装方針

### 対象スコープ

ADR-0012 に基づく楽観ロック基盤実装。

### 実装方針

1. **Flyway migration** — `V1.0.0_03__add_tasks_version.sql`: `tasks.version BIGINT NOT NULL DEFAULT 0` 追加
2. **JPA Entity** — `TaskJpaEntity` に `@Version Long version` フィールド追加
3. **Domain** — `Task` に `version` フィールド追加・コンストラクタ更新
4. **Repository Adapter** — `toDomain()` で version 引き渡し、`save()` で version ミスマッチ検知(→ `ObjectOptimisticLockingFailureException`)
5. **TaskResponse** — `version` フィールド追加(ADR-0012 §3)
6. **TaskController** — `changeStatus` に `@RequestHeader("If-Match")` 必須化・ETag レスポンスヘッダ付与、GET にも ETag 付与
7. **ChangeTaskStatusUseCase** — `ifMatchVersion` パラメータ追加、バージョン不一致 → `ObjectOptimisticLockingFailureException`
8. **TaskExceptionHandler** — `IllegalArgumentException`(不正 If-Match フォーマット) → 400 追加
9. **テスト** — WebMvcTest 更新 + 統合 IT(`ChangeTaskStatusIT`: 一致/不一致/なし) + 同時実行 IT

### 依存関係

- `ErrorCode.E_PRECONDITION_FAILED` は既に追加済み(#418)
- `TaskExceptionHandler` の `ObjectOptimisticLockingFailureException` → 412 ハンドラも既に存在

着手します。
