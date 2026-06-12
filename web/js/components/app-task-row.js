// <app-task-row> — renders as display:contents; contains a <tr> for table layout.
// Usage:
//   const row = document.createElement('app-task-row');
//   row.setTask(task, currentUserId, tenantUsers);
//   tbody.appendChild(row);
// Fires (bubbling):
//   task-status-change   { taskId, status }
//   task-priority-change { taskId, priority }
//   task-field-commit    { taskId, field, value }
//   task-desc-open       { taskId, triggerEl }
//   task-row-click       { taskId }

const _rowTpl = document.createElement('template');
_rowTpl.innerHTML = `<tr tabindex="0">
  <td class="cell-status"></td>
  <td class="cell-title-desc"></td>
  <td class="cell-owner">
    <span class="d-inline-flex align-items-center">
      <span class="owner-mini" aria-hidden="true"></span>
      <span class="owner-name"></span>
    </span>
  </td>
  <td class="cell-assignee"></td>
  <td class="cell-duedate td-nowrap"></td>
  <td class="cell-priority"></td>
  <td class="cell-visibility"></td>
</tr>`;

// Sub-templates for static cell structure (no data)
const _titleEditTpl = document.createElement('template');
_titleEditTpl.innerHTML =
  '<div class="task-title edit-cell" data-action="cell-edit" data-field="title" title="クリックしてタイトルを編集"></div>';

const _descEditTpl = document.createElement('template');
_descEditTpl.innerHTML =
  '<div class="task-desc edit-cell" data-action="desc-edit" title="クリックして説明を編集"></div>';

const _descEmptyTpl = document.createElement('template');
_descEmptyTpl.innerHTML =
  '<div class="task-desc text-muted fst-italic edit-cell add-desc-hint" data-action="desc-edit" title="説明を追加">説明を追加...</div>';

function todayJST() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  return new Date(`${parts}T00:00:00`);
}

/** @param {string | null | undefined} dueDate */
function dueLabelNode(dueDate) {
  if (!dueDate) return document.createTextNode('—');
  const today = todayJST();
  const due = new Date(`${dueDate}T00:00:00`);
  const diff = Math.round((due.getTime() - today.getTime()) / 86400000);
  const md = dueDate.slice(5).replace('-', '/');
  if (diff < 0) {
    const span = document.createElement('span');
    span.className = 'due-overdue';
    span.textContent = `${md}(${diff}日)`;
    return span;
  }
  if (diff === 0) {
    const span = document.createElement('span');
    span.className = 'due-today';
    span.textContent = '今日';
    return span;
  }
  if (diff === 1) return document.createTextNode(`明日 ${md}`);
  return document.createTextNode(md);
}

class AppTaskRow extends HTMLElement {
  /** @type {Task | null} */
  #task = null;
  /** @type {number | null} */
  #currentUserId = null;
  /** @type {TenantUser[]} */
  #tenantUsers = [];
  /** @type {{ td: HTMLElement, originalNodes: Node[], abort: () => void } | null} */
  #editState = null;

  // Bind once so the same reference can be removed in disconnectedCallback
  #handleClick = this.#onClick.bind(this);
  #handleChange = this.#onChange.bind(this);
  #handleKeydown = this.#onKeydown.bind(this);

  connectedCallback() {
    this.addEventListener('click', this.#handleClick);
    this.addEventListener('change', this.#handleChange);
    this.addEventListener('keydown', this.#handleKeydown);
    if (this.#task) this.#render();
  }

  disconnectedCallback() {
    this.removeEventListener('click', this.#handleClick);
    this.removeEventListener('change', this.#handleChange);
    this.removeEventListener('keydown', this.#handleKeydown);
  }

  /**
   * @param {Task} task
   * @param {number | null} currentUserId
   * @param {TenantUser[]} tenantUsers
   */
  setTask(task, currentUserId, tenantUsers) {
    this.#task = task;
    this.#currentUserId = currentUserId;
    this.#tenantUsers = tenantUsers;
    if (this.isConnected) this.#render();
  }

  cancelEdit() {
    if (!this.#editState) return;
    const { td, originalNodes, abort } = this.#editState;
    abort?.();
    td.replaceChildren(...originalNodes);
    this.#editState = null;
  }

  #render() {
    this.cancelEdit();
    const task = this.#task;
    if (!task) return;
    const canEdit = task.editable;
    const canChangeStatus = task.editable || task.assignee?.id === this.#currentUserId;
    const taskIdStr = String(task.id);

    const tr = /** @type {HTMLTableRowElement} */ (
      /** @type {DocumentFragment} */ (_rowTpl.content.cloneNode(true)).firstElementChild
    );
    tr.dataset.taskId = taskIdStr;

    // --- Status cell ---
    const statusTd = /** @type {HTMLTableCellElement} */ (tr.querySelector('.cell-status'));
    if (canChangeStatus) {
      statusTd.dataset.noRowClick = '';
      const badge = document.createElement('app-status-badge');
      badge.setAttribute('status', task.status);
      badge.setAttribute('task-id', taskIdStr);
      badge.setAttribute('editable', '');
      statusTd.appendChild(badge);
    } else {
      const badge = document.createElement('app-status-badge');
      badge.setAttribute('status', task.status);
      statusTd.appendChild(badge);
    }

    // --- Title + description cell ---
    const titleTd = /** @type {HTMLTableCellElement} */ (tr.querySelector('.cell-title-desc'));
    if (canEdit) {
      titleTd.dataset.noRowClick = '';
      const titleDiv = /** @type {HTMLElement} */ (
        /** @type {DocumentFragment} */ (_titleEditTpl.content.cloneNode(true)).firstElementChild
      );
      titleDiv.dataset.taskId = taskIdStr;
      titleDiv.textContent = task.title;
      titleTd.appendChild(titleDiv);

      if (task.description) {
        const descDiv = /** @type {HTMLElement} */ (
          /** @type {DocumentFragment} */ (_descEditTpl.content.cloneNode(true)).firstElementChild
        );
        descDiv.dataset.taskId = taskIdStr;
        const preview = task.description.slice(0, 80) + (task.description.length > 80 ? '…' : '');
        descDiv.textContent = preview;
        titleTd.appendChild(descDiv);
      } else {
        const descDiv = /** @type {HTMLElement} */ (
          /** @type {DocumentFragment} */ (_descEmptyTpl.content.cloneNode(true)).firstElementChild
        );
        descDiv.dataset.taskId = taskIdStr;
        titleTd.appendChild(descDiv);
      }
    } else {
      const titleDiv = document.createElement('div');
      titleDiv.className = 'task-title';
      titleDiv.textContent = task.title;
      titleTd.appendChild(titleDiv);
      if (task.description) {
        const descDiv = document.createElement('div');
        descDiv.className = 'task-desc';
        const preview = task.description.slice(0, 80) + (task.description.length > 80 ? '…' : '');
        descDiv.textContent = preview;
        titleTd.appendChild(descDiv);
      }
    }

    // --- Owner cell ---
    const ownerIni = (task.owner?.fullName || '?').slice(0, 1);
    const ownerTd = /** @type {HTMLTableCellElement} */ (tr.querySelector('.cell-owner'));
    /** @type {HTMLElement} */ (ownerTd.querySelector('.owner-mini')).textContent = ownerIni;
    /** @type {HTMLElement} */ (ownerTd.querySelector('.owner-name')).textContent =
      task.owner?.fullName ?? '—';

    // --- Assignee cell ---
    const assigneeTd = /** @type {HTMLTableCellElement} */ (tr.querySelector('.cell-assignee'));
    const assigneeName = task.assignee?.fullName ?? '—';
    if (canEdit) {
      assigneeTd.dataset.noRowClick = '';
      const span = document.createElement('span');
      span.className = 'edit-cell';
      span.dataset.action = 'cell-edit';
      span.dataset.taskId = taskIdStr;
      span.dataset.field = 'assigneeId';
      span.title = 'クリックして担当者を変更';
      span.textContent = assigneeName;
      assigneeTd.appendChild(span);
    } else {
      const span = document.createElement('span');
      if (!canChangeStatus) span.title = '編集権限がありません';
      span.textContent = assigneeName;
      assigneeTd.appendChild(span);
    }

    // --- Due date cell ---
    const dueTd = /** @type {HTMLTableCellElement} */ (tr.querySelector('.cell-duedate'));
    if (canEdit) {
      dueTd.dataset.noRowClick = '';
      const span = document.createElement('span');
      span.className = 'edit-cell';
      span.dataset.action = 'cell-edit';
      span.dataset.taskId = taskIdStr;
      span.dataset.field = 'dueDate';
      span.title = 'クリックして期限を変更';
      span.appendChild(dueLabelNode(task.dueDate));
      dueTd.appendChild(span);
    } else {
      dueTd.appendChild(dueLabelNode(task.dueDate));
    }

    // --- Priority cell ---
    const priorityTd = /** @type {HTMLTableCellElement} */ (tr.querySelector('.cell-priority'));
    if (canEdit) {
      priorityTd.dataset.noRowClick = '';
      const badge = document.createElement('app-priority-badge');
      badge.setAttribute('priority', task.priority);
      badge.setAttribute('task-id', taskIdStr);
      badge.setAttribute('editable', '');
      priorityTd.appendChild(badge);
    } else {
      const badge = document.createElement('app-priority-badge');
      badge.setAttribute('priority', task.priority);
      priorityTd.appendChild(badge);
    }

    // --- Visibility cell ---
    const visTd = /** @type {HTMLTableCellElement} */ (tr.querySelector('.cell-visibility'));
    const visBadge = document.createElement('app-visibility-badge');
    visBadge.setAttribute('visibility', task.visibility);
    visTd.appendChild(visBadge);

    this.replaceChildren(tr);
  }

  // ---- Inline cell editing ----

  /**
   * @param {Element} triggerEl
   * @param {string} field
   */
  #activateCellEdit(triggerEl, field) {
    const task = this.#task;
    if (!task?.editable) return;

    this.cancelEdit();

    const td = /** @type {HTMLElement} */ (triggerEl.closest('td'));
    const originalNodes = Array.from(td.childNodes).map((n) => n.cloneNode(true));
    /** @type {HTMLInputElement | HTMLSelectElement | undefined} */
    let input;

    if (field === 'title') {
      input = document.createElement('input');
      input.type = 'text';
      input.className = 'form-control form-control-sm inline-input';
      input.value = task.title;
      input.maxLength = 100;
    } else if (field === 'dueDate') {
      input = document.createElement('input');
      input.type = 'date';
      input.className = 'form-control form-control-sm inline-input';
      input.value = task.dueDate ?? '';
    } else if (field === 'assigneeId') {
      input = document.createElement('select');
      input.className = 'form-select form-select-sm inline-input';
      const emptyOpt = document.createElement('option');
      emptyOpt.value = '';
      emptyOpt.textContent = '未割当';
      input.appendChild(emptyOpt);
      this.#tenantUsers.forEach((u) => {
        const opt = document.createElement('option');
        opt.value = String(u.userId);
        opt.textContent = u.fullName;
        opt.selected = task.assignee?.id === u.userId;
        /** @type {HTMLSelectElement} */ (input).appendChild(opt);
      });
      input.value = task.assignee?.id != null ? String(task.assignee.id) : '';
    }

    if (!input) return;

    const originalValue = input.value;
    let done = false;
    this.#editState = {
      td,
      originalNodes,
      abort: () => {
        done = true;
      },
    };

    td.replaceChildren(input);
    input.focus();
    if (field === 'title') /** @type {HTMLInputElement} */ (input).select();

    const doCommit = async () => {
      if (done) return;
      done = true;
      this.#editState = null;
      const val = input.value;
      if (field === 'title' && !val.trim()) {
        td.replaceChildren(...originalNodes);
        return;
      }
      const commitVal = field === 'dueDate' && !val ? null : val;
      if (commitVal === (originalValue || null)) {
        td.replaceChildren(...originalNodes);
        return;
      }
      this.dispatchEvent(
        new CustomEvent('task-field-commit', {
          bubbles: true,
          detail: { taskId: task.id, field, value: commitVal },
        }),
      );
    };

    input.addEventListener('keydown', (e) => {
      const ke = /** @type {KeyboardEvent} */ (e);
      if (ke.key === 'Escape') {
        done = true;
        td.replaceChildren(...originalNodes);
        this.#editState = null;
      }
      if (ke.key === 'Enter') {
        ke.preventDefault();
        doCommit();
      }
    });

    if (field === 'dueDate' || field === 'assigneeId') {
      input.addEventListener('change', doCommit);
    }
    input.addEventListener('blur', doCommit);
  }

  // ---- Event handlers ----

  /** @param {MouseEvent} e */
  #onClick(e) {
    const actionEl = /** @type {HTMLElement | null} */ (
      /** @type {Element} */ (e.target).closest('[data-action]')
    );
    if (actionEl) {
      const { action, taskId, field } = actionEl.dataset;
      const id = Number(taskId);
      if (action === 'cell-edit') {
        this.#activateCellEdit(actionEl, field ?? '');
      } else if (action === 'desc-edit') {
        this.dispatchEvent(
          new CustomEvent('task-desc-open', {
            bubbles: true,
            detail: { taskId: id, triggerEl: actionEl },
          }),
        );
      }
      return;
    }
    // Row click when not in a no-click cell
    if (!(/** @type {Element} */ (e.target).closest('[data-no-row-click]'))) {
      const tr = /** @type {HTMLElement | null} */ (
        /** @type {Element} */ (e.target).closest('tr[data-task-id]')
      );
      if (tr) {
        this.dispatchEvent(
          new CustomEvent('task-row-click', {
            bubbles: true,
            detail: { taskId: Number(tr.dataset.taskId) },
          }),
        );
      }
    }
  }

  /** @param {Event} e */
  #onChange(e) {
    const actionEl = /** @type {HTMLSelectElement | null} */ (
      /** @type {Element} */ (e.target).closest('[data-action]')
    );
    if (!actionEl) return;
    const taskId = Number(actionEl.dataset.taskId);
    if (actionEl.dataset.action === 'status-change') {
      this.dispatchEvent(
        new CustomEvent('task-status-change', {
          bubbles: true,
          detail: { taskId, status: actionEl.value, selectEl: actionEl },
        }),
      );
    } else if (actionEl.dataset.action === 'priority-change') {
      this.dispatchEvent(
        new CustomEvent('task-priority-change', {
          bubbles: true,
          detail: { taskId, priority: actionEl.value, selectEl: actionEl },
        }),
      );
    }
  }

  /** @param {KeyboardEvent} e */
  #onKeydown(e) {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    const tr = /** @type {HTMLElement | null} */ (
      /** @type {Element} */ (e.target).closest('tr[data-task-id]')
    );
    if (tr && e.target === tr) {
      e.preventDefault();
      this.dispatchEvent(
        new CustomEvent('task-row-click', {
          bubbles: true,
          detail: { taskId: Number(tr.dataset.taskId) },
        }),
      );
    }
  }
}
customElements.define('app-task-row', AppTaskRow);
