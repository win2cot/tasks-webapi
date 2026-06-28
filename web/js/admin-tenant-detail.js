// @ts-check

/** @type {HTMLElement} */ (mustQuery(document, '#btn-logout')).addEventListener('click', () =>
  Auth.logout(),
);

/** URL クエリ ?id= から得たテナント ID。 */
const detailTenantId = Number(new URLSearchParams(window.location.search).get('id'));
/** 直近に読み込んだテナント(状態切替の現在値参照に使用)。 @type {Tenant|null} */
let detailTenant = null;

/**
 * エラーメッセージを表示し、ローディングを止める。
 * @param {string} message
 */
function showError(message) {
  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  const el = /** @type {HTMLElement} */ (mustQuery(document, '#error-alert'));
  el.textContent = message;
  el.classList.remove('d-none');
}

/**
 * フォーム横の結果メッセージを表示する。
 * @param {string} selector
 * @param {string} message
 * @param {boolean} ok
 */
function showFeedback(selector, message, ok) {
  const el = /** @type {HTMLElement} */ (mustQuery(document, selector));
  el.textContent = message;
  el.classList.remove('d-none', 'text-success', 'text-danger');
  el.classList.add(ok ? 'text-success' : 'text-danger');
}

/**
 * テナント状態バッジの [ラベル, Bootstrap クラス] を返す。
 * @param {string} status
 * @returns {[string, string]}
 */
function statusBadge(status) {
  if (status === 'ACTIVE') return ['ACTIVE', 'text-bg-success'];
  if (status === 'SUSPENDED') return ['SUSPENDED', 'text-bg-warning'];
  return [status, 'text-bg-secondary'];
}

/**
 * ISO 日時を分単位の表記にする。
 * @param {string} iso
 * @returns {string}
 */
function formatDateTime(iso) {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '';
}

/**
 * 状態切替ボタンの表示(ラベル・色・説明)を現在状態に合わせて更新する。
 * @param {'ACTIVE'|'SUSPENDED'|'DELETED'} status
 */
function renderStatusControl(status) {
  const btn = /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-toggle-status'));
  const label = /** @type {HTMLElement} */ (mustQuery(document, '#btn-toggle-label'));
  const desc = /** @type {HTMLElement} */ (mustQuery(document, '#status-desc'));
  btn.classList.remove('btn-outline-warning', 'btn-outline-success');
  if (status === 'ACTIVE') {
    label.textContent = 'このテナントを Suspend する';
    btn.classList.add('btn-outline-warning');
    desc.textContent =
      'Suspend するとこのテナントのユーザーは業務 API へアクセスできなくなります。';
    btn.disabled = false;
  } else if (status === 'SUSPENDED') {
    label.textContent = 'このテナントを Reactivate する';
    btn.classList.add('btn-outline-success');
    desc.textContent = 'Reactivate するとこのテナントのユーザーは再び業務 API を利用できます。';
    btn.disabled = false;
  } else {
    label.textContent = '操作不可';
    btn.classList.add('btn-outline-secondary');
    desc.textContent = 'このテナントは状態を切り替えできません。';
    btn.disabled = true;
  }
}

/**
 * テナント詳細を画面へ反映する。
 * @param {Tenant} t
 */
function render(t) {
  detailTenant = t;
  /** @type {HTMLElement} */ (mustQuery(document, '#page-title')).textContent = t.name;
  /** @type {HTMLElement} */ (mustQuery(document, '#d-id')).textContent = String(t.id);
  /** @type {HTMLElement} */ (mustQuery(document, '#d-code')).textContent = t.code;
  /** @type {HTMLElement} */ (mustQuery(document, '#d-plan')).textContent = t.plan;
  /** @type {HTMLElement} */ (mustQuery(document, '#d-user-count')).textContent =
    t.userCount.toLocaleString('ja-JP');
  /** @type {HTMLElement} */ (mustQuery(document, '#d-task-count')).textContent =
    t.taskCount.toLocaleString('ja-JP');
  /** @type {HTMLElement} */ (mustQuery(document, '#d-created')).textContent = formatDateTime(
    t.createdAt,
  );
  /** @type {HTMLElement} */ (mustQuery(document, '#d-updated')).textContent = formatDateTime(
    t.updatedAt,
  );

  const badge = /** @type {HTMLElement} */ (mustQuery(document, '#d-status'));
  const [label, cls] = statusBadge(t.status);
  badge.className = `badge ${cls}`;
  badge.textContent = label;

  /** @type {HTMLInputElement} */ (mustQuery(document, '#name-input')).value = t.name;
  renderStatusControl(t.status);

  /** @type {HTMLElement} */ (mustQuery(document, '#loading')).classList.add('d-none');
  /** @type {HTMLElement} */ (mustQuery(document, '#detail')).classList.remove('d-none');
}

/** テナント名編集フォームの送信処理を登録する。 */
function wireNameForm() {
  const form = /** @type {HTMLFormElement} */ (mustQuery(document, '#name-form'));
  const input = /** @type {HTMLInputElement} */ (mustQuery(document, '#name-input'));
  const btn = /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-save-name'));

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = input.value.trim();
    if (!name) {
      showFeedback('#name-feedback', 'テナント名を入力してください。', false);
      return;
    }
    btn.disabled = true;
    try {
      const updated = await Api.updateTenant(detailTenantId, name);
      render(updated);
      showFeedback('#name-feedback', '保存しました。', true);
    } catch (err) {
      const ex = /** @type {{ status?: number, message?: string }} */ (err);
      showFeedback(
        '#name-feedback',
        ex.status === 403
          ? '変更する権限がありません。'
          : ex.status === 404
            ? 'このテナントは存在しません。'
            : `保存に失敗しました: ${ex.message ?? '不明なエラー'}`,
        false,
      );
    } finally {
      btn.disabled = false;
    }
  });
}

/** 状態切替ボタンの処理を登録する。 */
function wireStatusToggle() {
  const btn = /** @type {HTMLButtonElement} */ (mustQuery(document, '#btn-toggle-status'));
  btn.addEventListener('click', async () => {
    if (!detailTenant) return;
    const next = detailTenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    const verb = next === 'SUSPENDED' ? 'Suspend' : 'Reactivate';
    const ok = await /** @type {AppConfirmDialogElement} */ (
      mustQuery(document, '#confirm-dialog')
    ).open({
      title: `テナントを ${verb}`,
      body: `テナント「${detailTenant.name}」を ${verb} しますか?`,
      confirmLabel: verb,
      confirmVariant: next === 'SUSPENDED' ? 'danger' : 'primary',
    });
    if (!ok) return;
    btn.disabled = true;
    try {
      const updated = await Api.updateTenantStatus(detailTenantId, next);
      render(updated);
      showFeedback('#status-feedback', `${verb} しました。`, true);
    } catch (err) {
      const ex = /** @type {{ status?: number, message?: string }} */ (err);
      showFeedback(
        '#status-feedback',
        ex.status === 403
          ? '変更する権限がありません。'
          : ex.status === 404
            ? 'このテナントは存在しません。'
            : `状態切替に失敗しました: ${ex.message ?? '不明なエラー'}`,
        false,
      );
      btn.disabled = false;
    }
  });
}

async function main() {
  const authenticated = await Auth.init();
  if (!authenticated) {
    return;
  }

  // 非 APP_ADMIN にはシェルを一切描画せず、自分のホーム(個人ダッシュボード)へ即時退避する。
  // 静的 SPA のためファイル存在自体は隠せないが、ログイン中ユーザーへの casual disclosure を防ぐ
  // (#812, NIST AC-4)。認可確定後にゲート(admin-gated)を解除してシェルを表示する。
  if (!Auth.isAppAdmin()) {
    window.location.replace('dashboard.html');
    return;
  }
  document.body.classList.remove('admin-gated');

  const user = Auth.getUser();
  const displayName = user?.name || user?.preferred_username || '';
  /** @type {HTMLElement} */ (mustQuery(document, '#nav-username')).textContent = displayName;
  /** @type {HTMLElement} */ (mustQuery(document, '#user-avatar')).textContent =
    displayName.slice(0, 1) || '?';

  if (!Number.isInteger(detailTenantId) || detailTenantId <= 0) {
    showError('テナント ID が指定されていません。');
    return;
  }

  // プラットフォーム API はテナント非依存。残存する X-Tenant-Id を送らないようクリアする。
  Api.setTenantId(null);

  wireNameForm();
  wireStatusToggle();

  try {
    const tenant = await Api.getTenant(detailTenantId);
    render(tenant);
  } catch (err) {
    const e = /** @type {{ status?: number, message?: string }} */ (err);
    showError(
      e.status === 404
        ? '指定されたテナントは存在しません。'
        : e.status === 403
          ? 'このページは SaaS 運営者(APP_ADMIN)のみ利用できます。'
          : `テナント情報の取得に失敗しました: ${e.message ?? '不明なエラー'}`,
    );
  }
}

main();
