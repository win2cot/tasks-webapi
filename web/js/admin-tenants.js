// @ts-check

/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

const TENANT_PAGE_SIZE = 50;

/** 現在のページ番号(0 始まり)。 */
let tenantListPage = 0;
/** 現在の状態フィルタ(空はすべて)。 @type {'ACTIVE'|'SUSPENDED'|''} */
let tenantListStatus = '';
/** 現在の検索キーワード。 */
let tenantListKeyword = '';

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
 * テナント状態バッジの [ラベル, Bootstrap クラス] を返す。
 * @param {string} status
 * @returns {[string, string]}
 */
function statusBadge(status) {
  if (status === 'ACTIVE') return ['ACTIVE', 'text-bg-success'];
  if (status === 'SUSPENDED') return ['SUSPENDED', 'text-bg-warning'];
  return [status, 'text-bg-secondary'];
}

/**
 * ISO 日時を YYYY-MM-DD 表記にする。
 * @param {string} iso
 * @returns {string}
 */
function formatDate(iso) {
  return iso ? iso.slice(0, 10) : '';
}

/**
 * 1 テナント分の行要素を生成する。テナント名は S-14 詳細へのリンク。
 * @param {Tenant} t
 * @returns {HTMLTableRowElement}
 */
function buildRow(t) {
  const tr = document.createElement('tr');

  const tdName = document.createElement('td');
  const link = document.createElement('a');
  link.href = `admin-tenant-detail.html?id=${t.id}`;
  link.textContent = t.name;
  link.className = 'fw-semibold text-decoration-none';
  tdName.append(link);

  const tdCode = document.createElement('td');
  tdCode.className = 'text-muted small font-monospace';
  tdCode.textContent = t.code;

  const tdPlan = document.createElement('td');
  tdPlan.textContent = t.plan;

  const tdStatus = document.createElement('td');
  const [label, cls] = statusBadge(t.status);
  const badge = document.createElement('span');
  badge.className = `badge ${cls}`;
  badge.textContent = label;
  tdStatus.append(badge);

  const tdUsers = document.createElement('td');
  tdUsers.className = 'text-end';
  tdUsers.textContent = t.userCount.toLocaleString('ja-JP');

  const tdTasks = document.createElement('td');
  tdTasks.className = 'text-end';
  tdTasks.textContent = t.taskCount.toLocaleString('ja-JP');

  const tdCreated = document.createElement('td');
  tdCreated.className = 'text-muted small';
  tdCreated.textContent = formatDate(t.createdAt);

  tr.append(tdName, tdCode, tdPlan, tdStatus, tdUsers, tdTasks, tdCreated);
  return tr;
}

/**
 * 一覧ページを描画する。
 * @param {TenantPage} page
 */
function render(page) {
  const tbody = /** @type {HTMLElement} */ (mustQuery(document, '#tenants-tbody'));
  tbody.replaceChildren();
  for (const t of page.content) {
    tbody.append(buildRow(t));
  }

  const empty = /** @type {HTMLElement} */ (mustQuery(document, '#empty-message'));
  empty.classList.toggle('d-none', page.content.length > 0);

  const from = page.totalElements === 0 ? 0 : page.number * page.size + 1;
  const to = page.number * page.size + page.content.length;
  /** @type {HTMLElement} */ (mustQuery(document, '#page-info')).textContent =
    `${page.totalElements.toLocaleString('ja-JP')} 件中 ${from}–${to} 件(${page.number + 1} / ${Math.max(page.totalPages, 1)} ページ)`;

  /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-prev')).disabled = page.number <= 0;
  /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-next')).disabled =
    page.number + 1 >= page.totalPages;

  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#result')).classList.remove('d-none');
}

/** 現在のフィルタ条件でテナント一覧を取得して描画する。 */
async function load() {
  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.remove('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#error-alert')).classList.add('d-none');
  try {
    /** @type {{status?: 'ACTIVE'|'SUSPENDED', keyword?: string, page: number, size: number}} */
    const params = { page: tenantListPage, size: TENANT_PAGE_SIZE };
    if (tenantListStatus) params.status = tenantListStatus;
    if (tenantListKeyword) params.keyword = tenantListKeyword;
    const page = await Api.listTenants(params);
    render(page);
  } catch (err) {
    const e = /** @type {{ status?: number, message?: string }} */ (err);
    showError(
      e.status === 403
        ? 'このページは SaaS 運営者(APP_ADMIN)のみ利用できます。'
        : `テナント一覧の取得に失敗しました: ${e.message ?? '不明なエラー'}`,
    );
  }
}

/** フィルタフォームとページングボタンのイベントを登録する。 */
function wireControls() {
  const form = /** @type {HTMLFormElement} */ (mustQuery(document, '#filter-form'));
  const statusSelect = /** @type {HTMLSelectElement} */ (mustQuery(document, '#filter-status'));
  const keywordInput = /** @type {HTMLInputElement} */ (mustQuery(document, '#filter-keyword'));

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    tenantListStatus = /** @type {'ACTIVE'|'SUSPENDED'|''} */ (statusSelect.value);
    tenantListKeyword = keywordInput.value.trim();
    tenantListPage = 0;
    load();
  });

  /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-prev')).addEventListener(
    'click',
    () => {
      if (tenantListPage > 0) {
        tenantListPage -= 1;
        load();
      }
    },
  );
  /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-next')).addEventListener(
    'click',
    () => {
      tenantListPage += 1;
      load();
    },
  );
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

  wireControls();
  await load();
}

main();
