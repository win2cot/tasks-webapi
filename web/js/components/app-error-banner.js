// <app-error-banner> — dismissible error banner.
// Methods: show(message), hide()
// Fires: error-retry (bubbling)
const _errTpl = document.createElement('template');
_errTpl.innerHTML = `
<div class="alert alert-danger d-none d-flex align-items-center gap-2 mb-3"
  role="alert" aria-live="assertive">
  <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
  <span class="err-message"></span>
  <button class="btn btn-sm btn-outline-danger ms-auto btn-retry">
    <i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i>再試行
  </button>
</div>`;

class AppErrorBanner extends HTMLElement {
  /** @type {Element | null} */
  #alert = null;

  connectedCallback() {
    this.replaceChildren(_errTpl.content.cloneNode(true));
    this.#alert = this.firstElementChild;
    if (!this.#alert) return;
    /** @type {HTMLButtonElement} */ (this.#alert.querySelector('.btn-retry')).addEventListener(
      'click',
      () => {
        this.dispatchEvent(new CustomEvent('error-retry', { bubbles: true }));
      },
    );
  }

  /** @param {string} message */
  show(message) {
    if (!this.#alert) return;
    /** @type {HTMLElement} */ (this.#alert.querySelector('.err-message')).textContent = message;
    this.#alert.classList.remove('d-none');
  }

  hide() {
    this.#alert?.classList.add('d-none');
  }
}
customElements.define('app-error-banner', AppErrorBanner);
