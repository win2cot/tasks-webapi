// <app-status-badge status="NOT_STARTED" [editable] [task-id="1"]>
// editable=true → inline select; false → read-only badge
const _stBadgeTpl = document.createElement('template');
_stBadgeTpl.innerHTML = '<span class="st-badge" title="編集権限がありません"></span>';

const _stSelTpl = document.createElement('template');
_stSelTpl.innerHTML =
  '<select class="inline-sel" aria-label="ステータス" data-action="status-change"></select>';

class AppStatusBadge extends HTMLElement {
  static get observedAttributes() {
    return ['status', 'editable', 'task-id'];
  }

  connectedCallback() {
    this.#render();
  }
  attributeChangedCallback() {
    if (this.isConnected) this.#render();
  }

  #render() {
    const status = this.getAttribute('status') || 'NOT_STARTED';
    const editable = this.hasAttribute('editable');
    const taskId = this.getAttribute('task-id') || '';
    const [label, cls] = TaskMeta.status(status);

    if (editable) {
      const sel = _stSelTpl.content.cloneNode(true).firstElementChild;
      sel.className = `inline-sel st-sel-${status}`;
      sel.dataset.taskId = taskId;
      TaskMeta.STATUS_OPTIONS.forEach(({ v, l }) => {
        const opt = document.createElement('option');
        opt.value = v;
        opt.textContent = l;
        opt.selected = v === status;
        sel.appendChild(opt);
      });
      this.replaceChildren(sel);
    } else {
      const span = _stBadgeTpl.content.cloneNode(true).firstElementChild;
      span.className = `st-badge ${cls}`;
      span.textContent = label;
      this.replaceChildren(span);
    }
  }
}
customElements.define('app-status-badge', AppStatusBadge);
