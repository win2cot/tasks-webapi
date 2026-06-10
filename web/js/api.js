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
   * 401 を受信した場合は token リフレッシュ後に 1 回だけリトライする。
   * @param {string} path — API パス (/api/... 形式)
   * @param {RequestInit} [options]
   * @returns {Promise<any>} レスポンス JSON
   */
  async function request(path, options = {}) {
    const response = await _fetch(path, await Auth.getToken(), options);

    if (response.status === 401) {
      const newToken = await Auth.refreshToken();
      const retryResponse = await _fetch(path, newToken, options);
      return _parseResponse(retryResponse);
    }

    return _parseResponse(response);
  }

  async function _fetch(path, token, options) {
    const headers = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    };

    if (_tenantId !== null) {
      headers['X-Tenant-Id'] = String(_tenantId);
    }

    return fetch(`${BASE_URL}${path}`, { ...options, headers });
  }

  async function _parseResponse(response) {
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

  /**
   * POST /api/auth/tenants/{tenantId}/select — アクティブテナントを切替える。
   * X-Tenant-Id ヘッダ不要(API 側でグローバルセキュリティをオーバーライド済み)。
   * @param {number} tenantId
   * @returns {Promise<void>}
   */
  function selectTenant(tenantId) {
    return request(`/api/auth/tenants/${tenantId}/select`, { method: 'POST' });
  }

  /**
   * GET /api/tasks — タスク一覧取得(A-1、listTasks)。
   * @param {Object} [params]
   * @param {string}  [params.targetDate]     - 表示対象日 (YYYY-MM-DD)
   * @param {boolean} [params.includeOverdue] - 期限切れを含める (default: true)
   * @param {string}  [params.status]         - ステータス絞り込み (カンマ区切り可)
   * @param {number}  [params.ownerId]        - 所有者 ID
   * @param {number}  [params.assigneeId]     - 担当者 ID
   * @param {string}  [params.priority]       - 優先度 (HIGH / MEDIUM / LOW)
   * @param {string}  [params.visibility]     - 公開範囲 (TENANT / STAKEHOLDERS / PRIVATE)
   * @param {string}  [params.keyword]        - タイトル/説明部分一致キーワード
   * @param {number}  [params.page]           - ページ番号 (0 始まり)
   * @param {number}  [params.size]           - 1 ページ件数 (max 100)
   * @param {string}  [params.sort]           - ソート指定 (<field>,<asc|desc>)
   * @returns {Promise<TaskPage>}
   */
  function listTasks(params = {}) {
    const q = new URLSearchParams();
    if (params.targetDate != null && params.targetDate !== '')
      q.set('targetDate', params.targetDate);
    if (params.includeOverdue !== undefined)
      q.set('includeOverdue', String(params.includeOverdue));
    if (params.status)     q.set('status',     params.status);
    if (params.ownerId)    q.set('ownerId',    String(params.ownerId));
    if (params.assigneeId) q.set('assigneeId', String(params.assigneeId));
    if (params.priority)   q.set('priority',   params.priority);
    if (params.visibility) q.set('visibility', params.visibility);
    if (params.keyword)    q.set('keyword',    params.keyword);
    if (params.page !== undefined) q.set('page', String(params.page));
    if (params.size !== undefined) q.set('size', String(params.size));
    if (params.sort)       q.set('sort',       params.sort);
    const qs = q.toString();
    return request(`/api/tasks${qs ? '?' + qs : ''}`);
  }

  return { setTenantId, request, getMe, selectTenant, listTasks };
})();
