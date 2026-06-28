import { loginAs, MEMBER1 } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// S-03 個人ダッシュボード(#813)
// ログイン後の既定表示として数値カードと 4 セクション(期限切れ / 今日対応 / 今後 / 本日完了)
// が描画され、タスク一覧へ遷移できることを確認する。

test.describe('個人ダッシュボード (S-03)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, MEMBER1.username, MEMBER1.password);
  });

  test('数値カードと 4 セクションが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeVisible();

    // 集計取得後にコンテンツが表示される(ローディングが解除される)。
    await expect(page.locator('#content')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('#loading')).toBeHidden();

    // 数値カード 4 枚は数値(空でない)を表示する。
    for (const id of [
      '#card-today-due',
      '#card-overdue',
      '#card-completed-today',
      '#card-my-open',
    ]) {
      await expect(page.locator(id)).toHaveText(/\d+/);
    }

    // 4 セクションの見出しが存在する。
    for (const name of ['期限切れ', '今日対応', '今後', '本日完了']) {
      await expect(page.getByRole('heading', { name: new RegExp(name) })).toBeVisible();
    }

    // ナビバーのテナントチップにテナント名が表示される。
    await expect(page.locator('app-tenant-switcher')).toContainText('テナント1');
  });

  test('サイドバーからタスク一覧へ遷移できる', async ({ page }) => {
    await expect(page.locator('#content')).toBeVisible({ timeout: 15_000 });

    await page.getByRole('link', { name: 'タスク一覧' }).first().click();
    await page.waitForURL(/\/tasks\.html/);
    await expect(page.locator('#task-panel')).toBeVisible({ timeout: 15_000 });
  });
});
