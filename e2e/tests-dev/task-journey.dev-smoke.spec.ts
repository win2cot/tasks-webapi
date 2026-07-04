import { expect, test } from '@playwright/test';
import { forceDeleteTasksByTitle } from './support/api';
import { DEV_MEMBER, devLogin } from './support/dev-auth';

/**
 * dev-smoke: タスクの作成 → ステータス変更 → 削除(後始末)を実 UI で通す。
 *
 * native 実機でしか出ない事象(@Valid の validator hints 欠落による write 500、@AuthenticationPrincipal
 * の SpEL による read 500、If-Match/ETag)を、ユーザー操作の経路で踏む。dev データを汚さないよう作成した
 * タスクは最後に削除する(冪等 create + cleanup)。
 */
test.describe('dev-smoke: task journey', { tag: '@dev-smoke' }, () => {
  /** Asia/Tokyo の当日(YYYY-MM-DD)。一覧は当日タスクのみ表示するため due を当日にする。 */
  function todayInJst(): string {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());
  }

  // 作成〜削除の途中で失敗しても共有テナントにゴミを残さないよう、作成したタスクを afterEach で
  // 強制削除する(ADR-0041 決定5)。正常に UI 削除できた場合は createdTitle を消して二重削除を避ける。
  let createdTitle: string | undefined;

  test.afterEach(async ({ request }) => {
    if (createdTitle) {
      await forceDeleteTasksByTitle(
        request,
        DEV_MEMBER.username,
        DEV_MEMBER.password,
        createdTitle,
      );
      createdTitle = undefined;
    }
  });

  test('タスクを作成・ステータス変更・削除できる', async ({ page }) => {
    await devLogin(page, DEV_MEMBER.username, DEV_MEMBER.password);

    await page.goto('/tasks.html');
    await expect(page.locator('#task-panel')).toBeVisible();

    const title = `dev-smoke ${Date.now()}`;
    createdTitle = title; // 失敗時 afterEach が掃除できるよう先に記録

    // --- 作成(POST /api/tasks, @Valid 経路)---
    await page.click('#btn-new-task');
    const drawer = page.locator('app-task-drawer .offcanvas');
    await expect(drawer).toBeVisible();
    await drawer.locator('#newTitle').fill(title);
    await drawer.locator('#newDue').fill(todayInJst());
    // priority=MEDIUM / visibility=TENANT は既定のまま(所有者=自分で一覧に出る)
    await drawer.locator('button[type="submit"]').click();
    await expect(drawer).toBeHidden();

    const row = page.locator('app-task-row').filter({ hasText: title });
    await expect(row).toBeVisible();

    // --- 詳細を開く(編集不可セルをクリック)→ ステータス変更(PATCH /status, ETag)---
    await row.locator('.cell-owner').click();
    await expect(drawer).toBeVisible();
    await drawer.locator('select.inline-sel').first().selectOption('IN_PROGRESS');
    // 反映確認: 詳細ドロワーのステータス select が更新される
    await expect(drawer.locator('select.inline-sel').first()).toHaveValue('IN_PROGRESS');

    // --- 削除(後始末、DELETE /api/tasks/{id}, If-Match)---
    await drawer.locator('.btn-outline-danger').click();
    await drawer.locator('button.btn-danger').click();
    await expect(drawer).toBeHidden();
    await expect(page.locator('app-task-row').filter({ hasText: title })).toHaveCount(0);
    createdTitle = undefined; // 正常に UI 削除できたので afterEach の掃除は不要
  });
});
