// <app-task-drawer> — Bootstrap Offcanvas drawer for task create / detail / edit.
//
// Public API:
//   setUsers(tenantUsers)         — set tenant user list for dropdowns
//   setCurrentUser(userId)        — set logged-in user ID for permission checks
//   open() / openNew(opts?)       — open in "new task" mode
//   openDetail(taskId)            — load task and open in detail mode
//   openEdit(taskId)              — load task and open in edit mode
//
// Fires (bubbling):
//   drawer-task-created  { task }        — POST /api/tasks succeeded
//   drawer-task-updated  { task }        — PATCH succeeded (detail re-fetched)
//   drawer-task-deleted  { taskId }      — DELETE succeeded
//   drawer-closed                        — offcanvas fully hidden

const PRI_LABEL = /** @type {Record<string, string>} */ ({ HIGH: '高', MEDIUM: '中', LOW: '低' });
const ST_LABEL = /** @type {Record<string, string>} */ ({
  NOT_STARTED: '未着手',
  IN_PROGRESS: '進行中',
  DONE: '完了',
  ON_HOLD: '保留',
});
const VIS_LABEL = /** @type {Record<string, string>} */ ({
  TENANT: 'テナント',
  STAKEHOLDERS: '関係者',
  PRIVATE: '非公開',
});
const VIS_ICON = /** @type {Record<string, string>} */ ({
  TENANT: 'bi-globe2',
  STAKEHOLDERS: 'bi-people-fill',
  PRIVATE: 'bi-lock-fill',
});

class AppTaskDrawer extends HTMLElement {
  /** @type {BootstrapOffcanvas | null} */
  #oc = null;
  /** @type {TaskDetail | null} */
  #task = null;
  /** @type {string | null} */
  #etag = null;
  /** @type {TenantUser[]} */
  #tenantUsers = [];
  /** @type {number | null} */
  #currentUserId = null;
  /** @type {Stakeholder[]} */
  #stakeholders = [];
  /** @type {Array<{userId: number, fullName: string}>} */
  #selectedSH = [];
  /** @type {string | null} */
  #prevPath = null;
  #skipHideUrl = false;
  /** @type {(() => void) | null} */
  #popHandler = null;

  connectedCallback() {
    const el = document.createElement('div');
    el.className = 'offcanvas offcanvas-end';
    el.setAttribute('tabindex', '-1');
    el.innerHTML =
      '<div class="offcanvas-header border-bottom"></div>' + '<div class="offcanvas-body"></div>';
    this.appendChild(el);

    this.#oc = bootstrap.Offcanvas.getOrCreateInstance(el);
    el.addEventListener('hidden.bs.offcanvas', () => this.#onHidden());

    this.#popHandler = () => this.#onPopState();
    window.addEventListener('popstate', /** @type {EventListener} */ (this.#popHandler));
  }

  disconnectedCallback() {
    window.removeEventListener('popstate', /** @type {EventListener} */ (this.#popHandler));
  }

  // ---- Public API ----

  /** @param {TenantUser[]} users */
  setUsers(users) {
    this.#tenantUsers = users || [];
  }
  /** @param {number | null} userId */
  setCurrentUser(userId) {
    this.#currentUserId = userId;
  }

  open() {
    this.openNew();
  }

  // List URL to restore when closing after a direct-URL auto-open
  static #LIST_URL = '/tasks.html';

  /** @param {Record<string, string>} [opts] */
  openNew(opts = {}) {
    this.#prevPath = this.#safeListUrl('/tasks/new');
    this.#task = null;
    this.#etag = null;
    this.#stakeholders = [];
    this.#selectedSH = [];
    this.#renderNew(opts);
    this.#pushUrl('/tasks/new');
    this.#oc?.show();
  }

  /** @param {number} taskId */
  async openDetail(taskId) {
    this.#prevPath = this.#safeListUrl(`/tasks/${taskId}`);
    this.#renderLoading();
    this.#pushUrl(`/tasks/${taskId}`);
    this.#oc?.show();
    await this.#loadAndRenderDetail(taskId);
  }

  /** @param {number} taskId */
  async openEdit(taskId) {
    this.#prevPath = this.#safeListUrl(`/tasks/${taskId}/edit`);
    this.#renderLoading();
    this.#pushUrl(`/tasks/${taskId}/edit`);
    this.#oc?.show();
    if (!this.#task || this.#task.id !== taskId) {
      await this.#loadTaskData(taskId);
    }
    if (this.#task) this.#renderEdit();
  }

  /** @param {string} drawerUrl */
  #safeListUrl(drawerUrl) {
    const cur = location.pathname + location.search;
    return cur === drawerUrl ? AppTaskDrawer.#LIST_URL : cur;
  }

  // ---- Private helpers ----

  get #hdr() {
    return /** @type {HTMLElement} */ (this.firstElementChild?.querySelector('.offcanvas-header'));
  }
  get #bdy() {
    return /** @type {HTMLElement} */ (this.firstElementChild?.querySelector('.offcanvas-body'));
  }

  /** @param {string} type */
  #can(type) {
    const t = this.#task;
    if (!t) return false;
    if (type === 'edit') return !!t.editable;
    if (type === 'delete') return !!t.deletable;
    if (type === 'status') return t.editable || t.assignee?.id === this.#currentUserId;
    if (type === 'stakeholder') return t.editable || t.assignee?.id === this.#currentUserId;
    if (type === 'visibility') return !!t.editable;
    return false;
  }

  /** @param {number} taskId */
  async #loadTaskData(taskId) {
    try {
      const [{ task, etag }, stakeholders] = await Promise.all([
        Api.getTask(taskId),
        Api.listStakeholders(taskId).catch(() => []),
      ]);
      this.#task = task;
      this.#etag = etag;
      this.#stakeholders = stakeholders;
    } catch (err) {
      this.#renderError(err);
      this.#task = null;
    }
  }

  /** @param {number} taskId */
  async #loadAndRenderDetail(taskId) {
    await this.#loadTaskData(taskId);
    if (this.#task) {
      this.#renderDetail();
    }
  }

  /** @param {string} url */
  #pushUrl(url) {
    history.pushState({ drawer: true }, '', url);
  }
  /** @param {string} url */
  #replaceUrl(url) {
    history.replaceState({ drawer: true }, '', url);
  }

  #onHidden() {
    if (!this.#skipHideUrl && this.#prevPath) {
      history.replaceState(null, '', this.#prevPath);
    }
    this.#skipHideUrl = false;
    this.dispatchEvent(new CustomEvent('drawer-closed', { bubbles: true }));
  }

  #onPopState() {
    const inst = bootstrap.Offcanvas.getInstance(this.firstElementChild);
    if (inst) {
      this.#skipHideUrl = true;
      inst.hide();
    }
  }

  // ---- Shared rendering helpers ----

  #makeCloseBtn() {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn-close';
    btn.setAttribute('data-bs-dismiss', 'offcanvas');
    btn.setAttribute('aria-label', '閉じる');
    return btn;
  }

  /** @param {string} msg */
  #makeErrorMsg(msg) {
    const d = document.createElement('div');
    d.className = 'alert alert-danger py-2 small mb-3';
    d.setAttribute('role', 'alert');
    d.textContent = msg;
    return d;
  }

  /** @param {string} [label] */
  #makeSpinner(label = '読み込み中...') {
    const d = document.createElement('div');
    d.className = 'text-center py-5';
    d.innerHTML =
      '<div class="spinner-border text-primary" role="status">' +
      '<span class="visually-hidden">' +
      label +
      '</span></div>' +
      '<p class="mt-2 small text-muted">' +
      label +
      '</p>';
    return d;
  }

  // ---- Loading / error states ----

  #renderLoading() {
    this.#hdr.replaceChildren();
    this.#bdy.replaceChildren(this.#makeSpinner());
  }

  /** @param {any} err */
  #renderError(err) {
    const hdr = this.#hdr;
    hdr.replaceChildren();
    const title = document.createElement('h5');
    title.className = 'offcanvas-title';
    title.textContent = 'エラー';
    hdr.append(title, this.#makeCloseBtn());

    this.#bdy.replaceChildren(
      this.#makeErrorMsg(
        err.status === 404
          ? 'このタスクは表示できません(存在しないか参照権限がありません)'
          : `タスクの取得に失敗しました: ${err.message || ''}`,
      ),
    );
  }

  // ---- NEW MODE ----

  /** @param {Record<string, string>} [opts] */
  #renderNew(opts = {}) {
    const hdr = this.#hdr;
    hdr.replaceChildren();
    const title = document.createElement('h5');
    title.className = 'offcanvas-title';
    title.innerHTML =
      '<i class="bi bi-plus-circle me-2 text-primary" aria-hidden="true"></i>新規タスク';
    hdr.append(title, this.#makeCloseBtn());

    const form = this.#buildNewForm(opts);
    this.#bdy.replaceChildren(form);
  }

  /** @param {Record<string, string>} [opts] */
  #buildNewForm(opts = {}) {
    const form = document.createElement('form');
    form.className = 'new-task-form';
    form.noValidate = true;

    // Error area
    const errDiv = document.createElement('div');
    errDiv.className = 'd-none';
    errDiv.id = 'new-form-error';
    form.appendChild(errDiv);

    // Title
    const titleGrp = this.#fieldGroup('newTitle', 'タイトル', true);
    const titleInput = document.createElement('input');
    titleInput.id = 'newTitle';
    titleInput.className = 'form-control';
    titleInput.maxLength = 100;
    titleInput.required = true;
    titleInput.placeholder = '例: 設計レビュー';
    titleInput.setAttribute('aria-required', 'true');
    titleGrp.appendChild(titleInput);
    form.appendChild(titleGrp);

    // Description
    const descGrp = this.#fieldGroup('newDesc', '説明', false);
    const descTa = document.createElement('textarea');
    descTa.id = 'newDesc';
    descTa.className = 'form-control';
    descTa.rows = 3;
    descTa.maxLength = 2000;
    descTa.placeholder = '任意・最大2000文字';
    descGrp.appendChild(descTa);
    form.appendChild(descGrp);

    // Due date + Assignee row
    const row = document.createElement('div');
    row.className = 'row g-3';

    const dueCol = document.createElement('div');
    dueCol.className = 'col-6';
    const dueGrp = this.#fieldGroup('newDue', '期限日', true);
    const dueInput = document.createElement('input');
    dueInput.type = 'date';
    dueInput.id = 'newDue';
    dueInput.className = 'form-control';
    dueInput.required = true;
    if (opts.dueDate) dueInput.value = opts.dueDate;
    dueGrp.appendChild(dueInput);
    dueCol.appendChild(dueGrp);

    const assCol = document.createElement('div');
    assCol.className = 'col-6';
    const assGrp = this.#fieldGroup('newAssignee', '担当者', false);
    const assSel = document.createElement('select');
    assSel.id = 'newAssignee';
    assSel.className = 'form-select new-assignee-sel';
    const emptyOpt = document.createElement('option');
    emptyOpt.value = '';
    emptyOpt.textContent = '未割当';
    assSel.appendChild(emptyOpt);
    this.#tenantUsers.forEach((u) => {
      const opt = document.createElement('option');
      opt.value = String(u.userId);
      opt.textContent = u.fullName;
      assSel.appendChild(opt);
    });
    assGrp.appendChild(assSel);
    assCol.appendChild(assGrp);
    row.append(dueCol, assCol);
    form.appendChild(row);

    // Priority
    const priGrp = document.createElement('div');
    priGrp.className = 'mt-3';
    const priFs = document.createElement('fieldset');
    const priLeg = document.createElement('legend');
    priLeg.className = 'form-label small fw-bold';
    priLeg.innerHTML = '優先度 <span class="text-danger" aria-hidden="true">*</span>';
    const priBtnGrp = document.createElement('div');
    priBtnGrp.className = 'btn-group w-100';
    priBtnGrp.setAttribute('role', 'group');
    [
      ['HIGH', 'btn-outline-danger', '高'],
      ['MEDIUM', 'btn-outline-warning', '中'],
      ['LOW', 'btn-outline-secondary', '低'],
    ].forEach(([val, cls, lbl], i) => {
      const inp = document.createElement('input');
      inp.type = 'radio';
      inp.className = 'btn-check';
      inp.name = 'newPri';
      inp.id = `newPri-${val}`;
      inp.value = val;
      if (i === 1) inp.checked = true;
      const lab = document.createElement('label');
      lab.className = `btn ${cls}`;
      lab.htmlFor = `newPri-${val}`;
      lab.textContent = lbl;
      priBtnGrp.append(inp, lab);
    });
    priFs.append(priLeg, priBtnGrp);
    priGrp.appendChild(priFs);
    form.appendChild(priGrp);

    // Visibility
    const visGrp = document.createElement('div');
    visGrp.className = 'mt-3';
    const visFs = document.createElement('fieldset');
    const visLeg = document.createElement('legend');
    visLeg.className = 'form-label small fw-bold';
    visLeg.innerHTML = '公開範囲 <span class="text-danger" aria-hidden="true">*</span>';
    const visBtnGrp = document.createElement('div');
    visBtnGrp.className = 'btn-group w-100';
    visBtnGrp.setAttribute('role', 'group');
    [
      ['TENANT', 'btn-outline-success', 'bi-globe2', 'テナント'],
      ['STAKEHOLDERS', 'btn-outline-primary', 'bi-people-fill', '関係者'],
      ['PRIVATE', 'btn-outline-secondary', 'bi-lock-fill', '非公開'],
    ].forEach(([val, cls, icon, lbl], i) => {
      const inp = document.createElement('input');
      inp.type = 'radio';
      inp.className = 'btn-check';
      inp.name = 'newVis';
      inp.id = `newVis-${val}`;
      inp.value = val;
      if (i === 0) inp.checked = true;
      const lab = document.createElement('label');
      lab.className = `btn ${cls}`;
      lab.htmlFor = `newVis-${val}`;
      lab.innerHTML = `<i class="bi ${icon}" aria-hidden="true"></i> ${lbl}`;
      visBtnGrp.append(inp, lab);
    });
    visFs.append(visLeg, visBtnGrp);
    visGrp.appendChild(visFs);
    form.appendChild(visGrp);

    // Stakeholder picker (shown only when STAKEHOLDERS is selected)
    const shSection = document.createElement('div');
    shSection.id = 'new-sh-section';
    shSection.className = 'd-none mt-3';
    const shLabel = document.createElement('div');
    shLabel.className = 'form-label small fw-bold';
    shLabel.textContent = '関係者';
    const shChips = document.createElement('div');
    shChips.id = 'new-sh-chips';
    shChips.className = 'drawer-chips mb-2';
    const shRow = document.createElement('div');
    shRow.className = 'd-flex gap-2';
    const shSel = document.createElement('select');
    shSel.id = 'new-sh-sel';
    shSel.className = 'form-select form-select-sm flex-grow-1';
    const shOptBlank = document.createElement('option');
    shOptBlank.value = '';
    shOptBlank.textContent = '追加する...';
    shSel.appendChild(shOptBlank);
    this.#tenantUsers.forEach((u) => {
      const opt = document.createElement('option');
      opt.value = String(u.userId);
      opt.textContent = u.fullName;
      shSel.appendChild(opt);
    });
    const shAddBtn = document.createElement('button');
    shAddBtn.type = 'button';
    shAddBtn.className = 'btn btn-sm btn-outline-secondary';
    shAddBtn.textContent = '追加';
    shRow.append(shSel, shAddBtn);
    shSection.append(shLabel, shChips, shRow);
    form.appendChild(shSection);

    // Toggle stakeholder section on visibility change
    visBtnGrp.addEventListener('change', () => {
      const val = /** @type {HTMLInputElement | null} */ (
        form.querySelector('input[name="newVis"]:checked')
      )?.value;
      shSection.classList.toggle('d-none', val !== 'STAKEHOLDERS');
      this.#syncNewShPickerOpts(shSel, shChips);
    });

    shAddBtn.addEventListener('click', () => {
      const uid = Number(shSel.value);
      if (!uid) return;
      const user = this.#tenantUsers.find((u) => u.userId === uid);
      if (!user || this.#selectedSH.some((s) => s.userId === uid)) return;
      this.#selectedSH.push({ userId: uid, fullName: user.fullName });
      this.#renderNewShChips(shChips, shSel);
    });

    // Submit
    const footer = document.createElement('div');
    footer.className = 'd-flex gap-2 mt-4';
    const submitBtn = document.createElement('button');
    submitBtn.type = 'submit';
    submitBtn.className = 'btn btn-primary flex-grow-1';
    submitBtn.textContent = '作成';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn btn-light';
    cancelBtn.setAttribute('data-bs-dismiss', 'offcanvas');
    cancelBtn.textContent = '取消';
    footer.append(submitBtn, cancelBtn);
    form.appendChild(footer);

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      this.#submitNew(form);
    });
    return form;
  }

  /**
   * @param {HTMLSelectElement} sel
   * @param {Element} chipsEl
   */
  #syncNewShPickerOpts(sel, chipsEl) {
    const currentIds = new Set(this.#selectedSH.map((s) => s.userId));
    Array.from(sel.options).forEach((o) => {
      if (o.value === '') return;
      o.hidden = currentIds.has(Number(o.value));
    });
    this.#renderNewShChips(chipsEl, sel);
  }

  /**
   * @param {Element} chipsEl
   * @param {HTMLSelectElement} sel
   */
  #renderNewShChips(chipsEl, sel) {
    chipsEl.replaceChildren();
    this.#selectedSH.forEach((sh) => {
      const chip = this.#makeChip(sh.fullName, () => {
        this.#selectedSH = this.#selectedSH.filter((s) => s.userId !== sh.userId);
        this.#syncNewShPickerOpts(sel, chipsEl);
      });
      chipsEl.appendChild(chip);
    });
  }

  /** @param {HTMLFormElement} form */
  async #submitNew(form) {
    const errDiv = /** @type {HTMLElement} */ (form.querySelector('#new-form-error'));
    const submitBtn = /** @type {HTMLButtonElement} */ (form.querySelector('[type=submit]'));
    errDiv.className = 'd-none';

    const title = /** @type {HTMLInputElement} */ (form.querySelector('#newTitle')).value.trim();
    if (!title) {
      errDiv.textContent = 'タイトルは必須です';
      errDiv.className = 'alert alert-danger py-2 small mb-3';
      return;
    }
    const dueDate = /** @type {HTMLInputElement} */ (form.querySelector('#newDue')).value;
    if (!dueDate) {
      errDiv.textContent = '期限日は必須です';
      errDiv.className = 'alert alert-danger py-2 small mb-3';
      return;
    }
    const priority =
      /** @type {HTMLInputElement | null} */ (form.querySelector('input[name="newPri"]:checked'))
        ?.value || 'MEDIUM';
    const visibility =
      /** @type {HTMLInputElement | null} */ (form.querySelector('input[name="newVis"]:checked'))
        ?.value || 'TENANT';
    const assigneeRaw = /** @type {HTMLSelectElement} */ (form.querySelector('#newAssignee')).value;
    const assigneeId = assigneeRaw ? Number(assigneeRaw) : null;
    const desc =
      /** @type {HTMLTextAreaElement} */ (form.querySelector('#newDesc')).value.trim() || null;

    /** @type {{ title: string, dueDate: string, priority: string, visibility: string, description?: string | null, assigneeId?: number | null, stakeholderUserIds?: number[] }} */
    const body = { title, dueDate, priority, visibility };
    if (desc) body.description = desc;
    if (assigneeId) body.assigneeId = assigneeId;
    if (visibility === 'STAKEHOLDERS' && this.#selectedSH.length)
      body.stakeholderUserIds = this.#selectedSH.map((s) => s.userId);

    submitBtn.disabled = true;
    submitBtn.textContent = '作成中...';
    try {
      const task = await Api.createTask(body);
      this.#oc?.hide();
      this.dispatchEvent(
        new CustomEvent('drawer-task-created', { bubbles: true, detail: { task } }),
      );
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.textContent = '作成';
      errDiv.textContent = `作成に失敗しました: ${/** @type {any} */ (err).message || ''}`;
      errDiv.className = 'alert alert-danger py-2 small mb-3';
    }
  }

  // ---- DETAIL MODE ----

  #renderDetail() {
    const task = this.#task;
    if (!task) return;
    const hdr = this.#hdr;
    hdr.replaceChildren();

    // Header: visibility badge + title
    const visBadge = document.createElement('span');
    visBadge.className = `vis-badge vis-${task.visibility} me-2`;
    visBadge.innerHTML = `<i class="bi ${VIS_ICON[task.visibility]}" aria-hidden="true"></i>${VIS_LABEL[task.visibility]}`;

    const titleEl = document.createElement('h5');
    titleEl.className = 'offcanvas-title text-truncate flex-grow-1';
    titleEl.title = task.title;
    titleEl.textContent = task.title;

    hdr.append(visBadge, titleEl, this.#makeCloseBtn());

    // Body
    const bdy = this.#bdy;
    bdy.replaceChildren();

    // Error area
    const errDiv = document.createElement('div');
    errDiv.className = 'd-none';
    errDiv.id = 'detail-error';
    bdy.appendChild(errDiv);

    // Status
    const statusRow = document.createElement('div');
    statusRow.className = 'mb-3';
    const statusLabel = document.createElement('div');
    statusLabel.className = 'drawer-field-label';
    statusLabel.textContent = 'ステータス';
    statusRow.appendChild(statusLabel);
    if (this.#can('status')) {
      const sel = document.createElement('select');
      sel.className = `form-select form-select-sm d-inline-block w-auto inline-sel st-sel-${task.status}`;
      ['NOT_STARTED', 'IN_PROGRESS', 'DONE', 'ON_HOLD'].forEach((s) => {
        const opt = document.createElement('option');
        opt.value = s;
        opt.textContent = ST_LABEL[s];
        opt.selected = s === task.status;
        sel.appendChild(opt);
      });
      sel.addEventListener('change', () => this.#changeStatus(sel));
      statusRow.appendChild(sel);
    } else {
      const badge = document.createElement('span');
      badge.className = `st-badge st-${task.status}`;
      badge.textContent = ST_LABEL[task.status];
      statusRow.appendChild(badge);
    }
    bdy.appendChild(statusRow);

    // Description
    if (task.description) {
      const descRow = document.createElement('div');
      descRow.className = 'mb-3';
      const descLabel = document.createElement('div');
      descLabel.className = 'drawer-field-label';
      descLabel.textContent = '説明';
      const descText = document.createElement('p');
      descText.className = 'mb-0 small drawer-desc-text';
      descText.textContent = task.description;
      descRow.append(descLabel, descText);
      bdy.appendChild(descRow);
    }

    // Meta fields grid
    const meta = document.createElement('div');
    meta.className = 'drawer-meta-grid mb-3';
    /** @type {Array<[string, Node]>} */
    const metaFields = [
      ['優先度', this.#makePriBadge(task.priority)],
      ['期限', this.#dueDateEl(task.dueDate)],
      ['担当者', document.createTextNode(task.assignee?.fullName ?? '—')],
      ['所有者', document.createTextNode(task.owner?.fullName ?? '—')],
      ['作成日', document.createTextNode(this.#fmtDate(task.createdAt))],
      ['更新日', document.createTextNode(this.#fmtDate(task.updatedAt))],
    ];
    if (task.completedAt) {
      metaFields.push(['完了日時', document.createTextNode(this.#fmtDateTime(task.completedAt))]);
    }
    metaFields.forEach(([lbl, valNode]) => {
      const lblEl = document.createElement('span');
      lblEl.className = 'drawer-meta-label';
      lblEl.textContent = lbl;
      const valEl = document.createElement('span');
      valEl.className = 'drawer-meta-value';
      valEl.appendChild(valNode);
      meta.append(lblEl, valEl);
    });
    bdy.appendChild(meta);

    bdy.appendChild(document.createElement('hr'));

    // Visibility section
    const visRow = document.createElement('div');
    visRow.className = 'mb-3';
    const visLabel = document.createElement('div');
    visLabel.className = 'drawer-field-label';
    visLabel.textContent = '公開範囲';
    visRow.appendChild(visLabel);
    const visBadge2 = document.createElement('span');
    visBadge2.className = `vis-badge vis-${task.visibility}`;
    visBadge2.innerHTML = `<i class="bi ${VIS_ICON[task.visibility]}" aria-hidden="true"></i>${VIS_LABEL[task.visibility]}`;
    visRow.appendChild(visBadge2);
    if (this.#can('visibility')) {
      const changeBtn = document.createElement('button');
      changeBtn.type = 'button';
      changeBtn.className = 'btn btn-link btn-sm ms-2 p-0';
      changeBtn.textContent = '変更';
      changeBtn.addEventListener('click', () => this.#showVisibilityPicker(visRow));
      visRow.appendChild(changeBtn);
    }
    bdy.appendChild(visRow);

    // Stakeholders section
    bdy.appendChild(this.#buildStakeholdersSection());

    bdy.appendChild(document.createElement('hr'));

    // Action buttons
    const actions = document.createElement('div');
    actions.className = 'd-flex gap-2 flex-wrap';
    if (this.#can('edit')) {
      const editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-primary btn-sm';
      editBtn.innerHTML = '<i class="bi bi-pencil me-1" aria-hidden="true"></i>編集';
      editBtn.addEventListener('click', () => this.#switchToEdit());
      actions.appendChild(editBtn);
    }
    if (this.#can('delete')) {
      const delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-outline-danger btn-sm';
      delBtn.innerHTML = '<i class="bi bi-trash me-1" aria-hidden="true"></i>削除';
      delBtn.addEventListener('click', () => this.#showDeleteConfirm(actions));
      actions.appendChild(delBtn);
    }
    if (actions.children.length) bdy.appendChild(actions);
  }

  #buildStakeholdersSection() {
    const section = document.createElement('div');
    section.className = 'mb-3';
    const label = document.createElement('div');
    label.className = 'drawer-field-label';
    label.textContent = '関係者';
    section.appendChild(label);

    const chips = document.createElement('div');
    chips.className = 'drawer-chips';
    if (this.#stakeholders.length === 0) {
      const empty = document.createElement('span');
      empty.className = 'text-muted small';
      empty.textContent = 'なし';
      chips.appendChild(empty);
    } else {
      this.#stakeholders.forEach((sh) => {
        const rmFn = this.#can('stakeholder') ? () => this.#removeStakeholder(sh.userId) : null;
        chips.appendChild(this.#makeChip(sh.fullName, rmFn));
      });
    }
    section.appendChild(chips);

    if (this.#can('stakeholder')) {
      const addRow = document.createElement('div');
      addRow.className = 'd-flex gap-2 mt-2';
      const sel = document.createElement('select');
      sel.className = 'form-select form-select-sm flex-grow-1';
      const blankOpt = document.createElement('option');
      blankOpt.value = '';
      blankOpt.textContent = '関係者を追加...';
      sel.appendChild(blankOpt);
      const existIds = new Set(this.#stakeholders.map((s) => s.userId));
      this.#tenantUsers.forEach((u) => {
        if (existIds.has(u.userId)) return;
        const opt = document.createElement('option');
        opt.value = String(u.userId);
        opt.textContent = u.fullName;
        sel.appendChild(opt);
      });
      const addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = 'btn btn-sm btn-outline-secondary';
      addBtn.textContent = '追加';
      addBtn.addEventListener('click', () => this.#addStakeholder(Number(sel.value)));
      addRow.append(sel, addBtn);
      section.appendChild(addRow);
    }
    return section;
  }

  /** @param {HTMLElement} visRow */
  #showVisibilityPicker(visRow) {
    const task = this.#task;
    if (!task) return;
    // Replace the row content with an inline selector
    visRow.replaceChildren();
    const lbl = document.createElement('div');
    lbl.className = 'drawer-field-label';
    lbl.textContent = '公開範囲';
    const sel = document.createElement('select');
    sel.className = 'form-select form-select-sm d-inline-block w-auto me-2';
    Object.entries(VIS_LABEL).forEach(([val, name]) => {
      const opt = document.createElement('option');
      opt.value = val;
      opt.textContent = name;
      opt.selected = val === task.visibility;
      sel.appendChild(opt);
    });

    // SH picker shown when STAKEHOLDERS
    const shSection = document.createElement('div');
    shSection.className = 'mt-2 d-none';
    const shSel = document.createElement('select');
    shSel.className = 'form-select form-select-sm flex-grow-1';
    const shOptBlank = document.createElement('option');
    shOptBlank.value = '';
    shOptBlank.textContent = '関係者を選択...';
    shSel.appendChild(shOptBlank);
    this.#tenantUsers.forEach((u) => {
      const opt = document.createElement('option');
      opt.value = String(u.userId);
      opt.textContent = u.fullName;
      opt.selected = this.#stakeholders.some((s) => s.userId === u.userId);
      shSel.appendChild(opt);
    });
    shSel.multiple = true;
    shSection.appendChild(shSel);

    sel.addEventListener('change', () => {
      shSection.classList.toggle('d-none', sel.value !== 'STAKEHOLDERS');
    });
    if (task.visibility === 'STAKEHOLDERS') shSection.classList.remove('d-none');

    const saveBtn = document.createElement('button');
    saveBtn.type = 'button';
    saveBtn.className = 'btn btn-sm btn-primary me-1';
    saveBtn.textContent = '保存';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn btn-sm btn-light';
    cancelBtn.textContent = '取消';

    saveBtn.addEventListener('click', async () => {
      const newVis = sel.value;
      /** @type {number[] | undefined} */
      let shIds;
      if (newVis === 'STAKEHOLDERS') {
        shIds = Array.from(shSel.selectedOptions)
          .map((o) => Number(o.value))
          .filter(Boolean);
      }
      saveBtn.disabled = true;
      saveBtn.textContent = '保存中...';
      try {
        const etag = this.#etag;
        if (!etag) return;
        await Api.changeVisibility(task.id, etag, newVis, shIds);
        // Reload task + stakeholders and re-render
        await this.#loadAndRenderDetail(task.id);
        this.dispatchEvent(
          new CustomEvent('drawer-task-updated', {
            bubbles: true,
            detail: { task: this.#task },
          }),
        );
      } catch (err) {
        if (/** @type {any} */ (err).status === 412) {
          this.#showDetailError(
            '他のユーザーがこのタスクを編集しました。一旦閉じてから再度開いてください。',
          );
        } else {
          this.#showDetailError(
            `公開範囲の変更に失敗しました: ${/** @type {any} */ (err).message || ''}`,
          );
        }
        this.#renderDetail();
      }
    });

    cancelBtn.addEventListener('click', () => this.#renderDetail());

    const btnRow = document.createElement('div');
    btnRow.className = 'mt-2';
    btnRow.append(saveBtn, cancelBtn);
    visRow.append(lbl, sel, shSection, btnRow);
  }

  /** @param {HTMLElement} actionsEl */
  #showDeleteConfirm(actionsEl) {
    const task = this.#task;
    if (!task) return;
    actionsEl.replaceChildren();
    const msg = document.createElement('span');
    msg.className = 'small text-danger me-2 align-self-center';
    msg.textContent = `「${task.title}」を削除しますか？`;
    const doBtn = document.createElement('button');
    doBtn.type = 'button';
    doBtn.className = 'btn btn-danger btn-sm';
    doBtn.textContent = '削除する';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn btn-light btn-sm';
    cancelBtn.textContent = '取消';

    doBtn.addEventListener('click', async () => {
      const t = this.#task;
      if (!t) return;
      const etag = this.#etag;
      if (!etag) return;
      doBtn.disabled = true;
      doBtn.textContent = '削除中...';
      try {
        await Api.deleteTask(t.id, etag);
        const taskId = t.id;
        this.#oc?.hide();
        this.dispatchEvent(
          new CustomEvent('drawer-task-deleted', {
            bubbles: true,
            detail: { taskId },
          }),
        );
      } catch (err) {
        if (/** @type {any} */ (err).status === 412) {
          this.#showDetailError(
            '他のユーザーがこのタスクを編集しました。ページを再読み込みしてください。',
          );
        } else {
          this.#showDetailError(`削除に失敗しました: ${/** @type {any} */ (err).message || ''}`);
        }
        this.#renderDetail();
      }
    });

    cancelBtn.addEventListener('click', () => this.#renderDetail());
    actionsEl.append(msg, doBtn, cancelBtn);
  }

  /** @param {string} msg */
  #showDetailError(msg) {
    const errDiv = this.#bdy.querySelector('#detail-error');
    if (!errDiv) return;
    errDiv.textContent = msg;
    errDiv.className = 'alert alert-danger py-2 small mb-3';
  }

  /** @param {HTMLSelectElement} sel */
  async #changeStatus(sel) {
    const task = this.#task;
    if (!task) return;
    const prev = task.status;
    sel.disabled = true;
    try {
      const updated = await Api.changeStatus(task.id, sel.value);
      this.#task = updated;
      if (updated.version != null) this.#etag = `W/"${updated.version}"`;
      this.dispatchEvent(
        new CustomEvent('drawer-task-updated', {
          bubbles: true,
          detail: { task: updated },
        }),
      );
      this.#renderDetail();
    } catch (err) {
      sel.value = prev;
      sel.disabled = false;
      this.#showDetailError(
        `ステータスの更新に失敗しました: ${/** @type {any} */ (err).message || ''}`,
      );
    }
  }

  /** @param {number} userId */
  async #addStakeholder(userId) {
    if (!userId) return;
    const task = this.#task;
    if (!task) return;
    try {
      const sh = await Api.addStakeholder(task.id, userId);
      this.#stakeholders = [...this.#stakeholders, sh];
      this.#renderDetail();
    } catch (err) {
      this.#showDetailError(
        /** @type {any} */ (err).status === 409
          ? 'このユーザーはすでに関係者です'
          : `関係者の追加に失敗しました: ${/** @type {any} */ (err).message || ''}`,
      );
    }
  }

  /** @param {number} userId */
  async #removeStakeholder(userId) {
    const task = this.#task;
    if (!task) return;
    try {
      await Api.removeStakeholder(task.id, userId);
      this.#stakeholders = this.#stakeholders.filter((s) => s.userId !== userId);
      this.#renderDetail();
    } catch (err) {
      this.#showDetailError(
        `関係者の削除に失敗しました: ${/** @type {any} */ (err).message || ''}`,
      );
    }
  }

  // ---- EDIT MODE ----

  #switchToEdit() {
    const task = this.#task;
    if (!task) return;
    this.#replaceUrl(`/tasks/${task.id}/edit`);
    this.#renderEdit();
  }

  #switchToDetail() {
    const task = this.#task;
    if (!task) return;
    this.#replaceUrl(`/tasks/${task.id}`);
    this.#renderDetail();
  }

  #renderEdit() {
    const task = this.#task;
    if (!task) return;
    const hdr = this.#hdr;
    hdr.replaceChildren();
    const title = document.createElement('h5');
    title.className = 'offcanvas-title text-truncate flex-grow-1';
    title.textContent = 'タスク編集';
    hdr.append(title, this.#makeCloseBtn());

    const bdy = this.#bdy;
    bdy.replaceChildren();

    const errDiv = document.createElement('div');
    errDiv.className = 'd-none';
    errDiv.id = 'edit-form-error';
    bdy.appendChild(errDiv);

    const form = document.createElement('form');
    form.noValidate = true;
    bdy.appendChild(form);

    // Title
    const titleGrp = this.#fieldGroup('editTitle', 'タイトル', true);
    const titleInput = document.createElement('input');
    titleInput.id = 'editTitle';
    titleInput.className = 'form-control';
    titleInput.maxLength = 100;
    titleInput.required = true;
    titleInput.value = task.title;
    titleGrp.appendChild(titleInput);
    form.appendChild(titleGrp);

    // Description
    const descGrp = this.#fieldGroup('editDesc', '説明', false);
    const descTa = document.createElement('textarea');
    descTa.id = 'editDesc';
    descTa.className = 'form-control';
    descTa.rows = 4;
    descTa.maxLength = 2000;
    descTa.value = task.description || '';
    descGrp.appendChild(descTa);
    form.appendChild(descGrp);

    // Due + Assignee row
    const row = document.createElement('div');
    row.className = 'row g-3';
    const dueCol = document.createElement('div');
    dueCol.className = 'col-6';
    const dueGrp = this.#fieldGroup('editDue', '期限日', true);
    const dueInput = document.createElement('input');
    dueInput.type = 'date';
    dueInput.id = 'editDue';
    dueInput.className = 'form-control';
    dueInput.required = true;
    dueInput.value = task.dueDate || '';
    dueGrp.appendChild(dueInput);
    dueCol.appendChild(dueGrp);

    const assCol = document.createElement('div');
    assCol.className = 'col-6';
    const assGrp = this.#fieldGroup('editAssignee', '担当者', false);
    const assSel = document.createElement('select');
    assSel.id = 'editAssignee';
    assSel.className = 'form-select';
    const emptyOpt = document.createElement('option');
    emptyOpt.value = '';
    emptyOpt.textContent = '未割当';
    assSel.appendChild(emptyOpt);
    this.#tenantUsers.forEach((u) => {
      const opt = document.createElement('option');
      opt.value = String(u.userId);
      opt.textContent = u.fullName;
      opt.selected = task.assignee?.id === u.userId;
      assSel.appendChild(opt);
    });
    assGrp.appendChild(assSel);
    assCol.appendChild(assGrp);
    row.append(dueCol, assCol);
    form.appendChild(row);

    // Priority
    const priGrp = document.createElement('div');
    priGrp.className = 'mt-3';
    const priFs = document.createElement('fieldset');
    const priLeg = document.createElement('legend');
    priLeg.className = 'form-label small fw-bold';
    priLeg.innerHTML = '優先度 <span class="text-danger" aria-hidden="true">*</span>';
    const priBtnGrp = document.createElement('div');
    priBtnGrp.className = 'btn-group w-100';
    priBtnGrp.setAttribute('role', 'group');
    [
      ['HIGH', 'btn-outline-danger', '高'],
      ['MEDIUM', 'btn-outline-warning', '中'],
      ['LOW', 'btn-outline-secondary', '低'],
    ].forEach(([val, cls, lbl]) => {
      const inp = document.createElement('input');
      inp.type = 'radio';
      inp.className = 'btn-check';
      inp.name = 'editPri';
      inp.id = `editPri-${val}`;
      inp.value = val;
      inp.checked = task.priority === val;
      const lab = document.createElement('label');
      lab.className = `btn ${cls}`;
      lab.htmlFor = `editPri-${val}`;
      lab.textContent = lbl;
      priBtnGrp.append(inp, lab);
    });
    priFs.append(priLeg, priBtnGrp);
    priGrp.appendChild(priFs);
    form.appendChild(priGrp);

    // Buttons
    const footer = document.createElement('div');
    footer.className = 'd-flex gap-2 mt-4';
    const saveBtn = document.createElement('button');
    saveBtn.type = 'submit';
    saveBtn.className = 'btn btn-primary flex-grow-1';
    saveBtn.textContent = '保存';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn btn-light';
    cancelBtn.textContent = '取消';
    cancelBtn.addEventListener('click', () => this.#switchToDetail());
    footer.append(saveBtn, cancelBtn);
    form.appendChild(footer);

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      this.#submitEdit(form);
    });
  }

  /** @param {HTMLFormElement} form */
  async #submitEdit(form) {
    const task = this.#task;
    if (!task) return;
    const etag = this.#etag;
    if (!etag) return;
    const errDiv = /** @type {HTMLElement} */ (this.#bdy.querySelector('#edit-form-error'));
    const saveBtn = /** @type {HTMLButtonElement} */ (form.querySelector('[type=submit]'));
    errDiv.className = 'd-none';

    const title = /** @type {HTMLInputElement} */ (form.querySelector('#editTitle')).value.trim();
    if (!title) {
      errDiv.textContent = 'タイトルは必須です';
      errDiv.className = 'alert alert-danger py-2 small mb-3';
      return;
    }
    const dueDate = /** @type {HTMLInputElement} */ (form.querySelector('#editDue')).value;
    if (!dueDate) {
      errDiv.textContent = '期限日は必須です';
      errDiv.className = 'alert alert-danger py-2 small mb-3';
      return;
    }

    const priority = /** @type {HTMLInputElement | null} */ (
      form.querySelector('input[name="editPri"]:checked')
    )?.value;
    const assigneeRaw = /** @type {HTMLSelectElement} */ (form.querySelector('#editAssignee'))
      .value;
    const assigneeId = assigneeRaw ? Number(assigneeRaw) : null;
    const desc =
      /** @type {HTMLTextAreaElement} */ (form.querySelector('#editDesc')).value.trim() || null;

    /** @type {Record<string, unknown>} */
    const body = {};
    if (title !== task.title) body.title = title;
    if (dueDate !== task.dueDate) body.dueDate = dueDate;
    if (priority !== task.priority) body.priority = priority;
    // description: null to clear, string to set
    const prevDesc = task.description || null;
    if (desc !== prevDesc) body.description = desc;
    // assigneeId: null to clear
    const prevAss = task.assignee?.id ?? null;
    if (assigneeId !== prevAss) body.assigneeId = assigneeId;

    if (Object.keys(body).length === 0) {
      // No changes — just go back to detail
      this.#switchToDetail();
      return;
    }

    saveBtn.disabled = true;
    saveBtn.textContent = '保存中...';
    try {
      const updated = await Api.patchTask(task.id, etag, body);
      this.#task = updated;
      // Re-fetch ETag from updated task (patchTask returns TaskDetail with version)
      this.#etag = updated.version != null ? `W/"${updated.version}"` : this.#etag;
      this.dispatchEvent(
        new CustomEvent('drawer-task-updated', {
          bubbles: true,
          detail: { task: updated },
        }),
      );
      this.#switchToDetail();
    } catch (err) {
      saveBtn.disabled = false;
      saveBtn.textContent = '保存';
      if (/** @type {any} */ (err).status === 412) {
        errDiv.textContent =
          '他のユーザーがこのタスクを編集しました。一旦閉じてから再度開いてください。';
      } else if (/** @type {any} */ (err).status === 403) {
        errDiv.textContent = '編集権限がありません';
      } else {
        errDiv.textContent = `保存に失敗しました: ${/** @type {any} */ (err).message || ''}`;
      }
      errDiv.className = 'alert alert-danger py-2 small mb-3';
    }
  }

  // ---- Utilities ----

  /**
   * @param {string} id
   * @param {string} label
   * @param {boolean} required
   */
  #fieldGroup(id, label, required) {
    const grp = document.createElement('div');
    grp.className = 'mb-3';
    const lbl = document.createElement('label');
    lbl.className = 'form-label small fw-bold';
    lbl.htmlFor = id;
    lbl.textContent = label;
    if (required) {
      const star = document.createElement('span');
      star.className = 'text-danger ms-1';
      star.setAttribute('aria-hidden', 'true');
      star.textContent = '*';
      lbl.appendChild(star);
    }
    grp.appendChild(lbl);
    return grp;
  }

  /**
   * @param {string} label
   * @param {(() => void) | null} onRemove
   */
  #makeChip(label, onRemove) {
    const chip = document.createElement('span');
    chip.className = 'drawer-chip';
    chip.textContent = label;
    if (onRemove) {
      const rm = document.createElement('button');
      rm.type = 'button';
      rm.className = 'drawer-chip-rm';
      rm.setAttribute('aria-label', `${label}を削除`);
      rm.innerHTML = '&times;';
      rm.addEventListener('click', onRemove);
      chip.appendChild(rm);
    }
    return chip;
  }

  /** @param {string} priority */
  #makePriBadge(priority) {
    const span = document.createElement('span');
    span.className = `pri-badge pri-${priority}`;
    span.textContent = PRI_LABEL[priority];
    return span;
  }

  /** @param {string | null} dueDate */
  #dueDateEl(dueDate) {
    if (!dueDate) return document.createTextNode('—');
    const today = new Date(
      `${new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date())}T00:00:00`,
    );
    const due = new Date(`${dueDate}T00:00:00`);
    const diff = Math.round((due.getTime() - today.getTime()) / 86400000);
    const md = dueDate.slice(5).replace('-', '/');
    if (diff < 0) {
      const span = document.createElement('span');
      span.className = 'due-overdue';
      span.textContent = `${dueDate}(${diff}日)`;
      return span;
    }
    if (diff === 0) {
      const span = document.createElement('span');
      span.className = 'due-today';
      span.textContent = `今日 (${md})`;
      return span;
    }
    return document.createTextNode(`${dueDate} (あと${diff}日)`);
  }

  /** @param {string | null} iso */
  #fmtDate(iso) {
    if (!iso) return '—';
    return new Intl.DateTimeFormat('ja-JP', {
      timeZone: 'Asia/Tokyo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date(iso));
  }

  /** @param {string | null} iso */
  #fmtDateTime(iso) {
    if (!iso) return '—';
    return new Intl.DateTimeFormat('ja-JP', {
      timeZone: 'Asia/Tokyo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso));
  }
}

customElements.define('app-task-drawer', AppTaskDrawer);
