// @ts-check

/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

/**
 * @param {string} message
 */
function showError(message) {
  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  const el = /** @type {HTMLElement} */ (mustQuery(document, '#error-alert'));
  el.textContent = message;
  el.classList.remove('d-none');
}

/**
 * @param {string} id
 * @param {string} value
 */
function setField(id, value) {
  /** @type {HTMLElement} */ (mustQuery(document, id)).textContent = value;
}

/**
 * @param {UserProfile} profile
 */
function render(profile) {
  setField('#pf-full-name', profile.fullName);
  setField('#pf-full-name-kana', profile.fullNameKana);
  setField('#pf-email', profile.email);
  setField('#pf-department', profile.departmentName || '—');

  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#profile-card')).classList.remove('d-none');
}

async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) {
    return;
  }

  // SaaS Admin(APP_ADMIN)は業務画面の対象外。テナント未所属のため /api/auth/me 等が 403 になる前に
  // プラットフォーム監視へ誘導する(#816 役割不適合ページ着地のガード)。
  if (Auth.isAppAdmin()) {
    window.location.replace('admin.html');
    return;
  }

  const user = Auth.getUser();
  const displayName = user?.name || user?.preferred_username || '';
  /** @type {HTMLElement} */ (mustQuery(document, '#nav-username')).textContent = displayName;
  /** @type {HTMLElement} */ (mustQuery(document, '#user-avatar')).textContent =
    displayName.slice(0, 1) || '?';

  // テナントスイッチャ / 管理者サイドバーの表示用に所属テナントを取得(失敗してもプロフィール表示は継続)。
  try {
    const me = await Api.getMe();
    const activeTenantId = Api.resolveActiveTenant(me.tenants);
    /** @type {AppTenantSwitcherElement} */ (mustQuery(document, '#tenant-switcher')).setData(
      me.tenants,
      activeTenantId,
    );
    const activeTenant = me.tenants?.find((t) => t.id === activeTenantId);
    if (activeTenant?.role === 'TENANT_ADMIN') {
      document.body.classList.add('role-admin');
    }
  } catch {
    // テナント情報の取得失敗はプロフィール表示の妨げにしない。
  }

  try {
    const profile = await Api.getMyProfile();
    render(profile);
  } catch (err) {
    showError(`プロフィールの取得に失敗しました: ${/** @type {any} */ (err).message}`);
  }
}

main();
