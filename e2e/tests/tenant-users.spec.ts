import { ADMIN1, loginAs } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// S-08 ユーザー管理(テナント)— Tenant Admin 専用画面のハッピーパス(コーディング規約 §9.7)。
// tenant1-admin でログイン → ユーザー管理画面に遷移 → メンバー一覧表示 → ユーザー招待まで。

test.describe('S-08 ユーザー管理', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN1.username, ADMIN1.password);
    await page.goto('/tenant-users.html');
    await expect(page.locator('#content')).toBeVisible({ timeout: 15_000 });
  });

  test('メンバー一覧が表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ユーザー管理' })).toBeVisible();
    const table = page.locator('table[aria-label="メンバー一覧"]');
    await expect(table).toBeVisible();
    // tenant1 の既存メンバー(seed)が一覧に並ぶ。
    await expect(page.locator('#users-tbody')).toContainText('tenant1-member1@example.com');
    // 自分(tenant1-admin)の行には「自分」バッジが付き、ロール変更・削除が無効化される。
    await expect(page.locator('#users-tbody')).toContainText('自分');
  });

  test('メンバー削除はブラウザ標準ダイアログでなく共通確認モーダルで確認する (#811)', async ({
    page,
  }) => {
    const row = page.locator('#users-tbody tr', { hasText: 'tenant1-member1@example.com' });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /削除/ }).click();

    // 共通確認モーダルが表示され、対象メンバーの文言を含む(window.confirm ではない)。
    const dialog = page.locator('#confirm-dialog .modal');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('tenant1-member1@example.com');

    // キャンセルすると削除されず行が残る(非破壊)。
    await page.locator('#confirm-dialog [data-role="cancel"]').click();
    await expect(dialog).toBeHidden();
    await expect(row).toBeVisible();
  });

  test('ユーザーを招待できる', async ({ page }) => {
    const email = `invitee-${Date.now()}@example.com`;
    await page.fill('#invite-email', email);
    await page.selectOption('#invite-role', 'MEMBER');
    await page.click('#btn-invite');
    await expect(page.locator('#invite-feedback')).toContainText('招待メールを送信しました', {
      timeout: 10_000,
    });
  });
});
