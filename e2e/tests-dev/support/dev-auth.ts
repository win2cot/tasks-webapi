import type { Page } from '@playwright/test';

/**
 * dev-smoke 用の認証ヘルパ(ADR-0041 / #843)。
 *
 * 資格情報は環境変数で上書き可能(CI からは GitHub secret / SSM 注入)。未指定時は dev realm-export の
 * 既定シードユーザー(`tenant1-member1@example.com` 等)にフォールバックする。ログインは実 Keycloak の
 * ログインフォームを操作 = カスタム User Storage SPI(Keycloak → tasks DB federation)を dev 実機で通す。
 */
export const DEV_MEMBER = {
  username: process.env.DEV_SMOKE_USERNAME ?? 'tenant1-member1@example.com',
  password: process.env.DEV_SMOKE_PASSWORD ?? 'password',
} as const;

const KEYCLOAK_REALM_URL = /\/realms\/tasks\//;

/** SPA(`/`)→ Keycloak ログイン → dashboard までを通す。 */
export async function devLogin(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/');
  await page.waitForURL(KEYCLOAK_REALM_URL, { timeout: 30_000 });
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('#kc-login');
  await page.waitForURL(/\/dashboard\.html/, { timeout: 30_000 });
}

/** SPA からログアウトする。 */
export async function devLogout(page: Page): Promise<void> {
  await page.click('#btn-logout');
  await page.waitForURL(KEYCLOAK_REALM_URL, { timeout: 30_000 });
}
