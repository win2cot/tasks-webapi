# テナント切替ドロップダウン 手動テスト手順

> Issue #312 実装(Bootstrap 5 テナント切替 UI)の受入条件検証手順。  
> Playwright / Cypress は本プロジェクトに未配線のため、手動確認を行う。

**バージョン**: v1.0 (2026-06-05)

---

## 前提条件

ローカル環境が起動済みであること。起動手順は [local-setup.md](local-setup.md) §1–7 を参照。

- Docker Compose (`mysql`, `keycloak`) が `Up` / `Up (healthy)`
- `./gradlew :webapi:bootRun` が起動中
- `web/` を `http://localhost:5500` で配信中

---

## テストデータ準備

### テスト用テナント・ユーザーの作成

単一テナントシナリオと複数テナントシナリオの両方を検証するため、以下の SQL でデータを投入する。

> **注意**: `users` テーブルのレコードは初回ログイン時にバックエンドが自動生成する。  
> 以下の INSERT を実行する前に、`tenant1-member1@example.com` でいちどログインし、  
> `users` テーブルに行が作成されていることを確認すること。

```sql
-- MySQL に接続
-- docker compose -f docker-compose.local.yml exec mysql mysql -u tasks_webapi -ptasks_webapi tasks

-- (1) テナント 2 を追加 (テナント 1 は初期データが存在する想定)
INSERT INTO tenants (code, name, plan, status, created_at, updated_at)
VALUES ('tenant2', 'テナント2(テスト用)', 'STANDARD', 'ACTIVE', NOW(), NOW());

-- (2) tenant1-member1 の users.id を確認
SELECT id FROM users WHERE email = 'tenant1-member1@example.com';
-- 例: id = 1

-- (3) tenant1-member1 をテナント 2 に追加 (MEMBER として)
--     user_id は上で確認した値に置き換える
--     tenant_id は tenants テーブルで確認した id に置き換える
INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)
VALUES (
  (SELECT id FROM users WHERE email = 'tenant1-member1@example.com'),
  (SELECT id FROM tenants WHERE code = 'tenant2'),
  'MEMBER',
  'ACTIVE',
  NOW()
);
```

**確認コマンド**:

```bash
docker compose -f docker-compose.local.yml exec mysql \
  mysql -u tasks_webapi -ptasks_webapi tasks \
  -e "SELECT u.email, t.name, ut.role FROM user_tenants ut
      JOIN users u ON ut.user_id = u.id
      JOIN tenants t ON ut.tenant_id = t.id
      WHERE u.email = 'tenant1-member1@example.com';"
```

期待出力:

```text
+-----------------------------------+----------------------+--------+
| email                             | name                 | role   |
+-----------------------------------+----------------------+--------+
| tenant1-member1@example.com       | テナント1(または既存名)   | MEMBER |
| tenant1-member1@example.com       | テナント2(テスト用)      | MEMBER |
+-----------------------------------+----------------------+--------+
```

---

## テストシナリオ

### TC-1: 複数テナント所属ユーザで Bootstrap 5 dropdown が描画されること

**準備**: テストデータ準備セクションの SQL を実行済み

**手順**:

1. ブラウザで `http://localhost:5500/index.html` を開く
2. `tenant1-member1@example.com` / `password` でログイン
3. ページ読み込み後、ナビバー右側を確認する

**期待動作**:

- ナビバーに Bootstrap 5 ドロップダウンボタンが表示される
- ボタンのラベルは現在のアクティブテナント名(例: `テナント1`)
- ボタンをクリックするとドロップダウンメニューが開き、所属テナント一覧が表示される

---

### TC-2: アクティブテナントが強調表示されること

**準備**: TC-1 と同じ

**手順**:

1. TC-1 の手順でログイン後、ナビバーのドロップダウンボタンをクリック
2. メニュー内のテナント項目を確認する
3. ブラウザの DevTools(F12) → Elements タブで `<button data-tenant-id="...">` の class を確認する

**期待動作**:

- アクティブなテナントのメニュー項目に `active` クラスが付与されている

  ```html
  <button class="dropdown-item d-flex ... active" ...>テナント1</button>
  ```

- 非アクティブなテナントには `active` クラスがない

---

### TC-3: 別テナントを選択すると SELECT API が呼ばれページがリロードされること

**準備**: TC-1 と同じ

**手順**:

1. TC-1 の手順でログイン後、ナビバーのドロップダウンボタンをクリック
2. DevTools(F12) → Network タブを開き、`Fetch/XHR` フィルターを有効にする
3. ドロップダウンメニューから **非アクティブ** なテナント(テナント2)をクリック

**期待動作**:

- Network タブに `POST /api/auth/tenants/{id}/select` リクエストが記録される
  - リクエストメソッド: `POST`
  - レスポンスステータス: `204 No Content`
- リクエスト完了後、ページが自動的にリロードされる
- リロード後、ナビバーのドロップダウンラベルが選択したテナント名(テナント2)に変わっている

---

### TC-4: 単一テナント所属ユーザで badge chip が表示されること

**準備**: テストデータ準備の SQL を **実行していない** 状態、または 1 テナントにのみ所属するユーザーを使用

**手順**:

1. `tenant1-admin@example.com` / `password` でログイン
   (このユーザーが 1 テナントのみに所属している場合)
2. ページ読み込み後、ナビバー右側を確認する

**期待動作**:

- ドロップダウンボタンではなく、**badge chip** がナビバーに表示される
  - テナント名が badge のテキストとして表示される
  - badge をクリックしてもドロップダウンメニューは開かない
- DevTools の Elements タブで確認すると `<span class="badge ...">` として描画されている

> **補足**: `tenant1-admin@example.com` が複数テナントに所属している場合は、  
> 1 テナントのみに所属する専用テストユーザーを Keycloak Admin Console で作成して確認する。

---

### TC-5: バックエンドエラー時にエラー Toast が右上に表示されること

**手順**:

1. TC-1 の手順でログイン後(複数テナント状態)、ドロップダウンボタンをクリック
2. DevTools(F12) → Network タブを開き、`POST /api/auth/tenants/` のリクエストをブロックする
   - Chrome: Network タブで対象リクエストを右クリック → "Block request URL"
   - 未選択の状態では DevTools → Network → Request blocking(⊕ボタン)でパターンを追加:
     `/api/auth/tenants/`
3. 非アクティブなテナントをドロップダウンから選択

**期待動作**:

- ページ右上(top-0 / end-0)に赤色の Bootstrap Toast が表示される
- Toast のメッセージは「テナント切替に失敗しました: ...」
- Toast は自動的に消えない(`autohide: false`)
- Toast 右端の × ボタンで手動で閉じられる

**後始末**: DevTools の Request Blocking を解除する

---

## 確認チェックリスト

| # | シナリオ | 確認者 | 結果 |
|---|---------|--------|------|
| TC-1 | 複数テナント所属ユーザで dropdown が描画される | | ☐ |
| TC-2 | アクティブテナントに `.active` クラスが付与される | | ☐ |
| TC-3 | テナント切替で `POST 204` が返りページがリロードされる | | ☐ |
| TC-4 | 単一テナント所属ユーザで badge chip が表示される | | ☐ |
| TC-5 | バックエンドエラー時にエラー Toast が右上に表示される | | ☐ |

---

## 関連ドキュメント

- [local-setup.md §8](local-setup.md#8-動作確認-e2e) — 共通 E2E 確認手順
- [web/js/tenant-switcher.js](../../web/js/tenant-switcher.js) — テナント切替コンポーネント実装
- [web/js/api.js](../../web/js/api.js) — `selectTenant()` API クライアント
- [api/openapi.yaml](../../api/openapi.yaml) — `POST /api/auth/tenants/{tenantId}/select` OpenAPI 定義
