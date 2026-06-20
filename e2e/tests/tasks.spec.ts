import { loginAs, MEMBER1 } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// ADR-0023 §6 Step 4 — タスク一覧 + CRUD ハッピーパス

test.describe('タスク一覧 + CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, MEMBER1.username, MEMBER1.password);
    // タスクパネルが表示されるまで待機
    await expect(page.locator('#task-panel')).toBeVisible({ timeout: 15_000 });
  });

  test('タスク一覧パネルが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'タスク一覧' })).toBeVisible();
    await expect(page.locator('table[aria-label="タスク一覧"]')).toBeVisible();
  });

  test('タスクを作成・詳細表示・ステータス変更・削除できる', async ({ page }) => {
    const taskTitle = `E2E smoke ${Date.now()}`;

    // ── Create ─────────────────────────────────────────────────────────────
    await page.click('#btn-new-task');
    // ドロワーが開くまで待機
    const drawer = page.locator('app-task-drawer .offcanvas');
    await expect(drawer).toBeVisible({ timeout: 5_000 });

    await page.fill('#newTitle', taskTitle);
    await page.fill('#newDue', '2099-12-31');
    // 優先度はデフォルト MEDIUM、公開範囲はデフォルト TENANT のまま送信
    await drawer.locator('button[type="submit"]').click();

    // ドロワーが閉じてタスク一覧に反映されるのを待つ
    await expect(drawer).not.toBeVisible({ timeout: 10_000 });
    await expect(page.locator('#task-tbody')).toContainText(taskTitle, { timeout: 10_000 });

    // ── Read (detail) ──────────────────────────────────────────────────────
    // タスク行のオーナーセルをクリックして詳細ドロワーを開く
    // (.cell-title-desc は editable タスクでは data-no-row-click が付くため .cell-owner を使う)
    const taskRow = page.locator('app-task-row').filter({
      has: page.locator('.task-title', { hasText: taskTitle }),
    });
    await taskRow.locator('td.cell-owner').click();

    await expect(drawer).toBeVisible({ timeout: 5_000 });
    await expect(drawer.locator('.offcanvas-title')).toContainText(taskTitle);

    // ── Update (status change) ─────────────────────────────────────────────
    const statusSel = drawer.locator('select.inline-sel');
    await expect(statusSel).toBeVisible();
    await statusSel.selectOption('IN_PROGRESS');
    // 詳細ビューが再レンダリングされ、選択が反映される
    await expect(drawer.locator('select.inline-sel')).toHaveValue('IN_PROGRESS', {
      timeout: 5_000,
    });

    // ── Delete ─────────────────────────────────────────────────────────────
    await drawer.locator('.btn-outline-danger').click();
    // 削除確認ボタンが表示される
    await drawer.locator('button.btn-danger').click();

    // ドロワーが閉じてタスクが一覧から消えるのを確認
    await expect(drawer).not.toBeVisible({ timeout: 5_000 });
    await expect(page.locator('#task-tbody')).not.toContainText(taskTitle, { timeout: 10_000 });
  });
});
