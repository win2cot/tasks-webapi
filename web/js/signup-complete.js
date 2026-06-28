// @ts-check

/**
 * サインアップ確認リンク先(公開・未認証、ADR-0040 §3.3)。?token= を読み、GET /api/signup/{token} で email と状態を取得し、
 * 会員登録フォームを表示する。送信で POST /api/signup/{token}/complete を呼び、完了後はログインへ誘導する。
 */

const _envMatchDone = window.location.hostname.match(/^tasks(?:-(\w+))?\.dgz48\.xyz$/);
const BASE_URL_DONE = _envMatchDone
  ? `https://api${_envMatchDone[1] ? `-${_envMatchDone[1]}` : ''}.tasks.dgz48.xyz`
  : 'http://localhost:8080';

/** @param {string} id */
function el(id) {
  return /** @type {HTMLElement} */ (mustQuery(document, `#${id}`));
}

/**
 * 案内パネルを表示し、フォーム・ローディングを隠す。
 * @param {string} title
 * @param {string} message
 * @param {boolean} [withLoginLink]
 */
function showNotice(title, message, withLoginLink) {
  el('loading').classList.add('d-none');
  el('complete-form').classList.add('d-none');
  el('notice-title').textContent = title;
  el('notice-message').textContent = message;
  el('notice-link').classList.toggle('d-none', withLoginLink !== true);
  el('notice').classList.remove('d-none');
}

/** @param {string} message */
function showFormError(message) {
  const e = el('form-error');
  e.textContent = message;
  e.classList.remove('d-none');
}

function hideFormError() {
  el('form-error').classList.add('d-none');
}

/** URL の token クエリを返す(無ければ空文字)。 */
function readToken() {
  return new URLSearchParams(window.location.search).get('token') ?? '';
}

/**
 * サインアップ要求を取得して画面を初期化する。
 * @param {string} token
 */
async function loadSignup(token) {
  const res = await fetch(`${BASE_URL_DONE}/api/signup/${encodeURIComponent(token)}`, {
    headers: { Accept: 'application/json' },
  });
  if (res.status === 404) {
    showNotice(
      '確認リンクが無効です',
      'リンクが正しいかご確認ください。期限切れの可能性もあります。',
    );
    return;
  }
  if (!res.ok) {
    showNotice('エラー', '確認に失敗しました。時間をおいて再度お試しください。');
    return;
  }

  const detail = /** @type {{ email: string, status: string }} */ (await res.json());

  if (detail.status === 'EXPIRED') {
    showNotice(
      '確認リンクの有効期限が切れています',
      'お手数ですが、もう一度新規登録からやり直してください。',
    );
    return;
  }
  if (detail.status === 'USED') {
    showNotice(
      'この確認リンクは使用済みです',
      '既に登録が完了している可能性があります。ログインしてご確認ください。',
      true,
    );
    return;
  }
  if (detail.status === 'REVOKED') {
    showNotice(
      'この確認リンクは無効です',
      'お手数ですが、もう一度新規登録からやり直してください。',
    );
    return;
  }

  el('email-label').textContent = detail.email;
  el('loading').classList.add('d-none');
  el('complete-form').classList.remove('d-none');
}

/**
 * 登録フォーム送信。
 * @param {SubmitEvent} event
 * @param {string} token
 */
async function onSubmit(event, token) {
  event.preventDefault();
  hideFormError();

  const fullName = /** @type {HTMLInputElement} */ (el('full-name')).value.trim();
  const fullNameKana = /** @type {HTMLInputElement} */ (el('full-name-kana')).value.trim();
  const departmentName = /** @type {HTMLInputElement} */ (el('department-name')).value.trim();
  const password = /** @type {HTMLInputElement} */ (el('password')).value;

  if (fullName === '' || fullNameKana === '') {
    showFormError('氏名と氏名(カナ)を入力してください。');
    return;
  }
  if (password.length < 8) {
    showFormError('パスワードは 8 文字以上で設定してください。');
    return;
  }

  const button = /** @type {HTMLButtonElement} */ (el('btn-complete'));
  button.disabled = true;

  try {
    const res = await fetch(`${BASE_URL_DONE}/api/signup/${encodeURIComponent(token)}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({
        fullName,
        fullNameKana,
        departmentName: departmentName === '' ? null : departmentName,
        password,
      }),
    });

    if (res.ok) {
      showNotice(
        '登録が完了しました',
        '設定したメールアドレスとパスワードでログインしてください。',
        true,
      );
      return;
    }
    if (res.status === 409) {
      showFormError('この確認リンクは使用できない状態です(期限切れ・使用済み、または登録済み)。');
    } else if (res.status === 404) {
      showFormError('確認リンクが無効です。');
    } else if (res.status === 400) {
      showFormError('入力内容を確認してください(氏名・カナ・8 文字以上のパスワード)。');
    } else {
      showFormError('登録に失敗しました。時間をおいて再度お試しください。');
    }
    button.disabled = false;
  } catch {
    showFormError('通信に失敗しました。ネットワークをご確認ください。');
    button.disabled = false;
  }
}

async function main() {
  const token = readToken();
  if (token === '') {
    showNotice('確認トークンがありません', 'メールに記載されたリンクからアクセスしてください。');
    return;
  }
  /** @type {HTMLFormElement} */ (el('complete-form')).addEventListener('submit', (event) =>
    onSubmit(/** @type {SubmitEvent} */ (event), token),
  );
  await loadSignup(token);
}

main();
