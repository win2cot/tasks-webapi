/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

async function main() {
  const authenticated = await Auth.init();
  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');

  if (!authenticated) {
    return;
  }

  const user = Auth.getUser();
  /** @type {HTMLElement} */ (mustQuery(document, '#nav-username')).textContent =
    user?.name || user?.preferred_username || '';
  /** @type {HTMLElement} */ (mustQuery(document, '#nav-username')).classList.remove('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).classList.remove('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#content-logged-in')).classList.remove('d-none');

  try {
    const me = await Api.getMe();
    const activeTenantId = Api.resolveActiveTenant(me.tenants);

    if (activeTenantId !== null) {
      window.location.replace('tasks.html');
      return;
    }

    // テナント未選択: テナント選択 UI を表示
    /** @type {HTMLElement} */ (mustQuery(document, '#user-info')).textContent =
      'テナントを選択してタスク一覧を開いてください。';

    /** @type {AppTenantSwitcherElement} */ (mustQuery(document, '#tenant-switcher')).setData(
      me.tenants,
      null,
    );
  } catch (err) {
    /** @type {HTMLElement} */ (mustQuery(document, '#user-info')).textContent =
      `API 呼び出しエラー: ${/** @type {any} */ (err).message}`;
  }
}

main();
