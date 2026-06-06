# ADR-0016: ログイン時の初期テナント自動選択ポリシー

- **Status**: Accepted
- **Date**: 2026-06-06
- **Deciders**: win2cot
- **Related Issues**: #309

## 1. コンテキスト(Context)

マルチテナント設計では JWT に `tenant_id` を含めず、リクエストの `X-Tenant-Id` ヘッダでテナントを指定する方針を採用している(設計規約 §3.1)。

しかしログイン直後や `X-Tenant-Id` ヘッダを省略した業務 API リクエストでは、サーバー側で「どのテナントで動作するか」を決定する必要がある。複数テナントに所属するユーザーの場合、どのテナントを初期値にするかのルールを明文化しなければならない。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: joined_at ASC（最初に参加したテナントを選択）

- 概要: `user_tenants.joined_at` の昇順で最初のレコードを初期テナントとする
- 利点: 追加列不要、既存スキーマで実装可能、決定論的で一意
- 欠点: 「よく使うテナント」とは限らない。切替 UI(C-1〜C-3)で明示的に変更可能なので許容できる
- リスク: joined_at が同一の場合に順序が不定定になりうる(実運用では発生しにくい)

### 選択肢 B: last_accessed_at（最終アクセス順）

- 概要: `user_tenants` に `last_accessed_at` 列を追加し、最後にアクセスしたテナントを選択
- 利点: ユーザーの最近の利用状況を反映
- 欠点: スキーマ変更・更新コストが発生。テナント切替 API(C-1〜C-3)未実装段階では `last_accessed_at` の更新タイミング設計が複雑化する
- リスク: スキーマ変更が他機能の実装スコープに波及する

### 選択肢 C: user_tenants.is_default フラグ

- 概要: `user_tenants` に `is_default BOOLEAN` 列を追加し、ユーザーが明示的に選択したテナントを記録
- 利点: ユーザーの意図を最も忠実に反映
- 欠点: UX 設計(デフォルト設定画面)と API(C-1〜C-3)が実装されるまで意味をなさない。初期値管理が複雑

## 3. 決定(Decision)

**採用**: 選択肢 A — `joined_at ASC` で最初に参加したテナントを初期テナントとして選択する

## 4. 理由(Rationale)

- スキーマ変更ゼロ: 既存の `joined_at` 列だけで決定論的に選択できる
- Sprint 1 スコープに収まる: テナント切替 UI(C-1〜C-3)が完成すれば、初期テナントの「使い勝手の悪さ」は解消される。将来的に選択肢 B/C へ移行することを妨げない
- シンプルで検証しやすい: 単純な ORDER BY joined_at ASC LIMIT 1 相当であり、単体テストと IT で網羅しやすい

## 5. 影響(Consequences)

### 良い影響(Positive)

- `TenantContextFilter` がヘッダ未指定でも業務 API を処理できるようになる
- C-1〜C-3(テナント切替)が未実装でもフロントエンドがヘッダを省略可能になる

### 悪い影響・制約(Negative)

- 複数テナント所属ユーザーは「最初に参加したテナント」が自動選択される。意図しないテナントで動作する可能性があるが、C-1〜C-3 で切り替え可能
- 0 件の場合は 403 を返す。テナント未参加ユーザーが業務 API にアクセスすると拒否される

### 既存ドキュメント・規約への波及

- `docs/specs/設計規約.md` §3.1 のテナントコンテキスト設定フロー記述を更新する必要がある

## 6. 実装メモ(Implementation Notes)

- `UserTenantsResolverService#resolveInitial(Long userId)` が `Optional<TenantMembership>` を返す
- `TenantContextFilter` が `X-Tenant-Id` ヘッダ未指定かつ認証済みかつ非免除パスの場合に本サービスを呼び出す
- 免除パス: `/api/auth/**`, `/api/tenants/**`, `/actuator/**`
- 0 件 → 403 `E_FORBIDDEN`
- 1 件以上 → 先頭(joined_at 最小)を `TenantContext` にセット

## 7. 参考リンク(References)

- Issue #309
- 設計規約 §3.1 (テナントコンテキスト)
- ADR-0010 (Hibernate Filter によるテナント分離)
