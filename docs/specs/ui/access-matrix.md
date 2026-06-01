# ロール × 画面 権限マトリクス

## 改訂履歴

| バージョン | 日付 | 主な変更 | 担当 |
|---|---|---|---|
| 1.0 | 2026-06-01 | 初版作成(Issue #151)。コア 4 画面群 × 3 ロール × 3 visibility の権限マトリクスと 404 / 403 / 401 応答ポリシーを表形式で整理 | 開発チーム |

## 1. 本書の位置付け

本書は **ロール × 画面 × 操作 × visibility** の組み合わせで、誰が何を見られて何を編集できるか、認可違反時の HTTP 応答が何かを一枚にまとめたものである。フロントエンド実装が「リンク非表示 / ボタン disabled / 画面アクセス時の挙動」を判断する際の参照書として位置付ける。

- **認可ロジックの正本**: `TaskAuthorizationDomainService`(Domain 層、`xyz.dgz48.tasks.webapi.task.domain`)。本書は同サービスの結論を画面側に翻訳したミラーであり、矛盾が生じた場合は実装と基本設計書 §6.2.1 を優先する。
- **設計上の SSOT**: 基本設計書 §6.2.1(タスク認可)/ §6.2.2(ダッシュボード集計の認可スコープ)/ §6.2.3(認可違反時の応答ポリシー)、ADR-0005(3 役割評価モデル)、設計規約 §2.3(認可違反 HTTP ステータス強制ルール)。
- **画面遷移と visibility 効きどころ**: `docs/specs/ui/screen-flow.md` §3 / §4(V1〜V5 の注釈タグ)を参照。本書は画面遷移ではなく **操作粒度の認可判定** を担う。
- **本書が扱わないもの**: ハイファイモック、項目別の入力バリデーション、UI コンポーネントの形状(行内編集セルの見た目、ドロワー幅など)、API スキーマ。

## 2. 用語と前提

### 2.1 ロール表記

| 設計書表記 | 内部コード値 | 保持場所 | 主な役割 |
|---|---|---|---|
| SaaS Admin | `APP_ADMIN` | Keycloak realm role(本システム DB に保持しない) | テナント運営 API のみ操作可能。業務 API には触れない(基本設計書 §6.2.1) |
| Tenant Admin | `TENANT_ADMIN` | `user_tenants.role` | テナント運営権限(招待・ロール管理・監査ログ参照・S-15 集計参照)を持つ。業務タスクに対しては Member と同じ 3 役割評価で判定(ADR-0005) |
| Member | `MEMBER` | `user_tenants.role` | テナント所属の一般ユーザー。業務タスクに対する権限は 3 役割評価(所有者 / 担当者 / 関係者)で決まる |

> **2 軸直交**(基本設計書 §6.2): プラットフォーム軸(`APP_ADMIN`)とテナント軸(`TENANT_ADMIN` / `MEMBER`)は包含ではなく直交する。兼務ユーザー(`APP_ADMIN` + `user_tenants` 行あり)が業務 API を呼び出した場合は、テナント軸ロールで評価する。`APP_ADMIN` 単独で `user_tenants` に行を持たないユーザーが業務 API を呼び出した場合は 403。

### 2.2 タスク visibility 区分(ADR-0005)

| visibility | 参照可能ユーザー |
|---|---|
| `TENANT` | 同テナント全員 |
| `STAKEHOLDERS` | 所有者 ∪ 担当者 ∪ `task_stakeholders` 登録者 |
| `PRIVATE` | 所有者 ∪ 担当者 |

> `PRIVATE` の参照対象に **担当者** を含めるのは ADR-0005 で確定した拡張。3 役割評価(所有者・担当者・関係者)の一貫性を確保するため、所有者だけでなく担当者にも参照を開いている。

### 2.3 認可違反時の HTTP ステータス(基本設計書 §6.2.3 / 設計規約 §2.3 強制ルール)

| 違反種別 | HTTP | 例外クラス | 監査ログ action |
|---|---|---|---|
| 未認証(JWT 不正・期限切れ) | **401** Unauthorized | Spring Security 既定 | `LOGIN_FAILED` |
| 参照系(GET)で参照不可 | **404** Not Found | `TaskNotViewableException` 等 | `VIEW_DENIED` |
| 更新系(PUT / PATCH / DELETE)で権限不足 | **403** Forbidden | `TaskOwnershipException` 等 | `EDIT_DENIED` / `STATUS_CHANGE_DENIED` / `VISIBILITY_CHANGE_DENIED` / `STAKEHOLDER_EDIT_DENIED` / `DELETE_DENIED` |
| テナント越境アクセス | **403** Forbidden | `TenantBoundaryViolationException` | `TENANT_CROSSED` |
| ロールベース不可(SaaS Admin 専用 API 等) | **403** Forbidden | Spring Security `AccessDeniedException` | `ROLE_BASED_DENIED` |

> 「参照不可なら 404」の根拠は **NIST AC-4(情報フロー保護)**。リソースの存在自体を漏らさない。

### 2.4 コア 4 画面群

Issue #149 で確定した「コア 4 画面群」を以下の単位で本書のマトリクスに登場させる(画面 ID は基本設計書 §3.2 と整合)。

| # | 画面群 | 構成画面 ID | URL パス |
|---|---|---|---|
| 1 | ダッシュボード | S-03 | `/` |
| 2 | タスク一覧 | S-04 | `/tasks` |
| 3 | タスク詳細 | S-05(参照) / S-06(登録・編集) | `/tasks/{id}` / `/tasks/new` / `/tasks/{id}/edit` |
| 4 | テナント管理 | S-13(一覧) / S-14(詳細・状態切替) | `/admin/tenants` / `/admin/tenants/{id}` |

> S-15 テナント運営者向けダッシュボードは Sprint 0 Out of Scope(#149)のため本書では扱わない。基本設計書 §3.3.4 / §6.2.2.2 を参照。

## 3. 画面アクセス可否マトリクス(ロール × 画面)

「画面そのものへ到達できるか」を表す。visibility による行レベル可視性は §4、操作別の認可は §5 で扱う。

| 画面群 | 画面 ID | SaaS Admin 単独 | Tenant Admin | Member | 兼務(SaaS Admin + テナント所属) |
|---|---|---|---|---|---|
| ダッシュボード | S-03 | **不可**(業務 API 不可 / 403) | 可 | 可 | 業務テナント選択中は可 |
| タスク一覧 | S-04 | **不可** | 可 | 可 | 業務テナント選択中は可 |
| タスク詳細 | S-05 / S-06 | **不可** | 可(行レベル可視性は §4) | 可(行レベル可視性は §4) | 業務テナント選択中は可 |
| テナント管理 | S-13 / S-14 | 可 | **不可**(`hasRole('APP_ADMIN')` 不通過 / 403) | **不可** | `/admin/*` を開いた瞬間に SaaS Admin 動線へ切り替わる |

> **「不可」の HTTP 応答先**:
> - SaaS Admin 単独が業務画面(S-03 / S-04 / S-05 / S-06)を直接 URL で叩いた場合 → API 側で 403(`ROLE_BASED_DENIED`)
> - Tenant Admin / Member が SaaS Admin 画面(S-13 / S-14)を直接 URL で叩いた場合 → API 側で 403(`ROLE_BASED_DENIED`)
> - 未認証で任意画面を叩いた場合 → 401(`LOGIN_FAILED`)

> **画面遷移としての非直結**: 業務動線(S-03 / S-04 / S-05 / S-06)と SaaS Admin 動線(S-13 / S-14)は画面遷移としては直結しない。screen-flow.md §3 図では両動線の間に **点線(業務 API 不可、§6.2.1)** のみを引き、UI 上のナビゲーションリンクは張らない。兼務ユーザーは URL 直接遷移または画面外ナビ(別タブ等)で切り替える。

## 4. 行レベル可視性(visibility による絞り込み)

S-03 リスト 4 セクション / S-04 タスク一覧 / S-05 タスク詳細では §2.2 の visibility に従って各タスクの参照可否を判定する(screen-flow.md §4 V1 / V2 / V5)。

| visibility | SaaS Admin 単独 | Tenant Admin | Member | 補足 |
|---|---|---|---|---|
| `TENANT` | 行レベル以前に画面到達不可 | 同テナント全員(常に参照可) | 同テナント全員(常に参照可) | テナント越境は別判定で 403 |
| `STAKEHOLDERS` | 同上 | 所有者・担当者・関係者のときのみ | 所有者・担当者・関係者のときのみ | Tenant Admin であっても役割を持たなければ参照不可(ADR-0005) |
| `PRIVATE` | 同上 | 所有者・担当者のときのみ | 所有者・担当者のときのみ | Tenant Admin の業務タスク特権は ADR-0005 で撤廃済 |

> **判定の実装**: 一覧は `Hibernate Filter` で `tenant_id` 絞り込み後、UseCase 層で §6.2.1 ルールを適用。`STAKEHOLDERS` 判定は `StakeholderRepository.findTaskIdsByUser` で事前一括フェッチして N+1 を回避(基本設計書 §6.2.1 末尾の実装注意)。

> **行レベル不可の応答**:
> - 一覧画面(S-03 / S-04): 認可フィルタ通過後の集合のみ返るため、見えないタスクは **そもそも結果に含まれない**(404 / 403 の表面化なし)
> - 詳細画面(S-05)を直接 URL で叩いた場合: 認可フィルタ不通過なら **404 Not Found**(`VIEW_DENIED`、NIST AC-4 整合)

## 5. 操作別 認可マトリクス(タスク操作)

3 役割(所有者 / 担当者 / 関係者)× 操作の組み合わせで認可可否と HTTP ステータスを定める。Tenant Admin / Member は **テナント軸ロールに関わらず**、自身がタスクに対して持つ役割でのみ判定される(ADR-0005)。

| 操作 | API | 認可主体 | 不可時 HTTP | 行内編集 | ドロワー編集 | 関連 |
|---|---|---|---|---|---|---|
| タスク参照(単票) | `GET /api/tasks/{id}` (A-12) | §6.2.1 参照ルール(visibility に応じた所有者 ∪ 担当者 ∪ 関係者 ∪ テナント全員) | 404 (`VIEW_DENIED`) | — | — | 関係者一覧の参照(`GET /api/tasks/{id}/stakeholders`)は本ルール通過後に一律可 |
| タスク一覧参照 | `GET /api/tasks` (A-11) | テナント所属 + §6.2.1 参照フィルタ | 一覧から除外(404 / 403 表面化なし) | — | — | S-03 / S-04 |
| 新規作成 | `POST /api/tasks` (A-13) | テナント所属(Tenant Admin / Member) | 403 (`ROLE_BASED_DENIED`、SaaS Admin 単独時)| — | ✓(右ドロワー / フルページ S-06) | 所有者は作成者自身として登録 |
| 部分更新(タイトル / 説明 / 期限 / 担当者 / 優先度) | `PATCH /api/tasks/{id}`(A-14 後継、Issue #329 で部分更新化確定済。`api/openapi.yaml` §5.1 API 表は追従予定) | **所有者のみ** | 403 (`EDIT_DENIED`) | ✓(タイトル / 期限 / 担当者 / 優先度 / 説明はポップオーバー) | ✓ | screen-flow.md §5.2 / 基本設計書 §3.3.2 |
| ステータス変更 | `PATCH /api/tasks/{id}/status` (A-16) | **所有者 ∪ 担当者** | 403 (`STATUS_CHANGE_DENIED`) | ✓ | ✓ | 完了遷移時は S-03 でセクション間移動(screen-flow.md §5.1) |
| 公開範囲変更 | `PATCH /api/tasks/{id}/visibility` (A-17) | **所有者のみ** | 403 (`VISIBILITY_CHANGE_DENIED`) | — | ✓ | 誤操作リスクが大きいためドロワー内のみ |
| 関係者の追加 | `POST /api/tasks/{id}/stakeholders` (A-19) | **所有者 ∪ 担当者** | 403 (`STAKEHOLDER_EDIT_DENIED`) | — | ✓ | 指名自体は visibility 非依存(ADR-0005 §3.4) |
| 関係者の削除 | `DELETE /api/tasks/{id}/stakeholders/{userId}` (A-20) | **所有者 ∪ 担当者** | 403 (`STAKEHOLDER_EDIT_DENIED`) | — | ✓ | 同上 |
| 論理削除 | `DELETE /api/tasks/{id}` (A-15) | **所有者のみ** | 403 (`DELETE_DENIED`) | — | ✓(確認ダイアログ) | 物理削除は MVP 不実装(#167 / Phase 2) |

> **UI 出し分けと多層防御**: 認可不可ユーザーには行内編集セルを **無効表示**(クリック不可、ツールチップで理由表示)し、ボタンは非表示または `disabled`。クライアント UI を信用せずサーバ側でも必ず同等判定を実行する(NIST 多層防御)。

## 6. 操作別 認可マトリクス(テナント運営 / SaaS Admin 操作)

業務タスク以外の運営系操作。テナント運営(Tenant Admin)と SaaS Admin の責務境界を明確にする。

| 操作 | API | 認可主体 | 不可時 HTTP | 補足 |
|---|---|---|---|---|
| テナント一覧参照 | `GET /api/tenants` (A-04) | **SaaS Admin (`APP_ADMIN`)** | 403 (`ROLE_BASED_DENIED`) | S-13 |
| テナント名更新 | `PUT /api/tenants/{id}` (A-06) | **SaaS Admin** | 403 | テナント名のみ。状態切替は A-26、プラン変更は Phase 2 |
| テナント単体取得 | `GET /api/tenants/{id}` (A-25) | **SaaS Admin** は任意テナント / **一般ユーザー**は自所属テナントのみ | 403 / 404 | S-14 で使用。一般ユーザーが他テナントを叩いた場合は 404(基本設計書 §6.2.3 NIST AC-4 整合) |
| テナント状態切替(`ACTIVE` ↔ `SUSPENDED`) | `PATCH /api/tenants/{id}/status` (A-26) | **SaaS Admin** | 403 | S-14、`TENANT_SUSPENDED` / `TENANT_REACTIVATED` を監査ログに記録 |
| プラットフォーム集計 | `GET /api/platform/metrics` (A-27) | **SaaS Admin** | 403 | 業務動線からは到達しない |
| テナント内ユーザー招待 / ロール管理 | `/api/tenant/users/*` | **Tenant Admin** | 403 (`ROLE_BASED_DENIED`、Member / SaaS Admin 単独時) | S-13 / S-14 ではなく、別途テナント運営画面で扱う(本書スコープ外) |
| 監査ログ参照 | `/api/audit-logs` | **Tenant Admin** | 403 | テナント運営権限 |
| テナント運営者ダッシュボード集計 | `GET /api/tenant/dashboard/summary` (A-28) | **Tenant Admin** | 403 | S-15 用(本書スコープ外、基本設計書 §6.2.2.2 / §3.3.4) |

> **`X-Tenant-Id` 不要枠**: テナント運営 API(`/api/tenants` 配下の SaaS Admin 操作、`/api/platform/*`、`/api/auth/me`、`/api/auth/logout`)は `X-Tenant-Id` ヘッダ不要(基本設計書 §5.2)。それ以外の業務 API はすべて必須。

## 7. 認可境界の代表シナリオ

設計レビュー時に判断が分かれやすい境界ケースを列挙する(NIST AC-4 整合の挙動を確認)。すべての違反は監査ログに記録される(設計規約 §2.3 / 基本設計書 §6.2.3)。

| # | シナリオ | 期待される応答 | 監査ログ action | 根拠 |
|---|---|---|---|---|
| 1 | **Tenant Admin が同テナント内の他人の PRIVATE タスクに `GET /api/tasks/{id}` でアクセス** | **404 Not Found** | `VIEW_DENIED` | Tenant Admin の業務タスク特権は ADR-0005 で撤廃。PRIVATE の参照は所有者 ∪ 担当者のみ。NIST AC-4 整合で 403 ではなく 404(存在秘匿) |
| 2a | **Member が自分が所属しない他テナントの `X-Tenant-Id` をヘッダに指定して `GET /api/tasks/{id}` を叩いた** | **403 Forbidden** | `TENANT_CROSSED` | `TenantContextFilter` が `X-Tenant-Id` を `user_tenants` と照合して非所属テナントを検出 → `TenantBoundaryViolationException`(基本設計書 §6.2 層別責務表 / §6.2.3 / 設計規約 §2.3)。Hibernate Filter より前段で拒否される |
| 2b | **Member が自テナントの `X-Tenant-Id` をヘッダに指定したまま、他テナントの `tasks.id` を推測して `GET /api/tasks/{id}` を叩いた** | **404 Not Found** | `VIEW_DENIED` | `TenantContextFilter` は通過するが、`Hibernate Filter` が `WHERE tenant_id = :ctx` を自動付与するためクエリ結果が空 → タスク不存在として 404(NIST AC-4 整合、存在秘匿)。これは「越境」ではなく「自テナント内で見つからない」扱い |
| 3 | **SaaS Admin 単独(`APP_ADMIN` のみ、`user_tenants` に行なし)が `GET /api/tasks` を呼び出した** | **403 Forbidden** | `ROLE_BASED_DENIED` | SaaS Admin は業務 API に触れない(基本設計書 §6.2.1「SaaS Admin の扱い」)。`X-Tenant-Id` の有無に関わらず Spring Security レベルで拒否 |
| 4 | **Tenant Admin が他人が所有者の `STAKEHOLDERS` タスク(関係者でも担当者でもない)を直接 URL で開いた** | **404 Not Found** | `VIEW_DENIED` | 役割を持たない Tenant Admin は §6.2.1 参照ルール不通過。Tenant Admin であっても通常の参照認可フィルタが適用される(ADR-0005) |
| 5 | **担当者が自身に割り当てられたタスクの公開範囲を `PATCH /api/tasks/{id}/visibility` で変更しようとした** | **403 Forbidden** | `VISIBILITY_CHANGE_DENIED` | 公開範囲変更は所有者のみ。担当者はステータス変更と関係者編集は可能だが、visibility 変更は不可(基本設計書 §6.2.1 表) |
| 6 | **関係者が `STAKEHOLDERS` タスクのステータスを `PATCH /api/tasks/{id}/status` で変更しようとした** | **403 Forbidden** | `STATUS_CHANGE_DENIED` | ステータス変更は所有者 ∪ 担当者のみ。関係者は参照と関係者一覧確認まで(基本設計書 §6.2.1 表) |
| 7 | **未認証ユーザーが `/admin/tenants` を直接 URL で開いた** | **401 Unauthorized** | `LOGIN_FAILED` | JWT 検証失敗が最先(`TenantContextFilter` / `@PreAuthorize` より前段)。401 後の 403 マッピングは発生しない |
| 8 | **Tenant Admin が `/admin/tenants` を直接 URL で開いた**(`APP_ADMIN` を持たない場合) | **403 Forbidden** | `ROLE_BASED_DENIED` | `hasRole('APP_ADMIN')` 不通過。`@PreAuthorize` でメソッドレベル認可拒否 |
| 9 | **所有者が自タスクを `visibility = PRIVATE` に変更した後、過去に登録した関係者レコード** | レコードは **CASCADE 削除**(`STAKEHOLDER_PURGED` を `detail` に削除件数記録) | `STAKEHOLDER_PURGED` | PRIVATE では関係者リストが参照権限に影響しないため、不要なリスト残留を防ぐ(基本設計書 §4.2.5 / 要件定義書 §3.4.4) |
| 10 | **JWT 期限切れの状態で行内編集を行った** | **401 Unauthorized**(クライアントはリフレッシュトークンで再認証 → リトライ) | `LOGIN_FAILED` | 期限切れは Spring Security フィルタで検出、401 を返す(基本設計書 §6.2.3 / 設計規約 §2.3) |

## 8. UI への翻訳ガイドライン

本書のマトリクスをフロントエンドが「リンク表示 / ボタン disabled / 画面アクセス時の挙動」に翻訳する際の指針。

- **画面アクセス不可の経路**: ナビゲーションから当該画面へのリンクを **非表示** にする。URL 直接アクセスは API 側で 403 / 404 を返すので、フロント側でも `403` を受けたら案内画面へ、`404` を受けたらリスト画面へ戻すなど、フォールバック動線を用意する。
- **操作不可の項目**: 行内編集セルは **無効表示**(クリック不可、ツールチップに理由)。ドロワー内のフォーム項目は `disabled` 属性を付ける。ボタンは **非表示** または `disabled`(操作意図がないため非表示の方が UX 上は望ましいが、共有 UI 部品の事情で `disabled` を選択する場合は理由を tooltip 化する)。
- **多層防御の原則**: クライアント UI を信用せず、サーバ側で必ず同等判定を実行する(NIST 多層防御)。クライアント側 UI 出し分けは UX の改善目的であり、認可の境界ではない。
- **エラー応答の表示**: API から返る 401 / 403 / 404 は 6 フィールド構造(`timestamp` / `status` / `error` / `code` / `message` / `path`、設計規約 §2.4 / ADR-0011 `ErrorResponse` record)。UI は `code`(`E_UNAUTHORIZED` / `E_FORBIDDEN` / `E_NOT_FOUND` 等)で分岐し、`message` を表示用文言として使用する。

## 9. 関連書類

- 基本設計書 v1.5.0 §6.2.1 / §6.2.2 / §6.2.3(認可ロジック・集計スコープ・違反応答)
- 設計規約 v1.4 §2.3(認可違反 HTTP ステータス強制ルール)
- ADR-0005(3 役割評価モデル / Tenant Admin の業務タスク特権撤廃)
- ADR-0010(Hibernate Filter による `tenant_id` 自動付与 / テナント越境検出の実装方針)
- ADR-0011(`ErrorResponse` record + `ErrorCode` enum)
- `docs/specs/ui/screen-flow.md` v1.0(画面遷移と visibility 効きどころ V1〜V5)
- `api/openapi.yaml` v1.4.5(各操作の 401 / 403 / 404 応答仕様)
- 親 Issue: #149(Sprint 0 画面設計 初版)
- 本書発端 Issue: #151(ロール × 画面 権限マトリクス整理)

## 10. 整合性に関する確認

本書は基本設計書 §6.2.2 / §6.2.3 と矛盾しないように作成した。具体的には以下の対応がある。

- §6.2.2.1 個人視点ダッシュボードの集計対象 = §4 行レベル可視性で「TENANT は全員 / STAKEHOLDERS は所有者・担当者・関係者 / PRIVATE は所有者・担当者」に同期。
- §6.2.3 認可違反応答ポリシー(401 / 404 / 403)= §2.3 と §5 / §6 の不可時 HTTP 列で完全に同一。
- §6.2.3 監査ログ action 標準値 8 種(`VIEW_DENIED` / `EDIT_DENIED` / `DELETE_DENIED` / `STATUS_CHANGE_DENIED` / `VISIBILITY_CHANGE_DENIED` / `STAKEHOLDER_EDIT_DENIED` / `TENANT_CROSSED` / `ROLE_BASED_DENIED`)= §2.3 / §5 / §6 / §7 で出現する action 値と一致。

矛盾を検出した場合は、まず実装(`TaskAuthorizationDomainService`)と基本設計書 §6.2.1〜§6.2.3 を SSOT として確認し、本書を追従させる。
