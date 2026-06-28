// <app-profile-dialog id="profile-dialog"></app-profile-dialog>
// ヘッダーのアバター(#user-avatar)クリックで開く参照専用プロフィールダイアログ(S-09 / #814)。
// 氏名・メール等は GET /api/users/me から取得。編集は不可(属性編集の担い手は Tenant Admin /
// S-08 で別途検討)。接続時に #user-avatar へ自己結線するため、各ページの JS 変更は不要。
class AppProfileDialog extends HTMLElement {
  /** @type {BootstrapModal | null} */
  #modal = null;

  connectedCallback() {
    if (this.#modal) return;
    const titleId = `${this.id || 'app-profile'}-title`;
    this.innerHTML = `
      <div class="modal fade" tabindex="-1" aria-labelledby="${titleId}" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <h2 id="${titleId}" class="modal-title h5">
                <i class="bi bi-person-circle me-2" aria-hidden="true"></i>プロフィール
              </h2>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
            </div>
            <div class="modal-body">
              <div data-role="loading" class="text-center py-3">
                <div class="spinner-border spinner-border-sm text-primary" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
              </div>
              <div data-role="error" class="alert alert-danger d-none" role="alert"></div>
              <dl data-role="fields" class="row mb-0 d-none">
                <dt class="col-4 text-muted fw-normal">氏名</dt>
                <dd class="col-8" data-field="fullName">—</dd>
                <dt class="col-4 text-muted fw-normal">氏名(カナ)</dt>
                <dd class="col-8" data-field="fullNameKana">—</dd>
                <dt class="col-4 text-muted fw-normal">メール</dt>
                <dd class="col-8" data-field="email">—</dd>
                <dt class="col-4 text-muted fw-normal">部署</dt>
                <dd class="col-8" data-field="departmentName">—</dd>
              </dl>
              <p class="text-muted small mb-0 mt-3">
                氏名・部署などの変更が必要な場合はテナント管理者にご連絡ください。
              </p>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">閉じる</button>
            </div>
          </div>
        </div>
      </div>`;

    this.#modal = bootstrap.Modal.getOrCreateInstance(mustQuery(this, '.modal'));

    // ヘッダーのアバターをトリガーに自己結線する。
    const avatar = document.getElementById('user-avatar');
    if (avatar) {
      avatar.addEventListener('click', () => this.open());
    }
  }

  /** ダイアログを開き、プロフィールを取得して表示する。 */
  async open() {
    const loading = /** @type {HTMLElement} */ (mustQuery(this, '[data-role="loading"]'));
    const errorEl = /** @type {HTMLElement} */ (mustQuery(this, '[data-role="error"]'));
    const fields = /** @type {HTMLElement} */ (mustQuery(this, '[data-role="fields"]'));
    loading.classList.remove('d-none');
    errorEl.classList.add('d-none');
    fields.classList.add('d-none');
    this.#modal?.show();

    try {
      const profile = await Api.getMyProfile();
      this.#setField('fullName', profile.fullName);
      this.#setField('fullNameKana', profile.fullNameKana);
      this.#setField('email', profile.email);
      this.#setField('departmentName', profile.departmentName || '—');
      loading.classList.add('d-none');
      fields.classList.remove('d-none');
    } catch (err) {
      loading.classList.add('d-none');
      errorEl.textContent = `プロフィールの取得に失敗しました: ${/** @type {any} */ (err).message}`;
      errorEl.classList.remove('d-none');
    }
  }

  /**
   * @param {string} field
   * @param {string} value
   */
  #setField(field, value) {
    /** @type {HTMLElement} */ (mustQuery(this, `[data-field="${field}"]`)).textContent = value;
  }
}
customElements.define('app-profile-dialog', AppProfileDialog);
