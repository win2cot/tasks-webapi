// <app-pager> — pagination bar for the task table.
// Method: update({ currentPage, totalPages, totalElements, pageSize })
// Fires: page-change { page } (bubbling)
const _pagerTpl = document.createElement('template');
_pagerTpl.innerHTML = `
<div class="pager" role="navigation" aria-label="ページング">
  <span class="pager-info">0 件</span>
  <div class="ms-auto d-flex align-items-center gap-1">
    <button class="btn btn-sm btn-outline-secondary py-0 px-2 btn-prev"
      disabled aria-label="前のページ">
      <i class="bi bi-chevron-left" aria-hidden="true"></i> 前へ
    </button>
    <span class="pager-pages px-2" aria-live="polite">1 / 1</span>
    <button class="btn btn-sm btn-outline-secondary py-0 px-2 btn-next"
      disabled aria-label="次のページ">
      次へ <i class="bi bi-chevron-right" aria-hidden="true"></i>
    </button>
    <span class="ms-2 text-muted pager-size">50 件 / ページ</span>
  </div>
</div>`;

class AppPager extends HTMLElement {
  connectedCallback() {
    this.replaceChildren(_pagerTpl.content.cloneNode(true));
    this.querySelector('.btn-prev').addEventListener('click', () => {
      this.dispatchEvent(new CustomEvent('page-change', {
        bubbles: true,
        detail: { page: this.#currentPage - 1 },
      }));
    });
    this.querySelector('.btn-next').addEventListener('click', () => {
      this.dispatchEvent(new CustomEvent('page-change', {
        bubbles: true,
        detail: { page: this.#currentPage + 1 },
      }));
    });
  }

  #currentPage = 0;

  update({ currentPage, totalPages, totalElements, pageSize }) {
    this.#currentPage = currentPage;
    const start = totalElements > 0 ? currentPage * pageSize + 1 : 0;
    const end   = Math.min((currentPage + 1) * pageSize, totalElements);

    this.querySelector('.pager-info').textContent =
      totalElements > 0 ? `${totalElements} 件中 ${start}–${end} 件を表示` : '0 件';
    this.querySelector('.pager-pages').textContent =
      `${currentPage + 1} / ${Math.max(totalPages, 1)}`;
    this.querySelector('.btn-prev').disabled = currentPage <= 0;
    this.querySelector('.btn-next').disabled = currentPage >= totalPages - 1;
    this.querySelector('.pager-size').textContent = `${pageSize} 件 / ページ`;
  }
}
customElements.define('app-pager', AppPager);
