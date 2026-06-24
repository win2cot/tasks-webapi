import { loginAs, MEMBER1 } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// #554 — display:contents 行の a11y 検証(table / row / cell role の露出確認)
//
// <app-task-row> はテーブルレイアウトへの透過に display: contents を採用している
// (#546 / PR #553)。display: contents は table 要素の role 計算バグが歴史的に最も
// 多く、スクリーンリーダー実装差の懸念があるため、アクセシビリティツリー上で
// table / row / columnheader / cell role が正しく露出していることを機械検知する。
//
// 実機スクリーンリーダー(NVDA / VoiceOver)による確認は人手で 1 回実施し、
// 結果を Issue #554 に記録する(本 spec の対象外)。
// 確認手順・チェックリスト・記録テンプレ・fallback 判断: ../docs/a11y-display-contents.md

const todayJST = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());

test.describe('a11y: display:contents 行の role 露出 (#554)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, MEMBER1.username, MEMBER1.password);
    await expect(page.locator('#task-panel')).toBeVisible({ timeout: 15_000 });
  });

  test('タスク一覧テーブルが table role と 7 列の columnheader を露出する', async ({ page }) => {
    const table = page.getByRole('table', { name: 'タスク一覧' });
    await expect(table).toBeVisible();

    // ヘッダ行は table → row → columnheader のツリーで露出する(静的・常時存在)。
    // 期限 / 優先度の th は装飾アイコン(aria-hidden)を含むため正規表現で名前を照合する。
    const headerRow = table.getByRole('row').first();
    await expect(headerRow).toMatchAriaSnapshot(`
      - row:
        - columnheader "状態"
        - columnheader "タイトル"
        - columnheader "所有者"
        - columnheader "担当者"
        - columnheader /期限/
        - columnheader /優先度/
        - columnheader "公開範囲"
    `);
    await expect(table.getByRole('columnheader')).toHaveCount(7);
  });

  test('display:contents の app-task-row が row / cell role を露出する', async ({ page }) => {
    const taskTitle = `E2E a11y ${Date.now()}`;

    // メイン画面は当日対象タスクのみ表示する(#665)ため、期限=当日でタスクを作成して
    // 「本日」セクションに 1 行出現させる。
    await page.click('#btn-new-task');
    const drawer = page.locator('app-task-drawer .offcanvas');
    await expect(drawer).toBeVisible({ timeout: 5_000 });
    await page.fill('#newTitle', taskTitle);
    await page.fill('#newDue', todayJST());
    await drawer.locator('button[type="submit"]').click();
    await expect(drawer).not.toBeVisible({ timeout: 10_000 });
    await expect(page.locator('#task-tbody')).toContainText(taskTitle, { timeout: 10_000 });

    // <app-task-row> は display: contents で自身のボックスを生成しないが、内部 <tr> は
    // table のアクセシビリティツリーへ row として昇格し、7 つの <td> が cell として
    // 露出しなければならない。ここが display: contents の role 計算バグの検知ポイント。
    const dataRow = page.getByRole('row').filter({ hasText: taskTitle });
    await expect(dataRow).toHaveCount(1);
    await expect(dataRow.getByRole('cell')).toHaveCount(7);

    // アクセシビリティツリーのスナップショットで row → cell の入れ子構造を明示的に検証する。
    await expect(dataRow).toMatchAriaSnapshot(`
      - row:
        - cell
        - cell
        - cell
        - cell
        - cell
        - cell
        - cell
    `);

    // ── 後始末: 作成タスクを削除 ──────────────────────────────────────────────
    // .cell-title-desc は editable タスクで data-no-row-click が付くため cell-owner を使う。
    const taskRow = page.locator('app-task-row').filter({
      has: page.locator('.task-title', { hasText: taskTitle }),
    });
    await taskRow.locator('td.cell-owner').click();
    await expect(drawer).toBeVisible({ timeout: 5_000 });
    await drawer.locator('.btn-outline-danger').click();
    await drawer.locator('button.btn-danger').click();
    await expect(drawer).not.toBeVisible({ timeout: 5_000 });
  });
});
