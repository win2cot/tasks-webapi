// ---- Page state ----
let currentPage = 0;
let currentSort = 'dueDate,asc';
let totalPages = 1;
let totalElements = 0;
const PAGE_SIZE = 50;

// ---- Inline-edit state ----
let taskMap      = new Map(); // taskId(number) → Task object
let currentUserId = null;     // logged-in user's id
let tenantUsers  = [];        // TenantUser[]  — for assignee dropdown
let currentEdit  = null;      // { td, taskId, field, originalHTML } | null
let descEditTaskId = null;    // taskId being edited in the description overlay

// ---- Meta helpers (design-system.md §6.4) ----
function statusMeta(s) {
  return {
    NOT_STARTED: ['未着手', 'st-NOT_STARTED'],
    IN_PROGRESS:  ['進行中', 'st-IN_PROGRESS'],
    DONE:         ['完了',   'st-DONE'],
    ON_HOLD:      ['保留',   'st-ON_HOLD'],
  }[s] || [s, 'st-NOT_STARTED'];
}

function priMeta(p) {
  return {
    HIGH:   ['高', 'pri-HIGH'],
    MEDIUM: ['中', 'pri-MEDIUM'],
    LOW:    ['低', 'pri-LOW'],
  }[p] || [p, 'pri-LOW'];
}

function visMeta(v) {
  return {
    TENANT:       ['テナント', 'vis-TENANT',       'bi-globe2'],
    STAKEHOLDERS: ['関係者',   'vis-STAKEHOLDERS', 'bi-people-fill'],
    PRIVATE:      ['非公開',   'vis-PRIVATE',      'bi-lock-fill'],
  }[v] || [v, 'vis-TENANT', 'bi-globe2'];
}

// Derive option lists from the badge helpers so labels stay in sync (fix: no duplicate strings)
const STATUS_OPTIONS   = ['NOT_STARTED', 'IN_PROGRESS', 'DONE', 'ON_HOLD']
  .map(v => ({ v, l: statusMeta(v)[0] }));
const PRIORITY_OPTIONS = ['HIGH', 'MEDIUM', 'LOW']
  .map(v => ({ v, l: priMeta(v)[0] }));

function escHtml(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function dueLabelHtml(dueDate) {
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const due   = new Date(dueDate + 'T00:00:00');
  const diff  = Math.round((due - today) / 86400000);
  const md    = dueDate.slice(5).replace('-', '/');
  if (diff < 0)  return `<span class="due-overdue">${md}(${diff}日)</span>`;
  if (diff === 0) return `<span class="due-today">今日</span>`;
  if (diff === 1) return `明日 ${md}`;
  return md;
}

// ETag format per ADR-0012: W/"<version>"
function buildEtag(task) {
  return `W/"${task.version}"`;
}

// ---- Row HTML generation ----
// Uses data-* attributes instead of inline event handlers (ADR-0022: no unsafe-inline).
function rowHTML(task) {
  const [vl, vc, vi] = visMeta(task.visibility);
  const ownerIni   = (task.owner?.fullName || '?').slice(0, 1);
  const ownerName  = task.owner?.fullName ?? '—';
  const assigneeName = task.assignee?.fullName ?? '—';

  // Authorization flags (screen-flow.md §5.2, ADR-0005)
  const canEdit         = task.editable; // owner only
  const canChangeStatus = task.editable || task.assignee?.id === currentUserId;

  // -- Status cell --
  let statusCell;
  if (canChangeStatus) {
    const opts = STATUS_OPTIONS.map(o =>
      `<option value="${o.v}"${task.status === o.v ? ' selected' : ''}>${o.l}</option>`
    ).join('');
    statusCell = `<td data-no-row-click>
      <select class="inline-sel st-sel-${escHtml(task.status)}"
        aria-label="ステータス"
        data-action="status-change" data-task-id="${task.id}">${opts}</select>
    </td>`;
  } else {
    const [sl, sc] = statusMeta(task.status);
    statusCell = `<td><span class="st-badge ${sc}" title="編集権限がありません">${sl}</span></td>`;
  }

  // -- Title + description cell --
  const descText = task.description
    ? escHtml(task.description.slice(0, 80)) + (task.description.length > 80 ? '…' : '')
    : '';
  let titleCell;
  if (canEdit) {
    const descPart = task.description
      ? `<div class="task-desc edit-cell" data-action="desc-edit" data-task-id="${task.id}" title="クリックして説明を編集">${descText}</div>`
      : `<div class="task-desc text-muted fst-italic edit-cell add-desc-hint" data-action="desc-edit" data-task-id="${task.id}" title="説明を追加">説明を追加...</div>`;
    titleCell = `<td data-no-row-click>
      <div class="task-title edit-cell" data-action="cell-edit" data-task-id="${task.id}" data-field="title" title="クリックしてタイトルを編集">${escHtml(task.title)}</div>
      ${descPart}
    </td>`;
  } else {
    const descPart = task.description ? `<div class="task-desc">${descText}</div>` : '';
    titleCell = `<td>
      <div class="task-title">${escHtml(task.title)}</div>${descPart}
    </td>`;
  }

  // -- Owner cell (never editable inline) --
  const ownerCell = `<td>
    <span class="d-inline-flex align-items-center">
      <span class="owner-mini" aria-hidden="true">${escHtml(ownerIni)}</span>${escHtml(ownerName)}
    </span>
  </td>`;

  // -- Assignee cell --
  let assigneeCell;
  if (canEdit) {
    assigneeCell = `<td data-no-row-click>
      <span class="edit-cell" data-action="cell-edit" data-task-id="${task.id}" data-field="assigneeId" title="クリックして担当者を変更">${escHtml(assigneeName)}</span>
    </td>`;
  } else {
    const tooltip = canChangeStatus ? '' : ' title="編集権限がありません"';
    assigneeCell = `<td><span${tooltip}>${escHtml(assigneeName)}</span></td>`;
  }

  // -- Due date cell --
  let dueDateCell;
  if (canEdit) {
    dueDateCell = `<td data-no-row-click class="td-nowrap">
      <span class="edit-cell" data-action="cell-edit" data-task-id="${task.id}" data-field="dueDate" title="クリックして期限を変更">${dueLabelHtml(task.dueDate)}</span>
    </td>`;
  } else {
    dueDateCell = `<td class="td-nowrap">${dueLabelHtml(task.dueDate)}</td>`;
  }

  // -- Priority cell --
  let priorityCell;
  if (canEdit) {
    const opts = PRIORITY_OPTIONS.map(o =>
      `<option value="${o.v}"${task.priority === o.v ? ' selected' : ''}>${o.l}</option>`
    ).join('');
    priorityCell = `<td data-no-row-click>
      <select class="inline-sel pri-sel-${escHtml(task.priority)}"
        aria-label="優先度"
        data-action="priority-change" data-task-id="${task.id}">${opts}</select>
    </td>`;
  } else {
    const [pl, pc] = priMeta(task.priority);
    priorityCell = `<td><span class="pri-badge ${pc}" title="編集権限がありません">${pl}</span></td>`;
  }

  // -- Visibility cell (inline edit out of scope, screen-flow.md §5.2) --
  const visCell = `<td><span class="vis-badge ${vc}"><i class="bi ${vi}" aria-hidden="true"></i>${vl}</span></td>`;

  return `<tr data-task-id="${task.id}" tabindex="0">
    ${statusCell}${titleCell}${ownerCell}${assigneeCell}${dueDateCell}${priorityCell}${visCell}
  </tr>`;
}

// ---- Re-render a single row in place ----
function rerenderRow(taskId) {
  const task = taskMap.get(taskId);
  if (!task) return;
  const tr = document.querySelector(`tr[data-task-id="${taskId}"]`);
  if (!tr) return;
  const tmp = document.createElement('tbody');
  tmp.innerHTML = rowHTML(task);
  tr.replaceWith(tmp.firstElementChild);
}

// ---- Inline status change (PATCH /api/tasks/{id}/status, no ETag needed) ----
async function onStatusChange(sel, taskId) {
  const status = sel.value;
  sel.disabled = true;
  try {
    const updated = await Api.changeStatus(taskId, status);
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId); // revert to server state
    // PATCH /api/tasks/{id}/status は ADR-0012 の If-Match 対象外のため 412 は理論上返らない。API 契約変更に備えた防御的ガードとして残す。
    if (err.status === 412) showConflictBanner();
    else showError('ステータスの更新に失敗しました: ' + (err.message || ''));
  }
}

// ---- Inline priority change (PATCH /api/tasks/{id} with ETag) ----
async function onPriorityChange(sel, taskId) {
  const priority = sel.value;
  const task = taskMap.get(taskId);
  if (!task) return;
  sel.disabled = true;
  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), { priority });
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId);
    if (err.status === 412) showConflictBanner();
    else showError('優先度の更新に失敗しました: ' + (err.message || ''));
  }
}

// ---- Cancel active cell edit (restore original HTML) ----
function cancelCurrentEdit() {
  if (!currentEdit) return;
  const { td, originalHTML, abort } = currentEdit;
  // Set done=true via the closure's own abort handle BEFORE replacing innerHTML,
  // so the blur event fired by removing a focused input doesn't re-enter doCommit.
  if (abort) abort();
  td.innerHTML = originalHTML;
  currentEdit = null;
}

// ---- Commit a field patch (PATCH /api/tasks/{id} with ETag) ----
async function commitFieldEdit(taskId, field, value) {
  const task = taskMap.get(taskId);
  if (!task) { cancelCurrentEdit(); return; }

  let body;
  if (field === 'assigneeId') {
    body = { assigneeId: value ? Number(value) : null };
  } else if (field === 'dueDate') {
    body = { dueDate: value };
  } else if (field === 'title') {
    body = { title: value };
  } else if (field === 'description') {
    body = { description: value || null };
  }
  if (!body) { cancelCurrentEdit(); return; }

  currentEdit = null; // cell will be re-rendered on success/error

  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), body);
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    rerenderRow(taskId);
    if (err.status === 412) showConflictBanner();
    else showError('更新に失敗しました: ' + (err.message || ''));
  }
}

// ---- Activate inline editor for title / dueDate / assigneeId ----
function activateCellEdit(triggerEl, taskId, field) {
  const task = taskMap.get(taskId);
  if (!task?.editable) return;

  cancelCurrentEdit();
  closeDescOverlay();

  const td = triggerEl.closest('td');
  const originalHTML = td.innerHTML;
  let input;
  let originalValue;

  if (field === 'title') {
    input = document.createElement('input');
    input.type = 'text';
    input.className = 'form-control form-control-sm inline-input';
    input.value = task.title;
    input.maxLength = 100;
    originalValue = task.title;
  } else if (field === 'dueDate') {
    input = document.createElement('input');
    input.type = 'date';
    input.className = 'form-control form-control-sm inline-input';
    input.value = task.dueDate;
    originalValue = task.dueDate;
  } else if (field === 'assigneeId') {
    input = document.createElement('select');
    input.className = 'form-select form-select-sm inline-input';
    const opts = tenantUsers
      .map(u => `<option value="${u.userId}"${task.assignee?.id === u.userId ? ' selected' : ''}>${escHtml(u.fullName)}</option>`)
      .join('');
    input.innerHTML = `<option value="">未割当</option>${opts}`;
    input.value = task.assignee?.id != null ? String(task.assignee.id) : '';
    originalValue = input.value;
  }

  if (!input) return;

  td.innerHTML = '';
  td.appendChild(input);

  let done = false;
  currentEdit = { td, taskId, field, originalHTML, abort: () => { done = true; } };

  input.focus();
  if (field === 'title') input.select();

  async function doCommit() {
    if (done) return;
    done = true;
    const val = input.value;
    if (field === 'title' && !val.trim()) { cancelCurrentEdit(); return; }
    // Empty dueDate → null (allows clearing an existing due date via PATCH dueDate:null)
    const commitVal = (field === 'dueDate' && !val) ? null : val;
    if (commitVal === (originalValue || null)) { cancelCurrentEdit(); return; }
    await commitFieldEdit(taskId, field, commitVal);
  }

  input.addEventListener('keydown', e => {
    if (e.key === 'Escape') { done = true; cancelCurrentEdit(); }
    if (e.key === 'Enter')  { e.preventDefault(); doCommit(); }
  });

  // dueDate and assignee commit on selection change; title commits on blur
  if (field === 'dueDate' || field === 'assigneeId') {
    input.addEventListener('change', doCommit);
  }
  input.addEventListener('blur', doCommit);
}

// ---- Description overlay (screen-flow.md §5.2: popover型) ----
function openDescEdit(triggerEl, taskId) {
  const task = taskMap.get(taskId);
  if (!task?.editable) return;

  cancelCurrentEdit();
  descEditTaskId = taskId;

  const overlay = document.getElementById('desc-overlay');
  const ta      = document.getElementById('desc-overlay-ta');
  ta.value = task.description || '';

  // Position below the clicked element, keeping inside the viewport
  const rect = triggerEl.getBoundingClientRect();
  let top  = rect.bottom + 8;
  let left = rect.left;
  if (left + 310 > window.innerWidth) left = Math.max(8, window.innerWidth - 318);
  if (top + 190 > window.innerHeight)  top  = rect.top - 198;

  overlay.style.top  = top  + 'px';
  overlay.style.left = left + 'px';
  overlay.classList.remove('d-none');
  ta.focus();
}

function closeDescOverlay() {
  document.getElementById('desc-overlay').classList.add('d-none');
  descEditTaskId = null;
}

async function saveDescEdit() {
  if (descEditTaskId === null) return;
  const taskId = descEditTaskId;
  const task   = taskMap.get(taskId);
  const ta     = document.getElementById('desc-overlay-ta');
  const value  = ta.value.trim() || null;

  closeDescOverlay();
  if (!task) return; // task evicted from map (e.g. concurrent loadTasks) — discard silently
  if (value === (task.description || null)) return; // no change

  try {
    const updated = await Api.patchTask(taskId, buildEtag(task), { description: value });
    taskMap.set(taskId, updated);
    rerenderRow(taskId);
  } catch (err) {
    if (err.status === 412) showConflictBanner();
    else showError('説明の更新に失敗しました: ' + (err.message || ''));
  }
}

function cancelDescEdit() {
  closeDescOverlay();
}

// Close desc overlay when clicking outside of it
document.addEventListener('mousedown', e => {
  const overlay = document.getElementById('desc-overlay');
  if (!overlay.classList.contains('d-none') && !overlay.contains(e.target)) {
    cancelDescEdit();
  }
});

// Close desc overlay with Escape (keyboard-only accessibility)
document.getElementById('desc-overlay-ta').addEventListener('keydown', e => {
  if (e.key === 'Escape') { e.stopPropagation(); cancelDescEdit(); }
});

// ---- UI state helpers (design-system.md §6.11) ----
function showLoading(on) {
  document.getElementById('loading-skeleton').classList.toggle('d-none', !on);
  document.getElementById('task-panel').classList.toggle('d-none', on);
}

function showError(msg) {
  const banner = document.getElementById('error-banner');
  document.getElementById('error-message').textContent = msg;
  banner.classList.remove('d-none');
}

function clearError() {
  document.getElementById('error-banner').classList.add('d-none');
}

function showConflictBanner() {
  document.getElementById('conflict-banner').classList.remove('d-none');
}

function clearConflict() {
  document.getElementById('conflict-banner').classList.add('d-none');
}

// ---- Task list (A-1 GET /api/tasks) ----
async function loadTasks() {
  clearError();
  clearConflict();
  cancelCurrentEdit();
  closeDescOverlay();
  showLoading(true);
  try {
    const data = await Api.listTasks({ page: currentPage, size: PAGE_SIZE, sort: currentSort });
    totalPages    = data.totalPages    || 1;
    totalElements = data.totalElements || 0;
    // Rebuild taskMap from the current page (clear stale entries from previous pages)
    taskMap.clear();
    if (data.content) {
      data.content.forEach(t => taskMap.set(t.id, t));
    }
    renderTasks(data.content);
    renderPagination();
    showLoading(false);
  } catch (err) {
    document.getElementById('loading-skeleton').classList.add('d-none');
    showError(err.message || 'タスクの取得に失敗しました');
  }
}

function renderTasks(tasks) {
  document.getElementById('total-count').textContent = `${totalElements} 件`;
  const tbody = document.getElementById('task-tbody');
  if (!tasks || tasks.length === 0) {
    tbody.innerHTML = '<tr class="empty-row"><td colspan="7"><i class="bi bi-inbox me-2" aria-hidden="true"></i>タスクはありません</td></tr>';
    return;
  }
  tbody.innerHTML = tasks.map(rowHTML).join('');
}

function renderPagination() {
  const start = totalElements > 0 ? currentPage * PAGE_SIZE + 1 : 0;
  const end   = Math.min((currentPage + 1) * PAGE_SIZE, totalElements);
  document.getElementById('pager-info').textContent =
    totalElements > 0 ? `${totalElements} 件中 ${start}–${end} 件を表示` : '0 件';
  document.getElementById('pager-pages').textContent =
    `${currentPage + 1} / ${Math.max(totalPages, 1)}`;
  document.getElementById('btn-prev').disabled = currentPage <= 0;
  document.getElementById('btn-next').disabled = currentPage >= totalPages - 1;
}

function goPage(p) {
  if (p < 0 || p >= totalPages) return;
  currentPage = p;
  loadTasks();
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

// ---- Role switcher ----
function switchRole(r) {
  document.body.classList.toggle('role-admin', r === 'admin');
}

// ---- Row click (S-05 detail — wired in later sprint) ----
function onRowClick(id) {
  // TODO: open detail drawer (S-05, Sprint 3+)
  console.log('task row clicked:', id);
}

// ---- New task drawer ----
function openNew() {
  document.getElementById('new-task-form').reset();
  // Populate assignee options from tenantUsers
  const sel = document.getElementById('newAssignee');
  sel.innerHTML = '<option value="">未割当</option>' +
    tenantUsers.map(u => `<option value="${u.userId}">${escHtml(u.fullName)}</option>`).join('');
  bootstrap.Offcanvas.getOrCreateInstance(document.getElementById('newTaskDrawer')).show();
}

function submitNewTask(event) {
  event.preventDefault();
  // TODO: POST /api/tasks (Sprint 3+)
  bootstrap.Offcanvas.getInstance(document.getElementById('newTaskDrawer'))?.hide();
}

// ---- Event delegation for dynamically generated task rows ----
function setupTbodyDelegation() {
  const tbody = document.getElementById('task-tbody');

  tbody.addEventListener('click', e => {
    const actionEl = e.target.closest('[data-action]');
    if (actionEl) {
      const action = actionEl.dataset.action;
      const taskId = Number(actionEl.dataset.taskId);
      if (action === 'cell-edit') activateCellEdit(actionEl, taskId, actionEl.dataset.field);
      else if (action === 'desc-edit') openDescEdit(actionEl, taskId);
      return;
    }
    // Row click: only when not inside a data-no-row-click cell
    if (!e.target.closest('[data-no-row-click]')) {
      const tr = e.target.closest('tr[data-task-id]');
      if (tr) onRowClick(Number(tr.dataset.taskId));
    }
  });

  tbody.addEventListener('change', e => {
    const actionEl = e.target.closest('[data-action]');
    if (!actionEl) return;
    const taskId = Number(actionEl.dataset.taskId);
    if (actionEl.dataset.action === 'status-change')   onStatusChange(actionEl, taskId);
    else if (actionEl.dataset.action === 'priority-change') onPriorityChange(actionEl, taskId);
  });

  tbody.addEventListener('keydown', e => {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    const tr = e.target.closest('tr[data-task-id]');
    if (tr && e.target === tr) {
      e.preventDefault();
      onRowClick(Number(tr.dataset.taskId));
    }
  });
}

// ---- Init ----
async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) return;

  let me;
  try {
    me = await Api.getMe();
  } catch (err) {
    showError('ユーザー情報の取得に失敗しました: ' + err.message);
    return;
  }

  // Store current user id for assignee/status auth checks
  currentUserId = me.user?.id ?? null;

  // Populate navbar user info
  const user = Auth.getUser();
  const displayName = user?.name || user?.preferred_username || '';
  document.getElementById('nav-username').textContent = displayName;
  document.getElementById('user-avatar').textContent  = displayName.slice(0, 1) || '?';

  // テナント未選択はテナント選択画面へ
  if (!me.activeTenantId) {
    window.location.replace('index.html');
    return;
  }
  Api.setTenantId(me.activeTenantId);

  // Tenant switcher in navbar
  TenantSwitcher.render(
    document.getElementById('tenant-switcher-container'),
    me.tenants,
    me.activeTenantId,
  );

  // Reflect Tenant Admin role in sidebar
  const activeTenant = me.tenants?.find(t => t.id === me.activeTenantId);
  if (activeTenant?.role === 'TENANT_ADMIN') {
    document.getElementById('roleSel').value = 'admin';
    switchRole('admin');
  }

  // Load tenant users for assignee dropdowns (non-fatal if it fails)
  try {
    tenantUsers = await Api.listTenantUsers();
  } catch {
    tenantUsers = [];
  }

  updateSortCarets();
  await loadTasks();
}

// ---- Static HTML element event listeners ----
document.getElementById('btn-logout').addEventListener('click', () => Auth.logout());
document.getElementById('roleSel').addEventListener('change', e => switchRole(e.target.value));
document.getElementById('sortSel').addEventListener('change', e => onSortChange(e.target.value));
document.getElementById('btn-new-task').addEventListener('click', openNew);
document.getElementById('btn-retry').addEventListener('click', loadTasks);
document.getElementById('btn-conflict-reload').addEventListener('click', () => { loadTasks(); clearConflict(); });
document.getElementById('btn-conflict-close').addEventListener('click', clearConflict);
document.getElementById('th-dueDate').addEventListener('click', () => cycleSort('dueDate'));
document.getElementById('th-priority').addEventListener('click', () => cycleSort('priority'));
document.getElementById('btn-prev').addEventListener('click', () => goPage(currentPage - 1));
document.getElementById('btn-next').addEventListener('click', () => goPage(currentPage + 1));
document.getElementById('btn-desc-save').addEventListener('click', saveDescEdit);
document.getElementById('btn-desc-cancel').addEventListener('click', cancelDescEdit);
document.getElementById('new-task-form').addEventListener('submit', submitNewTask);

setupTbodyDelegation();
main();
