import { expect, test } from '@playwright/test';
import { loginAs, MEMBER1 } from '../fixtures/auth';

// ADR-0023 §6 Step 4 — Keycloak ログイン往復
// 認証情報を入力して Keycloak PKCE フローを完了し、
// tasks.html(タスク一覧)へ戻ることを確認する。
// ※ 未認証での Keycloak リダイレクトは top.spec.ts が担当する。

test('Keycloak ログイン後にタスク一覧へ遷移して認証済み状態になる', async ({ page }) => {
  await loginAs(page, MEMBER1.username, MEMBER1.password);
  await expect(page.getByRole('heading', { name: 'タスク一覧' })).toBeVisible();
  // ナビバーにユーザー名が表示される
  await expect(page.locator('#nav-username')).not.toBeEmpty();
});
