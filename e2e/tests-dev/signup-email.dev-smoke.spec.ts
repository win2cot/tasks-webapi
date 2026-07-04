import { expect, test } from '@playwright/test';
import { uniqueRecipient, waitForEmailTo } from './support/mail';

/**
 * dev-smoke: セルフサインアップ double opt-in の **実配信メール**検証(ADR-0041 / #843)。
 *
 * signup.html で一意アドレス(`signup-<uuid>@e2e.dgz48.xyz`)を送信 → アプリが実 SES で確認メールを送出 →
 * SES email receiving が S3 に格納 → テストが S3 から取得しリンク/トークンを抽出 → 確認リンクを開いて
 * トークンが有効(signup-complete が表示)であることを確認する。SES 受信基盤(#845)の疎通を通しで担保。
 *
 * データ汚染を避けるため complete(ユーザー作成)は行わず、トークン有効性の確認までに留める。
 */
test.describe('dev-smoke: signup email (double opt-in)', { tag: '@dev-smoke' }, () => {
  // メール実配信 + S3 受信ポーリングのため、既定 60s ではなくテスト単位で延長する。
  test('確認メールが実配信され、確認リンクのトークンが有効', { timeout: 180_000 }, async ({ page }) => {
    const recipient = uniqueRecipient('signup');

    // --- サインアップ要求(signup.html UI → POST /api/signup/request)---
    await page.goto('/signup.html');
    await page.fill('#email', recipient);
    await page.click('#btn-request');
    await expect(page.locator('#sent-notice')).toBeVisible();

    // --- 実配信メールを S3(SES 受信)から取得 ---
    const mail = await waitForEmailTo(recipient, { timeoutMs: 120_000 });
    expect(mail.subject.length).toBeGreaterThan(0);

    // --- 確認リンク(signup-complete.html?token=...)を抽出 ---
    const confirmUrl = mail.links.find(
      (u) => u.includes('signup-complete.html') && u.includes('token='),
    );
    expect(confirmUrl, `確認リンクがメールに見つからない: ${mail.links.join(', ')}`).toBeDefined();

    // --- 確認リンクを開く → GET /api/signup/{token} が pending を返しトークン有効(complete フォーム表示)---
    await page.goto(confirmUrl as string);
    await expect(page.locator('#complete-form')).toBeVisible();
    // 宛先メールがフォームにエコーされる(トークンに紐づく email が解決できている)
    await expect(page.locator('#email-label')).toContainText(recipient);
  });
});
