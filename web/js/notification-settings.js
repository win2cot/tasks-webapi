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
// 最後に保存に成功した設定。即時保存が失敗したときのロールバック先(#815)。
/** @type {NotificationSettings} */
let lastSaved = { emailDueToday: false, emailOverdue: false, emailStakeholder: false };

/** @type {ReturnType<typeof setTimeout> | undefined} */
let savedHintTimer;

/**
 * @param {NotificationSettings} settings
 */
function fillForm(settings) {
  checkbox('#email-due-today').checked = settings.emailDueToday;
  checkbox('#email-overdue').checked = settings.emailOverdue;
  checkbox('#email-stakeholder').checked = settings.emailStakeholder;
}

/**
 * 全トグルの活性/非活性を切り替える(保存中の二重操作・競合を防ぐ)。
 * @param {boolean} disabled
 */
function setTogglesDisabled(disabled) {
  for (const id of ['#email-due-today', '#email-overdue', '#email-stakeholder']) {
    checkbox(id).disabled = disabled;
  }
}

/** 「保存しました」を一時表示し、しばらくして自動的に隠す。 */
function flashSaved() {
  const hint = /** @type {HTMLElement} */ (mustQuery(document, '#saved-alert'));
  hint.classList.remove('d-none');
  clearTimeout(savedHintTimer);
  savedHintTimer = setTimeout(() => hint.classList.add('d-none'), 2000);
}

/**
 * トグル変更時に現在の全状態を即時保存する(明示保存ボタンは廃止、#815)。
 * 楽観的更新(UI は既に反映済み)+ 失敗時は最後の保存状態へロールバックする。
 */
async function onToggleChange() {
  /** @type {HTMLElement} */ (mustQuery(document, '#error-alert')).classList.add('d-none');
  /** @type {NotificationSettings} */
  const desired = {
    emailDueToday: checkbox('#email-due-today').checked,
    emailOverdue: checkbox('#email-overdue').checked,
    emailStakeholder: checkbox('#email-stakeholder').checked,
  };
  setTogglesDisabled(true);
  try {
    const saved = await Api.updateNotificationSettings(desired);
    lastSaved = saved;
    fillForm(saved);
    flashSaved();
  } catch (err) {
    fillForm(lastSaved); // ロールバック
    showError(`保存に失敗しました: ${/** @type {any} */ (err).message}`);
  } finally {
    setTogglesDisabled(false);
  }
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
    lastSaved = settings;
    fillForm(settings);
    /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
    /** @type {HTMLElement} */ (mustQuery(document, '#settings-form')).classList.remove('d-none');
    // 明示保存ボタンは廃止。各トグルの変更で即時保存する(#815)。
    for (const id of ['#email-due-today', '#email-overdue', '#email-stakeholder']) {
      checkbox(id).addEventListener('change', onToggleChange);
    }
  } catch (err) {
    showError(`設定の取得に失敗しました: ${/** @type {any} */ (err).message}`);
  }
}

main();
