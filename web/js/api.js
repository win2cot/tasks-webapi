// @ts-check
/**
 * tasks-webapi 呼び出しラッパー
 *
 * 全リクエストに Authorization: Bearer <token> と X-Tenant-Id ヘッダを付与する。
 * X-Tenant-Id は setTenantId() で設定する(未設定時は省略 — /api/auth/me 等のテナント不要 API 向け)。
 */

// --- API response type definitions (aligned with api/openapi.yaml) ---

/**
 * @typedef {'NOT_STARTED'|'IN_PROGRESS'|'DONE'|'ON_HOLD'} TaskStatus
 * @typedef {'HIGH'|'MEDIUM'|'LOW'} Priority
 * @typedef {'TENANT'|'STAKEHOLDERS'|'PRIVATE'} Visibility
 * @typedef {'TENANT_ADMIN'|'MEMBER'} Role
 */

/**
 * @typedef {object} UserSummary
 * @property {number} id
 * @property {string} fullName
 */

/**
 * @typedef {object} Task
 * @property {number} id
 * @property {number} version
 * @property {string} title
 * @property {string|null} description
 * @property {TaskStatus} status
 * @property {Priority} priority
 * @property {Visibility} visibility
 * @property {UserSummary} owner
 * @property {UserSummary|null} assignee
 * @property {string} dueDate
 * @property {string|null} completedAt
 * @property {string} createdAt
 * @property {string} updatedAt
 * @property {boolean} editable
 * @property {boolean} deletable
 */

/**
 * @typedef {Task & {tenantId: number}} TaskDetail
 */

/**
 * @typedef {object} TaskPage
 * @property {Task[]} content
 * @property {number} totalElements
 * @property {number} totalPages
 * @property {number} number
 * @property {number} size
 * @property {number} overdueCount
 */

/**
 * @typedef {object} TenantUser
 * @property {number} userId
 * @property {string} email
 * @property {string} fullName
 * @property {string|null} [departmentName]
 * @property {Role} role
 * @property {'ACTIVE'|'INVITED'|'DISABLED'} status
 * @property {string} joinedAt
 */

/**
 * @typedef {object} Stakeholder
 * @property {number} userId
 * @property {string} fullName
 * @property {string} email
 * @property {UserSummary} addedBy
 * @property {string} addedAt
 */

/**
 * @typedef {Error & {status: number}} ApiError
 */

/**
 * @typedef {object} TenantRef
 * @property {number} id
 * @property {string} name
 * @property {Role} role
 */

/**
 * @typedef {object} MeResponse
 * @property {{ id: number, fullName: string }} user
 * @property {number|null} activeTenantId
 * @property {TenantRef[]} tenants
 */

const Api = (() => {
  const BASE_URL = 'http://localhost:8080';

  /** @type {string|null} */
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

  /**
   * @param {string} path
   * @param {string} token
   * @param {RequestInit} options
   * @returns {Promise<Response>}
   */
  async function _fetch(path, token, options) {
    // options.headers は呼び出し元が常にプレーンオブジェクトを渡すためキャストが安全
    /** @type {Record<string, string>} */
    const headers = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      .../** @type {Record<string, string>} */ (options.headers ?? {}),
    };

    if (_tenantId !== null) {
      headers['X-Tenant-Id'] = String(_tenantId);
    }

    return fetch(`${BASE_URL}${path}`, { ...options, headers });
  }

  /**
   * @param {Response} response
   * @returns {Promise<any>}
   */
  async function _parseResponse(response) {
    if (!response.ok) {
      const body = await response.text().catch(() => '');
      const err = /** @type {ApiError} */ (
        new Error(`${response.status} ${response.statusText}: ${body}`)
      );
      err.status = response.status;
      throw err;
    }

    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      return response.json();
    }
    return response.text();
  }

  /**
   * @param {Response} response
   * @returns {Promise<never>}
   */
  async function _throwFromResponse(response) {
    const body = await response.text().catch(() => '');
    const err = /** @type {ApiError} */ (
      new Error(`${response.status} ${response.statusText}: ${body}`)
    );
    err.status = response.status;
    throw err;
  }

  /**
   * Returns the raw Response (handles 401 refresh), for callers that need headers.
   * @param {string} path
   * @param {RequestInit} [options]
   * @returns {Promise<Response>}
   */
  async function requestRaw(path, options = {}) {
    const response = await _fetch(path, await Auth.getToken(), options);
    if (response.status === 401) {
      const newToken = await Auth.refreshToken();
      return _fetch(path, newToken, options);
    }
    return response;
  }

  /**
   * GET /api/auth/me — 認証済みユーザー情報を取得する。
   * @returns {Promise<MeResponse>}
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
    if (params.includeOverdue !== undefined) q.set('includeOverdue', String(params.includeOverdue));
    if (params.status) q.set('status', params.status);
    if (params.ownerId) q.set('ownerId', String(params.ownerId));
    if (params.assigneeId) q.set('assigneeId', String(params.assigneeId));
    if (params.priority) q.set('priority', params.priority);
    if (params.visibility) q.set('visibility', params.visibility);
    if (params.keyword) q.set('keyword', params.keyword);
    if (params.page !== undefined) q.set('page', String(params.page));
    if (params.size !== undefined) q.set('size', String(params.size));
    if (params.sort) q.set('sort', params.sort);
    const qs = q.toString();
    return request(`/api/tasks${qs ? `?${qs}` : ''}`);
  }

  /**
   * PATCH /api/tasks/{id} — タスク部分更新(A-29)。
   * @param {number} id
   * @param {string} etag  - W/"<version>" 形式の If-Match 値
   * @param {Object} body  - TaskPatchRequest (title/description/priority/assigneeId/dueDate の任意組合せ)
   * @returns {Promise<TaskDetail>}
   */
  function patchTask(id, etag, body) {
    return request(`/api/tasks/${id}`, {
      method: 'PATCH',
      headers: { 'If-Match': etag },
      body: JSON.stringify(body),
    });
  }

  /**
   * PATCH /api/tasks/{id}/status — ステータス変更(A-16)。
   * @param {number} id
   * @param {string} status - TaskStatus 値
   * @returns {Promise<TaskDetail>}
   */
  function changeStatus(id, status) {
    return request(`/api/tasks/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  }

  /**
   * GET /api/tenant/users — テナント内ユーザー一覧。
   * @returns {Promise<TenantUser[]>}
   */
  function listTenantUsers() {
    return request('/api/tenant/users');
  }

  /**
   * GET /api/tasks/{id} — タスク詳細取得(A-12)。ETag も返す。
   * @param {number} id
   * @returns {Promise<{task: TaskDetail, etag: string|null}>}
   */
  async function getTask(id) {
    const resp = await requestRaw(`/api/tasks/${id}`);
    if (!resp.ok) await _throwFromResponse(resp);
    return { task: await resp.json(), etag: resp.headers.get('ETag') };
  }

  /**
   * POST /api/tasks — タスク作成(A-2)。
   * @param {Object} body — TaskCreateRequest
   * @returns {Promise<Task>}
   */
  function createTask(body) {
    return request('/api/tasks', { method: 'POST', body: JSON.stringify(body) });
  }

  /**
   * DELETE /api/tasks/{id} — タスク論理削除(A-15)。If-Match 必須(ADR-0012)。
   * @param {number} id
   * @param {string} etag — W/"<version>"
   * @returns {Promise<void>}
   */
  function deleteTask(id, etag) {
    return request(`/api/tasks/${id}`, {
      method: 'DELETE',
      headers: { 'If-Match': etag },
    });
  }

  /**
   * PATCH /api/tasks/{id}/visibility — 公開範囲変更(A-17)。
   * @param {number} id
   * @param {string} visibility — Visibility 値
   * @param {number[]} [stakeholderUserIds] visibility=STAKEHOLDERS のとき指定
   * @returns {Promise<Task>}
   */
  function changeVisibility(id, visibility, stakeholderUserIds) {
    /** @type {{visibility: string, stakeholderUserIds?: number[]}} */
    const body = { visibility };
    if (stakeholderUserIds) body.stakeholderUserIds = stakeholderUserIds;
    return request(`/api/tasks/${id}/visibility`, { method: 'PATCH', body: JSON.stringify(body) });
  }

  /**
   * GET /api/tasks/{id}/stakeholders — 関係者一覧取得。
   * @param {number} id
   * @returns {Promise<Stakeholder[]>}
   */
  function listStakeholders(id) {
    return request(`/api/tasks/${id}/stakeholders`);
  }

  /**
   * POST /api/tasks/{id}/stakeholders — 関係者追加(A-19)。
   * @param {number} taskId
   * @param {number} userId
   * @returns {Promise<Stakeholder>}
   */
  function addStakeholder(taskId, userId) {
    return request(`/api/tasks/${taskId}/stakeholders`, {
      method: 'POST',
      body: JSON.stringify({ userId }),
    });
  }

  /**
   * DELETE /api/tasks/{taskId}/stakeholders/{userId} — 関係者削除(A-20)。
   * @param {number} taskId
   * @param {number} userId
   * @returns {Promise<void>}
   */
  function removeStakeholder(taskId, userId) {
    return request(`/api/tasks/${taskId}/stakeholders/${userId}`, { method: 'DELETE' });
  }

  return {
    setTenantId,
    request,
    requestRaw,
    getMe,
    selectTenant,
    listTasks,
    getTask,
    createTask,
    patchTask,
    deleteTask,
    changeStatus,
    changeVisibility,
    listTenantUsers,
    listStakeholders,
    addStakeholder,
    removeStakeholder,
  };
})();
