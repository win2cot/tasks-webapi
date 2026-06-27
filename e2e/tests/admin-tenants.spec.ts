import { loginAsSaasAdmin, SAAS_ADMIN } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// S-13 テナント管理(一覧)— SaaS 運営者(APP_ADMIN)専用画面のハッピーパス(コーディング規約 §9.7)。
// admin@example.com でログイン → admin.html → サイドバーから admin-tenants.html へ →
// テナント一覧表示・状態フィルタ・S-14 詳細リンクの確認まで。

test.describe('S-13 テナント管理(一覧)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSaasAdmin(page, SAAS_ADMIN.username, SAAS_ADMIN.password);
    await page.goto('/admin-tenants.html');
    await expect(page.locator('#result')).toBeVisible({ timeout: 15_000 });
  });

  test('テナント一覧が表示され、各行が S-14 詳細へリンクする', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'テナント管理' })).toBeVisible();
    const table = page.locator('table[aria-label="テナント一覧"]');
    await expect(table).toBeVisible();

    // seed 済みの tenant1 が一覧に並ぶ。
    const tbody = page.locator('#tenants-tbody');
    await expect(tbody.locator('tr')).not.toHaveCount(0);

    // 行のテナント名は S-14 詳細(admin-tenant-detail.html?id=...)へのリンク。
    const firstLink = tbody.locator('tr').first().locator('a').first();
    await expect(firstLink).toHaveAttribute('href', /admin-tenant-detail\.html\?id=\d+/);

    // テナントスイッチャを持たない SaaS Admin 画面。
    await expect(page.locator('#app-tenant-switcher')).toHaveCount(0);
  });

  test('状態フィルタで ACTIVE のみに絞り込める', async ({ page }) => {
    await page.selectOption('#filter-status', 'ACTIVE');
    await page.click('#btn-search');

    // 絞り込み後も一覧が再描画される(空でなければ各行は ACTIVE バッジを持つ)。
    await expect(page.locator('#result')).toBeVisible();
    const badges = page.locator('#tenants-tbody tr .badge');
    const count = await badges.count();
    for (let i = 0; i < count; i++) {
      await expect(badges.nth(i)).toHaveText('ACTIVE');
    }
  });
});
