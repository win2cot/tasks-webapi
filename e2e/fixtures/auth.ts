import type { Page } from '@playwright/test';

export const MEMBER1 = {
  username: 'tenant1-member1@example.com',
  password: 'password',
} as const;

export const ADMIN1 = {
  username: 'tenant1-admin@example.com',
  password: 'password',
} as const;

/**
 * Keycloak PKCE ログインを実行して tasks.html に到達するまで待機する。
 * tenant1-member1 は tenant1 に 1 所属しているため index.html が自動でテナントを
 * 選択し tasks.html にリダイレクトする。
 */
export async function loginAs(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/');
  await page.waitForURL(/\/realms\/tasks\//);
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('#kc-login');
  // index.html が /api/auth/me を呼び、単一テナントを自動選択して tasks.html へ転送する。
  await page.waitForURL(/\/tasks\.html/, { timeout: 15_000 });
}
