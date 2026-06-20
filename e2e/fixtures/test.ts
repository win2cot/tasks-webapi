import { test as base, expect } from '@playwright/test';

// ADR-0023 §6 Step 5 / ADR-0022 (b) — CSP 違反 0 の共通フィクスチャ
// すべてのテストに auto で適用し、securitypolicyviolation イベントが 0 件であることを assert する。

type CspViolation = {
  blockedURI: string;
  violatedDirective: string;
  documentURI: string;
};

declare global {
  interface Window {
    __pwCspViolation: (v: CspViolation) => void;
  }
}

export const test = base.extend<{ _csp: void }>({
  _csp: [
    async ({ page }, use) => {
      const violations: CspViolation[] = [];

      // Node.js 側のコールバックをブラウザ全ナビゲーションで永続的に公開する
      await page.exposeFunction('__pwCspViolation', (v: CspViolation) => {
        violations.push(v);
      });

      // ページロード前に securitypolicyviolation リスナーを全ナビゲーションへ注入する
      await page.addInitScript(() => {
        document.addEventListener('securitypolicyviolation', (e) => {
          window.__pwCspViolation({
            blockedURI: e.blockedURI,
            violatedDirective: e.violatedDirective,
            documentURI: e.documentURI,
          });
        });
      });

      await use();

      expect(
        violations,
        `CSP violations detected:\n${JSON.stringify(violations, null, 2)}`,
      ).toHaveLength(0);
    },
    { auto: true },
  ],
});

export { expect } from '@playwright/test';
