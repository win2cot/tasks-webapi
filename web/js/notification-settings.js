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
 * @returns {HTMLInputElement}
 */
function checkbox(id) {
  return /** @type {HTMLInputElement} */ (mustQuery(document, id));
}

/**
 * @param {NotificationSettings} settings
 */
function fillForm(settings) {
  checkbox('#email-due-today').checked = settings.emailDueToday;
  checkbox('#email-overdue').checked = settings.emailOverdue;
  checkbox('#email-stakeholder').checked = settings.emailStakeholder;
}

/**
 * @param {SubmitEvent} event
 */
async function onSubmit(event) {
  event.preventDefault();
  /** @type {HTMLElement} */ (mustQuery(document, '#saved-alert')).classList.add('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#error-alert')).classList.add('d-none');

  const button = /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-save'));
  button.disabled = true;
  try {
    const saved = await Api.updateNotificationSettings({
      emailDueToday: checkbox('#email-due-today').checked,
      emailOverdue: checkbox('#email-overdue').checked,
      emailStakeholder: checkbox('#email-stakeholder').checked,
    });
    fillForm(saved);
    /** @type {HTMLElement} */ (mustQuery(document, '#saved-alert')).classList.remove('d-none');
  } catch (err) {
    showError(`保存に失敗しました: ${/** @type {any} */ (err).message}`);
  } finally {
    button.disabled = false;
  }
}

async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) {
    return;
  }

  const user = Auth.getUser();
  const displayName = user?.name || user?.preferred_username || '';
  /** @type {HTMLElement} */ (mustQuery(document, '#nav-username')).textContent = displayName;
  /** @type {HTMLElement} */ (mustQuery(document, '#user-avatar')).textContent =
    displayName.slice(0, 1) || '?';

  /** @type {MeResponse} */
  let me;
  try {
    me = await Api.getMe();
  } catch (err) {
    showError(`ユーザー情報の取得に失敗しました: ${/** @type {any} */ (err).message}`);
    return;
  }

  const activeTenantId = Api.resolveActiveTenant(me.tenants);
  if (activeTenantId === null) {
    window.location.replace('index.html');
    return;
  }

  /** @type {AppTenantSwitcherElement} */ (mustQuery(document, '#tenant-switcher')).setData(
    me.tenants,
    activeTenantId,
  );
  const activeTenant = me.tenants?.find((t) => t.id === activeTenantId);
  if (activeTenant?.role === 'TENANT_ADMIN') {
    document.body.classList.add('role-admin');
  }

  try {
    const settings = await Api.getNotificationSettings();
    fillForm(settings);
    /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
    const form = /** @type {HTMLFormElement} */ (mustQuery(document, '#settings-form'));
    form.classList.remove('d-none');
    form.addEventListener('submit', onSubmit);
  } catch (err) {
    showError(`設定の取得に失敗しました: ${/** @type {any} */ (err).message}`);
  }
}

main();
