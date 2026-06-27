// @ts-check

/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

/** ログイン中ユーザーの内部 ID(自己操作の抑止に使用)。 */
let myUserId = /** @type {number|null} */ (null);

/**
 * エラーメッセージを上部に表示する。
 * @param {string} message
 */
function showError(message) {
  const el = /** @type {HTMLElement} */ (mustQuery(document, '#error-alert'));
  el.textContent = message;
  el.classList.remove('d-none');
}

/**
 * 招待フォームの結果メッセージを表示する。
 * @param {string} message
 * @param {boolean} ok
 */
function showInviteFeedback(message, ok) {
  const el = /** @type {HTMLElement} */ (mustQuery(document, '#invite-feedback'));
  el.textContent = message;
  el.classList.remove('d-none', 'text-success', 'text-danger');
  el.classList.add(ok ? 'text-success' : 'text-danger');
}

/**
 * 状態バッジの [ラベル, Bootstrap クラス] を返す。
 * @param {string} status
 * @returns {[string, string]}
 */
function statusBadge(status) {
  if (status === 'ACTIVE') return ['有効', 'text-bg-success'];
  if (status === 'INVITED') return ['招待中', 'text-bg-warning'];
  return ['無効', 'text-bg-secondary'];
}

/**
 * 1 ユーザー分の行要素を生成する。
 * @param {TenantUser} u
 * @returns {HTMLTableRowElement}
 */
function buildRow(u) {
  const isSelf = u.userId === myUserId;
  const tr = document.createElement('tr');

  const tdName = document.createElement('td');
  tdName.textContent = u.fullName;
  if (isSelf) {
    const self = document.createElement('span');
    self.className = 'badge text-bg-light ms-2';
    self.textContent = '自分';
    tdName.append(self);
  }

  const tdEmail = document.createElement('td');
  tdEmail.className = 'text-muted small';
  tdEmail.textContent = u.email;

  // ロール(編集可能なセレクト。ACTIVE 以外・自分は変更不可)。
  const tdRole = document.createElement('td');
  const roleSelect = document.createElement('select');
  roleSelect.className = 'form-select form-select-sm';
  roleSelect.setAttribute('aria-label', `${u.fullName} のロール`);
  for (const [v, l] of [
    ['MEMBER', 'メンバー'],
    ['TENANT_ADMIN', 'テナント管理者'],
  ]) {
    const opt = document.createElement('option');
    opt.value = v;
    opt.textContent = l;
    if (u.role === v) opt.selected = true;
    roleSelect.append(opt);
  }
  roleSelect.disabled = isSelf || u.status !== 'ACTIVE';
  roleSelect.addEventListener('change', async () => {
    const newRole = /** @type {Role} */ (roleSelect.value);
    roleSelect.disabled = true;
    try {
      await Api.updateMemberRole(u.userId, newRole);
      u.role = newRole;
    } catch (err) {
      roleSelect.value = u.role;
      const e = /** @type {{ status?: number, message?: string }} */ (err);
      showError(
        e.status === 403
          ? 'ロールを変更する権限がありません(自分自身は変更できません)。'
          : `ロール変更に失敗しました: ${e.message ?? '不明なエラー'}`,
      );
    } finally {
      roleSelect.disabled = isSelf || u.status !== 'ACTIVE';
    }
  });
  tdRole.append(roleSelect);

  const tdStatus = document.createElement('td');
  const [label, cls] = statusBadge(u.status);
  const badge = document.createElement('span');
  badge.className = `badge ${cls}`;
  badge.textContent = label;
  tdStatus.append(badge);

  // 操作(削除。自分は不可)。
  const tdAct = document.createElement('td');
  tdAct.className = 'text-end';
  const delBtn = document.createElement('button');
  delBtn.type = 'button';
  delBtn.className = 'btn btn-outline-danger btn-sm';
  delBtn.setAttribute('aria-label', `${u.fullName} を削除`);
  const icon = document.createElement('i');
  icon.className = 'bi bi-trash';
  icon.setAttribute('aria-hidden', 'true');
  delBtn.append(icon);
  delBtn.disabled = isSelf;
  delBtn.addEventListener('click', async () => {
    if (!window.confirm(`${u.fullName}(${u.email})をテナントから削除しますか?`)) return;
    delBtn.disabled = true;
    try {
      await Api.removeMember(u.userId);
      tr.remove();
    } catch (err) {
      const e = /** @type {{ status?: number, message?: string }} */ (err);
      showError(
        e.status === 403
          ? '削除する権限がありません(自分自身は削除できません)。'
          : e.status === 404
            ? 'このメンバーは既に存在しません。'
            : `削除に失敗しました: ${e.message ?? '不明なエラー'}`,
      );
      delBtn.disabled = false;
    }
  });
  tdAct.append(delBtn);

  tr.append(tdName, tdEmail, tdRole, tdStatus, tdAct);
  return tr;
}

/**
 * メンバー一覧を描画する。
 * @param {TenantUser[]} users
 */
function renderUsers(users) {
  const tbody = /** @type {HTMLElement} */ (mustQuery(document, '#users-tbody'));
  tbody.replaceChildren();
  if (users.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 5;
    td.className = 'text-center text-muted py-4';
    td.textContent = 'メンバーがいません。';
    tr.append(td);
    tbody.append(tr);
    return;
  }
  for (const u of users) {
    tbody.append(buildRow(u));
  }
}

/** メンバー一覧を再取得して描画する。 */
async function reloadUsers() {
  const users = await Api.listTenantUsers();
  renderUsers(users);
}

/** 招待フォームの送信処理を登録する。 */
function wireInviteForm() {
  const form = /** @type {HTMLFormElement} */ (mustQuery(document, '#invite-form'));
  const emailInput = /** @type {HTMLInputElement} */ (mustQuery(document, '#invite-email'));
  const roleSelect = /** @type {HTMLSelectElement} */ (mustQuery(document, '#invite-role'));
  const btn = /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-invite'));

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = emailInput.value.trim();
    const role = /** @type {Role} */ (roleSelect.value);
    if (!email) {
      showInviteFeedback('メールアドレスを入力してください。', false);
      return;
    }
    btn.disabled = true;
    try {
      await Api.inviteUser({ email, role });
      showInviteFeedback(`${email} に招待メールを送信しました。`, true);
      form.reset();
    } catch (err) {
      const ex = /** @type {{ status?: number, message?: string }} */ (err);
      showInviteFeedback(
        ex.status === 409
          ? 'このメールアドレスは既にテナントのメンバーです。'
          : ex.status === 403
            ? '招待する権限がありません。'
            : `招待に失敗しました: ${ex.message ?? '不明なエラー'}`,
        false,
      );
    } finally {
      btn.disabled = false;
    }
  });
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
  myUserId = me.user.id;

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
  if (activeTenant?.role !== 'TENANT_ADMIN') {
    /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
    showError('このページはテナント管理者のみ利用できます。');
    return;
  }
  document.body.classList.add('role-admin');

  try {
    await reloadUsers();
    wireInviteForm();
    /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
    /** @type {HTMLElement} */ (mustQuery(document, '#content')).classList.remove('d-none');
  } catch (err) {
    const e = /** @type {{ status?: number, message?: string }} */ (err);
    /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
    showError(
      e.status === 403
        ? 'このページはテナント管理者のみ利用できます。'
        : `ユーザー一覧の取得に失敗しました: ${e.message ?? '不明なエラー'}`,
    );
  }
}

main();
