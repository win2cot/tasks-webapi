import { loginAs, MEMBER1 } from '../fixtures/auth';
import { expect, test } from '../fixtures/test';

// S-10 通知設定(#815)— 保存ボタンを廃し、トグル変更で即時自動保存する。
// 変更後に「保存しました」が一時表示され、エラーが出ないことを確認する。

test.describe('S-10 通知設定 即時自動保存 (#815)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, MEMBER1.username, MEMBER1.password);
    await page.goto('/notification-settings.html');
    await expect(page.locator('#settings-form')).toBeVisible({ timeout: 15_000 });
  });

  test('保存ボタンが無く、トグル変更で自動保存される', async ({ page }) => {
    // 明示保存ボタンは廃止されている。
    await expect(page.locator('#btn-save')).toHaveCount(0);

    const toggle = page.locator('#email-due-today');
    const before = await toggle.isChecked();

    // トグルを反転 → 自動保存 →「保存しました」が表示される。
    await toggle.click();
    await expect(page.locator('#saved-alert')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('#error-alert')).toBeHidden();
    expect(await toggle.isChecked()).toBe(!before);

    // 元の状態へ戻す(後続への影響を避ける)。
    await toggle.click();
    await expect(page.locator('#saved-alert')).toBeVisible({ timeout: 10_000 });
    expect(await toggle.isChecked()).toBe(before);
  });
});
