// <app-task-drawer> — Bootstrap Offcanvas drawer for creating a new task.
// Methods: setUsers(tenantUsers), open()
// Fires: drawer-submit (bubbling) — currently a no-op until Sprint 3 POST /api/tasks
const _drawerTpl = document.createElement('template');
_drawerTpl.innerHTML = `
<div class="offcanvas offcanvas-end" tabindex="-1" aria-labelledby="newTaskLabel">
  <div class="offcanvas-header border-bottom">
    <h5 class="offcanvas-title" id="newTaskLabel">
      <i class="bi bi-plus-circle me-2 text-primary" aria-hidden="true"></i>新規タスク
    </h5>
    <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="閉じる"></button>
  </div>
  <div class="offcanvas-body">
    <form class="new-task-form">
      <div class="mb-3">
        <label class="form-label small fw-bold" for="newTitle">
          タイトル <span class="text-danger" aria-hidden="true">*</span>
        </label>
        <input id="newTitle" class="form-control" maxlength="100"
          placeholder="例: 設計レビュー" required aria-required="true" />
      </div>
      <div class="mb-3">
        <label class="form-label small fw-bold" for="newDesc">説明</label>
        <textarea id="newDesc" class="form-control" rows="3"
          maxlength="2000" placeholder="任意・最大2000文字"></textarea>
      </div>
      <div class="row g-3">
        <div class="col-6">
          <label class="form-label small fw-bold" for="newDue">
            期限日 <span class="text-danger" aria-hidden="true">*</span>
          </label>
          <input type="date" id="newDue" class="form-control" required aria-required="true" />
        </div>
        <div class="col-6">
          <label class="form-label small fw-bold" for="newAssignee">担当者</label>
          <select id="newAssignee" class="form-select new-assignee-sel">
            <option value="">未割当</option>
          </select>
        </div>
      </div>
      <div class="mt-3">
        <fieldset>
          <legend class="form-label small fw-bold">
            優先度 <span class="text-danger" aria-hidden="true">*</span>
          </legend>
          <div class="btn-group w-100" role="group">
            <input type="radio" class="btn-check" name="newPri" id="newPri-H" value="HIGH">
            <label class="btn btn-outline-danger" for="newPri-H">高</label>
            <input type="radio" class="btn-check" name="newPri" id="newPri-M" value="MEDIUM" checked>
            <label class="btn btn-outline-warning" for="newPri-M">中</label>
            <input type="radio" class="btn-check" name="newPri" id="newPri-L" value="LOW">
            <label class="btn btn-outline-secondary" for="newPri-L">低</label>
          </div>
        </fieldset>
      </div>
      <div class="mt-3">
        <fieldset>
          <legend class="form-label small fw-bold">
            公開範囲 <span class="text-danger" aria-hidden="true">*</span>
          </legend>
          <div class="btn-group w-100" role="group">
            <input type="radio" class="btn-check" name="newVis" id="newVis-T" value="TENANT" checked>
            <label class="btn btn-outline-success" for="newVis-T">
              <i class="bi bi-globe2" aria-hidden="true"></i> テナント
            </label>
            <input type="radio" class="btn-check" name="newVis" id="newVis-S" value="STAKEHOLDERS">
            <label class="btn btn-outline-primary" for="newVis-S">
              <i class="bi bi-people-fill" aria-hidden="true"></i> 関係者
            </label>
            <input type="radio" class="btn-check" name="newVis" id="newVis-P" value="PRIVATE">
            <label class="btn btn-outline-secondary" for="newVis-P">
              <i class="bi bi-lock-fill" aria-hidden="true"></i> 非公開
            </label>
          </div>
        </fieldset>
      </div>
      <div class="d-flex gap-2 mt-4">
        <button type="submit" class="btn btn-primary flex-grow-1">作成</button>
        <button type="button" class="btn btn-light" data-bs-dismiss="offcanvas">取消</button>
      </div>
    </form>
  </div>
</div>`;

class AppTaskDrawer extends HTMLElement {
  #offcanvasEl = null;

  connectedCallback() {
    this.replaceChildren(_drawerTpl.content.cloneNode(true));
    this.#offcanvasEl = this.firstElementChild;
    this.querySelector('.new-task-form').addEventListener('submit', e => {
      e.preventDefault();
      // TODO: POST /api/tasks (Sprint 3+)
      this.dispatchEvent(new CustomEvent('drawer-submit', { bubbles: true }));
      bootstrap.Offcanvas.getInstance(this.#offcanvasEl)?.hide();
    });
  }

  setUsers(tenantUsers) {
    const sel = this.querySelector('.new-assignee-sel');
    if (!sel) return;
    // Keep the empty "未割当" option, replace user options
    while (sel.options.length > 1) sel.remove(1);
    tenantUsers.forEach(u => {
      const opt = document.createElement('option');
      opt.value       = u.userId;
      opt.textContent = u.fullName;
      sel.appendChild(opt);
    });
  }

  open() {
    if (!this.#offcanvasEl) return;
    this.querySelector('.new-task-form')?.reset();
    bootstrap.Offcanvas.getOrCreateInstance(this.#offcanvasEl).show();
  }
}
customElements.define('app-task-drawer', AppTaskDrawer);
