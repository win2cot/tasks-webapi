import type { APIRequestContext } from '@playwright/test';

/**
 * dev-smoke の Keycloak Admin クリーンアップ用ヘルパ(ADR-0041 / #843)。
 *
 * signup complete で作成された Keycloak ユーザーを afterEach で削除し、共有 dev の realm にログイン可能な
 * テストユーザーが溜まらないようにする。`tasks-webapi-admin`(service account, realm-management manage-users)の
 * client_credentials を用いる。secret は env(CI は SSM 注入)。
 *
 * 注意: アプリ側 `users` テーブル行は物理削除 API が未提供(MVP、#167 で統合)かつ dev RDS は非公開のため
 * E2E からは削除できない。本ヘルパは Keycloak ユーザー(= ログイン資格)のみ削除する。残る `users` 行は
 * ログイン不能な inert な孤児で、一意メール(`signup-<uuid>@e2e.dgz48.xyz`)ゆえ衝突もしない。#167 の
 * 物理削除 API 実装時に恒久クリーンアップへ移行する。
 */
const AUTH_BASE = process.env.DEV_SMOKE_AUTH_URL ?? 'https://auth-dev.dgz48.xyz';
const ADMIN_CLIENT_ID = process.env.DEV_SMOKE_KC_ADMIN_CLIENT_ID ?? 'tasks-webapi-admin';
const ADMIN_CLIENT_SECRET = process.env.DEV_SMOKE_KC_ADMIN_CLIENT_SECRET ?? '';
const REALM = 'tasks';

/** signup complete で作成された Keycloak ユーザーをメールで検索して削除する(best-effort、失敗しても投げない)。 */
export async function deleteKeycloakUserByEmail(
  request: APIRequestContext,
  email: string,
): Promise<void> {
  if (!ADMIN_CLIENT_SECRET) {
    return; // secret 未提供時はスキップ(ローカルで cleanup 不要な場合など)
  }
  try {
    const tokenRes = await request.post(
      `${AUTH_BASE}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          grant_type: 'client_credentials',
          client_id: ADMIN_CLIENT_ID,
          client_secret: ADMIN_CLIENT_SECRET,
        },
      },
    );
    if (!tokenRes.ok()) {
      return;
    }
    const token = ((await tokenRes.json()) as { access_token: string }).access_token;
    const auth = { Authorization: `Bearer ${token}` };

    const usersRes = await request.get(
      `${AUTH_BASE}/admin/realms/${REALM}/users?email=${encodeURIComponent(email)}&exact=true`,
      { headers: auth },
    );
    if (!usersRes.ok()) {
      return;
    }
    const users = (await usersRes.json()) as Array<{ id: string }>;
    for (const u of users) {
      await request.delete(`${AUTH_BASE}/admin/realms/${REALM}/users/${u.id}`, { headers: auth });
    }
  } catch {
    // best-effort: cleanup 失敗でテストは落とさない
  }
}
