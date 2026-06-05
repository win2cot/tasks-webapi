# ADR-0015: Token Lifespan Policy (Access / Refresh)

- **Status**: Accepted
- **Date**: 2026-06-05
- **Deciders**: win2cot
- **Related Issues**: #304

## Context

Sprint 1 A-2 にてトークンリフレッシュ機構を整備するにあたり、access token と refresh token の寿命を明示的に設計する必要がある。

既存の `keycloak/realm-export/tasks-realm.json` には値が存在していたが、根拠が文書化されていなかった。

## Decision

Keycloak Realm の token lifespan を下記の値に定める。

| パラメータ | 設定値 | 意味 |
|---|---|---|
| `accessTokenLifespan` | 300 秒 (5 分) | access token の有効期限 |
| `clients[].attributes.access.token.lifespan` | 300 秒 (5 分) | タスクAPIクライアント個別設定(realm 値を上書き) |
| `ssoSessionIdleTimeout` | 28800 秒 (8 時間) | SSO セッションアイドル TTL = refresh token のアイドル有効期限 |
| `ssoSessionMaxLifespan` | 36000 秒 (10 時間) | SSO セッション最大寿命 = 継続使用でも最大 10 時間でセッション失効 |

## Rationale

### Access Token (5 分)

- access token は短命にすることでトークン漏洩時の被害を最小化する。
- 5 分はセキュリティリスクと UX のバランスとして一般的な選択肢。
- frontend は keycloak-js の `updateToken(60)` ポーリング(30 秒間隔)で期限切れ前に自動更新するため、ユーザーは期限を意識しない。

### Refresh Token / SSO Session (8 時間 idle, 10 時間 max)

- `ssoSessionIdleTimeout` = 8 時間: 一般的な業務時間帯(8〜10 時間の勤務)において、短時間の離席でセッションが切れないよう 8 時間に設定。
- `ssoSessionMaxLifespan` = 10 時間: 残業時間を考慮し 10 時間を上限とする。10 時間を超えた継続利用は再ログインを要求し、長期的なセッション維持によるリスクを抑制する。
- 業務外(夜間・休日)にセッションが自動失効するため、盗まれたセッションの悪用ウィンドウを限定できる。

### 旧値 (`ssoSessionIdleTimeout: 1800`) の問題点

変更前の 1800 秒(30 分)は短すぎ、30 分の操作空白(会議・離席等)でセッションが切れ、ユーザーが再ログインを強いられていた。UX 低下とサポートコスト増の原因となるため 8 時間に延長する。

## Consequences

- **正**: 通常の業務セッション内でリフレッシュ期限切れによる強制ログアウトが発生しない。
- **正**: access token を 5 分に抑えることで漏洩リスクを局所化できる。
- **負**: `ssoSessionMaxLifespan` の 10 時間は「ログインしたまま翌日も使える」シナリオを防げない可能性があるが、idle チェック(8 時間)が先に効くため実害は限定的。
- **将来**: Phase 2 以降でデバイス管理・強制ログアウト機能(#167)が整備されたら、個別セッション失効でより細粒度の制御が可能になる。
