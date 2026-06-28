/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

async function main() {
  const authenticated = await Auth.init();
  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');

  if (!authenticated) {
    return;
  }

  // SaaS Admin(APP_ADMIN)は業務ダッシュボードではなくプラットフォーム監視へ誘導する(S-12)。
  if (Auth.isAppAdmin()) {
    window.location.replace('admin.html');
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
      // ログイン後の既定表示は個人ダッシュボード(S-03、基本設計書 §3.2)。
      window.location.replace('dashboard.html');
      return;
    }

    // テナント未選択: テナント選択 UI と「テナント作成」導線を表示
    /** @type {HTMLElement} */ (mustQuery(document, '#user-info')).textContent =
      me.tenants.length === 0
        ? '所属テナントがありません。新しいテナントを作成してください。'
        : 'テナントを選択してタスク一覧を開いてください。';

    /** @type {HTMLElement} */ (mustQuery(document, '#link-create-tenant')).classList.remove(
      'd-none',
    );

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
