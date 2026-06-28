// @ts-check

/**
 * 招待受諾画面(公開・未認証、ADR-0040 §3.3)。auth.js / keycloak は読み込まず、公開エンドポイント
 * GET/POST /api/invitations/{token} を素の fetch で呼ぶ。API ベース URL は api.js と同じ env→ホスト規則で導出する。
 */

const _envMatch = window.location.hostname.match(/^tasks(?:-(\w+))?\.dgz48\.xyz$/);
const BASE_URL = _envMatch
  ? `https://api${_envMatch[1] ? `-${_envMatch[1]}` : ''}.tasks.dgz48.xyz`
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
  el('accept-form').classList.add('d-none');
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
 * 招待詳細を取得して画面を初期化する。
 * @param {string} token
 */
async function loadInvitation(token) {
  const res = await fetch(`${BASE_URL}/api/invitations/${encodeURIComponent(token)}`, {
    headers: { Accept: 'application/json' },
  });
  if (res.status === 404) {
    showNotice(
      '招待が見つかりません',
      'リンクが正しいかご確認ください。招待が取り消された可能性もあります。',
    );
    return;
  }
  if (!res.ok) {
    showNotice('エラー', '招待の確認に失敗しました。時間をおいて再度お試しください。');
    return;
  }

  const detail =
    /** @type {{ email: string, tenantName: string, status: string, alreadyRegistered: boolean }} */ (
      await res.json()
    );

  if (detail.status === 'EXPIRED') {
    showNotice('招待の有効期限が切れています', 'テナント管理者に招待の再送を依頼してください。');
    return;
  }
  if (detail.status === 'USED') {
    showNotice(
      'この招待は使用済みです',
      '既に参加が完了している可能性があります。ログインしてご確認ください。',
      true,
    );
    return;
  }
  if (detail.status === 'REVOKED') {
    showNotice('この招待は無効化されています', 'テナント管理者に招待の再送を依頼してください。');
    return;
  }
  if (detail.alreadyRegistered) {
    showNotice(
      '既に登録済みのアカウントです',
      'このメールアドレスは登録済みです。ログインしてから参加してください。',
      true,
    );
    return;
  }

  el('tenant-label').textContent = detail.tenantName;
  el('email-label').textContent = detail.email;
  el('loading').classList.add('d-none');
  el('accept-form').classList.remove('d-none');
}

/**
 * 受諾フォーム送信。
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

  const button = /** @type {HTMLButtonElement} */ (el('btn-accept'));
  button.disabled = true;

  try {
    const res = await fetch(`${BASE_URL}/api/invitations/${encodeURIComponent(token)}/accept`, {
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
        '参加が完了しました',
        '設定したメールアドレスとパスワードでログインしてください。',
        true,
      );
      return;
    }
    if (res.status === 409) {
      showFormError('この招待は受諾できない状態です(期限切れ・使用済み、または登録済み)。');
    } else if (res.status === 404) {
      showFormError('招待が見つかりません。リンクをご確認ください。');
    } else if (res.status === 400) {
      showFormError('入力内容を確認してください(氏名・カナ・8 文字以上のパスワード)。');
    } else {
      showFormError('参加に失敗しました。時間をおいて再度お試しください。');
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
    showNotice('招待トークンがありません', 'メールに記載されたリンクからアクセスしてください。');
    return;
  }
  /** @type {HTMLFormElement} */ (el('accept-form')).addEventListener('submit', (event) =>
    onSubmit(/** @type {SubmitEvent} */ (event), token),
  );
  await loadInvitation(token);
}

main();
