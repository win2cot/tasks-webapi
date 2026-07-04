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
