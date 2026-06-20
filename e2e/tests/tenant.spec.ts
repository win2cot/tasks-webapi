import { loginAs, MEMBER1 } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// ADR-0023 §6 Step 4 — テナント切替
// ログイン後に単一テナントが自動選択され、ナビバーのテナントチップに
// テナント名「テナント1」が表示されることを確認する。

test('ログイン後にテナント名がナビバーに表示される', async ({ page }) => {
  await loginAs(page, MEMBER1.username, MEMBER1.password);

  const switcher = page.locator('app-tenant-switcher');
  await expect(switcher).toBeVisible();
  await expect(switcher).toContainText('テナント1');
});
