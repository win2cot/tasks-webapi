/**
 * tasks-webapi 呼び出しラッパー
 *
 * 全リクエストに Authorization: Bearer <token> と X-Tenant-Id ヘッダを付与する。
 * X-Tenant-Id は setTenantId() で設定する(未設定時は省略 — /api/auth/me 等のテナント不要 API 向け)。
 */

const Api = (() => {
  const BASE_URL = 'http://localhost:8080';

  let _tenantId = null;

  /**
   * 現在のテナント ID を設定する。
   * @param {string|null} tenantId
   */
  function setTenantId(tenantId) {
    _tenantId = tenantId;
  }

  /**
   * 共通 fetch ラッパー。token を自動付与し、エラー時は Error をスローする。
   * @param {string} path — API パス (/api/... 形式)
   * @param {RequestInit} [options]
   * @returns {Promise<any>} レスポンス JSON
   */
  async function request(path, options = {}) {
    const token = await Auth.getToken();

    const headers = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    };

    if (_tenantId !== null) {
      headers['X-Tenant-Id'] = String(_tenantId);
    }

    const response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      const body = await response.text().catch(() => '');
      throw new Error(`${response.status} ${response.statusText}: ${body}`);
    }

    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      return response.json();
    }
    return response.text();
  }

  /**
   * GET /api/auth/me — 認証済みユーザー情報を取得する。
   * @returns {Promise<Object>}
   */
  function getMe() {
    return request('/api/auth/me');
  }

  return { setTenantId, request, getMe };
})();
