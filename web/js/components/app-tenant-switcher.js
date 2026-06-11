// <app-tenant-switcher> — dropdown or chip for switching tenants.
// Method: setData(tenants, activeTenantId)
// Depends on: Api (Api.selectTenant, Api.setTenantId), Bootstrap 5

const _tsBadgeTpl = document.createElement('template');
_tsBadgeTpl.innerHTML = '<span class="badge bg-secondary bg-opacity-75 me-2 align-middle"></span>';

const _tsDropdownTpl = document.createElement('template');
_tsDropdownTpl.innerHTML = `
<div class="dropdown me-2">
  <button class="btn btn-outline-light btn-sm dropdown-toggle" type="button"
    data-bs-toggle="dropdown" aria-expanded="false">
    <span class="ts-label"></span>
  </button>
  <ul class="dropdown-menu dropdown-menu-end shadow ts-menu"></ul>
</div>`;

const _tsItemTpl = document.createElement('template');
_tsItemTpl.innerHTML = `
<li>
  <button class="dropdown-item d-flex justify-content-between align-items-center" type="button">
    <span class="ts-item-name"></span>
    <small class="ms-2 text-muted ts-item-role"></small>
  </button>
</li>`;

function _roleLabel(role) {
  return role === 'TENANT_ADMIN' ? '管理者' : 'メンバー';
}

async function _switchTenant(tenantId) {
  try {
    await Api.selectTenant(tenantId);
    Api.setTenantId(tenantId);
    window.location.reload();
  } catch (err) {
    _showErrorToast('テナント切替に失敗しました: ' + err.message);
  }
}

function _showErrorToast(message) {
  const toast = document.createElement('div');
  toast.className = 'toast align-items-center text-white bg-danger border-0 position-fixed top-0 end-0 m-3';
  toast.setAttribute('role', 'alert');
  toast.setAttribute('aria-live', 'assertive');
  toast.style.zIndex = '1100';

  const d = document.createElement('div');
  d.className = 'd-flex';

  const body = document.createElement('div');
  body.className = 'toast-body';
  body.textContent = message;

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'btn-close btn-close-white me-2 m-auto';
  btn.dataset.bsDismiss = 'toast';
  btn.setAttribute('aria-label', '閉じる');

  d.appendChild(body);
  d.appendChild(btn);
  toast.appendChild(d);
  document.body.appendChild(toast);
  new bootstrap.Toast(toast, { autohide: false }).show();
}

class AppTenantSwitcher extends HTMLElement {
  setData(tenants, activeTenantId) {
    this.replaceChildren();
    if (!tenants || tenants.length === 0) return;
    this.classList.remove('d-none');

    if (tenants.length === 1) {
      const chip = _tsBadgeTpl.content.cloneNode(true).firstElementChild;
      chip.textContent = tenants[0].name;
      chip.title = _roleLabel(tenants[0].role);
      this.appendChild(chip);
      return;
    }

    const activeTenant = tenants.find(t => t.id === activeTenantId) ?? null;
    const wrapper = _tsDropdownTpl.content.cloneNode(true).firstElementChild;
    wrapper.querySelector('.ts-label').textContent =
      activeTenant ? activeTenant.name : 'テナントを選択';

    const menu = wrapper.querySelector('.ts-menu');
    tenants.forEach(t => {
      const li  = _tsItemTpl.content.cloneNode(true).firstElementChild;
      const btn = li.querySelector('button');
      const isActive = t.id === activeTenantId;
      if (isActive) btn.classList.add('active');
      li.querySelector('.ts-item-name').textContent = t.name;
      const roleEl = li.querySelector('.ts-item-role');
      roleEl.textContent = _roleLabel(t.role);
      if (isActive) {
        roleEl.classList.remove('text-muted');
        roleEl.classList.add('text-white-50');
      }
      btn.addEventListener('click', () => {
        if (t.id === activeTenantId) return;
        _switchTenant(t.id);
      });
      menu.appendChild(li);
    });

    this.appendChild(wrapper);
  }
}
customElements.define('app-tenant-switcher', AppTenantSwitcher);
