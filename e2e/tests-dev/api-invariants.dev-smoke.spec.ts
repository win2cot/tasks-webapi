import { expect, test } from '@playwright/test';
import { API_BASE, getAccessToken } from './support/api';
import { DEV_MEMBER } from './support/dev-auth';

/**
 * dev-smoke: UI から踏めない API 不変条件(NIST)を薄く補完(ADR-0041 / #843)。
 *
 * 正しく作られた UI は越境リクエストや無認証リクエストを送らないため、これらは API を直接叩かないと固定できない。
 */
test.describe('dev-smoke: API invariants (NIST)', { tag: '@dev-smoke' }, () => {
  test('未認証リクエストは 401', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/tasks`, { headers: { 'X-Tenant-Id': '1' } });
    expect(res.status()).toBe(401);
  });

  test('非メンバーのテナント指定は 403(TenantContextFilter 境界、認可マトリクス §4.1)', async ({
    request,
  }) => {
    const token = await getAccessToken(request, DEV_MEMBER.username, DEV_MEMBER.password);
    const res = await request.get(`${API_BASE}/api/tasks`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '999999' },
    });
    // 非所属テナント指定は TenantContextFilter が 403 で弾く(SSOT: 認可マトリクス §4.1/§5.1)。
    // 404 は task 単位 Hibernate Filter 到達後の別シナリオであり、ここで許容すると境界層の regression を見逃す。
    expect(res.status()).toBe(403);
  });
});
