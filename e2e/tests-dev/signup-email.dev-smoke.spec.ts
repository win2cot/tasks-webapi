import { expect, test } from '@playwright/test';
import { devLoginNewMember } from './support/dev-auth';
import { deleteKeycloakUserByEmail } from './support/keycloak-admin';
import { uniqueRecipient, waitForEmailTo } from './support/mail';

/**
 * dev-smoke: セルフサインアップ double opt-in の **フルフロー**検証(ADR-0041 §6 / #843)。
 *
 * signup.html で一意アドレス(`signup-<uuid>@e2e.dgz48.xyz`)を送信 → アプリが実 SES で確認メール送出 →
 * SES email receiving が S3 へ格納 → テストが S3 から取得しリンク/トークンを抽出 → 確認リンクを開いて
 * complete(ユーザー作成 + Keycloak 資格プロビジョニング)→ 作成ユーザーでログイン到達まで確認する。
 * #845(受信)+ #854(送信 env/IAM)+ native への SES 焼込 を通しで担保する。
 *
 * データ汚染対策(ADR-0041 決定5): afterEach で作成した Keycloak ユーザーを Admin API で削除する。
 * 注: アプリ `users` 行は物理削除 API 未提供(MVP・#167)かつ dev RDS 非公開のため E2E から削除できず、
 * ログイン不能な inert 孤児として残る(一意メールゆえ衝突なし)。#167 実装時に恒久クリーンアップへ移行。
 */
test.describe('dev-smoke: signup email (double opt-in)', { tag: '@dev-smoke' }, () => {
  const PASSWORD = 'DevSmoke!Pw12345';
  let createdEmail: string | undefined;

  test.afterEach(async ({ request }) => {
    if (createdEmail) {
      await deleteKeycloakUserByEmail(request, createdEmail);
      createdEmail = undefined;
    }
  });

  test('確認メール実配信 → complete → 作成ユーザーでログインできる', async ({ page }) => {
    // メール実配信 + S3 受信ポーリング + ログイン往復のため、既定 60s ではなくテスト単位で延長する。
    test.setTimeout(180_000);
    const recipient = uniqueRecipient('signup');
    createdEmail = recipient; // complete 前でも afterEach が掃除できるよう先に記録

    // --- サインアップ要求(signup.html UI → POST /api/signup/request)---
    await page.goto('/signup.html');
    await page.fill('#email', recipient);
    await page.click('#btn-request');
    await expect(page.locator('#sent-notice')).toBeVisible();

    // --- 実配信メールを S3(SES 受信)から取得し確認リンクを抽出 ---
    const mail = await waitForEmailTo(recipient, { timeoutMs: 120_000 });
    const confirmUrl = mail.links.find(
      (u) => u.includes('signup-complete.html') && u.includes('token='),
    );
    expect(confirmUrl, `確認リンクがメールに見つからない: ${mail.links.join(', ')}`).toBeDefined();

    // --- 確認リンクを開く → complete フォーム(トークン有効・email エコー)---
    await page.goto(confirmUrl as string);
    await expect(page.locator('#complete-form')).toBeVisible();
    await expect(page.locator('#email-label')).toContainText(recipient);

    // --- complete(POST /api/signup/{token}/complete = ユーザー作成 + 資格プロビジョニング)---
    await page.fill('#full-name', 'devスモーク太郎');
    await page.fill('#full-name-kana', 'デブスモークタロウ');
    await page.fill('#password', PASSWORD);
    await page.click('#btn-complete');
    await expect(page.locator('#notice')).toBeVisible();

    // --- 作成ユーザーでログイン到達(プロビジョニングされた資格が Keycloak/SPI で認証できる)---
    // 登録直後はテナント未所属(ADR-0040 §3.5)。dashboard ではなく認証済み SPA(`/`)の
    // 「テナント作成」導線に着地することを確認する(= ログイン成功 + oidc_sub correlation 成立)。
    await devLoginNewMember(page, recipient, PASSWORD);
    await expect(page.locator('#content-logged-in')).toBeVisible();
    await expect(page.locator('#link-create-tenant')).toBeVisible();
  });
});
