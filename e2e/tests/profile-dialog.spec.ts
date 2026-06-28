import { loginAs, MEMBER1 } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// S-09 プロフィール(#814)— サイドバーの独立項目を廃し、ヘッダーのアバターを
// クリックすると参照専用プロフィールダイアログが開く。

test.describe('S-09 プロフィールダイアログ (#814)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, MEMBER1.username, MEMBER1.password);
    await expect(page.locator('#content')).toBeVisible({ timeout: 15_000 });
  });

  test('サイドバーにプロフィール項目が無い', async ({ page }) => {
    await expect(page.locator('.app-sidebar a[href="profile.html"]')).toHaveCount(0);
  });

  test('アバターをクリックするとプロフィールダイアログが開く', async ({ page }) => {
    await page.locator('#user-avatar').click();

    const dialog = page.locator('#profile-dialog .modal');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('heading', { name: 'プロフィール' })).toBeVisible();
    // GET /api/users/me の取得完了で本人のメールが表示される。
    await expect(dialog.locator('[data-field="email"]')).toHaveText(MEMBER1.username, {
      timeout: 10_000,
    });
  });
});
