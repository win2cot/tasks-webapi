// ---- Page state ----
let currentPage = 0;
let currentSort = 'dueDate,asc';
let totalPages    = 1;
let totalElements = 0;
const PAGE_SIZE = 50;

// ---- Task / user state ----
let taskMap      = new Map(); // taskId(number) → Task object
let rowMap       = new Map(); // taskId(number) → app-task-row element
let currentUserId = null;
let tenantUsers   = [];

// ---- CE references ----
const errorBanner    = document.getElementById('error-banner');
const conflictBanner = document.getElementById('conflict-banner');
const descPopover    = document.getElementById('desc-popover');
const taskDrawer     = document.getElementById('task-drawer');
const pager          = document.getElementById('task-pager');
const tbody          = document.getElementById('task-tbody');

// ETag format per ADR-0012: W/"<version>"
function buildEtag(task) {
  return `W/"${task.version}"`;
}

// ---- Render task list from API response ----
function renderTasks(tasks) {
  document.getElementById('total-count').textContent = `${totalElements} 件`;
  rowMap.clear();

  if (!tasks || tasks.length === 0) {
    const tr = document.createElement('tr');
    tr.className = 'empty-row';
    const td = document.createElement('td');
    td.colSpan = 7;
    const icon = document.createElement('i');
    icon.className = 'bi bi-inbox me-2';
    icon.setAttribute('aria-hidden', 'true');
    td.appendChild(icon);
    td.appendChild(document.createTextNode('タスクはありません'));
    tr.appendChild(td);
    tbody.replaceChildren(tr);
    return;
  }

  const rows = tasks.map(task => {
    const row = document.createElement('app-task-row');
    row.setTask(task, currentUserId, tenantUsers);
    rowMap.set(task.id, row);
    return row;
  });
  tbody.replaceChildren(...rows);
}

// ---- Re-render a single row after a successful PATCH ----
function rerenderRow(taskId) {
  const task = taskMap.get(taskId);
  const row  = rowMap.get(taskId);
  if (!task || !row) return;
  row.setTask(task, currentUserId, tenantUsers);
}

// ---- Inline status change (PATCH /api/tasks/{id}/status) ----
async function onStatusChange(taskId, status, selectEl) {
  selectEl.disabled = true;
  try {
    const updated = await Api.changeStatus(taskId, status);
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId); // revert
    // PATCH /api/tasks/{id}/status は ADR-0012 の If-Match 対象外のため 412 は理論上返らない
    if (err.status === 412) conflictBanner.show();
    else errorBanner.show('ステータスの更新に失敗しました: ' + (err.message || ''));
  }
}

// ---- Inline priority change (PATCH /api/tasks/{id} with ETag) ----
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
    if (err.status === 412) conflictBanner.show();
    else errorBanner.show('優先度の更新に失敗しました: ' + (err.message || ''));
  }
}

// ---- Field commit (title / dueDate / assigneeId via app-task-row) ----
async function onFieldCommit(taskId, field, value) {
  const task = taskMap.get(taskId);
  if (!task) return;

  let body;
  if (field === 'assigneeId') body = { assigneeId: value ? Number(value) : null };
  else if (field === 'dueDate') body = { dueDate: value };
  else if (field === 'title')   body = { title: value };
  if (!body) return;

  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), body);
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId);
    if (err.status === 412) conflictBanner.show();
    else errorBanner.show('更新に失敗しました: ' + (err.message || ''));
  }
}

// ---- Description save (from app-desc-popover) ----
async function onDescCommit(taskId, value) {
  const task = taskMap.get(taskId);
  if (!task) return;
  if (value === (task.description || null)) return;
  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), { description: value });
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    if (err.status === 412) conflictBanner.show();
    else errorBanner.show('説明の更新に失敗しました: ' + (err.message || ''));
  }
}

// ---- Cancel all active inline edits (called before reload) ----
function cancelAllEdits() {
  for (const row of rowMap.values()) row.cancelEdit();
  if (descPopover.isOpen) descPopover.close();
}

// ---- UI state helpers (design-system.md §6.11) ----
function showLoading(on) {
  document.getElementById('loading-skeleton').classList.toggle('d-none', !on);
  document.getElementById('task-panel').classList.toggle('d-none', on);
}

// ---- Task list (GET /api/tasks) ----
async function loadTasks() {
  errorBanner.hide();
  conflictBanner.hide();
  cancelAllEdits();
  showLoading(true);
  try {
    const data = await Api.listTasks({ page: currentPage, size: PAGE_SIZE, sort: currentSort });
    totalPages    = data.totalPages    || 1;
    totalElements = data.totalElements || 0;
    taskMap.clear();
    if (data.content) data.content.forEach(t => taskMap.set(t.id, t));
    renderTasks(data.content);
    pager.update({ currentPage, totalPages, totalElements, pageSize: PAGE_SIZE });
    showLoading(false);
  } catch (err) {
    document.getElementById('loading-skeleton').classList.add('d-none');
    errorBanner.show(err.message || 'タスクの取得に失敗しました');
  }
}

// ---- Sort ----
function onSortChange(value) {
  currentSort = value;
  currentPage = 0;
  updateSortCarets();
  loadTasks();
}

function cycleSort(field) {
  const [f, d] = currentSort.split(',');
  currentSort = f === field
    ? `${field},${d === 'asc' ? 'desc' : 'asc'}`
    : `${field},asc`;
  const sel = document.getElementById('sortSel');
  const match = [...sel.options].find(o => o.value === currentSort);
  if (match) sel.value = currentSort;
  currentPage = 0;
  updateSortCarets();
  loadTasks();
}

function updateSortCarets() {
  const [f, d] = currentSort.split(',');
  ['dueDate', 'priority'].forEach(field => {
    const el = document.getElementById(`sort-caret-${field}`);
    if (!el) return;
    el.className = f === field
      ? (d === 'asc' ? 'bi bi-caret-up-fill' : 'bi bi-caret-down-fill')
      : 'bi bi-chevron-expand';
  });
}

// ---- Row click (S-05 detail — wired in later sprint) ----
function onRowClick(id) {
  console.log('task row clicked:', id);
}

// ---- Event listeners on CEs ----

// app-task-row events (bubble up through tbody)
tbody.addEventListener('task-status-change',   e => onStatusChange(e.detail.taskId, e.detail.status, e.detail.selectEl));
tbody.addEventListener('task-priority-change', e => onPriorityChange(e.detail.taskId, e.detail.priority, e.detail.selectEl));
tbody.addEventListener('task-field-commit',    e => onFieldCommit(e.detail.taskId, e.detail.field, e.detail.value));
tbody.addEventListener('task-row-click',       e => onRowClick(e.detail.taskId));
tbody.addEventListener('task-desc-open', e => {
  const task = taskMap.get(e.detail.taskId);
  if (!task?.editable) return;
  descPopover.open(e.detail.taskId, e.detail.triggerEl, task.description);
});

// app-desc-popover events
descPopover.addEventListener('desc-commit', e => onDescCommit(e.detail.taskId, e.detail.value));

// app-error-banner retry
errorBanner.addEventListener('error-retry', loadTasks);

// app-conflict-banner
conflictBanner.addEventListener('conflict-reload', () => { conflictBanner.hide(); loadTasks(); });

// app-pager
pager.addEventListener('page-change', e => {
  const p = e.detail.page;
  if (p < 0 || p >= totalPages) return;
  currentPage = p;
  loadTasks();
});

// Static controls
document.getElementById('btn-logout').addEventListener('click', () => Auth.logout());
document.getElementById('sortSel').addEventListener('change',  e => onSortChange(e.target.value));
document.getElementById('btn-new-task').addEventListener('click', () => taskDrawer.open());
document.getElementById('th-dueDate').addEventListener('click', () => cycleSort('dueDate'));
document.getElementById('th-priority').addEventListener('click', () => cycleSort('priority'));

// ---- Init ----
async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) return;

  let me;
  try {
    me = await Api.getMe();
  } catch (err) {
    errorBanner.show('ユーザー情報の取得に失敗しました: ' + err.message);
    return;
  }

  currentUserId = me.user?.id ?? null;

  const user = Auth.getUser();
  const displayName = user?.name || user?.preferred_username || '';
  document.getElementById('nav-username').textContent = displayName;
  document.getElementById('user-avatar').textContent  = displayName.slice(0, 1) || '?';

  if (!me.activeTenantId) {
    window.location.replace('index.html');
    return;
  }
  Api.setTenantId(me.activeTenantId);

  document.getElementById('tenant-switcher').setData(me.tenants, me.activeTenantId);

  // Reflect Tenant Admin role in sidebar
  const activeTenant = me.tenants?.find(t => t.id === me.activeTenantId);
  if (activeTenant?.role === 'TENANT_ADMIN') {
    document.body.classList.add('role-admin');
  }

  try {
    tenantUsers = await Api.listTenantUsers();
  } catch {
    tenantUsers = [];
  }
  taskDrawer.setUsers(tenantUsers);

  updateSortCarets();
  await loadTasks();
}

main();
