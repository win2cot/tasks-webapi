// @ts-check

/**
 * セルフサインアップ要求画面(公開・未認証、ADR-0040 §3.3)。email を送ると確認メールが届く。
 * レスポンスは email の存在有無に関わらず常に同一(列挙耐性)なので、成功時は一律「送信しました」を表示する。
 */

const _envMatchReq = window.location.hostname.match(/^tasks(?:-(\w+))?\.dgz48\.xyz$/);
const BASE_URL_REQ = _envMatchReq
  ? `https://api${_envMatchReq[1] ? `-${_envMatchReq[1]}` : ''}.tasks.dgz48.xyz`
  : 'http://localhost:8080';

/** @param {string} id */
function el(id) {
  return /** @type {HTMLElement} */ (mustQuery(document, `#${id}`));
}

/** @param {string} message */
function showError(message) {
  const e = el('form-error');
  e.textContent = message;
  e.classList.remove('d-none');
}

function hideError() {
  el('form-error').classList.add('d-none');
}

/** @param {SubmitEvent} event */
async function onSubmit(event) {
  event.preventDefault();
  hideError();

  const email = /** @type {HTMLInputElement} */ (el('email')).value.trim();
  if (email === '') {
    showError('メールアドレスを入力してください。');
    return;
  }

  const button = /** @type {HTMLButtonElement} */ (el('btn-request'));
  button.disabled = true;

  try {
    const res = await fetch(`${BASE_URL_REQ}/api/signup/request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ email }),
    });

    if (res.ok) {
      el('signup-form').classList.add('d-none');
      el('sent-notice').classList.remove('d-none');
      return;
    }
    if (res.status === 400) {
      showError('メールアドレスの形式を確認してください。');
    } else {
      showError('送信に失敗しました。時間をおいて再度お試しください。');
    }
    button.disabled = false;
  } catch {
    showError('通信に失敗しました。ネットワークをご確認ください。');
    button.disabled = false;
  }
}

/** @type {HTMLFormElement} */ (el('signup-form')).addEventListener('submit', (event) =>
  onSubmit(/** @type {SubmitEvent} */ (event)),
);
