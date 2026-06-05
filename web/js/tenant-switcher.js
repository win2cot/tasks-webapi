/**
 * テナント切替ドロップダウンコンポーネント
 *
 * 所属テナントが複数ある場合は Bootstrap 5 dropdown を描画し、
 * 単一テナントの場合は非クリックの badge chip のみを表示する。
 *
 * 依存: api.js(Api.selectTenant / Api.setTenantId)、Bootstrap 5
 */

const TenantSwitcher = (() => {
  function _escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = String(str);
    return div.innerHTML;
  }

  function _roleLabel(role) {
    return role === 'TENANT_ADMIN' ? '管理者' : 'メンバー';
  }

  /**
   * エラーをトーストで右上に表示する。
   * @param {string} message
   */
  function _showErrorToast(message) {
    const toast = document.createElement('div');
    toast.className =
      'toast align-items-center text-white bg-danger border-0 position-fixed top-0 end-0 m-3';
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');
    toast.style.zIndex = '1100';
    toast.innerHTML =
      '<div class="d-flex">' +
      '<div class="toast-body">' +
      _escapeHtml(message) +
      '</div>' +
      '<button type="button" class="btn-close btn-close-white me-2 m-auto"' +
      ' data-bs-dismiss="toast" aria-label="閉じる"></button>' +
      '</div>';
    document.body.appendChild(toast);
    new bootstrap.Toast(toast, { autohide: false }).show();
  }

  /**
   * テナントを切替えてページをリロードする。
   * @param {number} tenantId
   */
  async function _switchTenant(tenantId) {
    try {
      await Api.selectTenant(tenantId);
      Api.setTenantId(tenantId);
      window.location.reload();
    } catch (err) {
      console.error('テナント切替に失敗しました:', err);
      _showErrorToast('テナント切替に失敗しました: ' + err.message);
    }
  }

  /**
   * テナント切替 UI をコンテナ要素に描画する。
   *
   * @param {HTMLElement} containerEl — 描画対象のコンテナ
   * @param {Array<{id: number, name: string, role: string}>} tenants — 所属テナント一覧
   * @param {number|null} activeTenantId — 現在アクティブなテナント ID
   */
  function render(containerEl, tenants, activeTenantId) {
    containerEl.innerHTML = '';

    if (tenants.length === 0) return;

    if (tenants.length === 1) {
      const chip = document.createElement('span');
      chip.className = 'badge bg-secondary bg-opacity-75 me-2 align-middle';
      chip.textContent = tenants[0].name;
      chip.title = _roleLabel(tenants[0].role);
      containerEl.appendChild(chip);
      containerEl.classList.remove('d-none');
      return;
    }

    // 複数テナント: Bootstrap 5 dropdown
    const activeTenant = tenants.find((t) => t.id === activeTenantId) || null;

    const itemsHtml = tenants
      .map((t) => {
        const isActive = t.id === activeTenantId;
        return (
          '<li>' +
          '<button class="dropdown-item d-flex justify-content-between align-items-center' +
          (isActive ? ' active' : '') +
          '" type="button" data-tenant-id="' +
          t.id +
          '">' +
          '<span>' +
          _escapeHtml(t.name) +
          '</span>' +
          '<small class="ms-2 ' +
          (isActive ? 'text-white-50' : 'text-muted') +
          '">' +
          _escapeHtml(_roleLabel(t.role)) +
          '</small>' +
          '</button>' +
          '</li>'
        );
      })
      .join('');

    const wrapper = document.createElement('div');
    wrapper.className = 'dropdown me-2';
    wrapper.id = 'tenant-switcher-dropdown';
    wrapper.innerHTML =
      '<button class="btn btn-outline-light btn-sm dropdown-toggle" type="button"' +
      ' data-bs-toggle="dropdown" aria-expanded="false">' +
      '<span id="tenant-switcher-label">' +
      (activeTenant ? _escapeHtml(activeTenant.name) : 'テナントを選択') +
      '</span>' +
      '</button>' +
      '<ul class="dropdown-menu dropdown-menu-end shadow">' +
      itemsHtml +
      '</ul>';

    containerEl.appendChild(wrapper);
    containerEl.classList.remove('d-none');

    wrapper.querySelectorAll('[data-tenant-id]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const tenantId = Number(btn.dataset.tenantId);
        if (tenantId === activeTenantId) return;
        _switchTenant(tenantId);
      });
    });
  }

  return { render };
})();
