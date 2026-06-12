// @ts-check
// <app-priority-badge priority="MEDIUM" [editable] [task-id="1"]>
const _priBadgeTpl = document.createElement('template');
_priBadgeTpl.innerHTML = '<span class="pri-badge" title="編集権限がありません"></span>';

const _priSelTpl = document.createElement('template');
_priSelTpl.innerHTML =
  '<select class="inline-sel" aria-label="優先度" data-action="priority-change"></select>';

class AppPriorityBadge extends HTMLElement {
  static get observedAttributes() {
    return ['priority', 'editable', 'task-id'];
  }

  connectedCallback() {
    this.#render();
  }
  attributeChangedCallback() {
    if (this.isConnected) this.#render();
  }

  #render() {
    const priority = this.getAttribute('priority') || 'MEDIUM';
    const editable = this.hasAttribute('editable');
    const taskId = this.getAttribute('task-id') || '';
    const [label, cls] = TaskMeta.priority(priority);

    if (editable) {
      const sel = /** @type {HTMLSelectElement} */ (
        /** @type {DocumentFragment} */ (_priSelTpl.content.cloneNode(true)).firstElementChild
      );
      sel.className = `inline-sel pri-sel-${priority}`;
      sel.dataset.taskId = taskId;
      TaskMeta.PRIORITY_OPTIONS.forEach(({ v, l }) => {
        const opt = document.createElement('option');
        opt.value = v;
        opt.textContent = l;
        opt.selected = v === priority;
        sel.appendChild(opt);
      });
      this.replaceChildren(sel);
    } else {
      const span = /** @type {HTMLElement} */ (
        /** @type {DocumentFragment} */ (_priBadgeTpl.content.cloneNode(true)).firstElementChild
      );
      span.className = `pri-badge ${cls}`;
      span.textContent = label;
      this.replaceChildren(span);
    }
  }
}
customElements.define('app-priority-badge', AppPriorityBadge);
