# Runbook: 認証・トークンリフレッシュ

最終更新: 2026-06-05 | Issue #304

## 概要

本システムは Keycloak OIDC を使用し、access token (5分) / refresh token (SSO session: 最大10時間) の2段構えで認証を管理する。

---

## 正常フロー

```
[frontend]                   [tasks-webapi]       [Keycloak]
   |                               |                   |
   |-- POST /realms/tasks/token -->|------------------>|
   |<-- access_token (5min) -------|<------------------|
   |    refresh_token (8h idle)    |                   |
   |                               |                   |
   |-- GET /api/tasks ------------>|                   |
   |   Authorization: Bearer <AT>  |                   |
   |                               |-- JWK verify -->  |
   |<-- 200 OK --------------------|                   |
   |                               |                   |
   | (access token expiring soon)  |                   |
   |-- keycloak.updateToken(60) -->|-----> Keycloak refresh endpoint
   |<-- new access_token ----------|<----- 200 OK      |
```

---

## Fail シナリオ一覧

### シナリオ 1: Access Token 期限切れ(通常リフレッシュ)

**症状**: API が `401 Unauthorized` を返す。

**原因**: access token の 5 分 TTL が切れた。

**対応(frontend 自動)**:
1. `api.js` の `request()` が 401 を検知する。
2. `Auth.refreshToken()` を呼び出し、`keycloak.updateToken(-1)` で強制リフレッシュを試みる。
3. 新しい access token を取得し、元のリクエストを 1 回リトライする。
4. リトライが成功すれば透過的に完了。

**対応(リトライも失敗した場合)**: シナリオ 2 または 3 を参照。

---

### シナリオ 2: Refresh Token(SSO Session)期限切れ

**症状**: `Auth.refreshToken()` 呼び出し時に Keycloak から `400 Bad Request` (`error: invalid_grant`)。frontend はログインページへリダイレクト。

**原因**: SSO セッションの idle timeout(8時間)または max lifespan(10時間)を超過した。

**ユーザー向けメッセージ**:
> 「セッションが期限切れになりました。再度ログインしてください。」

**対応**:
- ユーザーはログインページで再認証する。
- 問題が頻発する場合は ADR-0015 の token lifespan 設定を見直す。

---

### シナリオ 3: Refresh Token 失効(明示的ログアウト・管理者操作)

**症状**: `400 Bad Request` (`error: invalid_grant`) — シナリオ 2 と同じ応答。

**原因**:
- 別デバイスでログアウト済み
- Keycloak 管理コンソールでセッションを強制失効させた

**対応**: シナリオ 2 と同様、再ログイン。

---

### シナリオ 4: Keycloak 障害(トークン検証失敗)

**症状**: tasks-webapi が起動しているにもかかわらず、全ユーザーで `401 Unauthorized` が返り続ける。

**原因**: Keycloak が停止または JWK エンドポイント(`/realms/tasks/protocol/openid-connect/certs`)が到達不能になっている。Spring Security は JWK を定期キャッシュしているため、**キャッシュが有効な間は一時的に認証が継続する**。キャッシュ TTL(デフォルト 5 分)を超えると検証失敗が始まる。

**確認手順**:
```bash
# Keycloak ヘルスチェック
curl http://<keycloak-host>:18080/health/ready

# JWK エンドポイント確認
curl http://<keycloak-host>:18080/realms/tasks/protocol/openid-connect/certs
```

**対応**:
1. ECS タスクログ / CloudWatch でエラーを確認する。
2. Keycloak サービスを再起動または復旧させる。
3. tasks-webapi の JWK キャッシュは自動的に更新される(再起動不要)。

---

### シナリオ 5: access token が有効だが `InvalidBearerTokenException` で 401

**症状**: 特定ユーザーのみ `401` が返る。token の署名・期限は問題なし。

**原因**: `TasksJwtAuthenticationConverter` がトークンの `sub` クレームに対応するユーザーを DB に見つけられない。

**確認手順**:
```
# ログ例
ERROR ... InvalidBearerTokenException: User not found for provided sub claim
```

**対応**:
1. Keycloak の `sub` と `users.oidc_sub` が一致しているか確認する。
2. ユーザーが DB に存在しない場合はプロビジョニングフローの不具合を調査する(Issue #304 / ADR-0006 参照)。

---

## 関連ドキュメント

- [ADR-0015: Token Lifespan Policy](../adr/0015-token-lifespan-policy.md)
- [ADR-0006: Keycloak User Storage SPI](../adr/0006-keycloak-user-storage-spi.md)
- Keycloak Realm 設定: `keycloak/realm-export/tasks-realm.json`
- OpenAPI 認証セクション: `api/openapi.yaml` — トークンリフレッシュフロー
