// ---- Page state ----
let currentPage = 0;
let currentSort = 'dueDate,asc';
let totalPages = 1;
let totalElements = 0;
let currentKeyword = ''; // タスク検索キーワード(タイトル・説明部分一致、#669)
let overdueTotal = 0;
let currentTargetDate = ''; // 表示対象日 (YYYY-MM-DD, JST)
const PAGE_SIZE = 50;

// 当日 (JST) を YYYY-MM-DD で返す。
function todayDateStr() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

// YYYY-MM-DD に delta 日を加算した YYYY-MM-DD を返す。
// 日付演算は UTC で行い、タイムゾーン依存のずれを避ける(#666)。
/**
 * @param {string} dateStr — YYYY-MM-DD
 * @param {number} delta — 加算する日数(負値で減算)
 * @returns {string} YYYY-MM-DD
 */
function addDays(dateStr, delta) {
  const [y, m, d] = dateStr.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + delta));
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'UTC',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(dt);
}

// YYYY-MM-DD を「M月D日(曜)」の和文表記に整形する(表示基準日ラベル用)。
/** @param {string} dateStr — YYYY-MM-DD */
function formatDateLabel(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  const wd = ['日', '月', '火', '水', '木', '金', '土'][dt.getUTCDay()];
  return `${m}月${d}日(${wd})`;
}

// ---- Task / user state ----
const taskMap = new Map(); // taskId(number) → Task object
const rowMap = new Map(); // taskId(number) → app-task-row element
/** @type {number | null} */
let currentUserId = null;
/** @type {TenantUser[]} */
let tenantUsers = [];

// ---- CE references ----
const errorBanner = /** @type {AppErrorBannerElement} */ (mustQuery(document, '#error-banner'));
const conflictBanner = /** @type {AppConflictBannerElement} */ (
  mustQuery(document, '#conflict-banner')
);
const descPopover = /** @type {AppDescPopoverElement} */ (mustQuery(document, '#desc-popover'));
const taskDrawer = /** @type {AppTaskDrawerElement} */ (mustQuery(document, '#task-drawer'));
const pager = /** @type {AppPagerElement} */ (mustQuery(document, '#task-pager'));
const tbody = /** @type {HTMLElement} */ (mustQuery(document, '#task-tbody'));

// ---- Date navigation refs (#666) ----
const datePicker = /** @type {HTMLInputElement} */ (mustQuery(document, '#date-picker'));
const btnDateToday = /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-date-today'));

// ETag format per ADR-0012: W/"<version>"
/** @param {{ version: number }} task */
function buildEtag(task) {
  return `W/"${task.version}"`;
}

// ---- Render task list from API response ----
// 取得したタスクを「期限切れ」「本日(選択日)」の 2 セクションに分けて描画する
// (基本設計書 §3.3.1)。期限切れ = dueDate < 当日 かつ 未完了。期限切れは選択日に関わらず
// 当日基準で常時上部に表示する(#667)。各セクションは常時表示し、空の場合は空状態行を表示する。
/** @param {Task[]} tasks */
function renderTasks(tasks) {
  /** @type {HTMLElement} */ (mustQuery(document, '#total-count')).textContent =
    `${totalElements} 件`;
  rowMap.clear();

  const today = todayDateStr();
  /** @param {Task} t */
  const isOverdue = (t) => t.dueDate != null && t.dueDate < today && t.status !== 'DONE';

  const list = tasks ?? [];
  const overdue = list.filter(isOverdue);
  const onTarget = list.filter((t) => !isOverdue(t));
  const targetCount = Math.max(0, totalElements - overdueTotal);

  // 表示基準日が当日なら「本日」、それ以外は選択日のラベルを表示する(#666)。
  const isToday = currentTargetDate === todayDateStr();
  const targetLabel = isToday ? '本日' : formatDateLabel(currentTargetDate);
  const targetEmpty = isToday ? '本日のタスクはありません' : `${targetLabel}のタスクはありません`;

  /** @type {Node[]} */
  const nodes = [];
  nodes.push(groupHeadRow('bi-exclamation-triangle-fill text-danger', '期限切れ', overdueTotal));
  nodes.push(...sectionRows(overdue, '期限切れのタスクはありません'));
  nodes.push(groupHeadRow('bi-calendar-day text-primary', targetLabel, targetCount));
  nodes.push(...sectionRows(onTarget, targetEmpty));
  tbody.replaceChildren(...nodes);
}

// ---- Section group header row ----
/**
 * @param {string} iconClass — Bootstrap Icons クラス (色クラス込み可)
 * @param {string} label
 * @param {number} count
 * @returns {HTMLTableRowElement}
 */
function groupHeadRow(iconClass, label, count) {
  const tr = document.createElement('tr');
  tr.className = 'group-head';
  const td = document.createElement('td');
  td.colSpan = 7;
  const icon = document.createElement('i');
  icon.className = `bi ${iconClass} me-2`;
  icon.setAttribute('aria-hidden', 'true');
  td.appendChild(icon);
  td.appendChild(document.createTextNode(label));
  const cnt = document.createElement('span');
  cnt.className = 'sec-count ms-2';
  cnt.textContent = `${count} 件`;
  td.appendChild(cnt);
  tr.appendChild(td);
  return tr;
}

// ---- Build rows (or an empty-state row) for one section ----
/**
 * @param {Task[]} tasks
 * @param {string} emptyMsg
 * @returns {Node[]}
 */
function sectionRows(tasks, emptyMsg) {
  if (tasks.length === 0) {
    const tr = document.createElement('tr');
    tr.className = 'empty-row';
    const td = document.createElement('td');
    td.colSpan = 7;
    const icon = document.createElement('i');
    icon.className = 'bi bi-inbox me-2';
    icon.setAttribute('aria-hidden', 'true');
    td.appendChild(icon);
    td.appendChild(
      document.createTextNode(
        currentKeyword ? `「${currentKeyword}」に一致するタスクはありません` : emptyMsg,
      ),
    );
    tr.appendChild(td);
    return [tr];
  }
  return tasks.map((task) => {
    const row = /** @type {AppTaskRowElement} */ (document.createElement('app-task-row'));
    row.setTask(task, currentUserId, tenantUsers);
    rowMap.set(task.id, row);
    return row;
  });
}

// ---- Re-render a single row after a successful PATCH ----
/** @param {number} taskId */
function rerenderRow(taskId) {
  const task = taskMap.get(taskId);
  const row = rowMap.get(taskId);
  if (!task || !row) return;
  row.setTask(task, currentUserId, tenantUsers);
}

// ---- Inline status change (PATCH /api/tasks/{id}/status) ----
/**
 * @param {number} taskId
 * @param {string} status
 * @param {HTMLSelectElement} selectEl
 */
async function onStatusChange(taskId, status, selectEl) {
  selectEl.disabled = true;
  try {
    const updated = await Api.changeStatus(taskId, status);
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId); // revert
    errorBanner.show(`ステータスの更新に失敗しました: ${/** @type {any} */ (err).message || ''}`);
  }
}

// ---- Inline priority change (PATCH /api/tasks/{id} with ETag) ----
/**
 * @param {number} taskId
 * @param {string} priority
 * @param {HTMLSelectElement} selectEl
 */
async function onPriorityChange(taskId, priority, selectEl) {
  const task = taskMap.get(taskId);
  if (!task) return;
  selectEl.disabled = true;
  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), { priority });
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId);
    if (/** @type {any} */ (err).status === 412) conflictBanner.show();
    else errorBanner.show(`優先度の更新に失敗しました: ${/** @type {any} */ (err).message || ''}`);
  }
}

// ---- Field commit (title / dueDate / assigneeId via app-task-row) ----
/**
 * @param {number} taskId
 * @param {string} field
 * @param {string | null} value
 */
async function onFieldCommit(taskId, field, value) {
  const task = taskMap.get(taskId);
  if (!task) return;

  /** @type {Record<string, unknown> | undefined} */
  let body;
  if (field === 'assigneeId') body = { assigneeId: value ? Number(value) : null };
  else if (field === 'dueDate') body = { dueDate: value };
  else if (field === 'title') body = { title: value };
  if (!body) return;

  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), body);
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId);
    if (/** @type {any} */ (err).status === 412) conflictBanner.show();
    else errorBanner.show(`更新に失敗しました: ${/** @type {any} */ (err).message || ''}`);
  }
}

// ---- Description save (from app-desc-popover) ----
/**
 * @param {number} taskId
 * @param {string | null} value
 */
async function onDescCommit(taskId, value) {
  const task = taskMap.get(taskId);
  if (!task) return;
  if (value === (task.description || null)) return;
  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), { description: value });
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    if (/** @type {any} */ (err).status === 412) conflictBanner.show();
    else errorBanner.show(`説明の更新に失敗しました: ${/** @type {any} */ (err).message || ''}`);
  }
}

// ---- Cancel all active inline edits (called before reload) ----
function cancelAllEdits() {
  for (const row of rowMap.values()) row.cancelEdit();
  if (descPopover.isOpen) descPopover.close();
}

// ---- UI state helpers (design-system.md §6.11) ----
/** @param {boolean} on */
function showLoading(on) {
  /** @type {HTMLElement} */ (mustQuery(document, '#loading-skeleton')).classList.toggle(
    'd-none',
    !on,
  );
  /** @type {HTMLElement} */ (mustQuery(document, '#task-panel')).classList.toggle('d-none', on);
}

// ---- Task list (GET /api/tasks) ----
async function loadTasks() {
  errorBanner.hide();
  conflictBanner.hide();
  cancelAllEdits();
  showLoading(true);
  try {
    const data = await Api.listTasks({
      page: currentPage,
      size: PAGE_SIZE,
      sort: currentSort,
      keyword: currentKeyword || undefined,
      targetDate: currentTargetDate,
      includeOverdue: true,
    });
    totalPages = data.totalPages || 1;
    totalElements = data.totalElements || 0;
    overdueTotal = data.overdueCount || 0;
    taskMap.clear();
    if (data.content)
      data.content.forEach((t) => {
        taskMap.set(t.id, t);
      });
    renderTasks(data.content);
    pager.update({ currentPage, totalPages, totalElements, pageSize: PAGE_SIZE });
    showLoading(false);
  } catch (err) {
    /** @type {HTMLElement} */ (mustQuery(document, '#loading-skeleton')).classList.add('d-none');
    errorBanner.show(/** @type {any} */ (err).message || 'タスクの取得に失敗しました');
  }
}

// ---- Sort ----
/** @param {string} value */
function onSortChange(value) {
  currentSort = value;
  currentPage = 0;
  updateSortCarets();
  loadTasks();
}

/** @param {string} field */
function cycleSort(field) {
  const [f, d] = currentSort.split(',');
  currentSort = f === field ? `${field},${d === 'asc' ? 'desc' : 'asc'}` : `${field},asc`;
  const sel = /** @type {HTMLSelectElement} */ (mustQuery(document, '#sortSel'));
  const match = Array.from(sel.options).find((o) => o.value === currentSort);
  if (match) sel.value = currentSort;
  currentPage = 0;
  updateSortCarets();
  loadTasks();
}

function updateSortCarets() {
  const [f, d] = currentSort.split(',');
  ['dueDate', 'priority'].forEach((field) => {
    const el = document.getElementById(`sort-caret-${field}`);
    if (!el) return;
    el.className =
      f === field
        ? d === 'asc'
          ? 'bi bi-caret-up-fill'
          : 'bi bi-caret-down-fill'
        : 'bi bi-chevron-expand';
  });
}

// ---- Keyword search (#669, GET /api/tasks?keyword=) ----
/** @type {ReturnType<typeof setTimeout> | undefined} */
let searchDebounce;
const SEARCH_DEBOUNCE_MS = 300;

const searchInput = /** @type {HTMLInputElement} */ (mustQuery(document, '#search-input'));
const searchClear = /** @type {HTMLElement} */ (mustQuery(document, '#search-clear'));

/** 入力値を確定し、変化があれば 1 ページ目から再取得する。 */
function applyKeyword() {
  const next = searchInput.value.trim();
  searchClear.classList.toggle('d-none', next === '');
  if (next === currentKeyword) return;
  currentKeyword = next;
  currentPage = 0;
  loadTasks();
}

// ---- Date navigation (#666, 基本設計書 §3.3.1) ----
// 表示基準日を変更し、ピッカー値・「今日に戻る」ボタンの活性を同期して再取得する。
/** @param {string} dateStr — YYYY-MM-DD */
function setTargetDate(dateStr) {
  if (!dateStr || dateStr === currentTargetDate) return;
  currentTargetDate = dateStr;
  datePicker.value = dateStr;
  btnDateToday.disabled = dateStr === todayDateStr();
  currentPage = 0;
  loadTasks();
}

/** @param {number} delta — 加算する日数(前日 = -1, 翌日 = +1) */
function shiftTargetDate(delta) {
  setTargetDate(addDays(currentTargetDate, delta));
}

// ---- Row click → open detail drawer (S-05) ----
/** @param {number} id */
function onRowClick(id) {
  taskDrawer.openDetail(id);
}

// ---- Event listeners on CEs ----

// app-task-row events (bubble up through tbody)
tbody.addEventListener('task-status-change', (e) => {
  const { taskId, status, selectEl } = /** @type {CustomEvent} */ (e).detail;
  onStatusChange(taskId, status, selectEl);
});
tbody.addEventListener('task-priority-change', (e) => {
  const { taskId, priority, selectEl } = /** @type {CustomEvent} */ (e).detail;
  onPriorityChange(taskId, priority, selectEl);
});
tbody.addEventListener('task-field-commit', (e) => {
  const { taskId, field, value } = /** @type {CustomEvent} */ (e).detail;
  onFieldCommit(taskId, field, value);
});
tbody.addEventListener('task-row-click', (e) =>
  onRowClick(/** @type {CustomEvent} */ (e).detail.taskId),
);
tbody.addEventListener('task-desc-open', (e) => {
  const { taskId, triggerEl } = /** @type {CustomEvent} */ (e).detail;
  const task = taskMap.get(taskId);
  if (!task?.editable) return;
  descPopover.open(taskId, triggerEl, task.description);
});

// app-desc-popover events
descPopover.addEventListener('desc-commit', (e) => {
  const { taskId, value } = /** @type {CustomEvent} */ (e).detail;
  onDescCommit(taskId, value);
});

// app-error-banner retry
errorBanner.addEventListener('error-retry', loadTasks);

// app-conflict-banner
conflictBanner.addEventListener('conflict-reload', () => {
  conflictBanner.hide();
  loadTasks();
});

// app-task-drawer events
taskDrawer.addEventListener('drawer-task-created', () => loadTasks());
taskDrawer.addEventListener('drawer-task-deleted', () => loadTasks());
taskDrawer.addEventListener('drawer-task-updated', (e) => {
  const updated = /** @type {CustomEvent} */ (e).detail?.task;
  if (!updated) {
    loadTasks();
    return;
  }
  taskMap.set(updated.id, updated);
  rerenderRow(updated.id);
});

// app-pager
pager.addEventListener('page-change', (e) => {
  const p = /** @type {CustomEvent} */ (e).detail.page;
  if (p < 0 || p >= totalPages) return;
  currentPage = p;
  loadTasks();
});

// Static controls
/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);
/** @type {HTMLElement} */ (mustQuery(document, '#sortSel')).addEventListener('change', (e) =>
  onSortChange(/** @type {HTMLSelectElement} */ (e.target).value),
);
/** @type {HTMLElement} */ (mustQuery(document, '#btn-new-task')).addEventListener('click', () =>
  // 新規タスクの期限初期値は表示中の日付(基本設計書 §3.3.1)。
  taskDrawer.openNew({ dueDate: currentTargetDate }),
);

// Date navigation: prev / next / today (#666)
/** @type {HTMLElement} */ (mustQuery(document, '#btn-date-prev')).addEventListener('click', () =>
  shiftTargetDate(-1),
);
/** @type {HTMLElement} */ (mustQuery(document, '#btn-date-next')).addEventListener('click', () =>
  shiftTargetDate(1),
);
btnDateToday.addEventListener('click', () => setTargetDate(todayDateStr()));
datePicker.addEventListener('change', () => {
  // ピッカーが空(クリア)になった場合は当日へ戻す。
  setTargetDate(datePicker.value || todayDateStr());
});
/** @type {HTMLElement} */ (mustQuery(document, '#th-dueDate')).addEventListener('click', () =>
  cycleSort('dueDate'),
);
/** @type {HTMLElement} */ (mustQuery(document, '#th-priority')).addEventListener('click', () =>
  cycleSort('priority'),
);

// Search: debounce while typing, immediate on submit (Enter) / clear.
searchInput.addEventListener('input', () => {
  clearTimeout(searchDebounce);
  searchDebounce = setTimeout(applyKeyword, SEARCH_DEBOUNCE_MS);
});
/** @type {HTMLFormElement} */ (mustQuery(document, '#search-form')).addEventListener(
  'submit',
  (e) => {
    e.preventDefault();
    clearTimeout(searchDebounce);
    applyKeyword();
  },
);
searchClear.addEventListener('click', () => {
  clearTimeout(searchDebounce);
  searchInput.value = '';
  searchInput.focus();
  applyKeyword();
});

// ---- Init ----
async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) return;

  /** @type {MeResponse} */
  let me;
  try {
    me = await Api.getMe();
  } catch (err) {
    errorBanner.show(`ユーザー情報の取得に失敗しました: ${/** @type {any} */ (err).message}`);
    return;
  }

  currentUserId = me.user?.id ?? null;
  taskDrawer.setCurrentUser(currentUserId);

  const user = Auth.getUser();
  const displayName = user?.name || user?.preferred_username || '';
  /** @type {HTMLElement} */ (mustQuery(document, '#nav-username')).textContent = displayName;
  /** @type {HTMLElement} */ (mustQuery(document, '#user-avatar')).textContent =
    displayName.slice(0, 1) || '?';

  const activeTenantId = Api.resolveActiveTenant(me.tenants);

  if (activeTenantId === null) {
    window.location.replace('index.html');
    return;
  }

  /** @type {AppTenantSwitcherElement} */ (mustQuery(document, '#tenant-switcher')).setData(
    me.tenants,
    activeTenantId,
  );

  // Reflect Tenant Admin role in sidebar
  const activeTenant = me.tenants?.find((t) => t.id === activeTenantId);
  if (activeTenant?.role === 'TENANT_ADMIN') {
    document.body.classList.add('role-admin');
  }

  try {
    tenantUsers = await Api.listTenantUsers();
  } catch {
    tenantUsers = [];
  }
  taskDrawer.setUsers(tenantUsers);

  // 表示基準日の初期値は当日 (JST)。ピッカーへ反映し「今日に戻る」を無効化する(#666)。
  currentTargetDate = todayDateStr();
  datePicker.value = currentTargetDate;
  btnDateToday.disabled = true;

  updateSortCarets();
  await loadTasks();

  // Auto-open drawer based on URL path (direct URL / page reload with task URL)
  const path = location.pathname;
  const newMatch = /\/tasks\/new\/?$/.exec(path);
  const editMatch = /\/tasks\/(\d+)\/edit\/?$/.exec(path);
  const detailMatch = /\/tasks\/(\d+)\/?$/.exec(path);
  if (newMatch) {
    taskDrawer.openNew({ dueDate: currentTargetDate });
  } else if (editMatch) {
    await taskDrawer.openEdit(Number(editMatch[1]));
  } else if (detailMatch) {
    await taskDrawer.openDetail(Number(detailMatch[1]));
  }
}

main();
