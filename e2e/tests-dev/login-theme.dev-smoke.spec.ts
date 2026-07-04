import { expect, test } from '@playwright/test';

/**
 * dev-smoke: Keycloak ログイン画面(auth-dev)に tasks-login テーマの「新規登録」リンクが描画される。
 *
 * signup-link.js はホストが `auth(-env).dgz48.xyz` のときだけリンクを注入するため、これは **dev 実機でしか
 * 検証できない**(localhost では描画されない)。テーマ配備 + サインアップ導線の到達性を担保する。
 */
test.describe('dev-smoke: login theme signup link', { tag: '@dev-smoke' }, () => {
  test('ログイン画面に新規登録リンクが表示され signup.html を指す', async ({ page }) => {
    // SPA を開くと Keycloak(auth-dev)ログイン画面へリダイレクトされる
    await page.goto('/');
    await page.waitForURL(/\/realms\/tasks\//, { timeout: 30_000 });

    const signupLink = page.locator('#tasks-signup-link a');
    await expect(signupLink).toBeVisible();
    await expect(signupLink).toHaveText('新規登録');
    await expect(signupLink).toHaveAttribute(
      'href',
      /https:\/\/tasks(-\w+)?\.dgz48\.xyz\/signup\.html$/,
    );
  });
});
