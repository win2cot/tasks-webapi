// @ts-check

/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

/**
 * エラーメッセージを表示し、ローディングを止める。
 * @param {string} message
 */
function showError(message) {
  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  const el = /** @type {HTMLElement} */ (mustQuery(document, '#error-alert'));
  el.textContent = message;
  el.classList.remove('d-none');
}

/**
 * 数値カードへ件数を反映する。
 * @param {string} id
 * @param {number} value
 */
function setMetric(id, value) {
  /** @type {HTMLElement} */ (mustQuery(document, id)).textContent = value.toLocaleString('ja-JP');
}

/**
 * @param {PlatformMetrics} m
 */
function render(m) {
  setMetric('#m-total-tenants', m.totalTenants);
  setMetric('#m-active-tenants', m.activeTenants);
  setMetric('#m-suspended-tenants', m.suspendedTenants);
  setMetric('#m-total-users', m.totalUsers);
  setMetric('#m-total-tasks', m.totalTasks);
  setMetric('#m-new-24h', m.newTenantsLast24h);

  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#metrics')).classList.remove('d-none');
}

async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) {
    return;
  }

  const user = Auth.getUser();
  const displayName = user?.name || user?.preferred_username || '';
  /** @type {HTMLElement} */ (mustQuery(document, '#nav-username')).textContent = displayName;
  /** @type {HTMLElement} */ (mustQuery(document, '#user-avatar')).textContent =
    displayName.slice(0, 1) || '?';

  if (!Auth.isAppAdmin()) {
    showError('このページは SaaS 運営者(APP_ADMIN)のみ利用できます。');
    return;
  }

  // プラットフォーム API はテナント非依存。残存する X-Tenant-Id を送らないようクリアする。
  Api.setTenantId(null);

  try {
    const metrics = await Api.getPlatformMetrics();
    render(metrics);
  } catch (err) {
    const e = /** @type {{ status?: number, message?: string }} */ (err);
    showError(
      e.status === 403
        ? 'このページは SaaS 運営者(APP_ADMIN)のみ利用できます。'
        : `メトリクスの取得に失敗しました: ${e.message ?? '不明なエラー'}`,
    );
  }
}

main();
