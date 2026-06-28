// <app-confirm-dialog id="confirm-dialog"></app-confirm-dialog>
// 破壊的操作(削除・状態変更等)の確認に使う共通 Bootstrap モーダル。
// ブラウザ標準 window.confirm の代替で、アプリ UI と見た目・文言を統一する(#811)。
//
// 使い方:
//   const ok = await /** @type {AppConfirmDialogElement} */ (mustQuery(document, '#confirm-dialog'))
//     .open({ title: 'メンバーを削除', body: '…削除しますか?', confirmLabel: '削除', confirmVariant: 'danger' });
//   if (!ok) return;
//
// 確認ボタンで true、キャンセル / ESC / 背景クリック / × で false に解決する。
class AppConfirmDialog extends HTMLElement {
  /** @type {BootstrapModal | null} */
  #modal = null;
  /** @type {((ok: boolean) => void) | null} */
  #resolve = null;
  #confirmed = false;

  connectedCallback() {
    if (this.#modal) return;
    const titleId = `${this.id || 'app-confirm'}-title`;
    this.innerHTML = `
      <div class="modal fade" tabindex="-1" aria-labelledby="${titleId}" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <h2 id="${titleId}" class="modal-title h5"></h2>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
            </div>
            <div class="modal-body"></div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" data-role="cancel" data-bs-dismiss="modal"></button>
              <button type="button" class="btn" data-role="confirm"></button>
            </div>
          </div>
        </div>
      </div>`;

    const modalEl = /** @type {HTMLElement} */ (mustQuery(this, '.modal'));
    this.#modal = bootstrap.Modal.getOrCreateInstance(modalEl);

    /** @type {HTMLButtonElement} */ (mustQuery(this, '[data-role="confirm"]')).addEventListener(
      'click',
      () => {
        this.#confirmed = true;
        this.#modal?.hide();
      },
    );

    // ESC / 背景クリック / キャンセル / × はいずれも hidden を発火 → confirmed=false のまま解決。
    modalEl.addEventListener('hidden.bs.modal', () => {
      const resolve = this.#resolve;
      this.#resolve = null;
      if (resolve) resolve(this.#confirmed);
    });
  }

  /**
   * 確認モーダルを開く。
   * @param {{ title: string, body: string, confirmLabel?: string, cancelLabel?: string, confirmVariant?: string }} opts
   * @returns {Promise<boolean>}
   */
  open({
    title,
    body,
    confirmLabel = 'OK',
    cancelLabel = 'キャンセル',
    confirmVariant = 'primary',
  }) {
    /** @type {HTMLElement} */ (mustQuery(this, '.modal-title')).textContent = title;
    /** @type {HTMLElement} */ (mustQuery(this, '.modal-body')).textContent = body;
    /** @type {HTMLElement} */ (mustQuery(this, '[data-role="cancel"]')).textContent = cancelLabel;
    const confirmBtn = /** @type {HTMLButtonElement} */ (mustQuery(this, '[data-role="confirm"]'));
    confirmBtn.textContent = confirmLabel;
    confirmBtn.className = `btn btn-${confirmVariant}`;

    this.#confirmed = false;
    return new Promise((resolve) => {
      this.#resolve = resolve;
      this.#modal?.show();
    });
  }
}
customElements.define('app-confirm-dialog', AppConfirmDialog);
