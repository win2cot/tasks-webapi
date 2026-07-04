import { defineConfig, devices } from '@playwright/test';

/**
 * dev post-deploy smoke(ADR-0041 / #843)専用 Playwright 設定。
 *
 * 既存の hermetic スイート(`./tests`、ローカル compose 前提)とは完全分離し、`./tests-dev` を
 * live dev 環境に対して実行する。SPA は hostname から Keycloak/API オリジンを導出するため、
 * `BASE_URL` を dev SPA に向けるだけで auth-dev / api-dev も自動で使われる。
 */
export default defineConfig({
  testDir: './tests-dev',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  // live 環境は伝播やコールドスタートで揺れるため CI では 2 回まで再試行
  retries: process.env.CI ? 2 : 1,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],
  // 実 Keycloak ログイン往復 + native コールドスタートを考慮し余裕を持たせる
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: process.env.BASE_URL ?? 'https://tasks-dev.dgz48.xyz',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
