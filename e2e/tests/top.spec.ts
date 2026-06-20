import { expect, test } from '@playwright/test';

// 未認証ユーザーがトップ(/)を開くと Keycloak ログインページへリダイレクトされる。
// これが full-stack の「トップ表示」の正常系。
test('トップページが表示される', async ({ page }) => {
  await page.goto('/');
  await page.waitForURL(/\/realms\/tasks\//);
  await expect(page.getByRole('heading', { name: 'Sign in to your account' })).toBeVisible();
});
