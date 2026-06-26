// @ts-check

/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

/**
 * フォームエラーを表示する。
 * @param {string} message
 */
function showError(message) {
  const el = /** @type {HTMLElement} */ (mustQuery(document, '#form-error'));
  el.textContent = message;
  el.classList.remove('d-none');
}

function hideError() {
  /** @type {HTMLElement} */ (mustQuery(document, '#form-error')).classList.add('d-none');
}

/**
 * @param {SubmitEvent} event
 */
async function onSubmit(event) {
  event.preventDefault();
  hideError();

  const input = /** @type {HTMLInputElement} */ (mustQuery(document, '#tenant-name'));
  const name = input.value.trim();
  if (name === '') {
    showError('テナント名を入力してください。');
    return;
  }

  const button = /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-create'));
  button.disabled = true;

  try {
    // 非メンバーテナントの X-Tenant-Id が残っていると 403 になるためクリアする。
    Api.setTenantId(null);
    const result = await Api.createTenant({ name });
    Api.setTenantId(String(result.tenant.id));
    window.location.replace('tasks.html');
  } catch (err) {
    const e = /** @type {{ status?: number, message?: string }} */ (err);
    if (e.status === 400) {
      showError('テナント名が不正です(1〜255 文字で入力してください)。');
    } else if (e.status === 409) {
      showError('同名のテナントが既に存在します。別の名前でお試しください。');
    } else {
      showError(`テナント作成に失敗しました: ${e.message ?? '不明なエラー'}`);
    }
    button.disabled = false;
  }
}

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

  const form = /** @type {HTMLFormElement} */ (mustQuery(document, '#signup-form'));
  form.classList.remove('d-none');
  form.addEventListener('submit', onSubmit);
}

main();
