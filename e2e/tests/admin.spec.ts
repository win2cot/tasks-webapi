import { ADMIN1, loginAs, loginAsSaasAdmin, SAAS_ADMIN } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// S-12 プラットフォーム監視 — SaaS 運営者(APP_ADMIN)専用画面のハッピーパス(コーディング規約 §9.7)。
// admin@example.com でログイン → index.html が APP_ADMIN を検知して admin.html へ転送 →
// プラットフォームメトリクスカードが表示されるまで。

test.describe('S-12 プラットフォーム監視', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSaasAdmin(page, SAAS_ADMIN.username, SAAS_ADMIN.password);
  });

  test('ログイン後に admin.html へ自動転送される', async ({ page }) => {
    await expect(page).toHaveURL(/\/admin\.html/);
    await expect(page.getByRole('heading', { name: 'プラットフォーム監視' })).toBeVisible();
  });

  test('プラットフォームメトリクスが表示される', async ({ page }) => {
    // メトリクス取得完了でローディングが消え、メトリクス領域が表示される。
    await expect(page.locator('#metrics')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('#loading')).toBeHidden();

    // 各カードの件数が数値で埋まる(初期プレースホルダ「—」から置き換わる)。
    await expect(page.locator('#m-total-tenants')).not.toHaveText('—');
    await expect(page.locator('#m-total-users')).not.toHaveText('—');
    await expect(page.locator('#m-total-tasks')).not.toHaveText('—');

    // SaaS Admin 画面はテナントスイッチャを持たない。
    await expect(page.locator('#app-tenant-switcher')).toHaveCount(0);
  });
});

// #812 情報露出対策 — 非 APP_ADMIN が admin.html を直接開いてもシェルを描画せず
// 自分のホーム(dashboard.html)へ即時リダイレクトする(NIST AC-4 / 多層防御)。
test.describe('S-12 ロール境界 (#812)', () => {
  test('Tenant Admin が admin.html を直接開くと dashboard へ退避し admin シェルを見ない', async ({
    page,
  }) => {
    await loginAs(page, ADMIN1.username, ADMIN1.password);

    await page.goto('/admin.html');

    // シェルを描画せず dashboard.html へリダイレクトされる。
    await page.waitForURL(/\/dashboard\.html/, { timeout: 15_000 });
    // SaaS Admin 専用のメトリクス領域・見出しは見えない。
    await expect(page.locator('#metrics')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'プラットフォーム監視' })).toHaveCount(0);
  });
});
