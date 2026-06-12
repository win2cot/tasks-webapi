// <app-desc-popover> — singleton page-level description edit popover.
// Methods: open(taskId, triggerEl, description), close()
// Fires: desc-commit { taskId, value }, desc-cancel
const _descPopTpl = document.createElement('template');
_descPopTpl.innerHTML = `
<div class="desc-overlay d-none" role="dialog" aria-label="説明を編集" aria-modal="false">
  <label class="form-label small fw-bold mb-1" for="desc-popover-ta">説明</label>
  <textarea id="desc-popover-ta" class="form-control form-control-sm" rows="4"
    maxlength="2000" placeholder="説明（最大2000文字）"></textarea>
  <div class="d-flex gap-2 mt-2">
    <button class="btn btn-sm btn-primary btn-save">保存</button>
    <button class="btn btn-sm btn-light btn-cancel">取消</button>
  </div>
</div>`;

class AppDescPopover extends HTMLElement {
  /** @type {number | null} */
  #taskId = null;
  /** @type {HTMLElement | null} */
  #overlay = null;
  /** @type {HTMLTextAreaElement | null} */
  #ta = null;

  #handleMousedown = this.#onDocMousedown.bind(this);
  #handleKeydown = this.#onTaKeydown.bind(this);

  connectedCallback() {
    this.replaceChildren(_descPopTpl.content.cloneNode(true));
    this.#overlay = /** @type {HTMLElement} */ (this.firstElementChild);
    this.#ta = /** @type {HTMLTextAreaElement} */ (this.#overlay.querySelector('textarea'));
    if (!this.#overlay || !this.#ta) return;

    /** @type {HTMLButtonElement} */ (this.#overlay.querySelector('.btn-save')).addEventListener(
      'click',
      () => this.#commit(),
    );
    /** @type {HTMLButtonElement} */ (this.#overlay.querySelector('.btn-cancel')).addEventListener(
      'click',
      () => this.close(),
    );
    this.#ta.addEventListener('keydown', this.#handleKeydown);
    document.addEventListener('mousedown', this.#handleMousedown);
  }

  disconnectedCallback() {
    document.removeEventListener('mousedown', this.#handleMousedown);
  }

  /**
   * @param {number} taskId
   * @param {Element} triggerEl
   * @param {string | null} description
   */
  open(taskId, triggerEl, description) {
    this.#taskId = taskId;
    if (!this.#ta || !this.#overlay) return;
    this.#ta.value = description || '';

    const rect = triggerEl.getBoundingClientRect();
    let top = rect.bottom + 8;
    let left = rect.left;
    if (left + 310 > window.innerWidth) left = Math.max(8, window.innerWidth - 318);
    if (top + 190 > window.innerHeight) top = rect.top - 198;

    this.#overlay.style.top = `${top}px`;
    this.#overlay.style.left = `${left}px`;
    this.#overlay.classList.remove('d-none');
    this.#ta.focus();
  }

  close() {
    if (!this.#overlay) return;
    this.#overlay.classList.add('d-none');
    const taskId = this.#taskId;
    this.#taskId = null;
    this.dispatchEvent(new CustomEvent('desc-cancel', { bubbles: true, detail: { taskId } }));
  }

  get isOpen() {
    return this.#overlay && !this.#overlay.classList.contains('d-none');
  }

  #commit() {
    const taskId = this.#taskId;
    if (taskId === null || !this.#ta || !this.#overlay) return;
    const value = this.#ta.value.trim() || null;
    this.#overlay.classList.add('d-none');
    this.#taskId = null;
    this.dispatchEvent(
      new CustomEvent('desc-commit', {
        bubbles: true,
        detail: { taskId, value },
      }),
    );
  }

  /** @param {MouseEvent} e */
  #onDocMousedown(e) {
    if (this.isOpen && !this.#overlay?.contains(/** @type {Node} */ (e.target))) this.close();
  }

  /** @param {KeyboardEvent} e */
  #onTaKeydown(e) {
    if (e.key === 'Escape') {
      e.stopPropagation();
      this.close();
    }
  }
}
customElements.define('app-desc-popover', AppDescPopover);
