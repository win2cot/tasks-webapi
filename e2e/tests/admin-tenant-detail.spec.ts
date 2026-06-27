import type { Page } from '@playwright/test';
import { loginAsSaasAdmin, SAAS_ADMIN } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// S-14 テナント詳細・状態切替 — SaaS 運営者(APP_ADMIN)専用画面のハッピーパス(コーディング規約 §9.7)。
// S-13 一覧から詳細へ遷移 → 詳細表示 → 名称編集 → Suspend/Reactivate まで。
//
// seed は tenant1 のみで他スペックも利用するため、本スペックは tenant1 の状態を
// 必ず ACTIVE・名称を元に戻して終える(afterEach のセーフティネットで保証)。

/** 詳細画面を開き #detail が見えるまで待つ。 */
async function openFirstTenantDetail(page: Page): Promise<void> {
  await page.goto('/admin-tenants.html');
  await expect(page.locator('#result')).toBeVisible({ timeout: 15_000 });
  await page.locator('#tenants-tbody tr').first().locator('a').first().click();
  await expect(page).toHaveURL(/admin-tenant-detail\.html\?id=\d+/);
  await expect(page.locator('#detail')).toBeVisible({ timeout: 15_000 });
}

test.describe('S-14 テナント詳細・状態切替', () => {
  test.beforeEach(async ({ page }) => {
    // confirm ダイアログ(状態切替)は常に自動承認する。ハンドラは 1 度だけ登録する。
    page.on('dialog', (d) => d.accept());
    await loginAsSaasAdmin(page, SAAS_ADMIN.username, SAAS_ADMIN.password);
  });

  // セーフティネット: テストが途中失敗しても tenant1 を ACTIVE に戻す。
  test.afterEach(async ({ page }) => {
    await page.goto('/admin-tenant-detail.html?id=1');
    await expect(page.locator('#detail')).toBeVisible({ timeout: 15_000 });
    const badge = page.locator('#d-status');
    if ((await badge.textContent())?.trim() === 'SUSPENDED') {
      await page.click('#btn-toggle-status');
      await expect(badge).toHaveText('ACTIVE', { timeout: 10_000 });
    }
  });

  test('詳細表示・名称編集・状態切替ができる', async ({ page }) => {
    await openFirstTenantDetail(page);

    // 詳細表示(名称・状態・ユーザー数・タスク数)。
    await expect(page.locator('#d-code')).toHaveText('tenant1');
    await expect(page.locator('#d-status')).toHaveText('ACTIVE');
    await expect(page.locator('#d-user-count')).not.toHaveText('—');
    await expect(page.locator('#d-task-count')).not.toHaveText('—');

    const nameInput = page.locator('#name-input');
    const original = (await nameInput.inputValue()).trim();

    // 名称編集 → 反映。
    await nameInput.fill(`${original} (編集)`);
    await page.click('#btn-save-name');
    await expect(page.locator('#name-feedback')).toHaveText('保存しました。', { timeout: 10_000 });
    await expect(page.locator('#page-title')).toHaveText(`${original} (編集)`);

    // 名称を元に戻す(後続スペックへの影響を避ける)。
    await nameInput.fill(original);
    await page.click('#btn-save-name');
    await expect(page.locator('#page-title')).toHaveText(original, { timeout: 10_000 });

    // 状態切替: Suspend → 反映。
    await page.click('#btn-toggle-status');
    await expect(page.locator('#d-status')).toHaveText('SUSPENDED', { timeout: 10_000 });
    await expect(page.locator('#status-feedback')).toContainText('Suspend');

    // Reactivate → 反映(ACTIVE に戻す)。
    await page.click('#btn-toggle-status');
    await expect(page.locator('#d-status')).toHaveText('ACTIVE', { timeout: 10_000 });
    await expect(page.locator('#status-feedback')).toContainText('Reactivate');
  });
});
