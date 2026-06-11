// <app-visibility-badge visibility="TENANT">
const _visBadgeTpl = document.createElement('template');
_visBadgeTpl.innerHTML =
  '<span class="vis-badge"><i aria-hidden="true"></i></span>';

class AppVisibilityBadge extends HTMLElement {
  static get observedAttributes() { return ['visibility']; }

  connectedCallback() { this.#render(); }
  attributeChangedCallback() { if (this.isConnected) this.#render(); }

  #render() {
    const vis = this.getAttribute('visibility') || 'TENANT';
    const [label, cls, icon] = TaskMeta.visibility(vis);
    const span = _visBadgeTpl.content.cloneNode(true).firstElementChild;
    span.className = `vis-badge ${cls}`;
    span.querySelector('i').className = `bi ${icon}`;
    span.appendChild(document.createTextNode(label));
    this.replaceChildren(span);
  }
}
customElements.define('app-visibility-badge', AppVisibilityBadge);
