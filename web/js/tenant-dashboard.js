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
function setCard(id, value) {
  /** @type {HTMLElement} */ (mustQuery(document, id)).textContent = String(value);
}

/**
 * ブレークダウン表(ラベル + 件数)を描画する。0 件キーも明示する。
 * @param {string} tbodyId
 * @param {Array<{v: string, l: string}>} options 表示順 + ラベル
 * @param {Record<string, number>} counts
 */
function renderBreakdown(tbodyId, options, counts) {
  const tbody = /** @type {HTMLElement} */ (mustQuery(document, tbodyId));
  tbody.replaceChildren();
  for (const { v, l } of options) {
    const tr = document.createElement('tr');
    const th = document.createElement('th');
    th.scope = 'row';
    th.className = 'fw-normal';
    th.textContent = l;
    const td = document.createElement('td');
    td.className = 'text-end fw-semibold';
    td.textContent = String(counts?.[v] ?? 0);
    tr.append(th, td);
    tbody.append(tr);
  }
}

/**
 * @param {TenantDashboardSummary} summary
 */
function render(summary) {
  setCard('#card-total', summary.totalTaskCount);
  setCard('#card-today-due', summary.todayDueCount);
  setCard('#card-overdue', summary.overdueCount);
  setCard('#card-completed-today', summary.completedTodayCount);
  setCard('#card-members', summary.memberCount);

  renderBreakdown('#status-breakdown', TaskMeta.STATUS_OPTIONS, summary.statusBreakdown);
  renderBreakdown('#priority-breakdown', TaskMeta.PRIORITY_OPTIONS, summary.priorityBreakdown);

  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#summary')).classList.remove('d-none');
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

  /** @type {MeResponse} */
  let me;
  try {
    me = await Api.getMe();
  } catch (err) {
    showError(`ユーザー情報の取得に失敗しました: ${/** @type {any} */ (err).message}`);
    return;
  }

  const activeTenantId = Api.resolveActiveTenant(me.tenants);
  if (activeTenantId === null) {
    window.location.replace('index.html');
    return;
  }

  /** @type {AppTenantSwitcherElement} */ (mustQuery(document, '#tenant-switcher')).setData(
    me.tenants,
    activeTenantId,
  );

  const activeTenant = me.tenants?.find((t) => t.id === activeTenantId);
  if (activeTenant?.role === 'TENANT_ADMIN') {
    document.body.classList.add('role-admin');
  } else {
    // Tenant Admin 以外はこのダッシュボードを参照できない(API は 403)。
    showError('このページはテナント管理者のみ閲覧できます。');
    return;
  }

  try {
    const summary = await Api.getTenantDashboardSummary();
    render(summary);
  } catch (err) {
    const e = /** @type {{ status?: number, message?: string }} */ (err);
    if (e.status === 403) {
      showError('このページはテナント管理者のみ閲覧できます。');
    } else {
      showError(`集計の取得に失敗しました: ${e.message ?? '不明なエラー'}`);
    }
  }
}

main();
