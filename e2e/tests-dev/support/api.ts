import type { APIRequestContext } from '@playwright/test';

/**
 * dev-smoke の API 直叩き補完用ヘルパ(ADR-0041 / #843)。UI からは発生しない不変条件(NIST AC-4 の越境拒否・
 * 認証境界)を検証するため、Keycloak の password grant でトークンを取り、api-dev を直接叩く。
 */
const AUTH_BASE = process.env.DEV_SMOKE_AUTH_URL ?? 'https://auth-dev.dgz48.xyz';
export const API_BASE = process.env.DEV_SMOKE_API_URL ?? 'https://api-dev.tasks.dgz48.xyz';

/** tasks-webapi public client の password grant でアクセストークンを取得する。 */
export async function getAccessToken(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<string> {
  const res = await request.post(`${AUTH_BASE}/realms/tasks/protocol/openid-connect/token`, {
    form: {
      grant_type: 'password',
      client_id: 'tasks-webapi',
      username,
      password,
      scope: 'openid',
    },
  });
  if (!res.ok()) {
    throw new Error(`token 取得失敗: ${res.status()} ${await res.text()}`);
  }
  const body = (await res.json()) as { access_token: string };
  return body.access_token;
}

/**
 * 指定タイトルのタスクを API 経由で強制削除する best-effort クリーンアップ(dev 実データ汚染防止、ADR-0041 決定5)。
 * afterEach から呼び、テストが作成〜削除の途中で失敗しても共有テナントにゴミを残さないようにする。失敗しても投げない。
 */
export async function forceDeleteTasksByTitle(
  request: APIRequestContext,
  username: string,
  password: string,
  title: string,
): Promise<void> {
  try {
    const token = await getAccessToken(request, username, password);
    const auth = { Authorization: `Bearer ${token}` };
    // テナント未指定で許可される /api/auth/me からテナント id を得る
    const meRes = await request.get(`${API_BASE}/api/auth/me`, { headers: auth });
    if (!meRes.ok()) {
      return;
    }
    const me = (await meRes.json()) as { tenants?: Array<{ id: number }> };
    const tenantId = me.tenants?.[0]?.id;
    if (tenantId == null) {
      return;
    }
    const headers = { ...auth, 'X-Tenant-Id': String(tenantId) };
    const listRes = await request.get(
      `${API_BASE}/api/tasks?keyword=${encodeURIComponent(title)}&size=100`,
      { headers },
    );
    if (!listRes.ok()) {
      return;
    }
    const body = (await listRes.json()) as {
      content?: Array<{ id: number; version: number; title: string }>;
    };
    for (const t of body.content ?? []) {
      if (t.title !== title) {
        continue;
      }
      await request.delete(`${API_BASE}/api/tasks/${t.id}`, {
        headers: { ...headers, 'If-Match': `"${t.version}"` },
      });
    }
  } catch {
    // best-effort: クリーンアップ失敗でテストは落とさない
  }
}
