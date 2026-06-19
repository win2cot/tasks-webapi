# ADR-0034: アクティブテナントのクライアント所有と `/api/auth/me` からの `activeTenantId` 削除

- **Status**: Accepted
- **Date**: 2026-06-20
- **Deciders**: win2cot
- **Related Issues**: #682(PR #681 / Issue #663 起因)
- **Amends**: [ADR-0016](0016-initial-tenant-selection-policy.md)(`joined_at ASC` による初期テナント解決は「業務 API で `X-Tenant-Id` 省略時の防御的フォールバック」という元のスコープのまま存続。本 ADR はその自動解決が `/api/auth/me` には及ばないことを明確化し、アクティブテナントの SSOT をクライアントに置くことを新たに決定する。ADR-0016 の決定そのものは不変)

## 1. コンテキスト(Context)

PR #681(Issue #663)で「テナント選択がリロードで消えて `index.html` にループする」不具合を、`web/js/api.js` の `X-Tenant-Id` を `sessionStorage` に永続化することで修正した。これは単純なフロントのバグではなく、アクティブテナントの「正本(SSOT)をどこに置くか」が未決のまま実装が進んだことの露出である。

コードを確認した現状は次のとおり:

- `MeController` は受け取った `X-Tenant-Id` ヘッダの値をそのまま `MeResponse.activeTenantId` に詰めて返すだけで、解決も保存もしない(`/api/auth/me` は `TenantContextFilter` の免除パスのため ADR-0016 の自動解決は走らない。クラスコメントにも「自動解決 ADR-0016 は /me では行わない」と明記済み)。
- `SwitchTenantUseCase`(`POST /api/auth/tenants/{id}/select`)は所属検証のみで、アクティブテナントを永続化しない(204 を返すのみ)。
- サーバには「ユーザーが現在どのテナントで操作中か」を保持する列(例: `users.active_tenant_id`)が存在しない。ADR-0016 は意図的にステートレスで、毎リクエスト `joined_at ASC` で決定論的に導出する設計。

したがって `MeResponse.activeTenantId` は**循環している**: クライアントが `X-Tenant-Id` を送る → サーバがそれを `activeTenantId` として echo する → クライアントがそれを見て遷移を決める。クライアントは最初から値を知っており、サーバは情報を一切足していない。フロントでの実際の用途も `index.js` / `tasks.js` の遷移ゲート(`activeTenantId !== null` なら `tasks.html`、`null` ならテナント選択 UI)専用である。

つまり**サーバが「持っていないアクティブテナント状態」を返そうとしていること自体が仕様複雑化の発生源**であり、リロードループも「echo か自動解決か」という論点も、この幻フィールドを巡るものだった。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: クライアント SSOT を正式採用し、`/me` の `activeTenantId` echo を維持

- 概要: `X-Tenant-Id` 常時送出 + `sessionStorage` 保持を規約に明記。`/me` は従来どおりヘッダ値を echo する。ADR-0016 は業務 API の防御的フォールバックに位置づけ直す。
- 欠点: echo の循環構造が API 契約に残り続ける。サーバがアクティブテナントを持つかのような見かけが解消されない。

### 選択肢 B: ADR-0016 を `/api/auth/me` に適用

- 概要: `/me` でも自動解決を効かせ、`activeTenantId` を `joined_at ASC` で返す。ループは発生源で消え、PR #681 の単一テナント自動選択(F-3)も冗長になる。
- 欠点: `/me` が常に `joined_at ASC`(=最初に参加したテナント)を返すため、ユーザーが 2 件目に切り替えてリロードすると 1 件目に戻る。**複数テナント所属ユーザーの切替永続性が壊れる**。これを補うには結局クライアント側の永続化(`sessionStorage`)が必要で、サーバ自動解決を「正本」とは言えない。

### 選択肢 C(採用): `/api/auth/me` 契約から `activeTenantId` を削除

- 概要: `/me` はサーバが実際に持つ事実のみ返す = ユーザー識別情報 + 所属テナント一覧(`joined_at` 昇順)。`activeTenantId` フィールドは API 契約から削除する。「今どのテナントか」は完全にクライアントの関心事とし、クライアントが `sessionStorage` の選択とテナント一覧から決定する。
- 利点: 幻フィールドが消え、echo の循環と「echo か自動解決か」の論点が丸ごと不要になる。複数テナント初回の挙動(自動先頭選択 vs セレクタ表示)が純粋なフロント UX ポリシーに落ち、サーバ契約から複雑さが消える。
- 欠点: `MeResponse` から required フィールドを 1 つ削除する破壊的変更。ただし本番未稼働(Sprint 0 着手前)のため実害はない。フロントの遷移ゲートとテストの作り直しが必要。

## 3. 決定(Decision)

**採用**: 選択肢 C — `/api/auth/me` のレスポンスから `activeTenantId` を削除する。

- `/api/auth/me` はユーザー識別情報と所属テナント一覧(`joined_at` 昇順)のみを返す。
- アクティブテナントの SSOT はクライアント(`sessionStorage`)に置く。クライアントが「選択中テナント」を保持し、業務 API リクエスト時に `X-Tenant-Id` ヘッダで送出する。
- ADR-0016 の `joined_at ASC` 自動解決は、**業務 API で `X-Tenant-Id` が省略された場合の防御的フォールバックという元のスコープのまま据え置く**。`/api/auth/me` には適用しない。

## 4. 理由(Rationale)

- **概念の整合**: サーバが保持しない状態を API で返さない。`/me` は「サーバが知っている事実(誰で・どのテナントに所属しているか)」だけを返し、「今どれを選んでいるか」というクライアント状態を返さない。
- **複雑さの発生源を断つ**: echo の循環、`/me` 免除パスの特別扱い、案 A/案 B の論点がすべて消える。
- **切替永続性の正しい担保**: 複数テナント所属ユーザーの「今どれを選んでいるか」を覚えられるのはクライアントだけ(サーバはステートレス)。PR #681 の `sessionStorage` 永続化を「対症療法」ではなく「設計上の正本」として位置づけ直す。
- **ADR-0016 を壊さない**: `joined_at ASC` は業務 API のフォールバックとして引き続き有効。将来 `last_accessed_at` / `is_default` 列でサーバ側にアクティブテナントを持たせる拡張(ADR-0016 §2 の選択肢 B/C)に移行する余地も残る。その場合は本 ADR を改めて amend する。

## 5. 影響(Consequences)

### 良い影響(Positive)

- API 契約が「サーバが持つ事実」に純化され、`/me` の意味が明確になる。
- 複数テナント初回の UX(自動先頭選択か明示選択か)はフロント側ポリシーとして変更でき、サーバ改修を伴わない。
- リロードループは、クライアントが `sessionStorage` + 一覧から決定論的にアクティブテナントを導けるため発生源で解消する。

### 悪い影響・制約(Negative)

- `MeResponse` から `activeTenantId` を削除する破壊的変更(OpenAPI・DTO・フロント・テスト)。本番未稼働のため互換性の実害はない。
- フロントの遷移ゲート(`me.activeTenantId !== null`)を、クライアント側のアクティブテナント解決ロジックに置き換える必要がある。

### クライアント側アクティブテナント解決(フロント実装の指針)

`/me` 取得後、クライアントは次の順でアクティブテナントを決定する:

1. `sessionStorage` の `tenantId` があり、かつ `me.tenants` に含まれる → それを採用。
2. `me.tenants` が 0 件 → テナント未所属(セルフサインアップ導線 / 業務 API は 403)。
3. `me.tenants` が 1 件 → 先頭を自動採用し `sessionStorage` に保存(現行 F-3 をサーバ非依存で踏襲)。
4. `me.tenants` が複数で有効な保存値なし → テナント選択 UI を表示(現行 UX を踏襲)。複数初回に自動で先頭を採るかは将来のフロント UX 判断に委ねる。

`me.tenants` の順序は `joined_at` 昇順を契約とし、ADR-0016 の業務 API フォールバックと「先頭 = `joined_at` 最小」の規則を一致させる。

### 既存ドキュメント・規約への波及

- `docs/specs/設計規約.md` §3.3 にアクティブテナントの SSOT(クライアント `sessionStorage`)と `/me` の仕様を追記。
- `docs/specs/基本設計書.md` の「アクティブテナントは Cookie または X-Tenant-Id ヘッダで送出」記述、および §5.3.1 `GET /api/auth/me` のレスポンス例を更新。
- `api/openapi.yaml` の `MeResponse` から `activeTenantId` を削除。

## 6. 実装メモ(Implementation Notes)

- 実装は本 ADR を実装する follow-up Issue で追跡する(#682 の blocked-by)。スコープ: `MeResponse` / `MeController` / `api/openapi.yaml` から `activeTenantId` 削除、`web/js/index.js` ・ `web/js/tasks.js` ・ `web/js/components/app-tenant-switcher.js` の遷移ゲートをクライアント解決ロジックに置換、`MeControllerWebMvcTest` ・ `MeIT` の `activeTenantId` アサーション更新。
- `SwitchTenantUseCase` / `POST /api/auth/tenants/{id}/select` は所属検証 API として現状維持(サーバにアクティブテナントを保存しない方針は不変)。
- `TenantContextFilter` と `UserTenantsResolverService` は変更しない(ADR-0016 の業務 API フォールバックは存続)。

## 7. 参考リンク(References)

- Issue #682 / PR #681 / Issue #663
- [ADR-0016](0016-initial-tenant-selection-policy.md)(初期テナント自動選択ポリシー)
- [ADR-0010](0010-tenant-isolation-hibernate-filter.md)(Hibernate Filter によるテナント分離)
- 設計規約 §3.3(マルチテナント)
