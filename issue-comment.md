## 着手宣言

Issue #469 の実装を開始します。

## 実装方針

### 実装スコープ
- **ページング / ソート**: page / size + dueDate / priority / createdAt / updatedAt / title、デフォルト `dueDate,asc`
- **絞込フィルタ**: status(複数)/ ownerId / assigneeId / visibility
- **認可フィルタ**: TaskAuthorizationDomainService を SSOT とした visibility 3 役割評価(ADR-0005)
- **マルチテナント分離**: Hibernate Filter(ADR-0010)+ deleted_at IS NULL
- **スコープ外(受理するが未実装明示)**: targetDate / includeOverdue / keyword / priority 絞込

### 主要な設計判断
1. **TaskStakeholderJpaEntity** を新規追加(task_stakeholders テーブルの JPA エンティティ。TenantFilteredEntity を継承)
2. **認可クエリ**: Criteria API + EXISTS サブクエリで visibility の 3 役割評価を DB レベルで実行。N+1 を回避。
3. **priority ソート**: DB ENUM の文字列順(HIGH < LOW < MEDIUM)では意味が通らないため Criteria API の CASE 式で HIGH=1 / MEDIUM=2 / LOW=3 の順序に変換
4. **UserSummary 取得**: ListTasksUseCase は Page<Task>を返し、TaskController がページ内の ownerIds / assigneeIds を一括取得(N+1 防止)してレスポンスに埋め込む
5. **overdueCount**: 参照認可フィルタを適用した別 count クエリで算出(today = Clock から取得)

### テスト計画
- ListTasksUseCaseTest: Mockito 単体テスト
- ListTasksIT: Testcontainers 統合テスト(visibility 3 パターン × 3 役割、クロステナント漏洩、フィルタ、ページング、ソート)
- TaskControllerWebMvcTest: listTasks エンドポイントの WebMVC テスト追加
