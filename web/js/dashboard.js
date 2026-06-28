// @ts-check
// 個人ダッシュボード(S-03)。/api/dashboard/summary の数値カードと
// /api/dashboard/tasks の 4 セクション(期限切れ / 今日 / 今後 / 本日完了)を描画する。
// 参照専用のため #810(native @RequestBody 書込 500)の影響を受けない。

/** 「今後」に含める期限の先読み日数(1〜14)。基本設計書 §3.3 の既定値に合わせる。 */
const DUE_WITHIN_DAYS = 3;

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
 * 1 タスクを表すリスト項目を生成する。タイトルクリックで該当タスクの詳細
 * (tasks.html のドロワー)へ遷移する。期限・担当・ステータス・優先度を併記する。
 * @param {Task} task
 * @returns {HTMLLIElement}
 */
function taskItem(task) {
  const li = document.createElement('li');
  li.className = 'dash-task-item d-flex align-items-center flex-wrap gap-2 py-2 border-bottom';

  const link = document.createElement('a');
  link.href = `/tasks/${task.id}`;
  link.className = 'dash-task-title text-truncate fw-medium me-auto';
  link.textContent = task.title;
  li.append(link);

  const status = document.createElement('app-status-badge');
  status.setAttribute('status', task.status);
  li.append(status);

  const priority = document.createElement('app-priority-badge');
  priority.setAttribute('priority', task.priority);
  li.append(priority);

  const due = document.createElement('span');
  due.className = 'small text-muted';
  due.innerHTML = '<i class="bi bi-calendar3 me-1" aria-hidden="true"></i>';
  due.append(document.createTextNode(task.dueDate));
  li.append(due);

  const assignee = document.createElement('span');
  assignee.className = 'small text-muted';
  assignee.innerHTML = '<i class="bi bi-person me-1" aria-hidden="true"></i>';
  assignee.append(document.createTextNode(task.assignee?.fullName || '未割当'));
  li.append(assignee);

  return li;
}

/**
 * タスクセクション(リスト + 件数バッジ)を描画する。空セクションは控えめな案内を出す。
 * @param {string} listId
 * @param {string} countId
 * @param {Task[]} tasks
 * @param {string} emptyMessage
 */
function renderSection(listId, countId, tasks, emptyMessage) {
  /** @type {HTMLElement} */ (mustQuery(document, countId)).textContent = String(tasks.length);
  const list = /** @type {HTMLElement} */ (mustQuery(document, listId));
  list.replaceChildren();
  if (tasks.length === 0) {
    const li = document.createElement('li');
    li.className = 'text-muted small py-2';
    li.textContent = emptyMessage;
    list.append(li);
    return;
  }
  for (const task of tasks) {
    list.append(taskItem(task));
  }
}

/**
 * @param {DashboardSummary} summary
 * @param {DashboardTaskSections} sections
 */
function render(summary, sections) {
  setCard('#card-today-due', summary.todayDueCount);
  setCard('#card-overdue', summary.overdueCount);
  setCard('#card-completed-today', summary.completedTodayCount);
  setCard('#card-my-open', summary.myOpenCount);

  renderSection(
    '#list-overdue',
    '#count-overdue',
    sections.overdue,
    '期限切れのタスクはありません。',
  );
  renderSection('#list-today', '#count-today', sections.today, '今日対応のタスクはありません。');
  renderSection('#list-upcoming', '#count-upcoming', sections.upcoming, '直近の予定はありません。');
  renderSection(
    '#list-completed',
    '#count-completed',
    sections.completedToday,
    '本日完了したタスクはありません。',
  );

  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#content')).classList.remove('d-none');
}

async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) {
    return;
  }

  // SaaS Admin(APP_ADMIN)は業務ダッシュボードの対象外 → プラットフォーム監視へ誘導(S-12)。
  if (Auth.isAppAdmin()) {
    window.location.replace('admin.html');
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
  }

  try {
    const [summary, sections] = await Promise.all([
      Api.getDashboardSummary(),
      Api.getDashboardTasks(DUE_WITHIN_DAYS),
    ]);
    render(summary, sections);
  } catch (err) {
    showError(`ダッシュボードの取得に失敗しました: ${/** @type {any} */ (err).message}`);
  }
}

main();
