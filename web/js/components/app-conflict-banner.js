// <app-conflict-banner> — 412-conflict notification banner.
// Methods: show(), hide()
// Fires: conflict-reload, conflict-close (both bubbling)
const _conflictTpl = document.createElement('template');
_conflictTpl.innerHTML = `
<div class="alert alert-warning d-none d-flex align-items-center gap-2 mb-3"
  role="alert" aria-live="polite">
  <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
  <span>別のユーザーによってタスクが更新されました。最新のデータに更新してください。</span>
  <button class="btn btn-sm btn-outline-warning ms-auto btn-reload">
    <i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i>再読み込み
  </button>
  <button class="btn-close ms-1 btn-dismiss" aria-label="閉じる"></button>
</div>`;

class AppConflictBanner extends HTMLElement {
  #alert = null;

  connectedCallback() {
    this.replaceChildren(_conflictTpl.content.cloneNode(true));
    this.#alert = this.firstElementChild;
    this.#alert.querySelector('.btn-reload').addEventListener('click', () => {
      this.dispatchEvent(new CustomEvent('conflict-reload', { bubbles: true }));
    });
    this.#alert.querySelector('.btn-dismiss').addEventListener('click', () => {
      this.hide();
      this.dispatchEvent(new CustomEvent('conflict-close', { bubbles: true }));
    });
  }

  show() { this.#alert?.classList.remove('d-none'); }
  hide() { this.#alert?.classList.add('d-none'); }
}
customElements.define('app-conflict-banner', AppConflictBanner);
