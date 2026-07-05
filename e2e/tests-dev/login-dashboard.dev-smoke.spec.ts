import { expect, test } from '@playwright/test';
import { DEV_MEMBER, devLogin, devLogout } from './support/dev-auth';

/**
 * dev-smoke: ログイン(OIDC 認可コード + PKCE = カスタム User Storage SPI を dev 実機で通す)→
 * ダッシュボード表示 → ログアウト。native コールドスタート + SPI federation の到達性を担保する。
 */
test.describe('dev-smoke: login & dashboard', { tag: '@dev-smoke' }, () => {
  test('member がログインしてダッシュボードが表示される', async ({ page }) => {
    await devLogin(page, DEV_MEMBER.username, DEV_MEMBER.password);

    // ダッシュボードのコンテンツと 4 枚の数値カードが描画される(裏で GET /api/dashboard/* が成功)
    await expect(page.locator('#content')).toBeVisible();
    await expect(page.locator('#card-today-due')).toBeVisible();
    await expect(page.locator('#card-overdue')).toBeVisible();
    await expect(page.locator('#card-completed-today')).toBeVisible();
    await expect(page.locator('#card-my-open')).toBeVisible();

    // ナビにユーザー名が出る(GET /api/auth/me = SPI 経由のユーザー解決が成功)
    await expect(page.locator('#nav-username')).not.toBeEmpty();

    await devLogout(page);
  });
});
